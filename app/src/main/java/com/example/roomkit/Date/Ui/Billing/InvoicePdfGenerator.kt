package com.example.roomkit.Date.Billing

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.DecimalFormat

object InvoicePdfGenerator {

    // Standard A4 dimensions at 72 DPI
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842

    private const val MARGIN_LEFT = 40f
    private const val MARGIN_RIGHT = 555f
    private const val MARGIN_BOTTOM = 800f

    private val khrFormat = DecimalFormat("#,###")

    fun generateAndSavePdf(context: Context, model: InvoicePdfModel): Uri {
        require(model.currency == "KHR") { "Unsupported invoice currency." }
        val document = PdfDocument()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#111827") }
        val grayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#6B7280") }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E5E7EB")
            strokeWidth = 1f
        }

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas
        var currentY = 50f

        // Helper for multi-page break
        fun checkAndCreateNewPage(neededHeight: Float) {
            if (currentY + neededHeight > MARGIN_BOTTOM) {
                // Footer for current page
                grayPaint.textSize = 9f
                canvas.drawText("Page $pageNumber", MARGIN_RIGHT - 30f, 820f, grayPaint)

                document.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas
                currentY = 50f
            }
        }

        // Wrap by measured glyph width, including long unbroken references; paginate each line.
        fun drawWrapped(text: String, x: Float = MARGIN_LEFT, width: Float = MARGIN_RIGHT - MARGIN_LEFT) {
            grayPaint.textSize = 9f
            val clean = text.replace('\n', ' ').replace('\r', ' ')
            var line = ""
            for (cp in clean.codePoints().toArray()) {
                val next = String(Character.toChars(cp))
                if (line.isNotEmpty() && grayPaint.measureText(line + next) > width) {
                    checkAndCreateNewPage(14f)
                    canvas.drawText(line, x, currentY, grayPaint)
                    currentY += 13f
                    line = ""
                }
                line += next
            }
            if (line.isNotEmpty()) {
                checkAndCreateNewPage(14f)
                canvas.drawText(line, x, currentY, grayPaint)
                currentY += 13f
            }
        }

        // --- 1. HEADER SECTION ---
        textPaint.textSize = 22f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("ROOMKIT", MARGIN_LEFT, currentY, textPaint)

        // Status Badge (Right aligned)
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val statusText = model.status.uppercase()
        val badgeColor = when (statusText) {
            "PAID" -> Color.parseColor("#059669")
            "OVERDUE" -> Color.parseColor("#DC2626")
            "PARTIALLY_PAID" -> Color.parseColor("#1D4ED8")
            "VOID" -> Color.parseColor("#6B7280")
            else -> Color.parseColor("#D97706")
        }
        paint.color = badgeColor
        val statusWidth = paint.measureText(statusText)
        canvas.drawText(statusText, MARGIN_RIGHT - statusWidth, currentY - 5f, paint)

        currentY += 16f
        grayPaint.textSize = 11f
        canvas.drawText("Monthly Rental Invoice · ${model.currency}", MARGIN_LEFT, currentY, grayPaint)

        currentY += 20f
        canvas.drawLine(MARGIN_LEFT, currentY, MARGIN_RIGHT, currentY, linePaint)
        currentY += 22f

        // --- 2. METADATA & PROPERTY GRID ---
        textPaint.textSize = 9f
        textPaint.typeface = Typeface.DEFAULT
        grayPaint.textSize = 9f

        // Column 1: Invoice Meta
        canvas.drawText("Invoice Number:", MARGIN_LEFT, currentY, grayPaint)
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(model.invoiceNumberFormatted, MARGIN_LEFT + 75f, currentY, textPaint)

        // Column 2: Tenant Info
        canvas.drawText("Tenant:", 320f, currentY, grayPaint)
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(model.tenantName, 370f, currentY, textPaint)
        currentY += 15f

        textPaint.typeface = Typeface.DEFAULT
        canvas.drawText("Billing Period:", MARGIN_LEFT, currentY, grayPaint)
        canvas.drawText(model.billingPeriodFormatted, MARGIN_LEFT + 75f, currentY, textPaint)

        canvas.drawText("Phone:", 320f, currentY, grayPaint)
        canvas.drawText(model.tenantPhone, 370f, currentY, textPaint)
        currentY += 15f

        canvas.drawText("Issue Date:", MARGIN_LEFT, currentY, grayPaint)
        canvas.drawText(model.issueDateFormatted, MARGIN_LEFT + 75f, currentY, textPaint)

        canvas.drawText("Property:", 320f, currentY, grayPaint)
        canvas.drawText(model.buildingName, 370f, currentY, textPaint)
        currentY += 15f

        canvas.drawText("Due Date:", MARGIN_LEFT, currentY, grayPaint)
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(model.dueDateFormatted, MARGIN_LEFT + 75f, currentY, textPaint)

        textPaint.typeface = Typeface.DEFAULT
        canvas.drawText("Room:", 320f, currentY, grayPaint)
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Room ${model.roomNumber}", 370f, currentY, textPaint)

        currentY += 25f
        canvas.drawLine(MARGIN_LEFT, currentY, MARGIN_RIGHT, currentY, linePaint)
        currentY += 20f

        // --- 3. BILLING TABLE HEADER ---
        paint.color = Color.parseColor("#374151")
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        model.billingPeriodDates?.let { period ->
            checkAndCreateNewPage(28f)
            grayPaint.textSize = 9f
            canvas.drawText("Service period: $period", MARGIN_LEFT, currentY, grayPaint)
            currentY += 24f
        }
        canvas.drawText("DESCRIPTION", MARGIN_LEFT, currentY, paint)
        val amountHeader = "AMOUNT"
        canvas.drawText(amountHeader, MARGIN_RIGHT - paint.measureText(amountHeader), currentY, paint)

        currentY += 10f
        canvas.drawLine(MARGIN_LEFT, currentY, MARGIN_RIGHT, currentY, linePaint)
        currentY += 18f

        fun drawTableRow(label: String, sublabel: String?, amountStr: String, isBold: Boolean = false) {
            checkAndCreateNewPage(30f)
            textPaint.textSize = 10f
            textPaint.typeface = if (isBold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
            canvas.drawText(label, MARGIN_LEFT, currentY, textPaint)

            val amtWidth = textPaint.measureText(amountStr)
            canvas.drawText(amountStr, MARGIN_RIGHT - amtWidth, currentY, textPaint)

            if (!sublabel.isNullOrBlank()) {
                currentY += 12f
                grayPaint.textSize = 8.5f
                canvas.drawText(sublabel, MARGIN_LEFT, currentY, grayPaint)
            }
            currentY += 18f
        }

        // Read the stored item descriptions, amounts and meter-rate snapshots.
        val items = model.items.sortedBy { it.sortOrder }
        items.filter { it.itemCode != "DISCOUNT" }.forEach { item ->
            if (item.amountKhr != 0L || item.itemCode in setOf("RENT", "ELECTRICITY", "WATER")) {
                val usage = if (item.unit != "FIXED" && item.quantity != null && item.unitPriceKhr != null)
                    "${item.quantity} ${item.unit} × ${khrFormat.format(item.unitPriceKhr)} ៛/${item.unit}" else null
                drawTableRow(item.description, usage, CurrencyFormatter.formatKhr(item.amountKhr))
            }
        }
        val discount = items.single { it.itemCode == "DISCOUNT" }
        currentY += 6f
        canvas.drawLine(MARGIN_LEFT, currentY, MARGIN_RIGHT, currentY, linePaint)
        currentY += 16f

        checkAndCreateNewPage(if (model.discountKhr != 0L) 155f else 130f)
        // --- 4. UTILITY SUMMARY & TOTALS ---
        drawTableRow("Utility Subtotal", null, CurrencyFormatter.formatKhr(InvoiceItems.utilitySubtotal(items)))
        if (discount.amountKhr != 0L) drawTableRow(discount.description, null, "−" + CurrencyFormatter.formatKhr(Math.negateExact(discount.amountKhr)))

        currentY += 6f
        canvas.drawLine(MARGIN_LEFT, currentY, MARGIN_RIGHT, currentY, linePaint)
        currentY += 20f

        // INVOICE TOTAL (BOLD)
        textPaint.textSize = 13f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("INVOICE TOTAL", MARGIN_LEFT, currentY, textPaint)
        val totalFormatted = CurrencyFormatter.formatKhr(model.totalKhr)
        canvas.drawText(totalFormatted, MARGIN_RIGHT - textPaint.measureText(totalFormatted), currentY, textPaint)

        currentY += 16f
        textPaint.textSize = 10f
        textPaint.typeface = Typeface.DEFAULT
        canvas.drawText("Total Paid", MARGIN_LEFT, currentY, textPaint)
        val paidFormatted = CurrencyFormatter.formatKhr(model.amountPaidKhr)
        canvas.drawText(paidFormatted, MARGIN_RIGHT - textPaint.measureText(paidFormatted), currentY, textPaint)

        currentY += 16f
        textPaint.textSize = 13f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("CURRENT INVOICE BALANCE", MARGIN_LEFT, currentY, textPaint)
        val balanceFormatted = CurrencyFormatter.formatKhr(model.balanceKhr)
        canvas.drawText(balanceFormatted, MARGIN_RIGHT - textPaint.measureText(balanceFormatted), currentY, textPaint)

        currentY += 20f
        canvas.drawLine(MARGIN_LEFT, currentY, MARGIN_RIGHT, currentY, linePaint)
        currentY += 24f

        model.discountReason?.takeIf { it.isNotBlank() }?.let {
            drawWrapped("Discount reason: $it")
            currentY += 8f
        }

        // Live account information is separate from the invoice item table and total.
        checkAndCreateNewPage(150f)
        textPaint.textSize = 11f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("TENANCY ACCOUNT BALANCE", MARGIN_LEFT, currentY, textPaint)
        currentY += 18f
        fun accountRow(label: String, amount: Long, bold: Boolean = false) {
            textPaint.textSize = 10f
            textPaint.typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
            canvas.drawText(label, MARGIN_LEFT, currentY, textPaint)
            val value = CurrencyFormatter.formatKhr(amount)
            canvas.drawText(value, MARGIN_RIGHT - textPaint.measureText(value), currentY, textPaint)
            currentY += 17f
        }
        accountRow("Previous unpaid balance", model.accountBalance.previousUnpaidKhr)
        accountRow("Current invoice balance", model.accountBalance.currentInvoiceBalanceKhr)
        if (model.accountBalance.laterUnpaidKhr > 0L) accountRow("Later invoice balances", model.accountBalance.laterUnpaidKhr)
        accountRow("Total account balance", model.accountBalance.totalAccountBalanceKhr, true)
        grayPaint.textSize = 8.5f
        canvas.drawText("Same tenancy; balances at export. Previous debt is not charged again.", MARGIN_LEFT, currentY, grayPaint)
        currentY += 14f
        canvas.drawText("Payments and KHQR from this invoice cover this invoice only.", MARGIN_LEFT, currentY, grayPaint)
        currentY += 24f

        // --- 5. PAYMENT HISTORY SECTION ---
        checkAndCreateNewPage(60f)
        textPaint.textSize = 11f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("PAYMENT HISTORY", MARGIN_LEFT, currentY, textPaint)
        currentY += 16f

        if (model.payments.isEmpty()) {
            grayPaint.textSize = 9.5f
            canvas.drawText("No payments recorded yet.", MARGIN_LEFT, currentY, grayPaint)
            currentY += 20f
        } else {
            // Header
            grayPaint.textSize = 8.5f
            canvas.drawText("Date", MARGIN_LEFT, currentY, grayPaint)
            canvas.drawText("Method / Note", MARGIN_LEFT + 100f, currentY, grayPaint)
            val amtHdr = "Amount"
            canvas.drawText(amtHdr, MARGIN_RIGHT - grayPaint.measureText(amtHdr), currentY, grayPaint)

            currentY += 8f
            canvas.drawLine(MARGIN_LEFT, currentY, MARGIN_RIGHT, currentY, linePaint)
            currentY += 14f

            textPaint.textSize = 9.5f
            textPaint.typeface = Typeface.DEFAULT
            for (p in model.payments) {
                checkAndCreateNewPage(20f)
                canvas.drawText(p.paymentDateFormatted, MARGIN_LEFT, currentY, textPaint)
                val methodDesc = p.method
                canvas.drawText(methodDesc, MARGIN_LEFT + 100f, currentY, textPaint)

                val amtStr = CurrencyFormatter.formatKhr(p.amountKhr)
                canvas.drawText(amtStr, MARGIN_RIGHT - textPaint.measureText(amtStr), currentY, textPaint)
                currentY += 15f
                p.referenceNumber?.takeIf { it.isNotBlank() }?.let { drawWrapped("Reference: $it") }
                p.note?.takeIf { it.isNotBlank() }?.let { drawWrapped("Note: $it") }
                currentY += 5f
            }
        }

        // --- 6. PAGE FOOTER ---
        grayPaint.textSize = 9f
        canvas.drawText("Page $pageNumber", MARGIN_RIGHT - 30f, 820f, grayPaint)
        document.finishPage(page)

        // --- 7. WRITE TO STORAGE ---
        val cleanNumber = model.invoiceNumberFormatted.replace("#", "").replace("-", "_").trim()
        val cleanPeriod = model.billingPeriodFormatted.replace(" ", "_").trim()
        val fileName = "RoomKit_Invoice_${cleanNumber}_$cleanPeriod.pdf"

        return savePdfToStorage(context, document, fileName)
    }

    private fun savePdfToStorage(context: Context, document: PdfDocument, fileName: String): Uri {
        var outputStream: OutputStream? = null
        var targetUri: Uri? = null

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/RoomKit")
                }
                val resolver = context.contentResolver
                targetUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    ?: throw IllegalStateException("Could not create MediaStore entry in Downloads/RoomKit")
                outputStream = resolver.openOutputStream(targetUri)
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "RoomKit")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                targetUri = Uri.fromFile(file)
                outputStream = FileOutputStream(file)
            }

            outputStream?.let { document.writeTo(it) }
                ?: throw IllegalStateException("Failed to open output stream for PDF generation")

            return targetUri
        } finally {
            outputStream?.flush()
            outputStream?.close()
            document.close()
        }
    }
}

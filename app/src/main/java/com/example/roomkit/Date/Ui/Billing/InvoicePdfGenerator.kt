@file:Suppress("PackageNaming", "PackageName")

package com.example.roomkit.Date.Billing

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.roomkit.Date.Payment.KhqrImages
import com.example.roomkit.Date.Payment.PaymentSettingsRepository
import java.util.Locale

/** Reference-style bilingual layout, proportionally fitted to portrait A4 or A5. */
object InvoicePdfGenerator {
    private const val MAX_INLINE_PAYMENTS = 3

    fun generateAndSavePdf(
        context: Context,
        model: InvoicePdfModel,
        paper: InvoicePaperSize = InvoicePaperSize.selected(context)
    ): Uri {
        require(model.currency == "KHR") { "Unsupported invoice currency." }
        val status = model.status.trim().uppercase(Locale.ROOT)
        val issued = status in setOf("ISSUED", "UNPAID", "PARTIAL", "PARTIALLY_PAID", "PAID", "OVERDUE")
        val receipt = issued && model.totalKhr > 0 && model.balanceKhr <= 0 && model.amountPaidKhr >= model.totalKhr
        val kind = if (receipt) "Receipt" else "Invoice"
        val prefs = PaymentSettingsRepository(context)
        val payment = if (issued && !receipt && model.balanceKhr > 0 && prefs.includeInPdf()) prefs.load() else null
        val qr = payment?.let { KhqrImages.render(it.payload) }
        val pdf = PdfDocument()
        try {
            fun renderDocument(target: PdfDocument, totalPages: Int): Int {
                val r = Renderer(context, target, paper, model.invoiceNumberFormatted, kind, totalPages)
                r.header(
                    model,
                    receipt,
                    when {
                        status in setOf("VOID", "VOIDED") -> "VOID"
                        status == "DRAFT" -> "DRAFT"
                        !issued -> "STATUS UNKNOWN"
                        receipt -> "PAID"
                        model.amountPaidKhr > 0 -> "PARTIALLY PAID"
                        else -> "UNPAID"
                    }
                )
                r.metadata(model, qr)
                r.table(model)
                r.summary(model, receipt, payment?.receiver, payment?.bank)
                r.notes(qr != null, receipt)

                val overflowPayments = model.payments.drop(MAX_INLINE_PAYMENTS)
                if (overflowPayments.isNotEmpty()) {
                    r.section("ប្រវត្តិការទូទាត់ (បន្ត)", "Payment history (continued)")
                    overflowPayments.forEach { p ->
                        r.paymentEntry(p)
                    }
                }

                model.discountReason?.takeIf { it.isNotBlank() }?.let {
                    r.paragraph("មូលហេតុបញ្ចុះតម្លៃ / Discount reason: $it")
                }
                return r.finish()
            }

            // Measure the identical layout first to print accurate page counts.
            val preview = PdfDocument()
            val pageCount = try {
                renderDocument(preview, 0)
            } finally {
                preview.close()
            }
            renderDocument(pdf, pageCount)

            val values = ContentValues().apply {
                put(
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    "RoomKit_${kind}_${model.invoiceId}_${paper.name}_${System.currentTimeMillis()}.pdf"
                )
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/RoomKit")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Unable to create PDF.")
            try {
                val stream = resolver.openOutputStream(uri) ?: error("Unable to open PDF output.")
                stream.use { pdf.writeTo(it) }
                check(
                    resolver.update(
                        uri,
                        ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                        null,
                        null
                    ) > 0
                ) { "Unable to finish PDF." }
                return uri
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } finally {
            qr?.recycle()
            pdf.close()
        }
    }

    private class Renderer(
        context: Context,
        private val pdf: PdfDocument,
        private val paper: InvoicePaperSize,
        private val number: String,
        private val kind: String,
        private val totalPages: Int
    ) {
        // A-series paper has the same aspect ratio. Use one composition on both sizes.
        private val scale = paper.width / 595f
        private val pageHeight = paper.height / scale
        private val margin = 18f
        private val width = 559f
        private val bottom = pageHeight - 33f

        private val ink = Color.rgb(15, 23, 42)
        private val navy = Color.rgb(28, 46, 63)
        private val orange = Color.rgb(255, 105, 0)
        private val muted = Color.rgb(91, 103, 119)
        private val border = Color.rgb(215, 222, 231)
        private val pale = Color.rgb(235, 241, 248)
        private val warm = Color.rgb(255, 249, 242)

        private val regular = runCatching {
            Typeface.createFromAsset(context.assets, "fonts/NotoSansKhmer-Regular.ttf")
        }.getOrElse { Typeface.create("sans-serif", Typeface.NORMAL) }

        private val bold = runCatching {
            Typeface.createFromAsset(context.assets, "fonts/NotoSansKhmer-Bold.ttf")
        }.getOrElse { Typeface.create(regular, Typeface.BOLD) }

        private var page: PdfDocument.Page? = null
        private val canvas get() = requireNotNull(page).canvas
        private var y = margin
        private var pageNumber = 0

        init {
            newPage()
        }

        private fun money(value: Long) = String.format(Locale.US, "%,d KHR", value)

        private fun text(
            value: String,
            w: Float,
            size: Float = 10f,
            color: Int = ink,
            strong: Boolean = false,
            align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
        ): StaticLayout {
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size
                this.color = color
                typeface = if (strong) bold else regular
            }
            return StaticLayout.Builder.obtain(value, 0, value.length, paint, w.toInt().coerceAtLeast(1))
                .setAlignment(align)
                .setIncludePad(false)
                .setLineSpacing(0.6f, 1f)
                .build()
        }

        private fun draw(l: StaticLayout, x: Float, top: Float) {
            canvas.save()
            canvas.translate(x, top)
            l.draw(canvas)
            canvas.restore()
        }

        private fun line(x: Float, top: Float, endX: Float, endY: Float, color: Int = border, stroke: Float = .6f) {
            canvas.drawLine(
                x,
                top,
                endX,
                endY,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color
                    strokeWidth = stroke
                }
            )
        }

        private fun box(x: Float, top: Float, w: Float, h: Float, color: Int, radius: Float = 0f, outline: Int? = null) {
            val rect = RectF(x, top, x + w, top + h)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
            canvas.drawRoundRect(rect, radius, radius, paint)
            outline?.let {
                paint.color = it
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = .7f
                canvas.drawRoundRect(rect, radius, radius, paint)
            }
        }

        private fun pair(kh: String, en: String, w: Float, size: Float = 11f, color: Int = ink): Pair<StaticLayout, StaticLayout> =
            text(kh, w, size, color, true) to text(en, w, size * .76f, color)

        private fun pairHeight(p: Pair<StaticLayout, StaticLayout>) =
            p.first.height + (if (p.second.text.isEmpty()) 0f else p.second.height + 1f)

        private fun drawPair(p: Pair<StaticLayout, StaticLayout>, x: Float, top: Float) {
            draw(p.first, x, top)
            if (p.second.text.isNotEmpty()) draw(p.second, x, top + p.first.height + 1)
        }

        private fun newPage() {
            pageNumber++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(paper.width, paper.height, pageNumber).create())
            canvas.drawColor(Color.WHITE)
            canvas.scale(scale, scale)
            y = margin
            if (pageNumber > 1) {
                draw(text("RoomKit · $kind · $number · continued", width, 9f, muted), margin, y)
                y += 24f
            }
        }

        private fun closePage() {
            line(margin, pageHeight - 25, margin + width, pageHeight - 25, orange, 1f)
            draw(text("RoomKit · $number", width - 50, 9f, ink, true), margin, pageHeight - 21)
            draw(
                text(
                    if (totalPages > 0) "$pageNumber / $totalPages" else "$pageNumber",
                    45f,
                    9f,
                    ink,
                    true,
                    Layout.Alignment.ALIGN_OPPOSITE
                ),
                margin + width - 45,
                pageHeight - 21
            )
            pdf.finishPage(requireNotNull(page))
            page = null
        }

        private fun ensure(height: Float) {
            require(height <= bottom - margin - 24) { "An invoice section is too tall. Shorten its text before exporting." }
            if (y + height > bottom) {
                closePage()
                newPage()
            }
        }

        fun finish(): Int {
            if (page != null) closePage()
            return pageNumber
        }

        fun header(m: InvoicePdfModel, receipt: Boolean, status: String) {
            val top = y
            // Vector RoomKit building mark: crisp on paper, no bitmap logo dependency.
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = orange
                style = Paint.Style.STROKE
                strokeWidth = 2.5f
                strokeJoin = Paint.Join.ROUND
            }
            val path = Path().apply {
                moveTo(47f, top + 34)
                lineTo(47f, top + 5)
                lineTo(62f, top + 1)
                lineTo(62f, top + 34)
                lineTo(47f, top + 34)
                moveTo(62f, top + 12)
                lineTo(77f, top + 16)
                lineTo(77f, top + 34)
                lineTo(62f, top + 34)
            }
            canvas.drawPath(path, paint)
            box(52f, top + 10, 5f, 5f, orange)
            box(52f, top + 21, 5f, 5f, orange)
            draw(text("RoomKit", 110f, 23f, ink, true, Layout.Alignment.ALIGN_CENTER), margin, top + 37)
            line(135f, top + 2, 135f, top + 67)
            val property = pair("អគារ៖ ${m.buildingName}", "Property: ${m.buildingName}", 180f, 15f)
            drawPair(property, 150f, top + 5)
            val title = text(
                if (receipt) "បង្កាន់ដៃទទួលប្រាក់" else "វិក្កយបត្រជួលបន្ទប់",
                239f,
                23f,
                ink,
                true,
                Layout.Alignment.ALIGN_OPPOSITE
            )
            draw(title, 338f, top)
            draw(text(if (receipt) "R E C E I P T" else "I N V O I C E", 130f, 16f, muted, true), 338f, top + title.height + 4)
            val khStatus = when (status) {
                "PAID" -> "បានទូទាត់"
                "UNPAID" -> "មិនទាន់ទូទាត់"
                "PARTIALLY PAID" -> "បានទូទាត់ខ្លះ"
                "VOID" -> "មោឃៈ"
                "DRAFT" -> "ព្រាង"
                else -> "មិនស្គាល់ស្ថានភាព"
            }
            val badgeKh = text(khStatus, 103f, 10.5f, orange, true, Layout.Alignment.ALIGN_CENTER)
            val badgeEn = text(status, 103f, 8.5f, orange, true, Layout.Alignment.ALIGN_CENTER)
            val badgeH = badgeKh.height + badgeEn.height + 8f
            val badgeY = top + title.height + 3
            box(470f, badgeY, 107f, badgeH, warm, 7f, orange)
            draw(badgeKh, 472f, badgeY + 3)
            draw(badgeEn, 472f, badgeY + badgeKh.height + 4)
            y = top + maxOf(79f, title.height + badgeH + 8f, pairHeight(property) + 12)
            line(margin, y, margin + width, y, orange, 2.3f)
            y += 12
        }

        private data class InfoRow(val kh: String, val en: String, val value: String)

        private fun infoHeight(rows: List<InfoRow>, w: Float): Float = rows.sumOf { row ->
            val p = pair(row.kh, row.en, w * .51f, 10.5f)
            maxOf(32f, pairHeight(p) + 7, text(row.value, w * .46f, 11f, ink, true).height + 8f).toDouble()
        }.toFloat()

        private fun infoRows(rows: List<InfoRow>, x: Float, top: Float, w: Float) {
            var yy = top
            rows.forEachIndexed { index, row ->
                val p = pair(row.kh, row.en, w * .51f, 10.5f)
                val v = text(row.value, w * .46f, 11f, ink, true, Layout.Alignment.ALIGN_OPPOSITE)
                val h = maxOf(32f, pairHeight(p) + 7, v.height + 8f)
                drawPair(p, x, yy + 3)
                draw(v, x + w * .54f, yy + (h - v.height) / 2)
                yy += h
                if (index < rows.lastIndex) line(x, yy, x + w, yy)
            }
        }

        fun metadata(m: InvoicePdfModel, qr: Bitmap?) {
            val col = if (qr != null) 183f else 269f
            val gap = 17f
            val left = listOf(
                InfoRow("លេខវិក្កយបត្រ", "Invoice #", m.invoiceNumberFormatted),
                InfoRow("កាលបរិច្ឆេទចេញ", "Issue date", m.issueDateFormatted),
                InfoRow("កាលបរិច្ឆេទកំណត់", "Due date", m.dueDateFormatted)
            )
            val right = listOf(
                InfoRow("ឈ្មោះអ្នកជួល", "Tenant name", m.tenantName),
                InfoRow("លេខបន្ទប់", "Room", m.roomNumber),
                InfoRow("ខែគិតថ្លៃ", "Billing month", m.billingPeriodFormatted)
            ) + (m.billingPeriodDates?.takeIf { it.isNotBlank() }?.let {
                listOf(InfoRow("រយៈពេលសេវាកម្ម", "Service period", it))
            } ?: emptyList())

            val headingA = pair("ព័ត៌មានវិក្កយបត្រ", "Invoice details", col, 14f)
            val headingB = pair("អតិថិជនទទួលបន្ទុក", "Bill to", col, 14f)
            val headH = maxOf(pairHeight(headingA), pairHeight(headingB)) + 6
            val h = maxOf(headH + infoHeight(left, col), headH + infoHeight(right, col), if (qr != null) 157f else 0f)
            ensure(h + 12)
            drawPair(headingA, margin, y)
            drawPair(headingB, margin + col + gap, y)
            infoRows(left, margin, y + headH, col)
            infoRows(right, margin + col + gap, y + headH, col)
            line(margin + col + gap / 2, y + 3, margin + col + gap / 2, y + h - 3)
            if (qr != null) {
                val x = margin + width - 132
                box(x, y, 132f, 157f, Color.rgb(249, 247, 245), 9f)
                box(x + 10, y + 10, 112f, 112f, Color.WHITE, 3f)
                canvas.drawBitmap(qr, null, RectF(x + 14, y + 14, x + 118, y + 118), Paint().apply { isFilterBitmap = false })
                draw(text("ស្កេនដើម្បីទូទាត់ · KHR", 128f, 10f, ink, true, Layout.Alignment.ALIGN_CENTER), x + 2, y + 125)
                draw(text("Scan to pay · KHR", 128f, 8.5f, muted, align = Layout.Alignment.ALIGN_CENTER), x + 2, y + 142)
            }
            y += h + 12
        }

        fun table(m: InvoicePdfModel) {
            val widths = floatArrayOf(174f, 129f, 123f, 133f)
            fun layouts(cells: List<Pair<String, String>>, heading: Boolean) = cells.mapIndexed { i, cell ->
                val align = if (i == 0) Layout.Alignment.ALIGN_NORMAL else if (i == 3) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_CENTER
                text(cell.first, widths[i] - 20, if (heading) 12f else 11.5f, if (heading) Color.WHITE else ink, heading || i == 0 || i == 3, align) to
                        text(cell.second, widths[i] - 20, 9f, if (heading) Color.WHITE else ink, false, align)
            }

            val heads = layouts(
                listOf("បរិយាយ" to "Description", "បរិមាណ" to "Quantity", "តម្លៃឯកតា" to "Rate", "ទឹកប្រាក់" to "Amount"),
                true
            )

            fun rowHeight(ls: List<Pair<StaticLayout, StaticLayout>>) = maxOf(35f, ls.maxOf { pairHeight(it) } + 9f)
            fun drawRow(ls: List<Pair<StaticLayout, StaticLayout>>, color: Int, h: Float) {
                box(margin, y, width, h, color)
                var x = margin
                ls.forEachIndexed { i, p ->
                    drawPair(p, x + 10, y + (h - pairHeight(p)) / 2)
                    x += widths[i]
                }
                y += h
            }

            fun heading() {
                val h = rowHeight(heads)
                ensure(h)
                drawRow(heads, navy, h)
            }
            heading()

            m.items.filter { it.amountKhr != 0L || it.itemCode in setOf("RENT", "ELECTRICITY", "WATER") }.forEachIndexed { index, item ->
                val label = when (item.itemCode) {
                    "RENT" -> "ថ្លៃជួលបន្ទប់" to "Room rent"
                    "ELECTRICITY" -> "ថ្លៃអគ្គិសនី" to "Electricity"
                    "WATER" -> if (m.isWaterMetered) "ថ្លៃទឹក" to "Metered water" else "ថ្លៃទឹក (ថេរ)" to "Fixed water"
                    "RUBBISH" -> "ថ្លៃសំរាម" to "Rubbish fee"
                    "OTHER" -> "ថ្លៃផ្សេងៗ" to "Other charges"
                    "DISCOUNT" -> "ការបញ្ចុះតម្លៃ" to "Discount"
                    else -> item.description to ""
                }
                val fixed = item.unit == "FIXED"
                val qty = when {
                    fixed && item.itemCode in setOf("RENT", "WATER", "RUBBISH") -> "១ ខែ" to "1 month"
                    fixed -> "1" to ""
                    else -> (item.quantity?.toString() ?: "—") to item.unit.orEmpty()
                }
                val rate = if (fixed) item.amountKhr else item.unitPriceKhr
                val ls = layouts(
                    listOf(label, qty, (rate?.let { String.format(Locale.US, "%,d", it) } ?: "—") to "", money(item.amountKhr) to ""),
                    false
                )
                val h = rowHeight(ls)
                if (y + h > bottom) {
                    closePage()
                    newPage()
                    heading()
                }
                ensure(h)
                drawRow(ls, if (index % 2 == 0) Color.WHITE else pale, h)
            }
            line(margin, y, margin + width, y)
            y += 12
        }

        private data class PaymentRowLayout(
            val title: StaticLayout,
            val meta: StaticLayout,
            val amount: StaticLayout,
            val height: Float
        )

        private fun buildPaymentRows(payments: List<PaymentPdfItem>, innerW: Float): List<PaymentRowLayout> =
            payments.map { p ->
                val leftW = innerW * 0.56f
                val rightW = innerW * 0.42f
                val title = text(
                    "${p.paymentDateFormatted} · ${p.method.replace('_', ' ')}",
                    leftW,
                    10f,
                    ink,
                    strong = true
                )
                val metaParts = buildList {
                    add("#${p.paymentId}")
                    p.referenceNumber?.takeIf { it.isNotBlank() }?.let { add("Ref: $it") }
                    p.note?.takeIf { it.isNotBlank() }?.let { add(it) }
                }
                val meta = text(metaParts.joinToString(" · "), leftW, 8.5f, muted)
                val amount = text(
                    money(p.amountKhr),
                    rightW,
                    10.5f,
                    ink,
                    strong = true,
                    align = Layout.Alignment.ALIGN_OPPOSITE
                )
                val leftH = title.height + 1f + meta.height
                val rowH = maxOf(28f, leftH + 8f, amount.height + 8f)
                PaymentRowLayout(title, meta, amount, rowH)
            }

        fun summary(m: InvoicePdfModel, receipt: Boolean, receiver: String?, bank: String?) {
            val col = 257f
            val rightX = margin + 287
            val innerW = col - 20f

            val paymentRows = buildList {
                if (!bank.isNullOrBlank()) add(InfoRow("ធនាគារ", "Bank", bank))
                if (!receiver.isNullOrBlank()) add(InfoRow("ឈ្មោះគណនី", "Account name", receiver))
                add(InfoRow("រូបិយប័ណ្ណ", "Currency", "KHR"))
            }
            val accountRows = buildList {
                add(InfoRow("បំណុលមុនមិនទាន់ទូទាត់", "Previous unpaid", money(m.accountBalance.previousUnpaidKhr)))
                if (m.accountBalance.laterUnpaidKhr > 0) add(InfoRow("បំណុលក្រោយ", "Later unpaid", money(m.accountBalance.laterUnpaidKhr)))
                add(InfoRow("សមតុល្យគណនីសរុប", "Total account balance", money(m.accountBalance.totalAccountBalanceKhr)))
            }

            val pHeading = pair("ព័ត៌មានសម្រាប់ទូទាត់", "Payment details", col, 13f)
            val aHeading = pair("សេចក្តីសង្ខេបគណនី", "Account summary", col - 16, 12f)
            val ph = pairHeight(pHeading) + 6
            val paymentH = infoHeight(paymentRows, innerW) + 8
            val accountH = pairHeight(aHeading) + infoHeight(accountRows, innerW) + 12

            val totalRows = listOf(
                InfoRow("ចំនួនសរុបវិក្កយបត្រ", "Invoice total", money(m.totalKhr)),
                InfoRow("បានទូទាត់", "Paid", money(m.amountPaidKhr))
            )
            val totalsH = infoHeight(totalRows, col)

            val dueLabel = pair(
                if (receipt) "បានទូទាត់រួចរាល់" else "សមតុល្យត្រូវទូទាត់",
                if (receipt) "Paid in full · Balance" else "Balance due",
                col * .49f,
                12f,
                Color.WHITE
            )
            val dueValue = text(money(m.balanceKhr), col * .47f, 17f, Color.WHITE, true, Layout.Alignment.ALIGN_OPPOSITE)
            val dueH = maxOf(50f, pairHeight(dueLabel) + 14, dueValue.height + 14f)

            // Payment history card directly below Balance due on Page 1
            val inlinePayments = m.payments.take(MAX_INLINE_PAYMENTS)
            val historyHeading = pair("ប្រវត្តិការទូទាត់", "Payment history", innerW, 12f)
            val historyRows = buildPaymentRows(inlinePayments, innerW)
            val empty = pair("មិនទាន់មានការទូទាត់", "No payments recorded yet.", col - 28, 11f)

            val historyCardH = if (inlinePayments.isEmpty()) {
                pairHeight(empty) + 16f
            } else {
                6f + pairHeight(historyHeading) + 6f + historyRows.sumOf { it.height.toDouble() }.toFloat() + 4f
            }

            val rightH = totalsH + 12f + dueH + 12f + historyCardH
            val h = maxOf(ph + paymentH + 9 + accountH, rightH)
            ensure(h + 10)

            val top = y
            // Left column: Payment details + Account summary
            drawPair(pHeading, margin, top)
            box(margin, top + ph, col, paymentH, warm, 7f, Color.rgb(255, 219, 188))
            infoRows(paymentRows, margin + 10, top + ph + 4, innerW)

            val ay = top + ph + paymentH + 9
            box(margin, ay, col, accountH, pale, 7f, border)
            drawPair(aHeading, margin + 10, ay + 5)
            infoRows(accountRows, margin + 10, ay + pairHeight(aHeading) + 8, innerW)

            // Center vertical divider
            line(margin + 272, top + 4, margin + 272, top + h - 4)

            // Right column: Invoice total, Paid, Balance due, and Payment history
            infoRows(totalRows, rightX, top, col)
            val dy = top + totalsH + 12f
            box(rightX, dy, col, dueH, orange, 7f)
            drawPair(dueLabel, rightX + 8, dy + (dueH - pairHeight(dueLabel)) / 2)
            draw(dueValue, rightX + col * .51f, dy + (dueH - dueValue.height) / 2)

            val hy = dy + dueH + 12f
            box(rightX, hy, col, historyCardH, Color.rgb(249, 250, 252), 7f, border)
            if (inlinePayments.isEmpty()) {
                drawPair(empty, rightX + 14, hy + 8)
            } else {
                drawPair(historyHeading, rightX + 10, hy + 6)
                var ry = hy + 6f + pairHeight(historyHeading) + 6f
                line(rightX + 10, ry - 2f, rightX + col - 10, ry - 2f)
                historyRows.forEachIndexed { idx, row ->
                    val leftH = row.title.height + 1f + row.meta.height
                    val textTop = ry + (row.height - leftH) / 2f
                    draw(row.title, rightX + 10, textTop)
                    draw(row.meta, rightX + 10, textTop + row.title.height + 1f)
                    draw(row.amount, rightX + 10 + innerW * 0.58f, ry + (row.height - row.amount.height) / 2f)
                    ry += row.height
                    if (idx < historyRows.lastIndex) {
                        line(rightX + 10, ry, rightX + col - 10, ry)
                    }
                }
            }

            y = top + h + 10
        }

        fun notes(hasQr: Boolean, receipt: Boolean) {
            val notes = buildList {
                if (hasQr) {
                    add(
                        "បញ្ចូលសមតុល្យវិក្កយបត្រក្នុងកម្មវិធីធនាគារ និងពិនិត្យអ្នកទទួលមុនបង់ប្រាក់។" to
                                "Enter the invoice balance in your banking app. Confirm the receiver before paying."
                    )
                }
                add(
                    "បំណុលមុនមិនត្រូវបានគិតថ្លៃម្ដងទៀតទេ។ ឯកសារនេះសម្រាប់វិក្កយបត្រនេះប៉ុណ្ណោះ។" to
                            "Previous debt is not charged again. This document covers this invoice only."
                )
                if (receipt) {
                    add("សូមអរគុណសម្រាប់ការទូទាត់។" to "Paid in full. Thank you for your payment.")
                }
            }
            val ls = notes.map { pair(it.first, it.second, width - 118, 9f) }
            val h = maxOf(38f, ls.sumOf { (pairHeight(it) + 5).toDouble() }.toFloat()) + 12
            ensure(h)
            line(margin, y, margin + width, y)
            drawPair(pair("ចំណាំ៖", "Notes", 90f, 12f), margin + 8, y + 9)
            line(margin + 96, y + 8, margin + 96, y + h - 4)
            var yy = y + 8
            ls.forEach {
                canvas.drawCircle(margin + 108, yy + 5, 2f, Paint().apply { color = orange })
                drawPair(it, margin + 118, yy)
                yy += pairHeight(it) + 5
            }
            y += h + 5
        }

        fun section(kh: String, en: String) {
            val p = pair(kh, en, width, 12f)
            ensure(pairHeight(p) + 45)
            drawPair(p, margin, y)
            y += pairHeight(p) + 7
        }

        fun paragraph(value: String) {
            val l = text(value, width, 9f, muted)
            for (i in 0 until l.lineCount) {
                val row = text(value.substring(l.getLineStart(i), l.getLineEnd(i)).trimEnd(), width, 9f, muted)
                ensure(row.height + 2f)
                draw(row, margin, y)
                y += row.height + 2
            }
            y += 4
        }

        fun paymentEntry(p: PaymentPdfItem) {
            paragraph("#${p.paymentId} · ${p.paymentDateFormatted} · ${p.method.replace('_', ' ')} · ${money(p.amountKhr)}")
            p.referenceNumber?.takeIf { it.isNotBlank() }?.let { paragraph("Reference: $it") }
            p.note?.takeIf { it.isNotBlank() }?.let { paragraph("Note: $it") }
        }
    }
}
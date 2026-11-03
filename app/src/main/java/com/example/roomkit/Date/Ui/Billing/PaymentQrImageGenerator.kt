package com.example.roomkit.Date.Billing

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object PaymentQrImageGenerator {

    private const val CARD_WIDTH = 1080
    private const val CARD_HEIGHT = 1350

    fun createPaymentCardBitmap(
        qrBitmap: Bitmap,
        roomNumber: String,
        tenantName: String,
        periodFormatted: String,
        invoiceNumberFormatted: String,
        remainingBalanceKhr: Long
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

        var y = 110f

        // 1. Title Pill
        val pillText = "Room $roomNumber • $tenantName"
        textPaint.textSize = 34f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.color = Color.parseColor("#374151")
        val pillWidth = textPaint.measureText(pillText) + 80f
        val pillRect = RectF((CARD_WIDTH - pillWidth) / 2f, y - 40f, (CARD_WIDTH + pillWidth) / 2f, y + 25f)
        paint.color = Color.parseColor("#F3F4F6")
        canvas.drawRoundRect(pillRect, 40f, 40f, paint)
        canvas.drawText(pillText, CARD_WIDTH / 2f, y, textPaint)

        // 2. Billing Period & Invoice
        y += 80f
        textPaint.textSize = 32f
        textPaint.typeface = Typeface.DEFAULT
        textPaint.color = Color.parseColor("#6B7280")
        canvas.drawText("$periodFormatted   ·   $invoiceNumberFormatted", CARD_WIDTH / 2f, y, textPaint)

        // 3. Amount Due Header
        y += 75f
        textPaint.textSize = 30f
        textPaint.color = Color.parseColor("#4B5563")
        canvas.drawText("Amount Due", CARD_WIDTH / 2f, y, textPaint)

        // 4. Large Amount Due Text
        y += 85f
        textPaint.textSize = 80f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.color = Color.parseColor("#111827")
        canvas.drawText(CurrencyFormatter.formatKhr(remainingBalanceKhr), CARD_WIDTH / 2f, y, textPaint)

        // 5. Centered QR Code Box
        y += 60f
        val qrBoxSize = 580f
        val qrLeft = (CARD_WIDTH - qrBoxSize) / 2f
        val qrTop = y
        val qrRect = RectF(qrLeft, qrTop, qrLeft + qrBoxSize, qrTop + qrBoxSize)

        // Border around QR
        paint.color = Color.parseColor("#111827")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
        canvas.drawRoundRect(qrRect, 32f, 32f, paint)

        // Inner QR Image
        paint.style = Paint.Style.FILL
        val qrPadding = 30f
        val destQrRect = RectF(qrLeft + qrPadding, qrTop + qrPadding, qrLeft + qrBoxSize - qrPadding, qrTop + qrBoxSize - qrPadding)
        canvas.drawBitmap(qrBitmap, null, destQrRect, paint)

        // 6. KHQR Label Badge
        y = qrTop + qrBoxSize + 60f
        val badgeWidth = 140f
        val badgeHeight = 44f
        val badgeRect = RectF((CARD_WIDTH - badgeWidth) / 2f, y - 32f, (CARD_WIDTH + badgeWidth) / 2f, y + 12f)
        paint.color = Color.parseColor("#DC2626") // KHQR Red
        canvas.drawRoundRect(badgeRect, 8f, 8f, paint)

        textPaint.textSize = 24f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.color = Color.WHITE
        canvas.drawText("KHQR", CARD_WIDTH / 2f, y, textPaint)

        // 7. Instructions
        y += 70f
        textPaint.textSize = 28f
        textPaint.typeface = Typeface.DEFAULT
        textPaint.color = Color.parseColor("#6B7280")
        canvas.drawText("Scan with ABA, ACLEDA, Wing, or any", CARD_WIDTH / 2f, y, textPaint)
        y += 40f
        canvas.drawText("supported Cambodian banking app to pay.", CARD_WIDTH / 2f, y, textPaint)

        // 8. Bottom Exact Amount Label
        y += 70f
        textPaint.textSize = 28f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.color = Color.parseColor("#6B7280")
        canvas.drawText("PAYMENT AMOUNT IS EXACT — ${CurrencyFormatter.formatKhr(remainingBalanceKhr)}", CARD_WIDTH / 2f, y, textPaint)

        return bitmap
    }

    fun saveToPicturesGallery(context: Context, bitmap: Bitmap, fileName: String): Uri {
        var outputStream: OutputStream? = null
        var uri: Uri? = null

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.png")
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/RoomKit")
                }
                val resolver = context.contentResolver
                uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: throw IllegalStateException("Could not create MediaStore entry in Pictures/RoomKit")
                outputStream = resolver.openOutputStream(uri)
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "RoomKit")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, "$fileName.png")
                uri = Uri.fromFile(file)
                outputStream = FileOutputStream(file)
            }

            outputStream?.let { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                ?: throw IllegalStateException("Unable to open output stream")

            return uri
        } finally {
            outputStream?.flush()
            outputStream?.close()
        }
    }

    fun saveToCacheForSharing(context: Context, bitmap: Bitmap, fileName: String): Uri {
        val cachePath = File(context.cacheDir, "shared_qr")
        if (!cachePath.exists()) {
            cachePath.mkdirs()
        }

        val file = File(cachePath, "$fileName.png")

        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
        }

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
}
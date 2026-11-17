package com.example.roomkit.Date.Billing

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File

object PaymentQrImageGenerator {
    fun createPaymentCardBitmap(
        qrBitmap: Bitmap, roomNumber: String, tenantName: String,
        periodFormatted: String, invoiceNumberFormatted: String,
        remainingBalanceKhr: Long, receiverName: String = "", accountId: String = ""
    ): Bitmap {
        val width = 1080
        fun layout(text: String, size: Float, bold: Boolean = false) = StaticLayout.Builder.obtain(
            text, 0, text.length, TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size; color = Color.rgb(17, 24, 39)
                typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            }, 960).setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(true).build()
        val before = listOf(layout("Room $roomNumber · $tenantName", 34f, true),
            layout("$periodFormatted · $invoiceNumberFormatted", 28f),
            layout("Amount due · ប្រាក់ត្រូវបង់", 30f),
            layout(CurrencyFormatter.formatKhr(remainingBalanceKhr), 64f, true))
        val after = listOf(layout("KHQR", 32f, true), layout(receiverName, 34f, true), layout(accountId, 26f),
            layout("Enter the amount above in your banking app.\nបញ្ចូលចំនួនទឹកប្រាក់ខាងលើក្នុងកម្មវិធីធនាគារ។", 28f),
            layout("For this invoice only. Confirm the receiver before paying.", 26f))
        val height = 120 + (before + after).sumOf { it.height + 24 } + 720
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        var y = 60f
        fun draw(block: StaticLayout) {
            canvas.save(); canvas.translate(60f, y); block.draw(canvas); canvas.restore()
            y += block.height + 24f
        }
        before.forEach { draw(it) }
        // Preserve square pixels and the QR's quiet zone; no decorative overlays.
        canvas.drawBitmap(qrBitmap, null, RectF(180f, y, 900f, y + 720f), Paint().apply { isFilterBitmap = false })
        y += 720f
        after.forEach { draw(it) }
        return bitmap
    }
    fun saveToPicturesGallery(context: Context, bitmap: Bitmap, fileName: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$fileName.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/RoomKit")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)) { "Cannot create image." }
        try {
            checkNotNull(resolver.openOutputStream(uri)).use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            check(resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null) > 0)
            return uri
        } catch (e: Exception) { resolver.delete(uri, null, null); throw e }
    }
    fun saveToCacheForSharing(context: Context, bitmap: Bitmap, fileName: String): Uri {
        val dir = File(context.cacheDir, "shared_payment_cards")
        check(dir.isDirectory || dir.mkdirs())
        val file = File.createTempFile(fileName.replace(Regex("[^A-Za-z0-9_]"), "_").take(60).padEnd(3, '_'), ".png", dir)
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        return FileProvider.getUriForFile(context, "${context.packageName}.paymentfiles", file)
    }
}

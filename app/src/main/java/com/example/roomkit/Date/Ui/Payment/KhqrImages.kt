@file:Suppress("PackageNaming")
package com.example.roomkit.Date.Payment

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.qrcode.QRCodeMultiReader
import com.google.zxing.qrcode.QRCodeWriter

object KhqrImages {
    /** Call on Dispatchers.IO/Default; retains payload, not a temporary picker URI. */
    fun importImage(context: Context, uri: Uri): StaticKhqr {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            require(w > 0 && h > 0) { "Invalid image size." }
            val ratio = minOf(1.0, 1800.0 / maxOf(w, h))
            decoder.setTargetSize(maxOf(1, (w * ratio).toInt()), maxOf(1, (h * ratio).toInt()))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val binary = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, pixels)))
            val results = try {
                QRCodeMultiReader().decodeMultiple(binary, mapOf(DecodeHintType.TRY_HARDER to true))
            } catch (_: NotFoundException) {
                throw IllegalArgumentException("No readable QR found. Use a clear image containing one receiving QR.")
            }
            require(results.size == 1) { "Choose an image with exactly one QR code." }
            return StaticKhqrParser.parse(results.single().text)
        } finally { bitmap.recycle() }
    }
    fun render(payload: String): Bitmap {
        StaticKhqrParser.parse(payload)
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 800, 800,
            mapOf(EncodeHintType.MARGIN to 4))
        val pixels = IntArray(matrix.width * matrix.height) { i ->
            if (matrix[i % matrix.width, i / matrix.width]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
        return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
    }
}

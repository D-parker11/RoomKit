package com.example.roomkit.Date.Billing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.*
import android.print.pdf.PrintedPdfDocument
import java.io.FileOutputStream

/** Respects printer media, printable margins, cancellation and requested page ranges. */
class InvoicePrintAdapter(
    private val context: Context,
    private val uri: Uri,
    private val documentName: String
) : PrintDocumentAdapter() {
    private var attributes: PrintAttributes? = null
    private var count = 0

    override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes,
                          cancellationSignal: CancellationSignal, callback: LayoutResultCallback, extras: Bundle?) {
        if (cancellationSignal.isCanceled) { callback.onLayoutCancelled(); return }
        try {
            val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: error("PDF unavailable")
            descriptor.use { fd -> PdfRenderer(fd).use { count = it.pageCount } }
            attributes = newAttributes
            callback.onLayoutFinished(PrintDocumentInfo.Builder(documentName)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(count).build(),
                oldAttributes != newAttributes)
        } catch (e: Exception) { callback.onLayoutFailed(e.message) }
    }

    override fun onWrite(pages: Array<out PageRange>, destination: ParcelFileDescriptor,
                         cancellationSignal: CancellationSignal, callback: WriteResultCallback) {
        val attrs = attributes ?: run { callback.onWriteFailed("Print layout not ready"); return }
        // Run rendering off the UI thread. Framework callbacks are posted back to the main thread.
        Thread {
            val main = android.os.Handler(android.os.Looper.getMainLooper())
            try {
                val selected = (0 until count).filter { index -> pages.any { index in it.start..it.end } }
                val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: error("PDF unavailable")
                descriptor.use { fd ->
                    PdfRenderer(fd).use { renderer ->
                        val output = PrintedPdfDocument(context, attrs)
                        try {
                            for (index in selected) {
                                if (cancellationSignal.isCanceled) throw PrintCancelled()
                                renderer.openPage(index).use { source ->
                                    // 216 dpi: suitable for text and KHQR without unbounded printer-resolution bitmaps.
                                    val bitmap = Bitmap.createBitmap(source.width*3, source.height*3, Bitmap.Config.ARGB_8888)
                                    try {
                                        bitmap.eraseColor(Color.WHITE)
                                        source.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                        val target = output.startPage(index)
                                        val area = target.info.contentRect
                                        val scale = minOf(area.width().toFloat()/bitmap.width, area.height().toFloat()/bitmap.height)
                                        val w = bitmap.width*scale; val h = bitmap.height*scale
                                        val x = area.left+(area.width()-w)/2; val y = area.top+(area.height()-h)/2
                                        target.canvas.drawBitmap(bitmap,null,RectF(x,y,x+w,y+h),null)
                                        output.finishPage(target)
                                    } finally { bitmap.recycle() }
                                }
                            }
                            if (cancellationSignal.isCanceled) throw PrintCancelled()
                            FileOutputStream(destination.fileDescriptor).use { output.writeTo(it) }
                        } finally { output.close() }
                    }
                }
                main.post {
                    if (cancellationSignal.isCanceled) callback.onWriteCancelled()
                    else callback.onWriteFinished(selected.map { PageRange(it,it) }.toTypedArray())
                }
            } catch (_: PrintCancelled) {
                main.post { callback.onWriteCancelled() }
            } catch (e: Exception) {
                main.post { if (cancellationSignal.isCanceled) callback.onWriteCancelled() else callback.onWriteFailed(e.message) }
            }
        }.start()
    }
    private class PrintCancelled : Exception()
}

package com.formready.resizer.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

data class PdfCompressResult(
    val bytes: ByteArray,
    val pageCount: Int,
    val originalBytes: Long,
    val withinLimit: Boolean
) {
    val sizeBytes: Long get() = bytes.size.toLong()
    val savedPercent: Int
        get() = if (originalBytes <= 0) 0
        else (((originalBytes - sizeBytes).toDouble() / originalBytes) * 100).roundToInt()
}

/**
 * Compresses by rasterising each page and re-embedding it as a JPEG.
 * Trade-off worth knowing: text stops being selectable. For form uploads that is fine,
 * and it is the only route without pulling in an AGPL PDF library.
 */
@Singleton
class PdfCompressor @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun compress(
        uri: Uri,
        quality: PdfQuality,
        maxBytes: Long? = null
    ): PdfCompressResult = withContext(Dispatchers.Default) {
        val working = copyToCache(uri)
        try {
            val originalSize = working.length()
            var jpegQuality = quality.jpegQuality
            var scale = scaleFor(quality)
            var attempt = 0
            var bytes = render(working, jpegQuality, scale)

            while (maxBytes != null && bytes.size > maxBytes && attempt < 6) {
                jpegQuality = (jpegQuality - 12).coerceAtLeast(25)
                scale = (scale * 0.85).coerceAtLeast(0.35)
                bytes = render(working, jpegQuality, scale)
                attempt++
            }

            PdfCompressResult(
                bytes = bytes,
                pageCount = pageCount(working),
                originalBytes = originalSize,
                withinLimit = maxBytes == null || bytes.size <= maxBytes
            )
        } finally {
            working.delete()
        }
    }

    private fun scaleFor(quality: PdfQuality): Double = when (quality) {
        PdfQuality.HIGH -> 1.6
        PdfQuality.BALANCED -> 1.2
        PdfQuality.SMALL -> 0.9
    }

    private fun render(file: File, jpegQuality: Int, scale: Double): ByteArray {
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(descriptor)
        val document = PdfDocument()
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        try {
            for (index in 0 until renderer.pageCount) {
                renderer.openPage(index).use { page ->
                    val width = (page.width * scale).roundToInt().coerceIn(1, 4000)
                    val height = (page.height * scale).roundToInt().coerceIn(1, 4000)

                    val raster = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    raster.eraseColor(Color.WHITE)
                    page.render(raster, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    val jpeg = ByteArrayOutputStream().use { out ->
                        raster.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)
                        out.toByteArray()
                    }
                    raster.recycle()

                    val decoded = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
                    val info = PdfDocument.PageInfo.Builder(page.width, page.height, index + 1).create()
                    val newPage = document.startPage(info)
                    val canvas: Canvas = newPage.canvas
                    canvas.drawColor(Color.WHITE)
                    canvas.drawBitmap(
                        decoded, null,
                        android.graphics.Rect(0, 0, page.width, page.height), paint
                    )
                    document.finishPage(newPage)
                    decoded.recycle()
                }
            }

            return ByteArrayOutputStream().use { out ->
                document.writeTo(out)
                out.toByteArray()
            }
        } finally {
            document.close()
            renderer.close()
            descriptor.close()
        }
    }

    private fun pageCount(file: File): Int {
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return PdfRenderer(descriptor).use { it.pageCount }.also { descriptor.close() }
    }

    private fun copyToCache(uri: Uri): File {
        val dir = File(context.cacheDir, "pdf").apply { mkdirs() }
        val file = File(dir, "source_${System.currentTimeMillis()}.pdf")
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Could not open that PDF.")
        return file
    }
}

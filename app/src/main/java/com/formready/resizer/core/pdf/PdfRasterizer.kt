package com.formready.resizer.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
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

/** One source document, optionally narrowed to a set of 1-based page numbers. */
data class PdfSource(val uri: Uri, val pages: List<Int>? = null)

data class PdfPagesResult(val pages: List<ByteArray>) {
    val totalBytes: Long get() = pages.sumOf { it.size.toLong() }
}

/**
 * Shared page-level PDF plumbing for merge, split and PDF-to-images.
 *
 * Everything here goes through PdfRenderer, so output pages are rasterised and their text is no
 * longer selectable. That is the cost of staying off an AGPL PDF library, and it is acceptable
 * for form uploads — the UI says so plainly wherever it matters.
 */
@Singleton
class PdfRasterizer @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun pageCount(uri: Uri): Int = withContext(Dispatchers.IO) {
        val file = copyToCache(uri)
        try {
            openRenderer(file).use { it.pageCount }
        } finally {
            file.delete()
        }
    }

    /** Renders selected pages (or all) to JPEG bytes, one entry per page. */
    suspend fun renderToJpegs(
        uri: Uri,
        pages: List<Int>? = null,
        quality: PdfQuality = PdfQuality.HIGH
    ): PdfPagesResult = withContext(Dispatchers.Default) {
        val file = copyToCache(uri)
        try {
            openRenderer(file).use { renderer ->
                val indices = resolveIndices(pages, renderer.pageCount)
                PdfPagesResult(
                    indices.map { index ->
                        renderer.openPage(index).use { page ->
                            val bitmap = rasterise(page, scaleFor(quality))
                            val bytes = encode(bitmap, quality.jpegQuality)
                            bitmap.recycle()
                            bytes
                        }
                    }
                )
            }
        } finally {
            file.delete()
        }
    }

    /**
     * Rebuilds one PDF from any number of sources and page selections.
     * Merge = several sources with no selection. Split = one source with a selection.
     */
    suspend fun rebuild(
        sources: List<PdfSource>,
        quality: PdfQuality,
        maxBytes: Long? = null
    ): PdfBuildResult = withContext(Dispatchers.Default) {
        require(sources.isNotEmpty()) { "Select at least one PDF first." }

        var jpegQuality = quality.jpegQuality
        var scale = scaleFor(quality)
        var attempt = 0
        var result = render(sources, jpegQuality, scale)

        while (maxBytes != null && result.first.size > maxBytes && attempt < 6) {
            jpegQuality = (jpegQuality - 12).coerceAtLeast(25)
            scale = (scale * 0.85).coerceAtLeast(0.35)
            result = render(sources, jpegQuality, scale)
            attempt++
        }

        PdfBuildResult(
            bytes = result.first,
            pageCount = result.second,
            withinLimit = maxBytes == null || result.first.size <= maxBytes
        )
    }

    private fun render(sources: List<PdfSource>, jpegQuality: Int, scale: Double): Pair<ByteArray, Int> {
        val document = PdfDocument()
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val temporaries = mutableListOf<File>()
        var pageNumber = 0

        try {
            sources.forEach { source ->
                val file = copyToCache(source.uri).also { temporaries += it }
                openRenderer(file).use { renderer ->
                    resolveIndices(source.pages, renderer.pageCount).forEach { index ->
                        renderer.openPage(index).use { page ->
                            val raster = rasterise(page, scale)
                            val jpeg = encode(raster, jpegQuality)
                            raster.recycle()

                            val decoded = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
                            pageNumber++
                            val info = PdfDocument.PageInfo
                                .Builder(page.width, page.height, pageNumber)
                                .create()
                            val newPage = document.startPage(info)
                            newPage.canvas.drawColor(Color.WHITE)
                            newPage.canvas.drawBitmap(
                                decoded, null, Rect(0, 0, page.width, page.height), paint
                            )
                            document.finishPage(newPage)
                            decoded.recycle()
                        }
                    }
                }
            }

            val bytes = ByteArrayOutputStream().use { out ->
                document.writeTo(out)
                out.toByteArray()
            }
            return bytes to pageNumber
        } finally {
            document.close()
            temporaries.forEach { it.delete() }
        }
    }

    private fun rasterise(page: PdfRenderer.Page, scale: Double): Bitmap {
        val width = (page.width * scale).roundToInt().coerceIn(1, 4000)
        val height = (page.height * scale).roundToInt().coerceIn(1, 4000)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bitmap
    }

    private fun encode(bitmap: Bitmap, quality: Int): ByteArray =
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }

    private fun scaleFor(quality: PdfQuality): Double = when (quality) {
        PdfQuality.HIGH -> 2.0
        PdfQuality.BALANCED -> 1.4
        PdfQuality.SMALL -> 1.0
    }

    /** Converts 1-based page numbers to 0-based indices, dropping anything out of range. */
    private fun resolveIndices(pages: List<Int>?, pageCount: Int): List<Int> =
        pages?.filter { it in 1..pageCount }?.map { it - 1 }?.distinct()
            ?: (0 until pageCount).toList()

    private fun openRenderer(file: File): PdfRenderer =
        PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))

    private fun copyToCache(uri: Uri): File {
        val dir = File(context.cacheDir, "pdf").apply { mkdirs() }
        val file = File(dir, "src_${System.nanoTime()}.pdf")
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Could not open that PDF.")
        return file
    }
}

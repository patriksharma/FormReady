package com.formready.resizer.core.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.formready.resizer.core.image.ImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.roundToInt

data class PdfBuildOptions(
    val pageSize: PageSize = PageSize.A4,
    val orientation: PageOrientation = PageOrientation.PORTRAIT,
    val quality: PdfQuality = PdfQuality.BALANCED,
    val marginPt: Int = 24,
    /** Optional ceiling. The builder re-runs at lower quality until it fits or runs out of room. */
    val maxBytes: Long? = null
)

data class PdfBuildResult(
    val bytes: ByteArray,
    val pageCount: Int,
    val withinLimit: Boolean
) {
    val sizeBytes: Long get() = bytes.size.toLong()
}

@Singleton
class ImagesToPdfBuilder @Inject constructor(
    private val imageLoader: ImageLoader
) {

    suspend fun build(uris: List<Uri>, options: PdfBuildOptions): PdfBuildResult =
        withContext(Dispatchers.Default) {
            require(uris.isNotEmpty()) { "Add at least one image first." }

            var quality = options.quality.jpegQuality
            var maxEdge = options.quality.maxEdgePx
            var attempt = 0
            var bytes = render(uris, options, quality, maxEdge)

            while (options.maxBytes != null && bytes.size > options.maxBytes && attempt < 6) {
                quality = (quality - 12).coerceAtLeast(30)
                maxEdge = (maxEdge * 0.8).roundToInt().coerceAtLeast(700)
                bytes = render(uris, options, quality, maxEdge)
                attempt++
            }

            PdfBuildResult(
                bytes = bytes,
                pageCount = uris.size,
                withinLimit = options.maxBytes == null || bytes.size <= options.maxBytes
            )
        }

    private suspend fun render(
        uris: List<Uri>,
        options: PdfBuildOptions,
        jpegQuality: Int,
        maxEdge: Int
    ): ByteArray {
        val document = PdfDocument()
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        try {
            uris.forEachIndexed { index, uri ->
                val original = imageLoader.decode(uri)
                val flattened = reencode(original, jpegQuality, maxEdge)
                if (flattened !== original) original.recycle()

                val (pageWidth, pageHeight) = pageDimensions(options, flattened)
                val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                val page = document.startPage(info)
                val canvas: Canvas = page.canvas
                canvas.drawColor(Color.WHITE)

                val margin = if (options.pageSize == PageSize.ORIGINAL) 0 else options.marginPt
                val available = Rect(
                    margin, margin,
                    pageWidth - margin, pageHeight - margin
                )
                canvas.drawBitmap(flattened, null, fit(flattened, available), paint)

                document.finishPage(page)
                flattened.recycle()
            }

            return ByteArrayOutputStream().use { out ->
                document.writeTo(out)
                out.toByteArray()
            }
        } finally {
            document.close()
        }
    }

    /** JPEG round-trip: this is what actually shrinks the embedded image data. */
    private fun reencode(source: Bitmap, quality: Int, maxEdge: Int): Bitmap {
        val longestEdge = maxOf(source.width, source.height)
        val scaled = if (longestEdge > maxEdge) {
            val factor = maxEdge.toDouble() / longestEdge
            Bitmap.createScaledBitmap(
                source,
                (source.width * factor).roundToInt().coerceAtLeast(1),
                (source.height * factor).roundToInt().coerceAtLeast(1),
                true
            )
        } else source

        val bytes = ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }
        if (scaled !== source) scaled.recycle()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: source
    }

    private fun pageDimensions(options: PdfBuildOptions, bitmap: Bitmap): Pair<Int, Int> {
        if (options.pageSize == PageSize.ORIGINAL) {
            return bitmap.width.coerceAtLeast(1) to bitmap.height.coerceAtLeast(1)
        }
        val portrait = options.pageSize.widthPt to options.pageSize.heightPt
        return if (options.orientation == PageOrientation.PORTRAIT) portrait
        else portrait.second to portrait.first
    }

    /** Aspect-correct centred placement inside [bounds]. */
    private fun fit(bitmap: Bitmap, bounds: Rect): Rect {
        val scale = min(
            bounds.width().toFloat() / bitmap.width,
            bounds.height().toFloat() / bitmap.height
        )
        val width = (bitmap.width * scale).roundToInt()
        val height = (bitmap.height * scale).roundToInt()
        val left = bounds.left + (bounds.width() - width) / 2
        val top = bounds.top + (bounds.height() - height) / 2
        return Rect(left, top, left + width, top + height)
    }
}

package com.formready.resizer.core.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.roundToInt

enum class SheetLayout(val label: String) {
    STACKED("Photo above signature"),
    SIDE_BY_SIDE("Photo beside signature")
}

data class SheetSpec(
    val widthPx: Int,
    val heightPx: Int,
    val layout: SheetLayout = SheetLayout.STACKED,
    /** Share of the sheet given to the photo, 0.5 to 0.9. */
    val photoShare: Float = 0.72f,
    val gapPx: Int = 12,
    val paddingPx: Int = 12,
    val drawBorder: Boolean = false
)

/**
 * Draws a photo and a signature onto one white sheet. Several application forms ask for both
 * in a single image; doing it by hand in a gallery editor is exactly the annoyance this removes.
 */
@Singleton
class SheetComposer @Inject constructor() {

    suspend fun compose(photo: Bitmap, signature: Bitmap, spec: SheetSpec): Bitmap =
        withContext(Dispatchers.Default) {
            val sheet = Bitmap.createBitmap(
                spec.widthPx.coerceAtLeast(40),
                spec.heightPx.coerceAtLeast(40),
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(sheet)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

            val pad = spec.paddingPx
            val gap = spec.gapPx
            val share = spec.photoShare.coerceIn(0.4f, 0.9f)

            val (photoBounds, signatureBounds) = when (spec.layout) {
                SheetLayout.STACKED -> {
                    val usable = sheet.height - pad * 2 - gap
                    val photoHeight = (usable * share).roundToInt()
                    Rect(pad, pad, sheet.width - pad, pad + photoHeight) to
                        Rect(pad, pad + photoHeight + gap, sheet.width - pad, sheet.height - pad)
                }
                SheetLayout.SIDE_BY_SIDE -> {
                    val usable = sheet.width - pad * 2 - gap
                    val photoWidth = (usable * share).roundToInt()
                    Rect(pad, pad, pad + photoWidth, sheet.height - pad) to
                        Rect(pad + photoWidth + gap, pad, sheet.width - pad, sheet.height - pad)
                }
            }

            canvas.drawBitmap(photo, null, fit(photo, photoBounds), paint)
            canvas.drawBitmap(signature, null, fit(signature, signatureBounds), paint)

            if (spec.drawBorder) {
                val border = Paint().apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 2f
                    color = Color.DKGRAY
                }
                canvas.drawRect(photoBounds, border)
                canvas.drawRect(signatureBounds, border)
            }

            sheet
        }

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

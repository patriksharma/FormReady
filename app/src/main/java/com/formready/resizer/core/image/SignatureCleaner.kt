package com.formready.resizer.core.image

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Signature-specific clean-up. Purely local pixel work, no model, no network.
 *  - trim(): removes the empty paper border so the signature fills the frame
 *  - whiten(): pushes light paper to pure white and dark ink to near-black
 */
@Singleton
class SignatureCleaner @Inject constructor() {

    suspend fun clean(
        source: Bitmap,
        trim: Boolean,
        whiten: Boolean,
        threshold: Int = 200
    ): Bitmap = withContext(Dispatchers.Default) {
        var result = source
        if (trim) result = trimBorders(result, threshold)
        if (whiten) result = whitenBackground(result, threshold)
        result
    }

    private fun trimBorders(source: Bitmap, threshold: Int): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        var top = 0
        var bottom = h - 1
        var left = 0
        var right = w - 1

        fun rowHasInk(y: Int): Boolean {
            for (x in 0 until w) if (isInk(pixels[y * w + x], threshold)) return true
            return false
        }

        fun columnHasInk(x: Int): Boolean {
            for (y in top..bottom) if (isInk(pixels[y * w + x], threshold)) return true
            return false
        }

        while (top < bottom && !rowHasInk(top)) top++
        while (bottom > top && !rowHasInk(bottom)) bottom--
        while (left < right && !columnHasInk(left)) left++
        while (right > left && !columnHasInk(right)) right--

        val padding = 4
        val x = (left - padding).coerceAtLeast(0)
        val y = (top - padding).coerceAtLeast(0)
        val width = (right - left + 1 + padding * 2).coerceAtMost(w - x)
        val height = (bottom - top + 1 + padding * 2).coerceAtMost(h - y)

        if (width <= 1 || height <= 1 || (width == w && height == h)) return source

        val cropped = Bitmap.createBitmap(source, x, y, width, height)
        if (cropped !== source) source.recycle()
        return cropped
    }

    private fun whitenBackground(source: Bitmap, threshold: Int): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        for (i in pixels.indices) {
            pixels[i] = if (isInk(pixels[i], threshold)) darken(pixels[i]) else Color.WHITE
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        source.recycle()
        return out
    }

    private fun isInk(pixel: Int, threshold: Int): Boolean {
        if (Color.alpha(pixel) < 32) return false
        val luminance = (Color.red(pixel) * 299 + Color.green(pixel) * 587 + Color.blue(pixel) * 114) / 1000
        return luminance < threshold
    }

    private fun darken(pixel: Int): Int {
        val factor = 0.65f
        return Color.rgb(
            (Color.red(pixel) * factor).toInt(),
            (Color.green(pixel) * factor).toInt(),
            (Color.blue(pixel) * factor).toInt()
        )
    }
}

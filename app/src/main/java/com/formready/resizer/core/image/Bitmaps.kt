package com.formready.resizer.core.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color

/**
 * JPEG has no alpha channel, so transparent pixels come out black unless they are flattened
 * onto a background first. Needed whenever a PNG or WEBP is converted to JPG.
 */
fun Bitmap.flattenOnto(background: Int = Color.WHITE): Bitmap {
    if (!hasAlpha()) return this
    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    Canvas(output).apply {
        drawColor(background)
        drawBitmap(this@flattenOnto, 0f, 0f, null)
    }
    recycle()
    return output
}

package com.formready.resizer.core.image

import kotlin.math.roundToInt

enum class SizeUnit(val label: String) {
    PX("px"), MM("mm"), CM("cm"), INCH("in");

    fun toPixels(value: Double, dpi: Int): Int = when (this) {
        PX -> value.roundToInt()
        MM -> (value / 25.4 * dpi).roundToInt()
        CM -> (value / 2.54 * dpi).roundToInt()
        INCH -> (value * dpi).roundToInt()
    }

    fun fromPixels(pixels: Int, dpi: Int): Double = when (this) {
        PX -> pixels.toDouble()
        MM -> pixels / dpi.toDouble() * 25.4
        CM -> pixels / dpi.toDouble() * 2.54
        INCH -> pixels / dpi.toDouble()
    }
}

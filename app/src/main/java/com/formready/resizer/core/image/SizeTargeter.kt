package com.formready.resizer.core.image

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

data class TargetSpec(
    val targetWidth: Int? = null,
    val targetHeight: Int? = null,
    /** Hard ceiling in bytes. null = no size constraint, encode at [maxQuality]. */
    val maxBytes: Long? = null,
    val allowDownscale: Boolean = true,
    val minQuality: Int = 20,
    val maxQuality: Int = 95
)

class TargetResult(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
    val quality: Int,
    /** false means even the smallest settings could not get under maxBytes. */
    val withinLimit: Boolean,
    val downscaleSteps: Int
) {
    val sizeBytes: Long get() = bytes.size.toLong()
}

/**
 * The core of FormReady. Hits an exact KB ceiling by binary-searching JPEG quality,
 * then stepping dimensions down only if quality alone cannot get there.
 */
@Singleton
class SizeTargeter @Inject constructor() {

    private companion object {
        const val MAX_DOWNSCALE_STEPS = 12
        const val DOWNSCALE_FACTOR = 0.9
        const val MIN_EDGE = 40
    }

    suspend fun compress(source: Bitmap, spec: TargetSpec): TargetResult = withContext(Dispatchers.Default) {
        var working = scaleTo(source, spec.targetWidth, spec.targetHeight)
        var ownsWorking = working !== source
        var steps = 0

        try {
            while (true) {
                coroutineContext.ensureActive()

                if (spec.maxBytes == null) {
                    val bytes = encode(working, spec.maxQuality)
                    return@withContext TargetResult(bytes, working.width, working.height, spec.maxQuality, true, steps)
                }

                val found = searchQuality(working, spec.maxBytes, spec.minQuality, spec.maxQuality)
                if (found != null) {
                    return@withContext TargetResult(
                        found.first, working.width, working.height, found.second, true, steps
                    )
                }

                val exhausted = !spec.allowDownscale ||
                    steps >= MAX_DOWNSCALE_STEPS ||
                    working.width <= MIN_EDGE ||
                    working.height <= MIN_EDGE

                if (exhausted) {
                    val bytes = encode(working, spec.minQuality)
                    return@withContext TargetResult(
                        bytes, working.width, working.height, spec.minQuality,
                        bytes.size <= spec.maxBytes, steps
                    )
                }

                val next = Bitmap.createScaledBitmap(
                    working,
                    (working.width * DOWNSCALE_FACTOR).roundToInt().coerceAtLeast(MIN_EDGE),
                    (working.height * DOWNSCALE_FACTOR).roundToInt().coerceAtLeast(MIN_EDGE),
                    true
                )
                if (ownsWorking) working.recycle()
                working = next
                ownsWorking = true
                steps++
            }
        } finally {
            if (ownsWorking && !working.isRecycled) working.recycle()
        }
        @Suppress("UNREACHABLE_CODE")
        error("unreachable")
    }

    /** Largest quality whose encoded size fits under [maxBytes], or null if even minQuality overshoots. */
    private fun searchQuality(bitmap: Bitmap, maxBytes: Long, minQuality: Int, maxQuality: Int): Pair<ByteArray, Int>? {
        var low = minQuality
        var high = maxQuality
        var best: Pair<ByteArray, Int>? = null

        while (low <= high) {
            val mid = (low + high) / 2
            val bytes = encode(bitmap, mid)
            if (bytes.size <= maxBytes) {
                best = bytes to mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return best
    }

    private fun encode(bitmap: Bitmap, quality: Int): ByteArray =
        ByteArrayOutputStream(bitmap.width * bitmap.height / 8).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), out)
            out.toByteArray()
        }

    private fun scaleTo(source: Bitmap, width: Int?, height: Int?): Bitmap {
        val w = width ?: source.width
        val h = height ?: source.height
        if (w == source.width && h == source.height) return source
        return Bitmap.createScaledBitmap(source, w.coerceAtLeast(1), h.coerceAtLeast(1), true)
    }
}

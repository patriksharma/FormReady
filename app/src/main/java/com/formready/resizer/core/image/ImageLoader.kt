package com.formready.resizer.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

data class SourceInfo(
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val mimeType: String?
)

/**
 * Decodes user images without blowing up the heap:
 * bounds pass -> inSampleSize against a pixel budget -> EXIF rotation applied.
 *
 * Note on the bounds pass: with inJustDecodeBounds = true, decodeStream always returns null
 * and reports its findings through the Options object. Null-checking its return value is
 * therefore wrong — check the stream and then outWidth/outHeight instead.
 */
@Singleton
class ImageLoader @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun readInfo(uri: Uri): SourceInfo = withContext(Dispatchers.IO) {
        val bounds = readBounds(uri)

        var width = bounds.outWidth
        var height = bounds.outHeight
        if (rotationOf(uri) % 180 != 0) {
            val swap = width
            width = height
            height = swap
        }

        SourceInfo(
            width = width,
            height = height,
            sizeBytes = sizeOf(uri),
            mimeType = bounds.outMimeType ?: context.contentResolver.getType(uri)
        )
    }

    /**
     * @param maxPixels upper bound on decoded pixels. 12 MP keeps a full-colour bitmap under ~48 MB.
     */
    suspend fun decode(uri: Uri, maxPixels: Int = 12_000_000): Bitmap = withContext(Dispatchers.IO) {
        val bounds = readBounds(uri)

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxPixels)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val decoded = openStream(uri).use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: error("That image could not be decoded. It may be corrupt or in an unsupported format.")

        applyRotation(decoded, rotationOf(uri))
    }

    /** Bounds-only decode. Throws with a readable message if the file is not an image. */
    private fun readBounds(uri: Uri): BitmapFactory.Options {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openStream(uri).use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            error("That file is not a readable image.")
        }
        return options
    }

    private fun openStream(uri: Uri): InputStream =
        context.contentResolver.openInputStream(uri)
            ?: error("Could not open the selected file. Try picking it again.")

    /** Some providers refuse statSize; a missing original size is not worth failing over. */
    private fun sizeOf(uri: Uri): Long = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
    }.getOrDefault(0L)

    private fun sampleSizeFor(width: Int, height: Int, maxPixels: Int): Int {
        var sample = 1
        while ((width.toLong() / sample) * (height.toLong() / sample) > maxPixels) sample *= 2
        return sample
    }

    private fun rotationOf(uri: Uri): Int = runCatching {
        openStream(uri).use { stream ->
            when (
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )
            ) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        }
    }.getOrDefault(0)

    private fun applyRotation(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }
}

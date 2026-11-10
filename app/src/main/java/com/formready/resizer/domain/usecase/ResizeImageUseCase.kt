package com.formready.resizer.domain.usecase

import com.formready.resizer.core.image.ImageLoader
import com.formready.resizer.core.image.SignatureCleaner
import com.formready.resizer.core.image.SizeTargeter
import com.formready.resizer.core.image.TargetResult
import com.formready.resizer.core.image.TargetSpec
import com.formready.resizer.core.image.flattenOnto
import com.formready.resizer.core.util.kbToBytes
import com.formready.resizer.domain.model.PhotoJob
import com.formready.resizer.domain.model.SignatureJob
import javax.inject.Inject
import kotlin.math.roundToInt

class ResizeImageUseCase @Inject constructor(
    private val imageLoader: ImageLoader,
    private val sizeTargeter: SizeTargeter,
    private val signatureCleaner: SignatureCleaner
) {

    suspend operator fun invoke(job: PhotoJob): TargetResult = run(job, null)

    suspend operator fun invoke(job: SignatureJob): TargetResult = run(job.photoJob, job)

    private suspend fun run(job: PhotoJob, signature: SignatureJob?): TargetResult {
        var bitmap = imageLoader.decode(job.sourceUri)
        try {
            if (job.flattenTransparency) {
                bitmap = bitmap.flattenOnto()
            }

            if (signature != null && (signature.trimBorders || signature.whitenBackground)) {
                bitmap = signatureCleaner.clean(
                    source = bitmap,
                    trim = signature.trimBorders,
                    whiten = signature.whitenBackground
                )
            }

            var width = job.widthValue?.let { job.unit.toPixels(it, job.dpi) }
            var height = job.heightValue?.let { job.unit.toPixels(it, job.dpi) }

            if (job.keepAspectRatio) {
                val ratio = bitmap.width.toDouble() / bitmap.height.toDouble()
                if (width != null && height == null) height = (width / ratio).roundToInt()
                if (height != null && width == null) width = (height * ratio).roundToInt()
            }

            return sizeTargeter.compress(
                bitmap,
                TargetSpec(
                    targetWidth = width?.coerceAtLeast(1),
                    targetHeight = height?.coerceAtLeast(1),
                    maxBytes = job.maxKb?.let { kbToBytes(it) }
                )
            )
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }
}

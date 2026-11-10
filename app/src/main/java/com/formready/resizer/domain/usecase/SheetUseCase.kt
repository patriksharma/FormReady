package com.formready.resizer.domain.usecase

import android.net.Uri
import com.formready.resizer.core.image.ImageLoader
import com.formready.resizer.core.image.SheetComposer
import com.formready.resizer.core.image.SheetSpec
import com.formready.resizer.core.image.SignatureCleaner
import com.formready.resizer.core.image.SizeTargeter
import com.formready.resizer.core.image.TargetResult
import com.formready.resizer.core.image.TargetSpec
import com.formready.resizer.core.util.kbToBytes
import javax.inject.Inject

/** Photo + signature drawn on one white sheet, then squeezed to the requested KB ceiling. */
class BuildSheetUseCase @Inject constructor(
    private val imageLoader: ImageLoader,
    private val sheetComposer: SheetComposer,
    private val signatureCleaner: SignatureCleaner,
    private val sizeTargeter: SizeTargeter
) {

    suspend operator fun invoke(
        photoUri: Uri,
        signatureUri: Uri,
        spec: SheetSpec,
        cleanSignature: Boolean,
        maxKb: Int?
    ): TargetResult {
        val photo = imageLoader.decode(photoUri)
        var signature = imageLoader.decode(signatureUri)
        var sheet: android.graphics.Bitmap? = null

        try {
            if (cleanSignature) {
                signature = signatureCleaner.clean(signature, trim = true, whiten = true)
            }
            sheet = sheetComposer.compose(photo, signature, spec)

            return sizeTargeter.compress(
                sheet,
                TargetSpec(maxBytes = maxKb?.let { kbToBytes(it) })
            )
        } finally {
            if (!photo.isRecycled) photo.recycle()
            if (!signature.isRecycled) signature.recycle()
            sheet?.takeIf { !it.isRecycled }?.recycle()
        }
    }
}

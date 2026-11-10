package com.formready.resizer.domain.model

import android.net.Uri
import com.formready.resizer.core.image.SizeUnit

data class PhotoJob(
    val sourceUri: Uri,
    val widthValue: Double?,
    val heightValue: Double?,
    val unit: SizeUnit,
    val dpi: Int,
    val maxKb: Int?,
    val keepAspectRatio: Boolean,
    /** PNG/WEBP/HEIC with transparency must be flattened before JPEG encoding. */
    val flattenTransparency: Boolean = false
)

data class SignatureJob(
    val photoJob: PhotoJob,
    val trimBorders: Boolean,
    val whitenBackground: Boolean
)

enum class ExportTool(val analyticsName: String) {
    PHOTO("photo"),
    SIGNATURE("signature"),
    COMPRESS_IMAGE("compress_image"),
    CONVERT_JPG("convert_jpg"),
    PHOTO_SIGNATURE_SHEET("photo_signature_sheet"),
    IMAGE_TO_PDF("image_to_pdf"),
    COMPRESS_PDF("compress_pdf"),
    PDF_TO_IMAGES("pdf_to_images"),
    MERGE_PDF("merge_pdf"),
    SPLIT_PDF("split_pdf")
}

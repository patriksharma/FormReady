package com.formready.resizer.domain.usecase

import android.net.Uri
import com.formready.resizer.core.pdf.ImagesToPdfBuilder
import com.formready.resizer.core.pdf.PdfBuildOptions
import com.formready.resizer.core.pdf.PdfBuildResult
import com.formready.resizer.core.pdf.PdfCompressResult
import com.formready.resizer.core.pdf.PdfCompressor
import com.formready.resizer.core.pdf.PdfPagesResult
import com.formready.resizer.core.pdf.PdfQuality
import com.formready.resizer.core.pdf.PdfRasterizer
import com.formready.resizer.core.pdf.PdfSource
import javax.inject.Inject

class BuildPdfUseCase @Inject constructor(
    private val builder: ImagesToPdfBuilder
) {
    suspend operator fun invoke(uris: List<Uri>, options: PdfBuildOptions): PdfBuildResult =
        builder.build(uris, options)
}

class CompressPdfUseCase @Inject constructor(
    private val compressor: PdfCompressor
) {
    suspend operator fun invoke(uri: Uri, quality: PdfQuality, maxKb: Int?): PdfCompressResult =
        compressor.compress(uri, quality, maxKb?.let { it.toLong() * 1024L })
}

class PdfToImagesUseCase @Inject constructor(
    private val rasterizer: PdfRasterizer
) {
    suspend fun pageCount(uri: Uri): Int = rasterizer.pageCount(uri)

    suspend operator fun invoke(uri: Uri, pages: List<Int>?, quality: PdfQuality): PdfPagesResult =
        rasterizer.renderToJpegs(uri, pages?.takeIf { it.isNotEmpty() }, quality)
}

class MergePdfUseCase @Inject constructor(
    private val rasterizer: PdfRasterizer
) {
    suspend operator fun invoke(uris: List<Uri>, quality: PdfQuality, maxKb: Int?): PdfBuildResult =
        rasterizer.rebuild(
            sources = uris.map { PdfSource(it) },
            quality = quality,
            maxBytes = maxKb?.let { it.toLong() * 1024L }
        )
}

class SplitPdfUseCase @Inject constructor(
    private val rasterizer: PdfRasterizer
) {
    suspend fun pageCount(uri: Uri): Int = rasterizer.pageCount(uri)

    suspend operator fun invoke(
        uri: Uri,
        pages: List<Int>,
        quality: PdfQuality,
        maxKb: Int?
    ): PdfBuildResult {
        require(pages.isNotEmpty()) { "Choose at least one page to extract." }
        return rasterizer.rebuild(
            sources = listOf(PdfSource(uri, pages)),
            quality = quality,
            maxBytes = maxKb?.let { it.toLong() * 1024L }
        )
    }
}

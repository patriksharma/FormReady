package com.formready.resizer.ui.pdftoimages

import android.app.Activity
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.formready.resizer.core.pdf.PageRanges
import com.formready.resizer.core.pdf.PdfQuality
import com.formready.resizer.core.storage.FileExporter
import com.formready.resizer.domain.model.ExportTool
import com.formready.resizer.domain.usecase.ExportCoordinator
import com.formready.resizer.domain.usecase.ExportOutcome
import com.formready.resizer.domain.usecase.PdfToImagesUseCase
import com.formready.resizer.domain.usecase.QuotaSnapshot
import com.formready.resizer.domain.usecase.RewardOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PagesResultInfo(
    val pageCount: Int,
    val totalBytes: Long
)

data class PdfToImagesState(
    val sourceUri: Uri? = null,
    val sourceName: String? = null,
    val totalPages: Int = 0,
    val rangeText: String = "",
    val quality: PdfQuality = PdfQuality.HIGH,
    val processing: Boolean = false,
    val result: PagesResultInfo? = null,
    val message: String? = null,
    val quota: QuotaSnapshot = QuotaSnapshot(),
    val showLimitDialog: Boolean = false
) {
    val selectedPages: List<Int> get() = PageRanges.parse(rangeText, totalPages)
    val canRun: Boolean get() = sourceUri != null && !processing
}

@HiltViewModel
class PdfToImagesViewModel @Inject constructor(
    private val pdfToImages: PdfToImagesUseCase,
    private val fileExporter: FileExporter,
    private val coordinator: ExportCoordinator
) : ViewModel() {

    private val _state = MutableStateFlow(PdfToImagesState())
    val state: StateFlow<PdfToImagesState> = _state.asStateFlow()

    private var pages: List<ByteArray> = emptyList()

    init {
        viewModelScope.launch {
            coordinator.quota.collect { _state.value = _state.value.copy(quota = it) }
        }
    }

    fun onPdfPicked(uri: Uri) {
        viewModelScope.launch {
            pages = emptyList()
            val count = runCatching { pdfToImages.pageCount(uri) }.getOrDefault(0)
            _state.value = _state.value.copy(
                sourceUri = uri,
                sourceName = fileExporter.displayNameOf(uri),
                totalPages = count,
                result = null
            )
        }
    }

    fun setRange(value: String) { _state.value = _state.value.copy(rangeText = value, result = null) }
    fun setQuality(value: PdfQuality) { _state.value = _state.value.copy(quality = value, result = null) }
    fun dismissLimitDialog() { _state.value = _state.value.copy(showLimitDialog = false) }
    fun consumeMessage() { _state.value = _state.value.copy(message = null) }

    fun run() {
        val current = _state.value
        val uri = current.sourceUri ?: return
        _state.value = current.copy(processing = true, result = null, message = null)

        viewModelScope.launch {
            runCatching { pdfToImages(uri, current.selectedPages, current.quality) }
                .onSuccess { out ->
                    pages = out.pages
                    _state.value = _state.value.copy(
                        processing = false,
                        result = PagesResultInfo(out.pages.size, out.totalBytes)
                    )
                }
                .onFailure { error ->
                    coordinator.recordFailure(ExportTool.PDF_TO_IMAGES, error)
                    _state.value = _state.value.copy(
                        processing = false,
                        message = error.message ?: "Could not read that PDF."
                    )
                }
        }
    }

    fun onResultReady(activity: Activity) = coordinator.onResultReady(activity, _state.value.quota.entitlements)

    fun save() = export {
        fileExporter.saveImages(pages, "FormReady_page")
        _state.value = _state.value.copy(message = "${pages.size} images saved to Pictures/FormReady")
    }

    fun share(onReady: (List<Uri>) -> Unit) = export {
        onReady(fileExporter.shareableUris(pages, "FormReady_page"))
    }

    private fun export(block: suspend () -> Unit) {
        if (pages.isEmpty()) return
        viewModelScope.launch {
            when (val outcome = coordinator.export(ExportTool.PDF_TO_IMAGES) { block() }) {
                ExportOutcome.LimitReached -> _state.value = _state.value.copy(showLimitDialog = true)
                is ExportOutcome.Failed -> _state.value = _state.value.copy(message = outcome.message)
                ExportOutcome.Success -> Unit
            }
        }
    }

    fun watchRewardedAd(activity: Activity) {
        coordinator.watchRewardedAd(activity, ExportTool.PDF_TO_IMAGES) { outcome ->
            viewModelScope.launch {
                val message = when (outcome) {
                    RewardOutcome.Granted -> when (coordinator.grantBonusExport()) {
                        RewardOutcome.Granted -> "1 extra export added. Tap Save or Share again."
                        else -> "You have already claimed today's bonus exports."
                    }
                    RewardOutcome.CapReached -> "You have already claimed today's bonus exports."
                    RewardOutcome.Loading -> "Loading ad…"
                    is RewardOutcome.Unavailable -> outcome.message
                }
                _state.value = _state.value.copy(
                    showLimitDialog = outcome == RewardOutcome.Loading && _state.value.showLimitDialog,
                    message = message
                )
            }
        }
    }
}

package com.formready.resizer.ui.splitpdf

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
import com.formready.resizer.domain.usecase.QuotaSnapshot
import com.formready.resizer.domain.usecase.RewardOutcome
import com.formready.resizer.domain.usecase.SplitPdfUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SplitResultInfo(
    val pageCount: Int,
    val sizeBytes: Long,
    val withinLimit: Boolean,
    val requestedKb: Int?
)

data class SplitPdfState(
    val sourceUri: Uri? = null,
    val sourceName: String? = null,
    val totalPages: Int = 0,
    val rangeText: String = "",
    val quality: PdfQuality = PdfQuality.BALANCED,
    val maxKbText: String = "",
    val processing: Boolean = false,
    val result: SplitResultInfo? = null,
    val message: String? = null,
    val quota: QuotaSnapshot = QuotaSnapshot(),
    val showLimitDialog: Boolean = false
) {
    val selectedPages: List<Int> get() = PageRanges.parse(rangeText, totalPages)
    val canSplit: Boolean get() = sourceUri != null && selectedPages.isNotEmpty() && !processing
}

@HiltViewModel
class SplitPdfViewModel @Inject constructor(
    private val splitPdf: SplitPdfUseCase,
    private val fileExporter: FileExporter,
    private val coordinator: ExportCoordinator
) : ViewModel() {

    private val _state = MutableStateFlow(SplitPdfState())
    val state: StateFlow<SplitPdfState> = _state.asStateFlow()

    private var resultBytes: ByteArray? = null

    init {
        viewModelScope.launch {
            coordinator.quota.collect { _state.value = _state.value.copy(quota = it) }
        }
    }

    fun onPdfPicked(uri: Uri) {
        viewModelScope.launch {
            resultBytes = null
            val count = runCatching { splitPdf.pageCount(uri) }.getOrDefault(0)
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
    fun setMaxKb(value: String) { _state.value = _state.value.copy(maxKbText = value) }
    fun dismissLimitDialog() { _state.value = _state.value.copy(showLimitDialog = false) }
    fun consumeMessage() { _state.value = _state.value.copy(message = null) }

    fun split() {
        val current = _state.value
        val uri = current.sourceUri ?: return
        _state.value = current.copy(processing = true, result = null, message = null)

        viewModelScope.launch {
            val maxKb = current.maxKbText.toIntOrNull()?.takeIf { it > 0 }
            runCatching { splitPdf(uri, current.selectedPages, current.quality, maxKb) }
                .onSuccess { out ->
                    resultBytes = out.bytes
                    _state.value = _state.value.copy(
                        processing = false,
                        result = SplitResultInfo(out.pageCount, out.sizeBytes, out.withinLimit, maxKb)
                    )
                }
                .onFailure { error ->
                    coordinator.recordFailure(ExportTool.SPLIT_PDF, error)
                    _state.value = _state.value.copy(
                        processing = false,
                        message = error.message ?: "Could not extract those pages."
                    )
                }
        }
    }

    fun onResultReady(activity: Activity) = coordinator.onResultReady(activity, _state.value.quota.entitlements)

    fun save() = export { bytes ->
        fileExporter.savePdf(bytes, fileExporter.timestampedName("FormReady_pages", "pdf"))
        _state.value = _state.value.copy(message = "Saved to Documents/FormReady")
    }

    fun share(onReady: (Uri) -> Unit) = export { bytes ->
        onReady(fileExporter.shareableUri(bytes, fileExporter.timestampedName("FormReady_pages", "pdf")))
    }

    private fun export(block: suspend (ByteArray) -> Unit) {
        val bytes = resultBytes ?: return
        viewModelScope.launch {
            when (val outcome = coordinator.export(ExportTool.SPLIT_PDF) { block(bytes) }) {
                ExportOutcome.LimitReached -> _state.value = _state.value.copy(showLimitDialog = true)
                is ExportOutcome.Failed -> _state.value = _state.value.copy(message = outcome.message)
                ExportOutcome.Success -> Unit
            }
        }
    }

    fun watchRewardedAd(activity: Activity) {
        coordinator.watchRewardedAd(activity, ExportTool.SPLIT_PDF) { outcome ->
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

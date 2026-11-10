package com.formready.resizer.ui.mergepdf

import android.app.Activity
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.formready.resizer.core.pdf.PdfQuality
import com.formready.resizer.core.storage.FileExporter
import com.formready.resizer.domain.model.ExportTool
import com.formready.resizer.domain.usecase.ExportCoordinator
import com.formready.resizer.domain.usecase.ExportOutcome
import com.formready.resizer.domain.usecase.MergePdfUseCase
import com.formready.resizer.domain.usecase.QuotaSnapshot
import com.formready.resizer.domain.usecase.RewardOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MergeEntry(val uri: Uri, val name: String)

data class MergeResultInfo(
    val pageCount: Int,
    val sizeBytes: Long,
    val withinLimit: Boolean,
    val requestedKb: Int?
)

data class MergePdfState(
    val files: List<MergeEntry> = emptyList(),
    val quality: PdfQuality = PdfQuality.BALANCED,
    val maxKbText: String = "",
    val processing: Boolean = false,
    val result: MergeResultInfo? = null,
    val message: String? = null,
    val quota: QuotaSnapshot = QuotaSnapshot(),
    val showLimitDialog: Boolean = false
) {
    val canMerge: Boolean get() = files.size >= 2 && !processing
}

@HiltViewModel
class MergePdfViewModel @Inject constructor(
    private val mergePdf: MergePdfUseCase,
    private val fileExporter: FileExporter,
    private val coordinator: ExportCoordinator
) : ViewModel() {

    private val _state = MutableStateFlow(MergePdfState())
    val state: StateFlow<MergePdfState> = _state.asStateFlow()

    private var resultBytes: ByteArray? = null

    init {
        viewModelScope.launch {
            coordinator.quota.collect { _state.value = _state.value.copy(quota = it) }
        }
    }

    fun addFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val existing = _state.value.files.map { it.uri }.toSet()
        val additions = uris.filterNot { it in existing }.map { uri ->
            MergeEntry(uri, fileExporter.displayNameOf(uri) ?: "PDF")
        }
        resultBytes = null
        _state.value = _state.value.copy(files = _state.value.files + additions, result = null)
    }

    fun remove(index: Int) {
        val files = _state.value.files.toMutableList()
        if (index !in files.indices) return
        files.removeAt(index)
        _state.value = _state.value.copy(files = files, result = null)
    }

    fun move(from: Int, to: Int) {
        val files = _state.value.files.toMutableList()
        if (from !in files.indices || to !in files.indices) return
        files.add(to, files.removeAt(from))
        _state.value = _state.value.copy(files = files, result = null)
    }

    fun setQuality(value: PdfQuality) { _state.value = _state.value.copy(quality = value, result = null) }
    fun setMaxKb(value: String) { _state.value = _state.value.copy(maxKbText = value) }
    fun dismissLimitDialog() { _state.value = _state.value.copy(showLimitDialog = false) }
    fun consumeMessage() { _state.value = _state.value.copy(message = null) }

    fun merge() {
        val current = _state.value
        if (current.files.size < 2) return
        _state.value = current.copy(processing = true, result = null, message = null)

        viewModelScope.launch {
            val maxKb = current.maxKbText.toIntOrNull()?.takeIf { it > 0 }
            runCatching { mergePdf(current.files.map { it.uri }, current.quality, maxKb) }
                .onSuccess { out ->
                    resultBytes = out.bytes
                    _state.value = _state.value.copy(
                        processing = false,
                        result = MergeResultInfo(out.pageCount, out.sizeBytes, out.withinLimit, maxKb)
                    )
                }
                .onFailure { error ->
                    coordinator.recordFailure(ExportTool.MERGE_PDF, error)
                    _state.value = _state.value.copy(
                        processing = false,
                        message = error.message ?: "Could not merge those PDFs."
                    )
                }
        }
    }

    fun onResultReady(activity: Activity) = coordinator.onResultReady(activity, _state.value.quota.entitlements)

    fun save() = export { bytes ->
        fileExporter.savePdf(bytes, fileExporter.timestampedName("FormReady_merged", "pdf"))
        _state.value = _state.value.copy(message = "Saved to Documents/FormReady")
    }

    fun share(onReady: (Uri) -> Unit) = export { bytes ->
        onReady(fileExporter.shareableUri(bytes, fileExporter.timestampedName("FormReady_merged", "pdf")))
    }

    private fun export(block: suspend (ByteArray) -> Unit) {
        val bytes = resultBytes ?: return
        viewModelScope.launch {
            when (val outcome = coordinator.export(ExportTool.MERGE_PDF) { block(bytes) }) {
                ExportOutcome.LimitReached -> _state.value = _state.value.copy(showLimitDialog = true)
                is ExportOutcome.Failed -> _state.value = _state.value.copy(message = outcome.message)
                ExportOutcome.Success -> Unit
            }
        }
    }

    fun watchRewardedAd(activity: Activity) {
        coordinator.watchRewardedAd(activity, ExportTool.MERGE_PDF) { outcome ->
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

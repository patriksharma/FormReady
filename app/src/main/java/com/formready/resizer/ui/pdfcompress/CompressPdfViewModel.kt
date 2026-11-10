package com.formready.resizer.ui.pdfcompress

import android.net.Uri
import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.formready.resizer.core.pdf.PdfQuality
import com.formready.resizer.core.storage.FileExporter
import com.formready.resizer.data.ads.AdsManager
import com.formready.resizer.data.billing.Entitlements
import com.formready.resizer.data.prefs.SettingsRepository
import com.formready.resizer.domain.model.ExportTool
import com.formready.resizer.domain.usecase.CompressPdfUseCase
import com.formready.resizer.domain.usecase.ExportGate
import com.formready.resizer.domain.usecase.ExportPermission
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CompressResultInfo(
    val sizeBytes: Long,
    val originalBytes: Long,
    val pageCount: Int,
    val savedPercent: Int,
    val withinLimit: Boolean,
    val requestedKb: Int?
)

data class CompressPdfState(
    val sourceUri: Uri? = null,
    val sourceName: String? = null,
    val sourceSize: Long = 0L,
    val quality: PdfQuality = PdfQuality.BALANCED,
    val maxKbText: String = "",
    val processing: Boolean = false,
    val result: CompressResultInfo? = null,
    val message: String? = null,
    val exportsToday: Int = 0,
    val bonusToday: Int = 0,
    val entitlements: Entitlements = Entitlements(),
    val showLimitDialog: Boolean = false
) {
    val canCompress: Boolean get() = sourceUri != null && !processing
    val exportsLeft: Int
        get() = if (entitlements.unlimitedExports) Int.MAX_VALUE
        else (SettingsRepository.FREE_EXPORTS_PER_DAY + bonusToday - exportsToday).coerceAtLeast(0)
}

@HiltViewModel
class CompressPdfViewModel @Inject constructor(
    private val compressPdf: CompressPdfUseCase,
    private val fileExporter: FileExporter,
    private val settings: SettingsRepository,
    private val exportGate: ExportGate,
    private val adsManager: AdsManager
) : ViewModel() {

    private val _state = MutableStateFlow(CompressPdfState())
    val state: StateFlow<CompressPdfState> = _state.asStateFlow()

    private var resultBytes: ByteArray? = null

    init {
        viewModelScope.launch {
            combine(
                settings.exportsToday,
                settings.bonusToday,
                settings.entitlements
            ) { used, bonus, entitlements ->
                _state.value.copy(exportsToday = used, bonusToday = bonus, entitlements = entitlements)
            }.collect { _state.value = it }
        }
    }

    fun onPdfPicked(uri: Uri) {
        viewModelScope.launch {
            resultBytes = null
            _state.value = _state.value.copy(
                sourceUri = uri,
                sourceName = fileExporter.displayNameOf(uri),
                sourceSize = fileExporter.sizeOf(uri),
                result = null
            )
        }
    }

    fun setQuality(value: PdfQuality) { _state.value = _state.value.copy(quality = value, result = null) }
    fun setMaxKb(value: String) { _state.value = _state.value.copy(maxKbText = value) }
    fun dismissLimitDialog() { _state.value = _state.value.copy(showLimitDialog = false) }
    fun consumeMessage() { _state.value = _state.value.copy(message = null) }

    /** Natural transition point: the finished result is about to be shown. */
    fun onResultReady(activity: Activity) {
        if (_state.value.entitlements.adsRemoved) return
        adsManager.showInterstitial(activity)
    }

    /** Explicit user tap only. Adds one export once the rewarded ad completes. */
    fun watchRewardedAd(activity: Activity) {
        exportGate.rewardOffered(ExportTool.COMPRESS_PDF)
        adsManager.showRewarded(
            activity = activity,
            onReward = {
                viewModelScope.launch {
                    _state.value = if (exportGate.grantBonusExport()) {
                        _state.value.copy(
                            showLimitDialog = false,
                            message = "1 extra export added. Tap Save or Share again."
                        )
                    } else {
                        _state.value.copy(message = "You have already claimed today's bonus exports.")
                    }
                }
            },
            onUnavailable = { reason ->
                _state.value = _state.value.copy(message = reason)
            },
            onLoading = {
                _state.value = _state.value.copy(message = "Loading ad…")
            }
        )
    }

    fun compress() {
        val current = _state.value
        val uri = current.sourceUri ?: return
        _state.value = current.copy(processing = true, result = null, message = null)

        viewModelScope.launch {
            val maxKb = current.maxKbText.toIntOrNull()?.takeIf { it > 0 }
            runCatching { compressPdf(uri, current.quality, maxKb) }
                .onSuccess { out ->
                    resultBytes = out.bytes
                    _state.value = _state.value.copy(
                        processing = false,
                        result = CompressResultInfo(
                            sizeBytes = out.sizeBytes,
                            originalBytes = out.originalBytes,
                            pageCount = out.pageCount,
                            savedPercent = out.savedPercent,
                            withinLimit = out.withinLimit,
                            requestedKb = maxKb
                        )
                    )
                }
                .onFailure { error ->
                    exportGate.recordFailure(ExportTool.COMPRESS_PDF, error)
                    _state.value = _state.value.copy(
                        processing = false,
                        message = error.message ?: "Could not compress that PDF."
                    )
                }
        }
    }

    fun save() = export { bytes ->
        fileExporter.savePdf(bytes, fileExporter.timestampedName("FormReady_compressed", "pdf"))
        _state.value = _state.value.copy(message = "Saved to Documents/FormReady")
    }

    fun share(onReady: (Uri) -> Unit) = export { bytes ->
        onReady(fileExporter.shareableUri(bytes, fileExporter.timestampedName("FormReady_compressed", "pdf")))
    }

    private fun export(block: suspend (ByteArray) -> Unit) {
        val bytes = resultBytes ?: return
        viewModelScope.launch {
            when (exportGate.check()) {
                ExportPermission.LimitReached -> _state.value = _state.value.copy(showLimitDialog = true)
                ExportPermission.Allowed -> runCatching {
                    block(bytes)
                    exportGate.recordSuccess(ExportTool.COMPRESS_PDF)
                }.onFailure {
                    exportGate.recordFailure(ExportTool.COMPRESS_PDF, it)
                    _state.value = _state.value.copy(message = it.message ?: "Export failed.")
                }
            }
        }
    }
}

package com.formready.resizer.ui.imagetopdf

import android.net.Uri
import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.formready.resizer.core.pdf.PageOrientation
import com.formready.resizer.core.pdf.PageSize
import com.formready.resizer.core.pdf.PdfBuildOptions
import com.formready.resizer.core.pdf.PdfQuality
import com.formready.resizer.core.storage.FileExporter
import com.formready.resizer.data.ads.AdsManager
import com.formready.resizer.data.billing.Entitlements
import com.formready.resizer.data.prefs.SettingsRepository
import com.formready.resizer.domain.model.ExportTool
import com.formready.resizer.domain.usecase.BuildPdfUseCase
import com.formready.resizer.domain.usecase.ExportGate
import com.formready.resizer.domain.usecase.ExportPermission
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PdfResultInfo(
    val sizeBytes: Long,
    val pageCount: Int,
    val withinLimit: Boolean,
    val requestedKb: Int?
)

data class ImagesToPdfState(
    val images: List<Uri> = emptyList(),
    val pageSize: PageSize = PageSize.A4,
    val orientation: PageOrientation = PageOrientation.PORTRAIT,
    val quality: PdfQuality = PdfQuality.BALANCED,
    val maxKbText: String = "",
    val processing: Boolean = false,
    val result: PdfResultInfo? = null,
    val message: String? = null,
    val exportsToday: Int = 0,
    val bonusToday: Int = 0,
    val entitlements: Entitlements = Entitlements(),
    val showLimitDialog: Boolean = false
) {
    val canBuild: Boolean get() = images.isNotEmpty() && !processing
    val exportsLeft: Int
        get() = if (entitlements.unlimitedExports) Int.MAX_VALUE
        else (SettingsRepository.FREE_EXPORTS_PER_DAY + bonusToday - exportsToday).coerceAtLeast(0)
}

@HiltViewModel
class ImagesToPdfViewModel @Inject constructor(
    private val buildPdf: BuildPdfUseCase,
    private val fileExporter: FileExporter,
    private val settings: SettingsRepository,
    private val exportGate: ExportGate,
    private val adsManager: AdsManager
) : ViewModel() {

    private val _state = MutableStateFlow(ImagesToPdfState())
    val state: StateFlow<ImagesToPdfState> = _state.asStateFlow()

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

    fun addImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _state.value = _state.value.copy(
            images = _state.value.images + uris.filterNot { it in _state.value.images },
            result = null
        )
        resultBytes = null
    }

    fun removeImage(index: Int) {
        val images = _state.value.images.toMutableList()
        if (index !in images.indices) return
        images.removeAt(index)
        _state.value = _state.value.copy(images = images, result = null)
    }

    fun moveImage(from: Int, to: Int) {
        val images = _state.value.images.toMutableList()
        if (from !in images.indices || to !in images.indices) return
        val item = images.removeAt(from)
        images.add(to, item)
        _state.value = _state.value.copy(images = images, result = null)
    }

    fun setPageSize(value: PageSize) { _state.value = _state.value.copy(pageSize = value, result = null) }
    fun setOrientation(value: PageOrientation) { _state.value = _state.value.copy(orientation = value, result = null) }
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
        exportGate.rewardOffered(ExportTool.IMAGE_TO_PDF)
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

    fun build() {
        val current = _state.value
        if (current.images.isEmpty()) return
        _state.value = current.copy(processing = true, result = null, message = null)

        viewModelScope.launch {
            val maxKb = current.maxKbText.toIntOrNull()?.takeIf { it > 0 }
            runCatching {
                buildPdf(
                    current.images,
                    PdfBuildOptions(
                        pageSize = current.pageSize,
                        orientation = current.orientation,
                        quality = current.quality,
                        maxBytes = maxKb?.let { it.toLong() * 1024L }
                    )
                )
            }.onSuccess { out ->
                resultBytes = out.bytes
                _state.value = _state.value.copy(
                    processing = false,
                    result = PdfResultInfo(out.sizeBytes, out.pageCount, out.withinLimit, maxKb)
                )
            }.onFailure { error ->
                exportGate.recordFailure(ExportTool.IMAGE_TO_PDF, error)
                _state.value = _state.value.copy(processing = false, message = error.message ?: "Could not build the PDF.")
            }
        }
    }

    fun save() = export { bytes ->
        fileExporter.savePdf(bytes, fileExporter.timestampedName("FormReady", "pdf"))
        _state.value = _state.value.copy(message = "Saved to Documents/FormReady")
    }

    fun share(onReady: (android.net.Uri) -> Unit) = export { bytes ->
        onReady(fileExporter.shareableUri(bytes, fileExporter.timestampedName("FormReady", "pdf")))
    }

    private fun export(block: suspend (ByteArray) -> Unit) {
        val bytes = resultBytes ?: return
        viewModelScope.launch {
            when (exportGate.check()) {
                ExportPermission.LimitReached -> _state.value = _state.value.copy(showLimitDialog = true)
                ExportPermission.Allowed -> runCatching {
                    block(bytes)
                    exportGate.recordSuccess(ExportTool.IMAGE_TO_PDF)
                }.onFailure {
                    exportGate.recordFailure(ExportTool.IMAGE_TO_PDF, it)
                    _state.value = _state.value.copy(message = it.message ?: "Export failed.")
                }
            }
        }
    }
}

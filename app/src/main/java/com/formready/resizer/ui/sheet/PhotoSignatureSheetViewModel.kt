package com.formready.resizer.ui.sheet

import android.app.Activity
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.formready.resizer.core.image.SheetLayout
import com.formready.resizer.core.image.SheetSpec
import com.formready.resizer.core.storage.FileExporter
import com.formready.resizer.domain.model.ExportTool
import com.formready.resizer.domain.usecase.BuildSheetUseCase
import com.formready.resizer.domain.usecase.ExportCoordinator
import com.formready.resizer.domain.usecase.ExportOutcome
import com.formready.resizer.domain.usecase.QuotaSnapshot
import com.formready.resizer.domain.usecase.RewardOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SheetResultInfo(
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val withinLimit: Boolean,
    val requestedKb: Int?,
    val preview: ImageBitmap?
)

data class SheetState(
    val photoUri: Uri? = null,
    val signatureUri: Uri? = null,
    val layout: SheetLayout = SheetLayout.STACKED,
    val widthText: String = "600",
    val heightText: String = "800",
    val maxKbText: String = "",
    val cleanSignature: Boolean = true,
    val drawBorder: Boolean = false,
    val processing: Boolean = false,
    val result: SheetResultInfo? = null,
    val message: String? = null,
    val quota: QuotaSnapshot = QuotaSnapshot(),
    val showLimitDialog: Boolean = false
) {
    val canBuild: Boolean get() = photoUri != null && signatureUri != null && !processing
}

@HiltViewModel
class PhotoSignatureSheetViewModel @Inject constructor(
    private val buildSheet: BuildSheetUseCase,
    private val fileExporter: FileExporter,
    private val coordinator: ExportCoordinator
) : ViewModel() {

    private val _state = MutableStateFlow(SheetState())
    val state: StateFlow<SheetState> = _state.asStateFlow()

    private var resultBytes: ByteArray? = null

    init {
        viewModelScope.launch {
            coordinator.quota.collect { _state.value = _state.value.copy(quota = it) }
        }
    }

    fun onPhotoPicked(uri: Uri) { _state.value = _state.value.copy(photoUri = uri, result = null) }
    fun onSignaturePicked(uri: Uri) { _state.value = _state.value.copy(signatureUri = uri, result = null) }
    fun setLayout(value: SheetLayout) { _state.value = _state.value.copy(layout = value, result = null) }
    fun setWidth(value: String) { _state.value = _state.value.copy(widthText = value) }
    fun setHeight(value: String) { _state.value = _state.value.copy(heightText = value) }
    fun setMaxKb(value: String) { _state.value = _state.value.copy(maxKbText = value) }
    fun setCleanSignature(value: Boolean) { _state.value = _state.value.copy(cleanSignature = value) }
    fun setDrawBorder(value: Boolean) { _state.value = _state.value.copy(drawBorder = value) }
    fun dismissLimitDialog() { _state.value = _state.value.copy(showLimitDialog = false) }
    fun consumeMessage() { _state.value = _state.value.copy(message = null) }

    fun build() {
        val current = _state.value
        val photo = current.photoUri ?: return
        val signature = current.signatureUri ?: return
        _state.value = current.copy(processing = true, result = null, message = null)

        viewModelScope.launch {
            val maxKb = current.maxKbText.toIntOrNull()?.takeIf { it > 0 }
            runCatching {
                val out = buildSheet(
                    photoUri = photo,
                    signatureUri = signature,
                    spec = SheetSpec(
                        widthPx = current.widthText.toIntOrNull() ?: 600,
                        heightPx = current.heightText.toIntOrNull() ?: 800,
                        layout = current.layout,
                        drawBorder = current.drawBorder
                    ),
                    cleanSignature = current.cleanSignature,
                    maxKb = maxKb
                )
                val preview = withContext(Dispatchers.Default) {
                    BitmapFactory.decodeByteArray(out.bytes, 0, out.bytes.size)?.asImageBitmap()
                }
                resultBytes = out.bytes
                SheetResultInfo(out.width, out.height, out.sizeBytes, out.withinLimit, maxKb, preview)
            }.onSuccess { info ->
                _state.value = _state.value.copy(processing = false, result = info)
            }.onFailure { error ->
                coordinator.recordFailure(ExportTool.PHOTO_SIGNATURE_SHEET, error)
                _state.value = _state.value.copy(
                    processing = false,
                    message = error.message ?: "Could not build the sheet."
                )
            }
        }
    }

    fun onResultReady(activity: Activity) = coordinator.onResultReady(activity, _state.value.quota.entitlements)

    fun save() = export { bytes ->
        fileExporter.saveImage(bytes, fileExporter.timestampedName("FormReady_sheet", "jpg"))
        _state.value = _state.value.copy(message = "Saved to Pictures/FormReady")
    }

    fun share(onReady: (Uri) -> Unit) = export { bytes ->
        onReady(fileExporter.shareableUri(bytes, fileExporter.timestampedName("FormReady_sheet", "jpg")))
    }

    private fun export(block: suspend (ByteArray) -> Unit) {
        val bytes = resultBytes ?: return
        viewModelScope.launch {
            when (val outcome = coordinator.export(ExportTool.PHOTO_SIGNATURE_SHEET) { block(bytes) }) {
                ExportOutcome.LimitReached -> _state.value = _state.value.copy(showLimitDialog = true)
                is ExportOutcome.Failed -> _state.value = _state.value.copy(message = outcome.message)
                ExportOutcome.Success -> Unit
            }
        }
    }

    fun watchRewardedAd(activity: Activity) {
        coordinator.watchRewardedAd(activity, ExportTool.PHOTO_SIGNATURE_SHEET) { outcome ->
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

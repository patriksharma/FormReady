package com.formready.resizer.ui.imagetool

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.formready.resizer.core.image.ImageLoader
import com.formready.resizer.core.image.SizeUnit
import com.formready.resizer.core.image.SourceInfo
import android.app.Activity
import com.formready.resizer.core.storage.FileExporter
import com.formready.resizer.data.ads.AdsManager
import com.formready.resizer.data.billing.Entitlements
import com.formready.resizer.data.prefs.SettingsRepository
import com.formready.resizer.data.presets.Preset
import com.formready.resizer.data.presets.PresetRepository
import com.formready.resizer.domain.model.ExportTool
import com.formready.resizer.domain.model.PhotoJob
import com.formready.resizer.domain.model.SignatureJob
import com.formready.resizer.domain.usecase.ExportGate
import com.formready.resizer.domain.usecase.ExportPermission
import com.formready.resizer.domain.usecase.ResizeImageUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

data class ImageResultInfo(
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val quality: Int,
    val withinLimit: Boolean,
    val requestedKb: Int?,
    val preview: ImageBitmap?
)

data class ImageToolState(
    val sourceUri: Uri? = null,
    val sourceInfo: SourceInfo? = null,
    val widthText: String = "",
    val heightText: String = "",
    val unit: SizeUnit = SizeUnit.PX,
    val dpi: Int = 300,
    val maxKbText: String = "",
    val keepAspect: Boolean = true,
    val trimBorders: Boolean = true,
    val whitenBackground: Boolean = true,
    val processing: Boolean = false,
    val result: ImageResultInfo? = null,
    val message: String? = null,
    val exportsToday: Int = 0,
    val bonusToday: Int = 0,
    val entitlements: Entitlements = Entitlements(),
    val presets: List<Preset> = emptyList(),
    val showLimitDialog: Boolean = false
) {
    val canProcess: Boolean get() = sourceUri != null && !processing
    val exportsLeft: Int
        get() = if (entitlements.unlimitedExports) Int.MAX_VALUE
        else (SettingsRepository.FREE_EXPORTS_PER_DAY + bonusToday - exportsToday).coerceAtLeast(0)
}

/**
 * Shared engine for the photo and signature screens. Both do the same thing;
 * the signature flow just adds trim / whiten before the resize.
 */
abstract class ImageToolViewModel(
    private val imageLoader: ImageLoader,
    private val resizeImage: ResizeImageUseCase,
    private val fileExporter: FileExporter,
    private val settings: SettingsRepository,
    private val presetRepository: PresetRepository,
    private val exportGate: ExportGate,
    private val adsManager: AdsManager
) : ViewModel() {

    abstract val tool: ExportTool
    protected abstract fun presetSource(repository: PresetRepository): Flow<List<Preset>>

    /** Compress-only and convert tools keep the original dimensions, so they hide the size fields. */
    open val showsSizeFields: Boolean get() = true

    /** True when the source may carry transparency that must be flattened before JPEG encoding. */
    open val flattensTransparency: Boolean get() = false

    protected val _state = MutableStateFlow(ImageToolState())
    val state: StateFlow<ImageToolState> = _state.asStateFlow()

    private var resultBytes: ByteArray? = null

    init {
        viewModelScope.launch {
            combine(
                settings.exportsToday,
                settings.bonusToday,
                settings.entitlements,
                settings.defaultDpi,
                presetSource(presetRepository)
            ) { used, bonus, entitlements, dpi, presets ->
                _state.value.copy(
                    exportsToday = used,
                    bonusToday = bonus,
                    entitlements = entitlements,
                    dpi = dpi,
                    presets = presets.filter { !it.premium || entitlements.premiumTemplates }
                )
            }.collect { _state.value = it }
        }
    }

    fun onImagePicked(uri: Uri) {
        viewModelScope.launch {
            runCatching { imageLoader.readInfo(uri) }
                .onSuccess { info ->
                    resultBytes = null
                    val current = _state.value
                    _state.value = current.copy(
                        sourceUri = uri,
                        sourceInfo = info,
                        result = null,
                        widthText = if (current.widthText.isBlank() && current.unit == SizeUnit.PX)
                            info.width.toString() else current.widthText,
                        heightText = if (current.heightText.isBlank() && current.unit == SizeUnit.PX)
                            info.height.toString() else current.heightText
                    )
                }
                .onFailure { showMessage(it.message ?: "Could not read that image.") }
        }
    }

    fun onWidthChanged(value: String) { _state.value = _state.value.copy(widthText = value) }
    fun onHeightChanged(value: String) { _state.value = _state.value.copy(heightText = value) }
    fun onMaxKbChanged(value: String) { _state.value = _state.value.copy(maxKbText = value) }
    fun onKeepAspectChanged(value: Boolean) { _state.value = _state.value.copy(keepAspect = value) }
    fun onTrimChanged(value: Boolean) { _state.value = _state.value.copy(trimBorders = value) }
    fun onWhitenChanged(value: Boolean) { _state.value = _state.value.copy(whitenBackground = value) }
    fun dismissLimitDialog() { _state.value = _state.value.copy(showLimitDialog = false) }

    fun onUnitChanged(unit: SizeUnit) {
        val current = _state.value
        val widthPx = current.widthText.toDoubleOrNull()?.let { current.unit.toPixels(it, current.dpi) }
        val heightPx = current.heightText.toDoubleOrNull()?.let { current.unit.toPixels(it, current.dpi) }
        _state.value = current.copy(
            unit = unit,
            widthText = widthPx?.let { format(unit.fromPixels(it, current.dpi)) } ?: current.widthText,
            heightText = heightPx?.let { format(unit.fromPixels(it, current.dpi)) } ?: current.heightText
        )
    }

    fun onPresetSelected(preset: Preset) {
        _state.value = _state.value.copy(
            unit = preset.unit,
            widthText = format(preset.width.toDouble()),
            heightText = format(preset.height.toDouble()),
            maxKbText = preset.maxKb?.toString() ?: ""
        )
    }

    fun onKbPresetSelected(kb: Int) { _state.value = _state.value.copy(maxKbText = kb.toString()) }

    fun saveCurrentAsPreset(name: String) {
        val current = _state.value
        val width = current.widthText.toDoubleOrNull()?.toInt() ?: return
        val height = current.heightText.toDoubleOrNull()?.toInt() ?: return
        viewModelScope.launch {
            presetRepository.save(
                Preset(
                    id = "custom_${System.currentTimeMillis()}",
                    name = name.ifBlank { "My preset" },
                    width = width,
                    height = height,
                    unit = current.unit,
                    maxKb = current.maxKbText.toIntOrNull(),
                    custom = true
                )
            )
            showMessage("Preset saved.")
        }
    }

    fun deletePreset(preset: Preset) {
        viewModelScope.launch { presetRepository.delete(preset) }
    }

    fun process() {
        val current = _state.value
        val uri = current.sourceUri ?: return
        _state.value = current.copy(processing = true, result = null, message = null)

        viewModelScope.launch {
            runCatching {
                val photoJob = PhotoJob(
                    sourceUri = uri,
                    widthValue = if (showsSizeFields) current.widthText.toDoubleOrNull() else null,
                    heightValue = if (showsSizeFields) current.heightText.toDoubleOrNull() else null,
                    unit = current.unit,
                    dpi = current.dpi,
                    maxKb = current.maxKbText.toIntOrNull()?.takeIf { it > 0 },
                    keepAspectRatio = current.keepAspect,
                    flattenTransparency = flattensTransparency
                )
                val out = if (tool == ExportTool.SIGNATURE) {
                    resizeImage(
                        SignatureJob(
                            photoJob = photoJob,
                            trimBorders = current.trimBorders,
                            whitenBackground = current.whitenBackground
                        )
                    )
                } else {
                    resizeImage(photoJob)
                }
                val preview = withContext(Dispatchers.Default) {
                    BitmapFactory.decodeByteArray(out.bytes, 0, out.bytes.size)?.asImageBitmap()
                }
                resultBytes = out.bytes
                ImageResultInfo(
                    width = out.width,
                    height = out.height,
                    sizeBytes = out.sizeBytes,
                    quality = out.quality,
                    withinLimit = out.withinLimit,
                    requestedKb = photoJob.maxKb,
                    preview = preview
                )
            }.onSuccess { info ->
                _state.value = _state.value.copy(processing = false, result = info)
            }.onFailure { error ->
                exportGate.recordFailure(tool, error)
                _state.value = _state.value.copy(processing = false)
                showMessage(error.message ?: "Something went wrong while processing.")
            }
        }
    }

    fun save() = export { bytes ->
        fileExporter.saveImage(bytes, fileExporter.timestampedName(prefix(), "jpg"))
        showMessage("Saved to Pictures/FormReady")
    }

    fun share(onReady: (Uri) -> Unit) = export { bytes ->
        onReady(fileExporter.shareableUri(bytes, fileExporter.timestampedName(prefix(), "jpg")))
    }

    private fun export(block: suspend (ByteArray) -> Unit) {
        val bytes = resultBytes ?: return
        viewModelScope.launch {
            when (exportGate.check()) {
                ExportPermission.LimitReached ->
                    _state.value = _state.value.copy(showLimitDialog = true)
                ExportPermission.Allowed -> runCatching {
                    block(bytes)
                    exportGate.recordSuccess(tool)
                }.onFailure {
                    exportGate.recordFailure(tool, it)
                    showMessage(it.message ?: "Export failed.")
                }
            }
        }
    }

    /**
     * Called by the screen when a finished result appears. This is the natural transition point,
     * so it is where the interstitial belongs — never while the work is still running.
     */
    fun onResultReady(activity: Activity) {
        if (_state.value.entitlements.adsRemoved) return
        adsManager.showInterstitial(activity)
    }

    /** Started only by an explicit user tap. Grants one extra export once the ad completes. */
    fun watchRewardedAd(activity: Activity) {
        exportGate.rewardOffered(tool)
        adsManager.showRewarded(
            activity = activity,
            onReward = {
                viewModelScope.launch {
                    if (exportGate.grantBonusExport()) {
                        _state.value = _state.value.copy(showLimitDialog = false)
                        showMessage("1 extra export added. Tap Save or Share again.")
                    } else {
                        showMessage("You have already claimed today's bonus exports.")
                    }
                }
            },
            onUnavailable = { reason -> showMessage(reason) },
            onLoading = { showMessage("Loading ad…") }
        )
    }

    fun consumeMessage() { _state.value = _state.value.copy(message = null) }

    protected fun showMessage(text: String) { _state.value = _state.value.copy(message = text) }

    private fun prefix() = if (tool == ExportTool.SIGNATURE) "FormReady_signature" else "FormReady_photo"

    private fun format(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.2f", value)
}

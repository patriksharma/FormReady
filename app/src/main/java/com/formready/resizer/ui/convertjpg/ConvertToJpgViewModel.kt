package com.formready.resizer.ui.convertjpg

import com.formready.resizer.core.image.ImageLoader
import com.formready.resizer.core.storage.FileExporter
import com.formready.resizer.data.ads.AdsManager
import com.formready.resizer.data.prefs.SettingsRepository
import com.formready.resizer.data.presets.Preset
import com.formready.resizer.data.presets.PresetRepository
import com.formready.resizer.domain.model.ExportTool
import com.formready.resizer.domain.usecase.ExportGate
import com.formready.resizer.domain.usecase.ResizeImageUseCase
import com.formready.resizer.ui.imagetool.ImageToolViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/**
 * PNG / WEBP / HEIC in, JPG out. Transparency is flattened onto white first, otherwise
 * transparent areas would encode as black.
 */
@HiltViewModel
class ConvertToJpgViewModel @Inject constructor(
    imageLoader: ImageLoader,
    resizeImage: ResizeImageUseCase,
    fileExporter: FileExporter,
    settings: SettingsRepository,
    presetRepository: PresetRepository,
    exportGate: ExportGate,
    adsManager: AdsManager
) : ImageToolViewModel(imageLoader, resizeImage, fileExporter, settings, presetRepository, exportGate, adsManager) {

    override val tool = ExportTool.CONVERT_JPG
    override val showsSizeFields = false
    override val flattensTransparency = true

    override fun presetSource(repository: PresetRepository): Flow<List<Preset>> = flowOf(emptyList())
}

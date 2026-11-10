package com.formready.resizer.ui.imagetool

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import com.formready.resizer.core.image.SizeUnit
import com.formready.resizer.core.storage.ShareIntents
import com.formready.resizer.core.util.formatSize
import com.formready.resizer.data.presets.DefaultPresets
import com.formready.resizer.ui.components.DailyLimitDialog
import com.formready.resizer.ui.components.FormReadyTopBar
import com.formready.resizer.ui.components.ImageSourceSheet
import com.formready.resizer.ui.components.findActivity
import com.formready.resizer.ui.components.Hint
import com.formready.resizer.ui.components.NumberField
import com.formready.resizer.ui.components.ResultRow
import com.formready.resizer.ui.components.SectionCard
import com.formready.resizer.ui.components.SwitchRow

/**
 * One screen body shared by the photo and signature tools.
 * [showSignatureOptions] adds the trim / whiten toggles.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageToolScreen(
    title: String,
    pickLabel: String,
    viewModel: ImageToolViewModel,
    showSignatureOptions: Boolean,
    showSizeFields: Boolean = true,
    actionLabel: String = "Resize & compress",
    note: String? = null,
    onBack: () -> Unit,
    onOpenPaywall: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.findActivity()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var showSourceSheet by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }

    val cropLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) result.uriContent?.let(viewModel::onImagePicked)
    }

    LaunchedEffect(state.result) {
        if (state.result != null) activity?.let(viewModel::onResultReady)
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    if (state.showLimitDialog) {
        DailyLimitDialog(
            onDismiss = viewModel::dismissLimitDialog,
            onUpgrade = {
                viewModel.dismissLimitDialog()
                onOpenPaywall()
            },
            onWatchAd = activity?.let { act -> { viewModel.watchRewardedAd(act) } }
        )
    }

    if (showSourceSheet) {
        ImageSourceSheet(
            onDismiss = { showSourceSheet = false },
            onPicked = { uris -> uris.firstOrNull()?.let(viewModel::onImagePicked) },
            title = pickLabel
        )
    }

    if (showSavePresetDialog) {
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
            title = { Text("Save preset") },
            text = {
                OutlinedTextField(
                    value = presetName,
                    onValueChange = { presetName = it },
                    label = { Text("Preset name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.saveCurrentAsPreset(presetName)
                    presetName = ""
                    showSavePresetDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showSavePresetDialog = false }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = { FormReadyTopBar(title, onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionCard("Choose an image") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showSourceSheet = true }) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("  $pickLabel")
                    }
                    OutlinedButton(
                        enabled = state.sourceUri != null,
                        onClick = {
                            state.sourceUri?.let { uri ->
                                cropLauncher.launch(
                                    CropImageContractOptions(
                                        uri,
                                        CropImageOptions(guidelines = CropImageView.Guidelines.ON)
                                    )
                                )
                            }
                        }
                    ) {
                        Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("  Crop")
                    }
                }
                state.sourceInfo?.let { info ->
                    Hint("Original: ${info.width} x ${info.height} px  •  ${info.sizeBytes.formatSize()}")
                }
                note?.let { Hint(it) }
            }

            if (showSizeFields) SectionCard("Presets") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.presets.forEach { preset ->
                        if (preset.custom) {
                            AssistChip(
                                onClick = { viewModel.onPresetSelected(preset) },
                                label = { Text(preset.name) },
                                trailingIcon = {
                                    IconButton(onClick = { viewModel.deletePreset(preset) }) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Delete preset",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            )
                        } else {
                            AssistChip(
                                onClick = { viewModel.onPresetSelected(preset) },
                                label = { Text(preset.name) }
                            )
                        }
                    }
                }
                TextButton(onClick = { showSavePresetDialog = true }) {
                    Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("  Save current settings as preset")
                }
                Hint("Presets are starting points. Always check the exact requirement printed on your form.")
            }

            if (showSizeFields) SectionCard("Size") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SizeUnit.entries.forEach { unit ->
                        FilterChip(
                            selected = state.unit == unit,
                            onClick = { viewModel.onUnitChanged(unit) },
                            label = { Text(unit.label) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField(
                        value = state.widthText,
                        onValueChange = viewModel::onWidthChanged,
                        label = "Width",
                        suffix = state.unit.label,
                        modifier = Modifier.weight(1f)
                    )
                    NumberField(
                        value = state.heightText,
                        onValueChange = viewModel::onHeightChanged,
                        label = "Height",
                        suffix = state.unit.label,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (state.unit != SizeUnit.PX) {
                    Hint("Converted at ${state.dpi} DPI (change it in Settings).")
                }
                SwitchRow(
                    "Keep aspect ratio when one side is left blank",
                    state.keepAspect,
                    viewModel::onKeepAspectChanged
                )
            }

            if (showSignatureOptions) {
                SectionCard("Signature clean-up") {
                    SwitchRow("Trim empty paper around the signature", state.trimBorders, viewModel::onTrimChanged)
                    SwitchRow("Force a clean white background", state.whitenBackground, viewModel::onWhitenChanged)
                    Hint("Both run locally on the pixels. Turn them off if your signature is on coloured paper.")
                }
            }

            SectionCard("Maximum file size") {
                NumberField(
                    value = state.maxKbText,
                    onValueChange = viewModel::onMaxKbChanged,
                    label = "Max size",
                    suffix = "KB",
                    allowDecimal = false,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DefaultPresets.kbLimits.forEach { kb ->
                        FilterChip(
                            selected = state.maxKbText == kb.toString(),
                            onClick = { viewModel.onKbPresetSelected(kb) },
                            label = { Text("$kb KB") }
                        )
                    }
                }
                Hint("Leave this empty to resize without a size ceiling.")
            }

            Button(
                onClick = viewModel::process,
                enabled = state.canProcess,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.processing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text("  Processing")
                } else {
                    Text(actionLabel)
                }
            }

            state.result?.let { result ->
                SectionCard("Result") {
                    result.preview?.let { preview ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(bitmap = preview, contentDescription = "Result preview", contentScale = ContentScale.Fit)
                        }
                    }
                    ResultRow("Dimensions", "${result.width} x ${result.height} px")
                    result.requestedKb?.let { ResultRow("Target", "$it KB") }
                    ResultRow("Output", result.sizeBytes.formatSize())
                    ResultRow("JPEG quality", result.quality.toString())
                    state.sourceInfo?.let { info ->
                        ResultRow("Original", info.sizeBytes.formatSize())
                    }
                    if (!result.withinLimit) {
                        Text(
                            "Could not reach that limit at these dimensions. Try smaller dimensions or a higher KB limit.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = viewModel::save, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("  Save")
                        }
                        FilledTonalButton(
                            onClick = { viewModel.share { uri -> context.startActivity(ShareIntents.share(uri)) } },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("  Share")
                        }
                    }
                    if (!state.entitlements.unlimitedExports) {
                        Hint("${state.exportsLeft} free exports left today.")
                        if (state.exportsLeft == 0 && activity != null) {
                            OutlinedButton(
                                onClick = { viewModel.watchRewardedAd(activity) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Watch ad for 1 more export") }
                        }
                    }
                }
            }
        }
    }
}

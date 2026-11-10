package com.formready.resizer.ui.sheet

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.formready.resizer.core.image.SheetLayout
import com.formready.resizer.core.storage.ShareIntents
import com.formready.resizer.core.util.formatSize
import com.formready.resizer.ui.components.DailyLimitDialog
import com.formready.resizer.ui.components.FormReadyTopBar
import com.formready.resizer.ui.components.ImageSourceSheet
import com.formready.resizer.ui.components.Hint
import com.formready.resizer.ui.components.NumberField
import com.formready.resizer.ui.components.ResultRow
import com.formready.resizer.ui.components.SectionCard
import com.formready.resizer.ui.components.SwitchRow
import com.formready.resizer.ui.components.findActivity

private enum class SheetTarget { PHOTO, SIGNATURE }

@Composable
fun PhotoSignatureSheetScreen(
    onBack: () -> Unit,
    onOpenPaywall: () -> Unit,
    viewModel: PhotoSignatureSheetViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.findActivity()
    val snackbarHostState = remember { SnackbarHostState() }

    var sourceFor by remember { mutableStateOf<SheetTarget?>(null) }

    LaunchedEffect(state.result) {
        if (state.result != null) activity?.let(viewModel::onResultReady)
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    sourceFor?.let { target ->
        ImageSourceSheet(
            onDismiss = { sourceFor = null },
            onPicked = { uris ->
                uris.firstOrNull()?.let { uri ->
                    when (target) {
                        SheetTarget.PHOTO -> viewModel.onPhotoPicked(uri)
                        SheetTarget.SIGNATURE -> viewModel.onSignaturePicked(uri)
                    }
                }
            },
            title = if (target == SheetTarget.PHOTO) "Add the photo" else "Add the signature"
        )
    }

    if (state.showLimitDialog) {
        DailyLimitDialog(
            onDismiss = viewModel::dismissLimitDialog,
            onUpgrade = { viewModel.dismissLimitDialog(); onOpenPaywall() },
            onWatchAd = activity?.let { act -> { viewModel.watchRewardedAd(act) } }
        )
    }

    Scaffold(
        topBar = { FormReadyTopBar("Photo + signature sheet", onBack) },
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
            SectionCard("Images") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { sourceFor = SheetTarget.PHOTO }) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("  Photo")
                    }
                    OutlinedButton(onClick = { sourceFor = SheetTarget.SIGNATURE }) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("  Signature")
                    }
                }
                Hint(
                    (if (state.photoUri != null) "Photo selected" else "No photo yet") + "  •  " +
                        (if (state.signatureUri != null) "Signature selected" else "No signature yet")
                )
            }

            SectionCard("Layout") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SheetLayout.entries.forEach { layout ->
                        FilterChip(
                            selected = state.layout == layout,
                            onClick = { viewModel.setLayout(layout) },
                            label = { Text(layout.label) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField(
                        value = state.widthText,
                        onValueChange = viewModel::setWidth,
                        label = "Sheet width",
                        suffix = "px",
                        allowDecimal = false,
                        modifier = Modifier.weight(1f)
                    )
                    NumberField(
                        value = state.heightText,
                        onValueChange = viewModel::setHeight,
                        label = "Sheet height",
                        suffix = "px",
                        allowDecimal = false,
                        modifier = Modifier.weight(1f)
                    )
                }
                SwitchRow("Clean up the signature (trim + white background)", state.cleanSignature, viewModel::setCleanSignature)
                SwitchRow("Draw a thin box around each area", state.drawBorder, viewModel::setDrawBorder)
            }

            SectionCard("Maximum file size") {
                NumberField(
                    value = state.maxKbText,
                    onValueChange = viewModel::setMaxKb,
                    label = "Max size",
                    suffix = "KB",
                    allowDecimal = false,
                    modifier = Modifier.fillMaxWidth()
                )
                Hint("Leave empty to export without a size ceiling.")
            }

            Button(
                onClick = viewModel::build,
                enabled = state.canBuild,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.processing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text("  Building sheet")
                } else {
                    Text("Create sheet")
                }
            }

            state.result?.let { result ->
                SectionCard("Result") {
                    result.preview?.let { preview ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(bitmap = preview, contentDescription = "Sheet preview", contentScale = ContentScale.Fit)
                        }
                    }
                    ResultRow("Dimensions", "${result.width} x ${result.height} px")
                    result.requestedKb?.let { ResultRow("Target", "$it KB") }
                    ResultRow("Output", result.sizeBytes.formatSize())
                    if (!result.withinLimit) {
                        Text(
                            "Could not reach that limit at this sheet size. Try a smaller sheet or a higher KB limit.",
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
                    if (!state.quota.unlimited) {
                        Hint("${state.quota.exportsLeft} free exports left today.")
                        if (state.quota.exportsLeft == 0 && activity != null) {
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

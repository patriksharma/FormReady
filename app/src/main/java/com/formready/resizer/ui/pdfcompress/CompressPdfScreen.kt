package com.formready.resizer.ui.pdfcompress

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureAsPdf
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.formready.resizer.core.pdf.PdfQuality
import com.formready.resizer.core.storage.ShareIntents
import com.formready.resizer.core.util.formatSize
import com.formready.resizer.ui.components.DailyLimitDialog
import com.formready.resizer.ui.components.FormReadyTopBar
import com.formready.resizer.ui.components.findActivity
import com.formready.resizer.ui.components.Hint
import com.formready.resizer.ui.components.NumberField
import com.formready.resizer.ui.components.ResultRow
import com.formready.resizer.ui.components.SectionCard

@Composable
fun CompressPdfScreen(
    onBack: () -> Unit,
    onOpenPaywall: () -> Unit,
    viewModel: CompressPdfViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.findActivity()
    val snackbarHostState = remember { SnackbarHostState() }

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let(viewModel::onPdfPicked) }

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
            onUpgrade = { viewModel.dismissLimitDialog(); onOpenPaywall() },
            onWatchAd = activity?.let { act -> { viewModel.watchRewardedAd(act) } }
        )
    }

    Scaffold(
        topBar = { FormReadyTopBar("Compress PDF", onBack) },
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
            SectionCard("1. Choose a PDF") {
                Button(onClick = { pickLauncher.launch(arrayOf("application/pdf")) }) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Select PDF")
                }
                state.sourceUri?.let {
                    Hint("${state.sourceName ?: "Selected PDF"}  •  ${state.sourceSize.formatSize()}")
                }
            }

            SectionCard("2. Compression") {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PdfQuality.entries.forEach { quality ->
                        FilterChip(
                            selected = state.quality == quality,
                            onClick = { viewModel.setQuality(quality) },
                            label = { Text(quality.label) }
                        )
                    }
                }
                NumberField(
                    value = state.maxKbText,
                    onValueChange = viewModel::setMaxKb,
                    label = "Max PDF size (optional)",
                    suffix = "KB",
                    allowDecimal = false,
                    modifier = Modifier.fillMaxWidth()
                )
                Hint(
                    "Pages are re-rendered as images, so text in the output is no longer selectable. " +
                        "That is fine for form uploads, but keep your original."
                )
            }

            Button(
                onClick = viewModel::compress,
                enabled = state.canCompress,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.processing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text("  Compressing")
                } else {
                    Text("Compress PDF")
                }
            }

            state.result?.let { result ->
                SectionCard("Result") {
                    ResultRow("Pages", result.pageCount.toString())
                    ResultRow("Original", result.originalBytes.formatSize())
                    result.requestedKb?.let { ResultRow("Target", "$it KB") }
                    ResultRow("Output", result.sizeBytes.formatSize())
                    ResultRow("Saved", "${result.savedPercent}%")
                    if (!result.withinLimit) {
                        Text(
                            "Could not get under that limit. Try 'Smallest file'.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (result.savedPercent <= 0) {
                        Hint("This PDF is already smaller than a rasterised copy. Keep the original.")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = viewModel::save, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("  Save")
                        }
                        FilledTonalButton(
                            onClick = {
                                viewModel.share { uri ->
                                    context.startActivity(ShareIntents.share(uri, "application/pdf"))
                                }
                            },
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

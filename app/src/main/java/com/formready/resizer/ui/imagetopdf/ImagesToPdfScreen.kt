package com.formready.resizer.ui.imagetopdf

import android.net.Uri
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import coil.compose.AsyncImage
import com.formready.resizer.core.pdf.PageOrientation
import com.formready.resizer.core.pdf.PageSize
import com.formready.resizer.core.pdf.PdfQuality
import com.formready.resizer.core.storage.ShareIntents
import com.formready.resizer.core.util.formatSize
import com.formready.resizer.ui.components.DailyLimitDialog
import com.formready.resizer.ui.components.EmptyBox
import com.formready.resizer.ui.components.FormReadyTopBar
import com.formready.resizer.ui.components.ImageSourceSheet
import com.formready.resizer.ui.components.findActivity
import com.formready.resizer.ui.components.Hint
import com.formready.resizer.ui.components.NumberField
import com.formready.resizer.ui.components.ResultRow
import com.formready.resizer.ui.components.SectionCard

@Composable
fun ImagesToPdfScreen(
    onBack: () -> Unit,
    onOpenPaywall: () -> Unit,
    viewModel: ImagesToPdfViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.findActivity()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSourceSheet by remember { mutableStateOf(false) }


    if (showSourceSheet) {
        ImageSourceSheet(
            onDismiss = { showSourceSheet = false },
            onPicked = viewModel::addImages,
            allowMultiple = true,
            title = "Add images"
        )
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
            onUpgrade = { viewModel.dismissLimitDialog(); onOpenPaywall() },
            onWatchAd = activity?.let { act -> { viewModel.watchRewardedAd(act) } }
        )
    }

    Scaffold(
        topBar = { FormReadyTopBar("Image to PDF", onBack) },
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
            SectionCard("1. Images") {
                Button(onClick = { showSourceSheet = true }) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Add images")
                }

                if (state.images.isEmpty()) {
                    EmptyBox("No images yet. Each image becomes one page, in this order.")
                } else {
                    state.images.forEachIndexed { index, uri ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(48.dp)
                            )
                            Text("Page ${index + 1}", modifier = Modifier.weight(1f))
                            IconButton(
                                enabled = index > 0,
                                onClick = { viewModel.moveImage(index, index - 1) }
                            ) { Icon(Icons.Default.ArrowUpward, contentDescription = "Move up") }
                            IconButton(
                                enabled = index < state.images.lastIndex,
                                onClick = { viewModel.moveImage(index, index + 1) }
                            ) { Icon(Icons.Default.ArrowDownward, contentDescription = "Move down") }
                            IconButton(onClick = { viewModel.removeImage(index) }) {
                                Icon(Icons.Default.Close, contentDescription = "Remove")
                            }
                        }
                    }
                }
            }

            SectionCard("2. Page setup") {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PageSize.entries.forEach { size ->
                        FilterChip(
                            selected = state.pageSize == size,
                            onClick = { viewModel.setPageSize(size) },
                            label = { Text(size.label) }
                        )
                    }
                }
                if (state.pageSize != PageSize.ORIGINAL) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PageOrientation.entries.forEach { orientation ->
                            FilterChip(
                                selected = state.orientation == orientation,
                                onClick = { viewModel.setOrientation(orientation) },
                                label = { Text(orientation.label) }
                            )
                        }
                    }
                }
            }

            SectionCard("3. Compression") {
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
                Hint("With a limit set, quality and image resolution step down until the PDF fits.")
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
                    Text("  Building PDF")
                } else {
                    Text("Create PDF")
                }
            }

            state.result?.let { result ->
                SectionCard("Result") {
                    ResultRow("Pages", result.pageCount.toString())
                    result.requestedKb?.let { ResultRow("Target", "$it KB") }
                    ResultRow("Output", result.sizeBytes.formatSize())
                    if (!result.withinLimit) {
                        Text(
                            "Could not get under that limit. Try 'Smallest file' or fewer pages.",
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

package com.formready.resizer.ui.compressimage

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.formready.resizer.ui.imagetool.ImageToolScreen

@Composable
fun CompressImageScreen(
    onBack: () -> Unit,
    onOpenPaywall: () -> Unit,
    viewModel: CompressImageViewModel = hiltViewModel()
) {
    ImageToolScreen(
        title = "Compress image",
        pickLabel = "Select image",
        viewModel = viewModel,
        showSignatureOptions = false,
        showSizeFields = false,
        actionLabel = "Compress",
        note = "Width and height stay exactly as they are. Only the file size comes down.",
        onBack = onBack,
        onOpenPaywall = onOpenPaywall
    )
}

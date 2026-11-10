package com.formready.resizer.ui.photo

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.formready.resizer.ui.imagetool.ImageToolScreen

@Composable
fun PhotoResizeScreen(
    onBack: () -> Unit,
    onOpenPaywall: () -> Unit,
    viewModel: PhotoResizeViewModel = hiltViewModel()
) {
    ImageToolScreen(
        title = "Photo resize",
        pickLabel = "Select photo",
        viewModel = viewModel,
        showSignatureOptions = false,
        onBack = onBack,
        onOpenPaywall = onOpenPaywall
    )
}

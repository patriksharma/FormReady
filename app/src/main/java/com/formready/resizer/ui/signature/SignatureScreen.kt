package com.formready.resizer.ui.signature

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.formready.resizer.ui.imagetool.ImageToolScreen

@Composable
fun SignatureScreen(
    onBack: () -> Unit,
    onOpenPaywall: () -> Unit,
    viewModel: SignatureViewModel = hiltViewModel()
) {
    ImageToolScreen(
        title = "Signature resize",
        pickLabel = "Select signature",
        viewModel = viewModel,
        showSignatureOptions = true,
        onBack = onBack,
        onOpenPaywall = onOpenPaywall
    )
}

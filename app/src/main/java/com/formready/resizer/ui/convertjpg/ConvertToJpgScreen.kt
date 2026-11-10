package com.formready.resizer.ui.convertjpg

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.formready.resizer.ui.imagetool.ImageToolScreen

@Composable
fun ConvertToJpgScreen(
    onBack: () -> Unit,
    onOpenPaywall: () -> Unit,
    viewModel: ConvertToJpgViewModel = hiltViewModel()
) {
    ImageToolScreen(
        title = "Convert to JPG",
        pickLabel = "Select image",
        viewModel = viewModel,
        showSignatureOptions = false,
        showSizeFields = false,
        actionLabel = "Convert to JPG",
        note = "PNG, WEBP and HEIC all convert. Transparent areas become white. " +
            "Leave the KB field empty to convert without extra compression.",
        onBack = onBack,
        onOpenPaywall = onOpenPaywall
    )
}

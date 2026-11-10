package com.formready.resizer.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.formready.resizer.ui.compressimage.CompressImageScreen
import com.formready.resizer.ui.convertjpg.ConvertToJpgScreen
import com.formready.resizer.ui.history.RecentExportsScreen
import com.formready.resizer.ui.home.HomeScreen
import com.formready.resizer.ui.imagetopdf.ImagesToPdfScreen
import com.formready.resizer.ui.mergepdf.MergePdfScreen
import com.formready.resizer.ui.paywall.PaywallScreen
import com.formready.resizer.ui.pdfcompress.CompressPdfScreen
import com.formready.resizer.ui.pdftoimages.PdfToImagesScreen
import com.formready.resizer.ui.photo.PhotoResizeScreen
import com.formready.resizer.ui.settings.SettingsScreen
import com.formready.resizer.ui.sheet.PhotoSignatureSheetScreen
import com.formready.resizer.ui.signature.SignatureScreen
import com.formready.resizer.ui.splitpdf.SplitPdfScreen

object Routes {
    const val HOME = "home"
    const val PHOTO = "photo"
    const val SIGNATURE = "signature"
    const val COMPRESS_IMAGE = "compress_image"
    const val CONVERT_JPG = "convert_jpg"
    const val SHEET = "photo_signature_sheet"
    const val IMAGES_TO_PDF = "images_to_pdf"
    const val COMPRESS_PDF = "compress_pdf"
    const val MERGE_PDF = "merge_pdf"
    const val SPLIT_PDF = "split_pdf"
    const val PDF_TO_IMAGES = "pdf_to_images"
    const val HISTORY = "recent_files"
    const val SETTINGS = "settings"
    const val PAYWALL = "paywall"
    fun paywall(source: String) = "$PAYWALL/$source"
}

@Composable
fun FormReadyNavHost() {
    val navController = rememberNavController()
    val openPaywall: (String) -> Unit = { source -> navController.navigate(Routes.paywall(source)) }
    val back: () -> Unit = { navController.popBackStack() }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpen = { navController.navigate(it) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenPaywall = { openPaywall("home") },
                onOpenHistory = { navController.navigate(Routes.HISTORY) }
            )
        }
        composable(Routes.PHOTO) {
            PhotoResizeScreen(onBack = back, onOpenPaywall = { openPaywall("photo") })
        }
        composable(Routes.SIGNATURE) {
            SignatureScreen(onBack = back, onOpenPaywall = { openPaywall("signature") })
        }
        composable(Routes.COMPRESS_IMAGE) {
            CompressImageScreen(onBack = back, onOpenPaywall = { openPaywall("compress_image") })
        }
        composable(Routes.CONVERT_JPG) {
            ConvertToJpgScreen(onBack = back, onOpenPaywall = { openPaywall("convert_jpg") })
        }
        composable(Routes.SHEET) {
            PhotoSignatureSheetScreen(onBack = back, onOpenPaywall = { openPaywall("sheet") })
        }
        composable(Routes.IMAGES_TO_PDF) {
            ImagesToPdfScreen(onBack = back, onOpenPaywall = { openPaywall("image_to_pdf") })
        }
        composable(Routes.COMPRESS_PDF) {
            CompressPdfScreen(onBack = back, onOpenPaywall = { openPaywall("compress_pdf") })
        }
        composable(Routes.MERGE_PDF) {
            MergePdfScreen(onBack = back, onOpenPaywall = { openPaywall("merge_pdf") })
        }
        composable(Routes.SPLIT_PDF) {
            SplitPdfScreen(onBack = back, onOpenPaywall = { openPaywall("split_pdf") })
        }
        composable(Routes.PDF_TO_IMAGES) {
            PdfToImagesScreen(onBack = back, onOpenPaywall = { openPaywall("pdf_to_images") })
        }
        composable(Routes.HISTORY) {
            RecentExportsScreen(onBack = back)
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = back, onOpenPaywall = { openPaywall("settings") })
        }
        composable(
            route = "${Routes.PAYWALL}/{source}",
            arguments = listOf(navArgument("source") { type = NavType.StringType })
        ) { entry ->
            PaywallScreen(
                source = entry.arguments?.getString("source") ?: "unknown",
                onBack = back
            )
        }
    }
}

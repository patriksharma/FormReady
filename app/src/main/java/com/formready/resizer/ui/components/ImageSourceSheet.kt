package com.formready.resizer.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File

/**
 * Camera / Gallery / Files chooser.
 *
 * The Android photo picker only offers gallery media, which leaves out two things people
 * actually need for form uploads: taking the photo right now, and picking a scan that a
 * scanner app dropped into Downloads.
 *
 * Camera goes through ActivityResultContracts.TakePicture with a FileProvider output uri,
 * so no CAMERA permission is needed — the photo is taken by the camera app, not by us.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageSourceSheet(
    onDismiss: () -> Unit,
    onPicked: (List<Uri>) -> Unit,
    allowMultiple: Boolean = false,
    title: String = "Add an image"
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pendingCameraUri = remember { mutableStateOf<Uri?>(null) }

    fun finish(uris: List<Uri>) {
        if (uris.isNotEmpty()) onPicked(uris)
        onDismiss()
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { saved ->
        if (saved) finish(listOfNotNull(pendingCameraUri.value)) else onDismiss()
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? -> finish(listOfNotNull(uri)) }

    val galleryMultiLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { uris: List<Uri> -> finish(uris) }

    val filesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> finish(listOfNotNull(uri)) }

    val filesMultiLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> -> finish(uris) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
            )

            SourceRow(
                icon = Icons.Default.PhotoCamera,
                title = "Take a photo",
                subtitle = "Use the camera now"
            ) {
                val uri = createCameraUri(context)
                if (uri == null) {
                    onDismiss()
                } else {
                    pendingCameraUri.value = uri
                    cameraLauncher.launch(uri)
                }
            }

            SourceRow(
                icon = Icons.Default.PhotoLibrary,
                title = "Choose from gallery",
                subtitle = "Photos and screenshots"
            ) {
                val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                if (allowMultiple) galleryMultiLauncher.launch(request) else galleryLauncher.launch(request)
            }

            SourceRow(
                icon = Icons.Default.Folder,
                title = "Browse files",
                subtitle = "Downloads, Drive, scanner apps"
            ) {
                val types = arrayOf("image/*")
                if (allowMultiple) filesMultiLauncher.launch(types) else filesLauncher.launch(types)
            }
        }
    }
}

@Composable
private fun SourceRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Temp file in cacheDir/camera, exposed through the app's FileProvider. */
private fun createCameraUri(context: android.content.Context): Uri? = runCatching {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    dir.listFiles()
        ?.filter { System.currentTimeMillis() - it.lastModified() > 3_600_000 }
        ?.forEach { it.delete() }
    val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}.getOrNull()

package com.formready.resizer.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.formready.resizer.core.util.formatSize
import com.formready.resizer.data.history.ExportRecord
import com.formready.resizer.ui.components.enterStaggered
import com.formready.resizer.ui.components.rememberPressScale
import com.formready.resizer.ui.navigation.Routes

private data class HomeAction(
    val route: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector
)

private data class HomeSection(val title: String, val actions: List<HomeAction>)

private val sections = listOf(
    HomeSection(
        "Photo & signature",
        listOf(
            HomeAction(Routes.PHOTO, "Photo", "Resize & compress to an exact KB size", Icons.Default.Badge),
            HomeAction(Routes.SIGNATURE, "Signature", "Trim, clean and resize a signature", Icons.Default.Draw),
            HomeAction(Routes.COMPRESS_IMAGE, "Compress image", "Reduce KB, keep the dimensions", Icons.Default.Compress),
            HomeAction(Routes.SHEET, "Photo + signature sheet", "Both on one image for forms that ask for it", Icons.Default.ViewAgenda)
        )
    ),
    HomeSection(
        "PDF",
        listOf(
            HomeAction(Routes.IMAGES_TO_PDF, "Image to PDF", "Create a PDF from images", Icons.Default.PictureAsPdf),
            HomeAction(Routes.COMPRESS_PDF, "Compress PDF", "Reduce PDF size", Icons.Default.PhotoSizeSelectLarge),
            HomeAction(Routes.MERGE_PDF, "Merge PDFs", "Join several PDFs into one", Icons.Default.CallMerge),
            HomeAction(Routes.SPLIT_PDF, "Extract pages", "Keep only the pages you need", Icons.Default.ContentCut),
            HomeAction(Routes.PDF_TO_IMAGES, "PDF to images", "Every page as a separate JPG", Icons.Default.Image)
        )
    ),
    HomeSection(
        "Convert",
        listOf(
            HomeAction(Routes.CONVERT_JPG, "Convert to JPG", "PNG, WEBP or HEIC into a JPG the form accepts", Icons.Default.SwapHoriz)
        )
    )
)

@Composable
fun HomeScreen(
    onOpen: (String) -> Unit,
    onSettings: () -> Unit,
    onOpenPaywall: () -> Unit,
    onOpenHistory: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                HomeHeader(
                    state = state,
                    onSettings = onSettings,
                    onOpenPaywall = onOpenPaywall,
                    onOpenHistory = onOpenHistory
                )
            }

            if (state.recent.isNotEmpty()) {
                item {
                    RowHeader("Recent files", actionLabel = "See all", onAction = onOpenHistory)
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(state.recent.size) { index ->
                            RecentChip(state.recent[index], onClick = onOpenHistory)
                        }
                    }
                }
            }

            var animationIndex = 0
            sections.forEach { section ->
                item {
                    Text(
                        section.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 2.dp)
                    )
                }
                section.actions.forEach { action ->
                    val index = animationIndex++
                    item(key = action.route) {
                        ActionCard(
                            action = action,
                            index = index,
                            onClick = { onOpen(action.route) }
                        )
                    }
                }
            }

            item {
                Text(
                    "Requirements differ between forms and countries. Always check the exact dimensions " +
                        "and KB limit printed on the form you are filling.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun HomeHeader(
    state: HomeState,
    onSettings: () -> Unit,
    onOpenPaywall: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(
                Brush.verticalGradient(
                    listOf(scheme.primaryContainer, scheme.surface)
                )
            )
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "FormReady",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Form-ready photos, signatures and PDFs — on your device",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onOpenHistory) {
                    Icon(Icons.Default.History, contentDescription = "Recent files")
                }
                IconButton(onClick = onSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    value = if (state.entitlements.unlimitedExports) "∞" else state.exportsLeft.toString(),
                    label = "exports left today",
                    modifier = Modifier.weight(1f),
                    onClick = if (state.entitlements.unlimitedExports) null else onOpenPaywall
                )
                StatTile(
                    value = state.totalExports.toString(),
                    label = "files created",
                    modifier = Modifier.weight(1f),
                    onClick = onOpenHistory
                )
                StatTile(
                    value = "10",
                    label = "tools",
                    modifier = Modifier.weight(1f),
                    onClick = null
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)?
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale by rememberPressScale(interactionSource)

    Surface(
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        onClick = onClick ?: {},
        enabled = onClick != null,
        interactionSource = interactionSource
    ) {
        Column(
            Modifier.padding(vertical = 12.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun RowHeader(title: String, actionLabel: String, onAction: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecentChip(record: ExportRecord, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale by rememberPressScale(interactionSource)

    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier
            .width(150.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(12.dp)) {
            Icon(
                if (record.isPdf) Icons.Default.PictureAsPdf else Icons.Default.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                record.label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                record.sizeBytes.formatSize(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionCard(action: HomeAction, index: Int, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale by rememberPressScale(interactionSource)
    val scheme = MaterialTheme.colorScheme

    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .enterStaggered(index)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp, pressedElevation = 4.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(scheme.primaryContainer, scheme.secondaryContainer)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(action.icon, contentDescription = null, tint = scheme.primary)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    action.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    action.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = scheme.onSurfaceVariant
            )
        }
    }
}

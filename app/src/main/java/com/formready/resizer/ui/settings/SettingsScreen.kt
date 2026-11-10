package com.formready.resizer.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StarRate
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.formready.resizer.ui.components.FormReadyTopBar
import com.formready.resizer.ui.components.Hint
import com.formready.resizer.ui.components.NumberField
import com.formready.resizer.ui.components.SectionCard
import com.formready.resizer.ui.components.SwitchRow
import com.formready.resizer.ui.theme.DarkModeOption
import com.formready.resizer.ui.theme.ThemePalette
import com.formready.resizer.ui.theme.supportsDynamicColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenPaywall: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showAbout by remember { mutableStateOf(false) }
    var showSignOut by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    if (showSignOut) {
        AlertDialog(
            onDismissRequest = { showSignOut = false },
            title = { Text("Sign out?") },
            text = { Text("You will need to sign in again to use the tools. Files you have already saved stay on your device.") },
            confirmButton = {
                TextButton(onClick = {
                    showSignOut = false
                    viewModel.signOut()
                }) { Text("Sign out") }
            },
            dismissButton = { TextButton(onClick = { showSignOut = false }) { Text("Cancel") } }
        )
    }

    if (showAbout) {
        AboutDialog(
            onDismiss = { showAbout = false },
            onWebsite = {
                showAbout = false
                AppInfo.openUrl(context, AppInfo.WEBSITE_URL)
            }
        )
    }

    Scaffold(
        topBar = { FormReadyTopBar("Settings", onBack) },
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
            SectionCard("Appearance") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    DarkModeOption.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = state.theme.darkMode == option,
                            onClick = { viewModel.setDarkMode(option) },
                            shape = SegmentedButtonDefaults.itemShape(index, DarkModeOption.entries.size)
                        ) { Text(option.label) }
                    }
                }

                if (supportsDynamicColor) {
                    SwitchRow(
                        "Use wallpaper colours (Material You)",
                        state.theme.dynamicColor,
                        viewModel::setDynamicColor
                    )
                }

                Text("Theme", style = MaterialTheme.typography.bodyMedium)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ThemePalette.entries.forEach { palette ->
                        PaletteSwatch(
                            palette = palette,
                            locked = palette.premium && !state.entitlements.premiumTemplates,
                            selected = state.theme.palette == palette && !state.theme.dynamicColor,
                            onClick = { viewModel.setPalette(palette) }
                        )
                    }
                }
                Hint(state.theme.palette.label)
            }

            SectionCard("Measurement") {
                NumberField(
                    value = state.dpi.toString(),
                    onValueChange = { it.toIntOrNull()?.let(viewModel::setDpi) },
                    label = "DPI used for mm / cm / inch",
                    allowDecimal = false,
                    modifier = Modifier.fillMaxWidth()
                )
                Hint("300 DPI suits most printed and scanned form requirements.")
            }

            SectionCard("Purchases") {
                val plan = when {
                    state.entitlements.premiumTemplates -> "Lifetime premium"
                    state.entitlements.lifetimeUnlimited -> "Lifetime unlimited"
                    state.entitlements.adsRemoved -> "Ads removed"
                    else -> "Free"
                }
                Text("Current plan: $plan")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onOpenPaywall) { Text("See options") }
                    OutlinedButton(onClick = viewModel::restorePurchases) { Text("Restore purchases") }
                }
            }

            SectionCard("Account") {
                val user = state.user
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            (user?.displayName ?: user?.email ?: "?").take(1).uppercase(),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            user?.displayName ?: "Signed in",
                            style = MaterialTheme.typography.titleSmall
                        )
                        user?.email?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                OutlinedButton(onClick = { showSignOut = true }) { Text("Sign out") }
                Hint("Signing out returns you to the login screen. Your saved files are not affected.")
            }

            SectionCard("About & support") {
                LinkRow("Rate FormReady", "A rating on Play helps other people find it", Icons.Default.StarRate) {
                    AppInfo.openPlayListing(context)
                }
                HorizontalDivider()
                LinkRow("Share the app", "Send the Play link to someone", Icons.Default.Share) {
                    AppInfo.shareApp(context)
                }
                HorizontalDivider()
                LinkRow("Contact us", AppInfo.SUPPORT_EMAIL, Icons.Default.MailOutline) {
                    AppInfo.contactSupport(context)
                }
                HorizontalDivider()
                LinkRow("Website", "advancedaiapps.com", Icons.Default.Language) {
                    AppInfo.openUrl(context, AppInfo.WEBSITE_URL)
                }
                HorizontalDivider()
                LinkRow("About FormReady", AppInfo.versionLabel, Icons.Default.Info) {
                    showAbout = true
                }
            }

            SectionCard("Privacy") {
                Hint(
                    "Photos, signatures and PDFs are processed entirely on this device. They are never " +
                            "uploaded, and analytics records only anonymous event counts — never file contents, " +
                            "file names or your personal information."
                )
                TextButton(onClick = { AppInfo.openUrl(context, AppInfo.PRIVACY_URL) }) {
                    Text("Privacy policy")
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit, onWebsite: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("About FormReady") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "FormReady prepares photos, signatures and PDFs for online application forms — " +
                            "resized, converted and squeezed to an exact KB limit."
                )
                Text(
                    "Everything runs on your device. No account, no upload, no internet needed for any " +
                            "of the tools."
                )
                Text("Made by Advanced AI Apps.", style = MaterialTheme.typography.bodySmall)
                Text(AppInfo.versionLabel, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = { TextButton(onClick = onWebsite) { Text("Website") } }
    )
}

@Composable
private fun LinkRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PaletteSwatch(
    palette: ThemePalette,
    locked: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(44.dp)
            .background(palette.swatch, CircleShape)
            .border(
                width = if (selected) 3.dp else 0.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when {
            selected -> Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
            locked -> Icon(Icons.Default.Lock, contentDescription = "Premium theme", tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}
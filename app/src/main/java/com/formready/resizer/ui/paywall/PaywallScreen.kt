package com.formready.resizer.ui.paywall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.formready.resizer.ui.components.FormReadyTopBar
import com.formready.resizer.ui.components.Hint
import com.formready.resizer.ui.components.SectionCard
import com.formready.resizer.ui.components.findActivity

@Composable
fun PaywallScreen(
    source: String,
    onBack: () -> Unit,
    viewModel: PaywallViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(source) { viewModel.onViewed(source) }

    LaunchedEffect(state.status) {
        state.status?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatus()
        }
    }

    Scaffold(
        topBar = { FormReadyTopBar("Upgrade", onBack) },
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
            Text(
                "Free use includes 3 exports per day with ads. One-time purchases, no subscriptions.",
                style = MaterialTheme.typography.bodyMedium
            )

            if (state.entitlements.anyPurchase) {
                SectionCard("Your plan") {
                    if (state.entitlements.premiumTemplates) Text("Lifetime premium — everything unlocked.")
                    else if (state.entitlements.lifetimeUnlimited) Text("Lifetime — unlimited exports, no ads.")
                    else Text("Ads removed. Unlimited exports are still a separate purchase.")
                }
            }

            state.offers.forEach { offer ->
                SectionCard(offer.title) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(offer.price, style = MaterialTheme.typography.titleLarge)
                        Button(
                            enabled = offer.available,
                            onClick = {
                                context.findActivity()?.let { viewModel.purchase(it, offer.productId) }
                            }
                        ) { Text("Buy") }
                    }
                    if (!offer.available) {
                        Hint("Not available yet. Products appear once they are live in Play Console and you are signed in to Play.")
                    }
                }
            }

            OutlinedButton(
                onClick = viewModel::restore,
                enabled = !state.restoring,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (state.restoring) "Restoring…" else "Restore purchases") }

            Hint(
                "The 30-day ad removal is tracked on this device. Lifetime purchases restore on any " +
                    "device signed in to the same Google account."
            )
        }
    }
}

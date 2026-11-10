package com.formready.resizer.ui.paywall

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.ProductDetails
import com.formready.resizer.data.analytics.Analytics
import com.formready.resizer.data.billing.BillingManager
import com.formready.resizer.data.billing.Entitlements
import com.formready.resizer.data.billing.Products
import com.formready.resizer.data.prefs.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PaywallOffer(
    val productId: String,
    val title: String,
    val price: String,
    val available: Boolean
)

data class PaywallState(
    val offers: List<PaywallOffer> = emptyList(),
    val entitlements: Entitlements = Entitlements(),
    val status: String? = null,
    val restoring: Boolean = false
)

@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val billingManager: BillingManager,
    private val settings: SettingsRepository,
    private val analytics: Analytics
) : ViewModel() {

    private val _state = MutableStateFlow(PaywallState())
    val state: StateFlow<PaywallState> = _state.asStateFlow()

    init {
        billingManager.start()
        viewModelScope.launch {
            combine(
                billingManager.products,
                settings.entitlements,
                billingManager.status
            ) { products, entitlements, status ->
                PaywallState(
                    offers = Products.all.map { id ->
                        val details = products.firstOrNull { it.productId == id }
                        PaywallOffer(
                            productId = id,
                            title = Products.title(id),
                            price = details.priceLabel() ?: Products.fallbackPrice(id),
                            available = details != null
                        )
                    },
                    entitlements = entitlements,
                    status = status,
                    restoring = _state.value.restoring
                )
            }.collect { _state.value = it }
        }
    }

    fun onViewed(source: String) = analytics.paywallViewed(source)

    fun purchase(activity: Activity, productId: String) = billingManager.launchPurchase(activity, productId)

    fun restore() {
        _state.value = _state.value.copy(restoring = true)
        viewModelScope.launch {
            val count = billingManager.restorePurchases()
            billingManager.setStatus(
                if (count > 0) "Purchases restored." else "No previous purchases found on this Google account."
            )
            _state.value = _state.value.copy(restoring = false)
        }
    }

    fun clearStatus() = billingManager.setStatus(null)

    private fun ProductDetails?.priceLabel(): String? =
        this?.oneTimePurchaseOfferDetails?.formattedPrice
}

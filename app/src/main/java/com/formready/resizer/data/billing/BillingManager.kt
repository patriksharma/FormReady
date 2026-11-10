package com.formready.resizer.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.formready.resizer.data.analytics.Analytics
import com.formready.resizer.data.auth.AuthRepository
import com.formready.resizer.data.user.UserRepository
import com.formready.resizer.data.prefs.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single place that owns Play Billing. Everything else reads [SettingsRepository.entitlements].
 *
 * One-time products only — no subscriptions. The 30-day ad removal is consumed on purchase and
 * tracked locally as an expiry date, so it can be bought again when it runs out.
 */
@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val analytics: Analytics,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) : PurchasesUpdatedListener {

    private val scope = CoroutineScope(SupervisorJob())

    private val _products = MutableStateFlow<List<ProductDetails>>(emptyList())
    val products: StateFlow<List<ProductDetails>> = _products.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    private var connected = false

    fun start() {
        if (connected || client.isReady) return
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connected = result.responseCode == BillingClient.BillingResponseCode.OK
                if (connected) {
                    scope.launch {
                        loadProducts()
                        restorePurchases()
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                connected = false
            }
        })
    }

    private suspend fun loadProducts() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                Products.all.map {
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(it)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                }
            )
            .build()

        val result = client.queryProductDetails(params)
        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            _products.value = result.productDetailsList.orEmpty()
        }
    }

    fun launchPurchase(activity: Activity, productId: String) {
        val details = _products.value.firstOrNull { it.productId == productId }
        if (details == null) {
            _status.value = "That product is not available right now. Check your Play account and try again."
            return
        }
        analytics.purchaseStarted(productId)
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
                )
            )
            .build()
        client.launchBillingFlow(activity, params)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase -> scope.launch { handlePurchase(purchase, isRestore = false) } }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _status.value = null
            }
            else -> {
                analytics.purchaseFailed(result.responseCode.toString())
                _status.value = "Purchase could not be completed (code ${result.responseCode})."
            }
        }
    }

    private suspend fun handlePurchase(purchase: Purchase, isRestore: Boolean) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        purchase.products.forEach { productId ->
            when (productId) {
                Products.ADS_30_DAYS -> {
                    // Consumed so it can be bought again; expiry lives locally.
                    val consumeParams = ConsumeParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                    val consumeResult = client.consumePurchase(consumeParams)
                    if (consumeResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        settings.extendAdFree(days = 30)
                        mirrorTier("ads_removed_30d")
                    }
                }
                Products.LIFETIME_UNLIMITED -> {
                    acknowledge(purchase)
                    settings.setLifetimeUnlimited(true)
                    mirrorTier("lifetime_unlimited")
                }
                Products.LIFETIME_PREMIUM -> {
                    acknowledge(purchase)
                    settings.setPremiumTemplates(true)
                    mirrorTier("lifetime_premium")
                }
            }
            if (!isRestore) {
                analytics.purchaseSuccessful(productId)
                _status.value = "Purchase complete. Thank you."
            }
        }
    }

    private suspend fun acknowledge(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        client.acknowledgePurchase(params)
    }

    suspend fun restorePurchases(): Int {
        if (!client.isReady) {
            start()
            return 0
        }
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val result = client.queryPurchasesAsync(params)
        result.purchasesList.forEach { handlePurchase(it, isRestore = true) }
        return result.purchasesList.count { it.purchaseState == Purchase.PurchaseState.PURCHASED }
    }

    /** Best-effort: the purchase is already valid locally whether or not this write lands. */
    private suspend fun mirrorTier(tier: String) {
        authRepository.currentUid?.let { uid -> userRepository.updateEntitlement(uid, tier) }
    }

    fun setStatus(message: String?) { _status.value = message }
}

package com.formready.resizer.data.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.formready.resizer.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Privacy-conscious by construction: this class only ever sends event names and a small set of
 * enum-like parameters. No file names, no image or PDF content, no user text, no file paths.
 * If Firebase is not configured, every call is a no-op.
 */
@Singleton
class Analytics @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val firebase: FirebaseAnalytics? by lazy {
        runCatching {
            if (!BuildConfig.FIREBASE_CONFIGURED || FirebaseApp.getApps(context).isEmpty()) null
            else FirebaseAnalytics.getInstance(context)
        }.getOrNull()
    }

    fun firstOpen() = log("fr_first_open")
    fun firstExport() = log("fr_first_export")
    fun exportSucceeded(tool: String) = log("fr_export_success", "tool" to tool)
    fun exportFailed(tool: String, reason: String) = log("fr_export_failed", "tool" to tool, "reason" to reason.take(40))
    fun paywallViewed(source: String) = log("fr_paywall_viewed", "source" to source)
    fun purchaseStarted(productId: String) = log("fr_purchase_started", "product" to productId)
    fun purchaseSuccessful(productId: String) = log("fr_purchase_success", "product" to productId)
    fun purchaseFailed(code: String) = log("fr_purchase_failed", "code" to code)
    fun signedIn() = log("fr_signed_in")
    fun interstitialShown() = log("fr_interstitial_shown")
    fun rewardOffered(source: String) = log("fr_reward_offered", "source" to source)
    fun rewardEarned() = log("fr_reward_earned")

    private fun log(name: String, vararg params: Pair<String, String>) {
        val analytics = firebase ?: return
        val bundle = Bundle().apply { params.forEach { (key, value) -> putString(key, value) } }
        runCatching { analytics.logEvent(name, bundle) }
    }
}
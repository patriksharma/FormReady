package com.formready.resizer.data.billing

data class Entitlements(
    val adFreeUntilMillis: Long = 0L,
    val lifetimeUnlimited: Boolean = false,
    val premiumTemplates: Boolean = false
) {
    val adsRemoved: Boolean
        get() = lifetimeUnlimited || premiumTemplates || System.currentTimeMillis() < adFreeUntilMillis

    val unlimitedExports: Boolean
        get() = lifetimeUnlimited || premiumTemplates

    val anyPurchase: Boolean
        get() = adsRemoved || unlimitedExports
}

object Products {
    const val ADS_30_DAYS = "remove_ads_30_days"
    const val LIFETIME_UNLIMITED = "lifetime_unlimited"
    const val LIFETIME_PREMIUM = "lifetime_premium_templates"

    val all = listOf(ADS_30_DAYS, LIFETIME_UNLIMITED, LIFETIME_PREMIUM)

    fun title(id: String): String = when (id) {
        ADS_30_DAYS -> "Remove ads for 30 days"
        LIFETIME_UNLIMITED -> "Lifetime: unlimited exports, no ads"
        LIFETIME_PREMIUM -> "Lifetime premium: everything + premium presets & themes"
        else -> id
    }

    fun fallbackPrice(id: String): String = when (id) {
        ADS_30_DAYS -> "₹79"
        LIFETIME_UNLIMITED -> "₹199"
        LIFETIME_PREMIUM -> "₹299"
        else -> ""
    }
}

package com.formready.resizer.data.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.formready.resizer.BuildConfig
import com.formready.resizer.data.analytics.Analytics
import com.formready.resizer.data.prefs.SettingsRepository
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Interstitial + rewarded only. No banners anywhere in the app.
 *
 * Interstitial: shown at the transition where a finished result appears, never during
 * processing, and at most once every [MIN_GAP_MS].
 * Rewarded: only ever started by an explicit user tap, because AdMob policy requires opt-in.
 *
 * Both formats load on demand when the cache is empty rather than reporting "no ad" — the
 * first tap of a session almost always arrives before a preload has finished, and giving up
 * at that moment makes the whole reward flow look broken.
 */
@Singleton
class AdsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val analytics: Analytics
) {

    companion object {
        private const val TAG = "FormReadyAds"

        // Google's public test units. Replace with your own before release.
        const val INTERSTITIAL_UNIT_ID = "ca-app-pub-7104554209854836/9264798221"
        const val REWARDED_UNIT_ID = "ca-app-pub-7104554209854836/2939063028"

        private const val MIN_GAP_MS = 45_000L
        private const val LOAD_TIMEOUT_MS = 10_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var interstitial: InterstitialAd? = null
    private var rewarded: RewardedAd? = null
    private var lastInterstitialAt = 0L
    private var initialised = false

    @Volatile private var adsRemoved = false

    /** Last load failure, so the UI can say something more useful than "no ad". */
    @Volatile var lastError: String? = null
        private set

    init {
        scope.launch {
            settings.entitlements.collect { entitlements ->
                adsRemoved = entitlements.adsRemoved
                if (adsRemoved) {
                    interstitial = null
                    rewarded = null
                }
            }
        }
    }

    /** Called once from the Application class. */
    fun initialise() {
        if (initialised) return
        initialised = true
        runCatching {
            MobileAds.initialize(context) { status ->
                status.adapterStatusMap.forEach { (adapter, state) ->
                    Log.d(TAG, "adapter $adapter -> ${state.initializationState} (${state.description})")
                }
                preload()
            }
        }.onFailure { Log.w(TAG, "MobileAds.initialize failed", it) }
    }

    fun preload() {
        if (adsRemoved) return
        scope.launch { if (interstitial == null) interstitial = loadInterstitial() }
        scope.launch { if (rewarded == null) rewarded = loadRewarded() }
    }

    val rewardedReady: Boolean get() = !adsRemoved && rewarded != null

    /**
     * Shows an interstitial if the gap has passed, loading one first if the cache is empty.
     * [onFinished] always runs exactly once, ad or no ad, so the UI never stalls.
     */
    fun showInterstitial(activity: Activity, onFinished: () -> Unit = {}) {
        if (adsRemoved) {
            onFinished()
            return
        }
        if (System.currentTimeMillis() - lastInterstitialAt < MIN_GAP_MS) {
            Log.d(TAG, "interstitial skipped: inside the ${MIN_GAP_MS / 1000}s gap")
            onFinished()
            return
        }

        scope.launch {
            val ad = interstitial ?: loadInterstitial()
            interstitial = null
            if (ad == null) {
                Log.w(TAG, "interstitial unavailable: $lastError")
                onFinished()
                return@launch
            }

            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    lastInterstitialAt = System.currentTimeMillis()
                    preload()
                    onFinished()
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    Log.w(TAG, "interstitial failed to show: ${error.code} ${error.message}")
                    preload()
                    onFinished()
                }
            }
            analytics.interstitialShown()
            ad.show(activity)
        }
    }

    /**
     * Must only be called from an explicit user tap. [onReward] runs once, after the ad
     * completes and the reward is actually earned. [onLoading] fires when a fetch is needed,
     * so the caller can say so instead of appearing to do nothing.
     */
    fun showRewarded(
        activity: Activity,
        onReward: () -> Unit,
        onUnavailable: (String) -> Unit = {},
        onLoading: () -> Unit = {}
    ) {
        if (adsRemoved) {
            onUnavailable("Ads are already removed on this account.")
            return
        }

        scope.launch {
            var ad = rewarded
            if (ad == null) {
                onLoading()
                ad = loadRewarded()
            }
            rewarded = null

            if (ad == null) {
                onUnavailable(
                    lastError?.let { "Could not load an ad ($it). Check your connection and try again." }
                        ?: "No ad available right now. Please try again in a moment."
                )
                preload()
                return@launch
            }

            var earned = false
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    preload()
                    if (earned) onReward() else onUnavailable("Watch the whole ad to earn the extra export.")
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    Log.w(TAG, "rewarded failed to show: ${error.code} ${error.message}")
                    preload()
                    onUnavailable("That ad could not be shown. Please try again.")
                }
            }
            ad.show(activity) {
                earned = true
                analytics.rewardEarned()
            }
        }
    }

    private suspend fun loadInterstitial(): InterstitialAd? = withTimeoutOrNull(LOAD_TIMEOUT_MS) {
        suspendCancellableCoroutine { continuation ->
            InterstitialAd.load(
                context,
                INTERSTITIAL_UNIT_ID,
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        Log.d(TAG, "interstitial loaded")
                        if (continuation.isActive) continuation.resume(ad)
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        lastError = "code ${error.code}: ${error.message}"
                        Log.w(TAG, "interstitial load failed: $lastError")
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
            )
        }
    }

    private suspend fun loadRewarded(): RewardedAd? = withTimeoutOrNull(LOAD_TIMEOUT_MS) {
        suspendCancellableCoroutine { continuation ->
            RewardedAd.load(
                context,
                REWARDED_UNIT_ID,
                AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        Log.d(TAG, "rewarded loaded")
                        if (continuation.isActive) continuation.resume(ad)
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        lastError = "code ${error.code}: ${error.message}"
                        Log.w(TAG, "rewarded load failed: $lastError")
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
            )
        }
    }

    /** True when this build is using Google's test ad units. */
    val usingTestUnits: Boolean
        get() = BuildConfig.DEBUG && INTERSTITIAL_UNIT_ID.startsWith("ca-app-pub-3940256099942544")
}

package com.formready.resizer.domain.usecase

import android.app.Activity
import com.formready.resizer.data.ads.AdsManager
import com.formready.resizer.data.billing.Entitlements
import com.formready.resizer.data.prefs.SettingsRepository
import com.formready.resizer.domain.model.ExportTool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

data class QuotaSnapshot(
    val exportsToday: Int = 0,
    val bonusToday: Int = 0,
    val entitlements: Entitlements = Entitlements()
) {
    val unlimited: Boolean get() = entitlements.unlimitedExports
    val exportsLeft: Int
        get() = if (unlimited) Int.MAX_VALUE
        else (SettingsRepository.FREE_EXPORTS_PER_DAY + bonusToday - exportsToday).coerceAtLeast(0)
}

sealed interface ExportOutcome {
    data object Success : ExportOutcome
    data object LimitReached : ExportOutcome
    data class Failed(val message: String) : ExportOutcome
}

sealed interface RewardOutcome {
    data object Granted : RewardOutcome
    data object CapReached : RewardOutcome
    data class Unavailable(val message: String) : RewardOutcome
    data object Loading : RewardOutcome
}

/**
 * Quota + ads + export bookkeeping in one injectable helper, so each tool ViewModel only has to
 * describe what it exports rather than repeat the same gate, interstitial and reward plumbing.
 */
@Singleton
class ExportCoordinator @Inject constructor(
    private val settings: SettingsRepository,
    private val exportGate: ExportGate,
    private val adsManager: AdsManager
) {

    val quota: Flow<QuotaSnapshot> = combine(
        settings.exportsToday,
        settings.bonusToday,
        settings.entitlements
    ) { used, bonus, entitlements -> QuotaSnapshot(used, bonus, entitlements) }

    /** Runs [block] only if the user has an export left, then records it. */
    suspend fun export(tool: ExportTool, block: suspend () -> Unit): ExportOutcome =
        when (exportGate.check()) {
            ExportPermission.LimitReached -> ExportOutcome.LimitReached
            ExportPermission.Allowed -> runCatching {
                block()
                exportGate.recordSuccess(tool)
            }.fold(
                onSuccess = { ExportOutcome.Success },
                onFailure = {
                    exportGate.recordFailure(tool, it)
                    ExportOutcome.Failed(it.message ?: "Export failed.")
                }
            )
        }

    fun recordFailure(tool: ExportTool, error: Throwable) = exportGate.recordFailure(tool, error)

    /** Interstitial at the transition where a finished result appears. */
    fun onResultReady(activity: Activity, entitlements: Entitlements) {
        if (entitlements.adsRemoved) return
        adsManager.showInterstitial(activity)
    }

    /** Explicit user tap only. [onOutcome] runs on the main thread when the ad finishes. */
    fun watchRewardedAd(activity: Activity, tool: ExportTool, onOutcome: (RewardOutcome) -> Unit) {
        exportGate.rewardOffered(tool)
        adsManager.showRewarded(
            activity = activity,
            onReward = { onOutcome(RewardOutcome.Granted) },
            onUnavailable = { reason -> onOutcome(RewardOutcome.Unavailable(reason)) },
            onLoading = { onOutcome(RewardOutcome.Loading) }
        )
    }

    suspend fun grantBonusExport(): RewardOutcome =
        if (exportGate.grantBonusExport()) RewardOutcome.Granted else RewardOutcome.CapReached
}

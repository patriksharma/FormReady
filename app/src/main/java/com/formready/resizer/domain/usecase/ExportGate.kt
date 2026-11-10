package com.formready.resizer.domain.usecase

import com.formready.resizer.data.analytics.Analytics
import com.formready.resizer.data.prefs.SettingsRepository
import com.formready.resizer.domain.model.ExportTool
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ExportPermission {
    data object Allowed : ExportPermission
    data object LimitReached : ExportPermission
}

/**
 * The single place that decides whether an export may happen.
 * Free allowance = FREE_EXPORTS_PER_DAY, plus any exports earned from rewarded ads.
 */
@Singleton
class ExportGate @Inject constructor(
    private val settings: SettingsRepository,
    private val analytics: Analytics
) {

    suspend fun check(): ExportPermission {
        val entitlements = settings.entitlements.first()
        if (entitlements.unlimitedExports) return ExportPermission.Allowed
        val used = settings.exportsUsedToday()
        val allowance = SettingsRepository.FREE_EXPORTS_PER_DAY + settings.bonusEarnedToday()
        return if (used < allowance) ExportPermission.Allowed else ExportPermission.LimitReached
    }

    /** True when the user has room left under the daily rewarded-ad cap. */
    suspend fun canEarnBonus(): Boolean {
        val entitlements = settings.entitlements.first()
        if (entitlements.unlimitedExports) return false
        return settings.bonusEarnedToday() < SettingsRepository.BONUS_EXPORTS_PER_DAY
    }

    suspend fun grantBonusExport(): Boolean = settings.grantBonusExport()

    suspend fun recordSuccess(tool: ExportTool) {
        val total = settings.recordExport()
        if (total == 1) analytics.firstExport()
        analytics.exportSucceeded(tool.analyticsName)
    }

    fun recordFailure(tool: ExportTool, reason: String) {
        analytics.exportFailed(tool.analyticsName, reason)
    }

    /**
     * Overload taking the exception itself. Call sites pass the Throwable so the failure can
     * be reported properly later; for now only its class name reaches analytics.
     */
    fun recordFailure(tool: ExportTool, error: Throwable) {
        analytics.exportFailed(tool.analyticsName, error.javaClass.simpleName)
    }

    fun rewardOffered(tool: ExportTool) = analytics.rewardOffered(tool.analyticsName)
}
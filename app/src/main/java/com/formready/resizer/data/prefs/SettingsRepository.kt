package com.formready.resizer.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.formready.resizer.data.billing.Entitlements
import com.formready.resizer.ui.theme.DarkModeOption
import com.formready.resizer.ui.theme.ThemePalette
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class ThemeSettings(
    val palette: ThemePalette = ThemePalette.INDIGO,
    val darkMode: DarkModeOption = DarkModeOption.SYSTEM,
    val dynamicColor: Boolean = false
)

private val Context.dataStore by preferencesDataStore(name = "formready_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val PALETTE = stringPreferencesKey("theme_palette")
        val DARK_MODE = stringPreferencesKey("dark_mode")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val DPI = intPreferencesKey("default_dpi")
        val EXPORT_DATE = stringPreferencesKey("export_date")
        val EXPORT_COUNT = intPreferencesKey("export_count")
        val TOTAL_EXPORTS = intPreferencesKey("total_exports")
        val FIRST_OPEN_LOGGED = booleanPreferencesKey("first_open_logged")
        val AD_FREE_UNTIL = longPreferencesKey("ad_free_until")
        val LIFETIME_UNLIMITED = booleanPreferencesKey("lifetime_unlimited")
        val PREMIUM_TEMPLATES = booleanPreferencesKey("premium_templates")
        val CUSTOM_PRESETS = stringSetPreferencesKey("custom_presets")
        val BONUS_DATE = stringPreferencesKey("bonus_date")
        val BONUS_COUNT = intPreferencesKey("bonus_count")
    }

    val theme: Flow<ThemeSettings> = context.dataStore.data.map { p ->
        ThemeSettings(
            palette = runCatching { ThemePalette.valueOf(p[Keys.PALETTE] ?: "") }
                .getOrDefault(ThemePalette.INDIGO),
            darkMode = runCatching { DarkModeOption.valueOf(p[Keys.DARK_MODE] ?: "") }
                .getOrDefault(DarkModeOption.SYSTEM),
            dynamicColor = p[Keys.DYNAMIC] ?: false
        )
    }

    val defaultDpi: Flow<Int> = context.dataStore.data.map { it[Keys.DPI] ?: 300 }

    val entitlements: Flow<Entitlements> = context.dataStore.data.map { p ->
        Entitlements(
            adFreeUntilMillis = p[Keys.AD_FREE_UNTIL] ?: 0L,
            lifetimeUnlimited = p[Keys.LIFETIME_UNLIMITED] ?: false,
            premiumTemplates = p[Keys.PREMIUM_TEMPLATES] ?: false
        )
    }

    /** Exports used today. Resets by itself when the date rolls over. */
    val exportsToday: Flow<Int> = context.dataStore.data.map { p ->
        if (p[Keys.EXPORT_DATE] == today()) p[Keys.EXPORT_COUNT] ?: 0 else 0
    }

    /** Extra exports earned today by watching rewarded ads. Resets with the date. */
    val bonusToday: Flow<Int> = context.dataStore.data.map { p ->
        if (p[Keys.BONUS_DATE] == today()) p[Keys.BONUS_COUNT] ?: 0 else 0
    }

    val totalExports: Flow<Int> = context.dataStore.data.map { it[Keys.TOTAL_EXPORTS] ?: 0 }

    val customPresets: Flow<Set<String>> = context.dataStore.data.map { it[Keys.CUSTOM_PRESETS] ?: emptySet() }

    suspend fun setPalette(value: ThemePalette) { context.dataStore.edit { it[Keys.PALETTE] = value.name } }
    suspend fun setDarkMode(value: DarkModeOption) { context.dataStore.edit { it[Keys.DARK_MODE] = value.name } }
    suspend fun setDynamicColor(value: Boolean) { context.dataStore.edit { it[Keys.DYNAMIC] = value } }
    suspend fun setDefaultDpi(value: Int) { context.dataStore.edit { it[Keys.DPI] = value.coerceIn(72, 1200) } }

    suspend fun extendAdFree(days: Int) {
        context.dataStore.edit { p ->
            val now = System.currentTimeMillis()
            val base = maxOf(p[Keys.AD_FREE_UNTIL] ?: 0L, now)
            p[Keys.AD_FREE_UNTIL] = base + TimeUnit.DAYS.toMillis(days.toLong())
        }
    }

    suspend fun setLifetimeUnlimited(value: Boolean) {
        context.dataStore.edit { it[Keys.LIFETIME_UNLIMITED] = value }
    }

    suspend fun setPremiumTemplates(value: Boolean) {
        context.dataStore.edit { it[Keys.PREMIUM_TEMPLATES] = value }
    }

    suspend fun saveCustomPreset(serialized: String) {
        context.dataStore.edit { p ->
            p[Keys.CUSTOM_PRESETS] = (p[Keys.CUSTOM_PRESETS] ?: emptySet()) + serialized
        }
    }

    suspend fun deleteCustomPreset(serialized: String) {
        context.dataStore.edit { p ->
            p[Keys.CUSTOM_PRESETS] = (p[Keys.CUSTOM_PRESETS] ?: emptySet()) - serialized
        }
    }

    /** Records one successful export. Returns total lifetime exports. */
    suspend fun recordExport(): Int {
        var total = 0
        context.dataStore.edit { p ->
            val sameDay = p[Keys.EXPORT_DATE] == today()
            p[Keys.EXPORT_DATE] = today()
            p[Keys.EXPORT_COUNT] = (if (sameDay) p[Keys.EXPORT_COUNT] ?: 0 else 0) + 1
            total = (p[Keys.TOTAL_EXPORTS] ?: 0) + 1
            p[Keys.TOTAL_EXPORTS] = total
        }
        return total
    }

    suspend fun markFirstOpenLoggedIfNeeded(): Boolean {
        var wasFirst = false
        context.dataStore.edit { p ->
            wasFirst = !(p[Keys.FIRST_OPEN_LOGGED] ?: false)
            p[Keys.FIRST_OPEN_LOGGED] = true
        }
        return wasFirst
    }

    suspend fun exportsUsedToday(): Int = exportsToday.first()

    suspend fun bonusEarnedToday(): Int = bonusToday.first()

    /** Adds one rewarded export. Returns false when the daily bonus cap is already reached. */
    suspend fun grantBonusExport(): Boolean {
        var granted = false
        context.dataStore.edit { p ->
            val sameDay = p[Keys.BONUS_DATE] == today()
            val current = if (sameDay) p[Keys.BONUS_COUNT] ?: 0 else 0
            if (current < BONUS_EXPORTS_PER_DAY) {
                p[Keys.BONUS_DATE] = today()
                p[Keys.BONUS_COUNT] = current + 1
                granted = true
            }
        }
        return granted
    }

    private fun today(): String = LocalDate.now().toString()

    companion object {
        const val FREE_EXPORTS_PER_DAY = 5

        /** Ceiling on rewarded-ad exports per day, so the lifetime purchase keeps its value. */
        const val BONUS_EXPORTS_PER_DAY = 3
    }
}

package com.formready.resizer.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.formready.resizer.data.auth.AuthRepository
import com.formready.resizer.data.auth.AuthState
import com.formready.resizer.data.auth.SignedInUser
import com.formready.resizer.data.billing.BillingManager
import com.formready.resizer.data.billing.Entitlements
import com.formready.resizer.data.prefs.SettingsRepository
import com.formready.resizer.data.prefs.ThemeSettings
import com.formready.resizer.ui.theme.DarkModeOption
import com.formready.resizer.ui.theme.ThemePalette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val theme: ThemeSettings = ThemeSettings(),
    val dpi: Int = 300,
    val entitlements: Entitlements = Entitlements(),
    val user: SignedInUser? = null,
    val message: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val authRepository: AuthRepository,
    private val billingManager: BillingManager
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                settings.theme,
                settings.defaultDpi,
                settings.entitlements,
                authRepository.state
            ) { theme, dpi, entitlements, authState ->
                _state.value.copy(
                    theme = theme,
                    dpi = dpi,
                    entitlements = entitlements,
                    user = (authState as? AuthState.SignedIn)?.user
                )
            }.collect { _state.value = it }
        }
    }

    fun setPalette(value: ThemePalette) = viewModelScope.launch {
        if (value.premium && !_state.value.entitlements.premiumTemplates) {
            _state.value = _state.value.copy(message = "That theme is part of the lifetime premium purchase.")
        } else {
            settings.setPalette(value)
        }
    }

    fun setDarkMode(value: DarkModeOption) = viewModelScope.launch { settings.setDarkMode(value) }
    fun setDynamicColor(value: Boolean) = viewModelScope.launch { settings.setDynamicColor(value) }
    fun setDpi(value: Int) = viewModelScope.launch { settings.setDefaultDpi(value) }

    /** Sign-in is required, so signing out returns the app to the login screen. */
    fun signOut() = authRepository.signOut()

    fun restorePurchases() {
        viewModelScope.launch {
            val count = billingManager.restorePurchases()
            _state.value = _state.value.copy(
                message = if (count > 0) "Purchases restored." else "No previous purchases found."
            )
        }
    }

    fun consumeMessage() { _state.value = _state.value.copy(message = null) }
}

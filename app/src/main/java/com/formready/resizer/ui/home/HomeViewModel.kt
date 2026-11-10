package com.formready.resizer.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.formready.resizer.data.billing.Entitlements
import com.formready.resizer.data.history.ExportHistoryRepository
import com.formready.resizer.data.history.ExportRecord
import com.formready.resizer.data.prefs.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeState(
    val entitlements: Entitlements = Entitlements(),
    val exportsToday: Int = 0,
    val bonusToday: Int = 0,
    val totalExports: Int = 0,
    val recent: List<ExportRecord> = emptyList()
) {
    val exportsLeft: Int
        get() = (SettingsRepository.FREE_EXPORTS_PER_DAY + bonusToday - exportsToday).coerceAtLeast(0)
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    settings: SettingsRepository,
    history: ExportHistoryRepository
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                settings.entitlements,
                settings.exportsToday,
                settings.bonusToday,
                settings.totalExports,
                history.records
            ) { entitlements, used, bonus, total, recent ->
                HomeState(entitlements, used, bonus, total, recent.take(6))
            }.collect { _state.value = it }
        }
    }
}

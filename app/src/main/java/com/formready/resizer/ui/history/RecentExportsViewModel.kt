package com.formready.resizer.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.formready.resizer.data.history.ExportHistoryRepository
import com.formready.resizer.data.history.ExportRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecentExportsState(
    val records: List<ExportRecord> = emptyList(),
    val message: String? = null
)

@HiltViewModel
class RecentExportsViewModel @Inject constructor(
    private val history: ExportHistoryRepository
) : ViewModel() {

    private val _state = MutableStateFlow(RecentExportsState())
    val state: StateFlow<RecentExportsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            history.records.collect { _state.value = _state.value.copy(records = it) }
        }
    }

    /** Removes the entry from this list only. The file itself stays where it was saved. */
    fun removeFromList(uri: String) {
        viewModelScope.launch {
            history.remove(uri)
            _state.value = _state.value.copy(message = "Removed from this list. The file is still saved.")
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            history.clear()
            _state.value = _state.value.copy(message = "List cleared. Your files are untouched.")
        }
    }

    fun consumeMessage() { _state.value = _state.value.copy(message = null) }
}

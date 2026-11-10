package com.formready.resizer.ui.login

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.formready.resizer.data.analytics.Analytics
import com.formready.resizer.data.auth.AuthRepository
import com.formready.resizer.data.auth.SignInCancelled
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginState(
    val signingIn: Boolean = false,
    val error: String? = null,
    val available: Boolean = true
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val analytics: Analytics
) : ViewModel() {

    private val _state = MutableStateFlow(LoginState(available = authRepository.isAvailable))
    val state: StateFlow<LoginState> = _state.asStateFlow()

    fun signIn(activityContext: Context) {
        if (_state.value.signingIn) return
        _state.value = _state.value.copy(signingIn = true, error = null)

        viewModelScope.launch {
            authRepository.signInWithGoogle(activityContext)
                .onSuccess {
                    analytics.signedIn()
                    _state.value = _state.value.copy(signingIn = false)
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        signingIn = false,
                        // Backing out of the picker is not a failure worth reporting.
                        error = if (error is SignInCancelled) null else error.message
                            ?: "Sign-in failed. Please try again."
                    )
                }
        }
    }

    fun consumeError() { _state.value = _state.value.copy(error = null) }
}

package com.formready.resizer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.formready.resizer.data.auth.AuthRepository
import com.formready.resizer.data.auth.AuthState
import com.formready.resizer.data.prefs.SettingsRepository
import com.formready.resizer.data.prefs.ThemeSettings
import com.formready.resizer.ui.login.LoginScreen
import com.formready.resizer.ui.navigation.FormReadyNavHost
import com.formready.resizer.ui.theme.FormReadyTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val theme by settingsRepository.theme.collectAsStateWithLifecycle(initialValue = ThemeSettings())
            val authState by authRepository.state.collectAsStateWithLifecycle()

            FormReadyTheme(settings = theme) {
                Surface(Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = authState,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "authGate"
                    ) { state ->
                        when (state) {
                            // Cached session is still resolving; a spinner beats a login
                            // screen that flashes away half a second later.
                            AuthState.Resolving -> Box(
                                Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) { CircularProgressIndicator() }

                            AuthState.SignedOut, AuthState.Unavailable -> LoginScreen()

                            is AuthState.SignedIn -> FormReadyNavHost()
                        }
                    }
                }
            }
        }
    }
}

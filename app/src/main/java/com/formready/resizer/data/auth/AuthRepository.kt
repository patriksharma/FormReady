package com.formready.resizer.data.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.formready.resizer.BuildConfig
import com.formready.resizer.data.user.UserRepository
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class SignedInUser(
    val uid: String,
    val displayName: String?,
    val email: String?,
    val photoUrl: String?
)

sealed interface AuthState {
    /** Resolving the cached session on launch. Show a splash, not the login screen. */
    data object Resolving : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val user: SignedInUser) : AuthState
    /** google-services.json is missing, so sign-in cannot work in this build. */
    data object Unavailable : AuthState
}

/** Thrown when the user backs out of the account picker — not an error worth showing. */
class SignInCancelled : Exception("Sign-in cancelled")

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userRepository: UserRepository
) {

    val isAvailable: Boolean =
        BuildConfig.FIREBASE_CONFIGURED && FirebaseApp.getApps(context).isNotEmpty()

    private val auth: FirebaseAuth? =
        if (isAvailable) runCatching { FirebaseAuth.getInstance() }.getOrNull() else null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<AuthState>(
        when {
            !isAvailable || auth == null -> AuthState.Unavailable
            else -> AuthState.Resolving
        }
    )
    val state: StateFlow<AuthState> = _state.asStateFlow()

    init {
        auth?.let { firebaseAuth ->
            firebaseAuth.addAuthStateListener { updated ->
                val user = updated.currentUser
                _state.value = if (user == null) {
                    AuthState.SignedOut
                } else {
                    AuthState.SignedIn(
                        SignedInUser(user.uid, user.displayName, user.email, user.photoUrl?.toString())
                    )
                }
            }
            // Refresh the profile document for an already-signed-in user on launch.
            firebaseAuth.currentUser?.let { user ->
                scope.launch { userRepository.sync(user) }
            }
        }
    }

    val currentUid: String? get() = auth?.currentUser?.uid

    suspend fun signInWithGoogle(activityContext: Context): Result<Unit> {
        val firebaseAuth = auth ?: return Result.failure(
            IllegalStateException("Google sign-in is not configured in this build.")
        )
        val serverClientId = webClientId() ?: return Result.failure(
            IllegalStateException("Missing web client id. Add the SHA-1 fingerprint in Firebase and re-download google-services.json.")
        )

        return runCatching {
            val option = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(true)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build()

            val response = try {
                CredentialManager.create(context).getCredential(activityContext, request)
            } catch (e: GetCredentialCancellationException) {
                throw SignInCancelled()
            } catch (e: NoCredentialException) {
                throw IllegalStateException("No Google account found on this device. Add one in Android settings and try again.")
            } catch (e: GetCredentialException) {
                throw IllegalStateException(e.message ?: "Sign-in failed. Please try again.")
            }

            val googleCredential = GoogleIdTokenCredential.createFrom(response.credential.data)
            val firebaseCredential = GoogleAuthProvider.getCredential(googleCredential.idToken, null)
            val result = firebaseAuth.signInWithCredential(firebaseCredential).await()

            // Profile sync must not block the user getting into the app.
            result.user?.let { user -> scope.launch { userRepository.sync(user) } }
            Unit
        }
    }

    fun signOut() {
        auth?.signOut()
        scope.launch {
            runCatching { CredentialManager.create(context).clearCredentialState(androidx.credentials.ClearCredentialStateRequest()) }
        }
        _state.value = AuthState.SignedOut
    }

    /** Resolved at runtime so the project still compiles without google-services.json. */
    private fun webClientId(): String? {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (id == 0) null else context.getString(id)
    }
}

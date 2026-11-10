package com.formready.resizer.data.user

import android.content.Context
import com.formready.resizer.BuildConfig
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes the signed-in user's profile to Firestore so FormReady users are identifiable
 * alongside the other Advanced AI Apps.
 *
 * What goes up: identity from the Google account, plus app/platform/locale so the audience
 * is readable. What never goes up: anything about the user's photos, signatures or PDFs.
 * Those are processed on the device and this class has no access to them.
 */
@Singleton
class UserRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val COLLECTION = "formready_users"
    }

    private val firestore: FirebaseFirestore? by lazy {
        runCatching { FirebaseFirestore.getInstance() }.getOrNull()
    }

    /**
     * Creates the document on first sign-in and refreshes it on every later sign-in.
     * created_at is written once; last_login_at and login_count move each time.
     */
    suspend fun sync(user: FirebaseUser): Result<Unit> = runCatching {
        val db = firestore ?: error("Firestore is not configured in this build.")
        val document = db.collection(COLLECTION).document(user.uid)

        val existing = document.get().await()

        val profile = mutableMapOf<String, Any>(
            "uid" to user.uid,
            "email" to (user.email ?: ""),
            "display_name" to (user.displayName ?: ""),
            "photo_url" to (user.photoUrl?.toString() ?: ""),
            "app" to "formready",
            "app_version" to BuildConfig.VERSION_NAME,
            "app_version_code" to BuildConfig.VERSION_CODE,
            "platform" to "android",
            "locale" to Locale.getDefault().toLanguageTag(),
            "country" to (Locale.getDefault().country ?: ""),
            "last_login_at" to FieldValue.serverTimestamp(),
            "login_count" to FieldValue.increment(1)
        )

        if (!existing.exists()) {
            profile["created_at"] = FieldValue.serverTimestamp()
            profile["first_app_version"] = BuildConfig.VERSION_NAME
        }

        document.set(profile, SetOptions.merge()).await()
        Unit
    }

    /** Mirrors purchase state so paid users are visible in the console. No amounts, no tokens. */
    suspend fun updateEntitlement(uid: String, tier: String): Result<Unit> = runCatching {
        val db = firestore ?: error("Firestore is not configured in this build.")
        db.collection(COLLECTION).document(uid)
            .set(
                mapOf(
                    "premium_tier" to tier,
                    "premium_updated_at" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .await()
        Unit
    }
}

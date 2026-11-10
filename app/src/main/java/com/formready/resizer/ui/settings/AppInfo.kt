package com.formready.resizer.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.formready.resizer.BuildConfig

/**
 * Contact and store details, kept in one place so they are changed once.
 * These match the rest of the Advanced AI Apps portfolio.
 */
object AppInfo {
    const val SUPPORT_EMAIL = "support@advancedaiapps.com"
    const val WEBSITE_URL = "https://advancedaiapps.com"
    const val PRIVACY_URL = "https://advancedaiapps.com/privacy.html"
    const val PLAY_PACKAGE = BuildConfig.APPLICATION_ID

    val versionLabel: String get() = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

    private val playWebUrl get() = "https://play.google.com/store/apps/details?id=$PLAY_PACKAGE"

    /** Opens the Play listing in the Play app, falling back to the browser. */
    fun openPlayListing(context: Context) {
        val playApp = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PLAY_PACKAGE"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(playApp)
        } catch (e: ActivityNotFoundException) {
            openUrl(context, playWebUrl)
        }
    }

    fun openUrl(context: Context, url: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun shareApp(context: Context) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                "FormReady — resize photos, signatures and PDFs to an exact KB size for online " +
                    "application forms. Works offline.\n$playWebUrl"
            )
        }
        runCatching {
            context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /**
     * Opens a mail app with the support address and a short device footer prefilled.
     * Only device model, Android version and app version — nothing about the user's files.
     */
    fun contactSupport(context: Context) {
        val body = buildString {
            append("\n\n---\n")
            append("Please keep the details below, they help with troubleshooting.\n")
            append("App: FormReady ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
            append("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
            append("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
        }
        val mail = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$SUPPORT_EMAIL")
            putExtra(Intent.EXTRA_SUBJECT, "FormReady support")
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(mail)
        } catch (e: ActivityNotFoundException) {
            // No mail app configured — fall back to the website contact page.
            openUrl(context, WEBSITE_URL)
        }
    }
}

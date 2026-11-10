package com.formready.resizer

import android.app.Application
import com.formready.resizer.data.ads.AdsManager
import com.formready.resizer.data.analytics.Analytics
import com.formready.resizer.data.billing.BillingManager
import com.formready.resizer.data.prefs.SettingsRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class FormReadyApp : Application() {

    @Inject lateinit var billingManager: BillingManager
    @Inject lateinit var adsManager: AdsManager
    @Inject lateinit var analytics: Analytics
    @Inject lateinit var settings: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        adsManager.initialise()
        billingManager.start()

        scope.launch {
            if (settings.markFirstOpenLoggedIfNeeded()) analytics.firstOpen()
        }
    }
}

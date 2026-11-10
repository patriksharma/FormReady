package com.formready.resizer.ui.ads

import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/**
 * Replace [TEST_BANNER_UNIT_ID] and the APPLICATION_ID in the manifest with your own AdMob ids
 * before publishing. Google's test ids are used here so debug builds never serve live ads.
 */
const val TEST_BANNER_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"

@Composable
fun AdBanner(visible: Boolean, modifier: Modifier = Modifier, unitId: String = TEST_BANNER_UNIT_ID) {
    if (!visible) return
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            FrameLayout(context).apply {
                addView(
                    AdView(context).apply {
                        setAdSize(AdSize.BANNER)
                        adUnitId = unitId
                        loadAd(AdRequest.Builder().build())
                    }
                )
            }
        }
    )
}

package com.zandaulion.bpdigitized

import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.zandaulion.bpdigitized.data.ocr.GeminiModelConfig
import com.zandaulion.bpdigitized.data.reminder.NotificationHelper
import dagger.hilt.android.HiltAndroidApp

import com.zandaulion.bpdigitized.domain.billing.BillingManager
import javax.inject.Inject

@HiltAndroidApp
class BpDigitizerApp : Application() {
    
    @Inject
    lateinit var billingManager: BillingManager

    override fun onCreate() {
        super.onCreate()
        
        FirebaseApp.initializeApp(this)
        val firebaseAppCheck = FirebaseAppCheck.getInstance()
        firebaseAppCheck.installAppCheckProviderFactory(
            if (BuildConfig.DEBUG) {
                // Debug builds can't pass Play Integrity (debug-signed, not
                // Play-delivered). Use the debug provider so local builds get a
                // registerable token; otherwise every scan 403s under enforcement.
                DebugAppCheckProviderFactory.getInstance()
            } else {
                PlayIntegrityAppCheckProviderFactory.getInstance()
            }
        )

        // Remote Config drives the Gemini model name so a retired model can be
        // swapped from the Firebase console without an app update. Fire-and-forget:
        // until the first fetch lands, the in-app default keeps scans working.
        Firebase.remoteConfig.apply {
            if (BuildConfig.DEBUG) {
                // Skip the 12 h fetch throttle so console changes show up
                // immediately when testing.
                setConfigSettingsAsync(remoteConfigSettings { minimumFetchIntervalInSeconds = 0 })
            }
            setDefaultsAsync(
                mapOf(
                    GeminiModelConfig.RC_KEY to GeminiModelConfig.DEFAULT,
                    GeminiModelConfig.LOCATION_RC_KEY to GeminiModelConfig.LOCATION_DEFAULT,
                )
            )
            fetchAndActivate()
        }

        NotificationHelper.createChannel(this)
        
        // Replace with your actual RevenueCat Google Play public API key
        billingManager.initialize("REVENUECAT_PUBLIC_API_KEY")
    }
}

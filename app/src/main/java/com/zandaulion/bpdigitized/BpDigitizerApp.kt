package com.zandaulion.bpdigitized

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
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
            PlayIntegrityAppCheckProviderFactory.getInstance()
        )

        NotificationHelper.createChannel(this)
        
        // Replace with your actual RevenueCat Google Play public API key
        billingManager.initialize("REVENUECAT_PUBLIC_API_KEY")
    }
}

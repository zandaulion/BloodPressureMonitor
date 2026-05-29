package com.zandaulion.bpdigitized

import android.app.Application
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
        NotificationHelper.createChannel(this)
        
        // Replace with your actual RevenueCat Google Play public API key
        billingManager.initialize("REVENUECAT_PUBLIC_API_KEY")
    }
}

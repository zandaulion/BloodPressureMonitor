package com.zandaulion.bpdigitized

import android.app.Application
import com.zandaulion.bpdigitized.data.reminder.NotificationHelper
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BpDigitizerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
    }
}

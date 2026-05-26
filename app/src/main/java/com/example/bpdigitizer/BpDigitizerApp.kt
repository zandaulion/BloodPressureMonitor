package com.example.bpdigitizer

import android.app.Application
import com.example.bpdigitizer.data.reminder.NotificationHelper
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BpDigitizerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
    }
}

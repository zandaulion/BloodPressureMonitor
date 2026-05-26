package com.example.bpdigitizer.data.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object NotificationHelper {
    const val CHANNEL_ID              = "bp_reminders"
    const val MORNING_NOTIFICATION_ID = 1001
    const val EVENING_NOTIFICATION_ID = 1002
    const val SNOOZE_NOTIFICATION_ID  = 1003

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "BP Reminders",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Daily blood pressure check reminders"
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }
}

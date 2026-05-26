package com.example.bpdigitizer.data.reminder

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.bpdigitizer.MainActivity
import com.example.bpdigitizer.R
import com.example.bpdigitizer.domain.repository.ReminderRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Fires when a scheduled reminder alarm goes off. Posts the notification and immediately
 * reschedules the same reminder for the following day (self-perpetuating pattern).
 */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var reminderRepository: ReminderRepository
    @Inject lateinit var reminderScheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val typeName = intent.getStringExtra(EXTRA_TYPE) ?: return
        val type = runCatching { ReminderType.valueOf(typeName) }.getOrNull() ?: return

        postNotification(context, type)

        // Reschedule next day using goAsync so we can read DataStore
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = reminderRepository.observe().first()
                if (settings.isEnabled) {
                    when (type) {
                        ReminderType.MORNING ->
                            reminderScheduler.schedule(ReminderType.MORNING, settings.morningMinutes)
                        ReminderType.EVENING ->
                            reminderScheduler.schedule(ReminderType.EVENING, settings.eveningMinutes)
                        ReminderType.SNOOZE  -> Unit // one-shot; no auto-reschedule
                    }
                }
            } finally {
                result.finish()
            }
        }
    }

    private fun postNotification(context: Context, type: ReminderType) {
        // Guard: POST_NOTIFICATIONS is a runtime permission on API 33+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) return

        val (title, text, notifId) = when (type) {
            ReminderType.MORNING -> Triple(
                "Morning BP Check",
                "Time for your morning BP check. Make sure you've rested for 5 minutes and haven't had coffee yet!",
                NotificationHelper.MORNING_NOTIFICATION_ID,
            )
            ReminderType.EVENING -> Triple(
                "Evening BP Check",
                "Ready for your evening BP check? Sit back, rest for 5 minutes, and take your reading.",
                NotificationHelper.EVENING_NOTIFICATION_ID,
            )
            ReminderType.SNOOZE  -> Triple(
                "BP Check (snoozed)",
                "Time for your blood pressure check.",
                NotificationHelper.SNOOZE_NOTIFICATION_ID,
            )
        }

        val openPi = PendingIntent.getActivity(
            context, notifId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val snoozePi = PendingIntent.getBroadcast(
            context, notifId + 100,
            Intent(context, SnoozeReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openPi)
            .setAutoCancel(true)
            .addAction(0, "Log Now",      openPi)
            .addAction(0, "Snooze (15m)", snoozePi)
            .build()

        NotificationManagerCompat.from(context).notify(notifId, notification)
    }

    companion object {
        const val EXTRA_TYPE = "reminder_type"
    }
}

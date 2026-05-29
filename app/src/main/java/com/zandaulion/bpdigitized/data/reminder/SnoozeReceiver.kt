package com.zandaulion.bpdigitized.data.reminder

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zandaulion.bpdigitized.domain.repository.ReminderRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Triggered by the "Snooze (15m)" notification action.
 * Dismisses the current notification and schedules a one-shot snooze alarm,
 * unless the snooze window would overlap with the next regular reading.
 */
@AndroidEntryPoint
class SnoozeReceiver : BroadcastReceiver() {

    @Inject lateinit var reminderRepository: ReminderRepository
    @Inject lateinit var reminderScheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        // Dismiss whichever notification is currently showing
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NotificationHelper.MORNING_NOTIFICATION_ID)
        nm.cancel(NotificationHelper.EVENING_NOTIFICATION_ID)

        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = reminderRepository.observe().first()
                val snoozeAt = System.currentTimeMillis() + ReminderScheduler.SNOOZE_MS

                // Edge case: skip snooze if it would land within 5 min before a regular alarm
                val nextMorning = ReminderScheduler.nextTriggerMillis(settings.morningMinutes)
                val nextEvening = ReminderScheduler.nextTriggerMillis(settings.eveningMinutes)
                val buffer      = 5L * 60_000
                val overlaps    = snoozeAt + buffer >= nextMorning || snoozeAt + buffer >= nextEvening

                if (!overlaps) {
                    reminderScheduler.scheduleSnooze()
                }
            } finally {
                result.finish()
            }
        }
    }
}

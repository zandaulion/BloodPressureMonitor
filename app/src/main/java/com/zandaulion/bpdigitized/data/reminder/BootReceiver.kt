package com.zandaulion.bpdigitized.data.reminder

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
 * Reschedules all active alarms after a device reboot (the OS clears AlarmManager on restart).
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var reminderRepository: ReminderRepository
    @Inject lateinit var reminderScheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = reminderRepository.observe().first()
                if (settings.isEnabled) {
                    reminderScheduler.schedule(ReminderType.MORNING, settings.morningMinutes)
                    reminderScheduler.schedule(ReminderType.EVENING, settings.eveningMinutes)
                }
            } finally {
                result.finish()
            }
        }
    }
}

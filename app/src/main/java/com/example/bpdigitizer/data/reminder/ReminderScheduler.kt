package com.example.bpdigitizer.data.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** Returns true if exact alarms can be scheduled (always true below API 31). */
    fun canScheduleExact(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarmManager.canScheduleExactAlarms()
        else true

    /**
     * Schedule the next occurrence of [type] at [minutesSinceMidnight].
     * If that time has already passed today, schedules for tomorrow.
     */
    fun schedule(type: ReminderType, minutesSinceMidnight: Int) {
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nextTriggerMillis(minutesSinceMidnight),
            pendingIntentFor(type),
        )
    }

    /** Schedule a one-shot 15-minute snooze alarm. */
    fun scheduleSnooze() {
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + SNOOZE_MS,
            pendingIntentFor(ReminderType.SNOOZE),
        )
    }

    fun cancel(type: ReminderType) = alarmManager.cancel(pendingIntentFor(type))

    fun cancelAll() = ReminderType.entries.forEach { cancel(it) }

    private fun pendingIntentFor(type: ReminderType): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_TYPE, type.name)
        }
        return PendingIntent.getBroadcast(
            context,
            type.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val SNOOZE_MS = 15L * 60 * 1_000

        /** Millis until the next wall-clock occurrence of [minutesSinceMidnight]. */
        fun nextTriggerMillis(minutesSinceMidnight: Int): Long {
            val now = System.currentTimeMillis()
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, minutesSinceMidnight / 60)
                set(Calendar.MINUTE,      minutesSinceMidnight % 60)
                set(Calendar.SECOND,      0)
                set(Calendar.MILLISECOND, 0)
            }
            if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
            return cal.timeInMillis
        }
    }
}

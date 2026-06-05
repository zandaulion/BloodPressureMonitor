package com.zandaulion.bpdigitized.presentation.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zandaulion.bpdigitized.data.reminder.ReminderScheduler
import com.zandaulion.bpdigitized.data.reminder.ReminderType
import com.zandaulion.bpdigitized.domain.model.ReminderSettings
import com.zandaulion.bpdigitized.domain.repository.ReminderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RemindersUi(
    val isEnabled: Boolean = false,
    val morningMinutes: Int = 7 * 60 + 30,
    val eveningMinutes: Int = 20 * 60,
)

@HiltViewModel
class RemindersViewModel @Inject constructor(
    private val reminderRepository: ReminderRepository,
    private val reminderScheduler: ReminderScheduler,
) : ViewModel() {

    val ui: StateFlow<RemindersUi> = reminderRepository.observe()
        .map { settings ->
            RemindersUi(
                isEnabled      = settings.isEnabled,
                morningMinutes = settings.morningMinutes,
                eveningMinutes = settings.eveningMinutes,
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            RemindersUi(),
        )

    /**
     * Master on/off switch. Persists the preference and schedules (or cancels) the alarms.
     * Permission prompting is handled by the screen; [ReminderScheduler] falls back to an
     * inexact alarm when the exact-alarm permission isn't granted, so this never blocks or
     * throws even if the user hasn't granted it yet.
     */
    fun toggleEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val current = ui.value
            save(ReminderSettings(enabled, current.morningMinutes, current.eveningMinutes))
            if (enabled) {
                reminderScheduler.schedule(ReminderType.MORNING, current.morningMinutes)
                reminderScheduler.schedule(ReminderType.EVENING, current.eveningMinutes)
            } else {
                reminderScheduler.cancelAll()
            }
        }
    }

    /** Called after the user picks a new morning time. */
    fun setMorningMinutes(minutes: Int) {
        viewModelScope.launch {
            val current = ui.value
            save(ReminderSettings(current.isEnabled, minutes, current.eveningMinutes))
            if (current.isEnabled) reminderScheduler.schedule(ReminderType.MORNING, minutes)
        }
    }

    /** Called after the user picks a new evening time. */
    fun setEveningMinutes(minutes: Int) {
        viewModelScope.launch {
            val current = ui.value
            save(ReminderSettings(current.isEnabled, current.morningMinutes, minutes))
            if (current.isEnabled) reminderScheduler.schedule(ReminderType.EVENING, minutes)
        }
    }

    private suspend fun save(settings: ReminderSettings) = reminderRepository.save(settings)
}

package com.example.bpdigitizer.presentation.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bpdigitizer.data.reminder.ReminderScheduler
import com.example.bpdigitizer.data.reminder.ReminderType
import com.example.bpdigitizer.domain.model.ReminderSettings
import com.example.bpdigitizer.domain.repository.ReminderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RemindersUi(
    val isEnabled: Boolean = false,
    val morningMinutes: Int = 7 * 60 + 30,
    val eveningMinutes: Int = 20 * 60,
    val needsExactAlarmPermission: Boolean = false,
)

sealed interface RemindersEvent {
    /** Ask the screen to launch the POST_NOTIFICATIONS permission request. */
    data object RequestNotificationPermission : RemindersEvent
    /** The SCHEDULE_EXACT_ALARM permission is missing — send user to settings. */
    data object OpenExactAlarmSettings : RemindersEvent
}

@HiltViewModel
class RemindersViewModel @Inject constructor(
    private val reminderRepository: ReminderRepository,
    private val reminderScheduler: ReminderScheduler,
) : ViewModel() {

    val ui: StateFlow<RemindersUi> = reminderRepository.observe()
        .map { settings ->
            RemindersUi(
                isEnabled                 = settings.isEnabled,
                morningMinutes            = settings.morningMinutes,
                eveningMinutes            = settings.eveningMinutes,
                needsExactAlarmPermission = !reminderScheduler.canScheduleExact(),
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            RemindersUi(),
        )

    private val _events = Channel<RemindersEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** Called when the master on/off switch is toggled. */
    fun toggleEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled && !reminderScheduler.canScheduleExact()) {
                _events.send(RemindersEvent.OpenExactAlarmSettings)
                return@launch
            }
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

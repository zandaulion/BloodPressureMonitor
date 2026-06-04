package com.zandaulion.bpdigitized.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zandaulion.bpdigitized.data.reminder.ReminderScheduler
import com.zandaulion.bpdigitized.data.reminder.ReminderType
import com.zandaulion.bpdigitized.domain.model.BiologicalSex
import com.zandaulion.bpdigitized.domain.model.ReminderSettings
import com.zandaulion.bpdigitized.domain.repository.OnboardingRepository
import com.zandaulion.bpdigitized.domain.repository.ReminderRepository
import com.zandaulion.bpdigitized.domain.repository.UserProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Editable state for the optional setup steps shown during onboarding. */
data class OnboardingForm(
    val birthYearText: String = "",
    val sex: BiologicalSex = BiologicalSex.PREFER_NOT_TO_SAY,
    val remindersEnabled: Boolean = false,
) {
    /** Age implied by [birthYearText], or null while it is blank/invalid. */
    val computedAge: Int?
        get() {
            val year = birthYearText.toIntOrNull() ?: return null
            val age = java.time.Year.now().value - year
            return if (age in 1..120) age else null
        }

    /** True when a non-empty birth year does not resolve to a plausible age. */
    val birthYearError: Boolean
        get() {
            if (birthYearText.isEmpty()) return false
            return computedAge == null
        }
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val onboardingRepository: OnboardingRepository,
    private val userProfileRepository: UserProfileRepository,
    private val reminderRepository: ReminderRepository,
    private val reminderScheduler: ReminderScheduler,
) : ViewModel() {

    /**
     * Null while the preference is still loading; true/false once known.
     * Used to defer choosing the nav start destination until we know
     * whether the intro should be shown.
     */
    val hasSeenOnboarding: StateFlow<Boolean?> =
        onboardingRepository.hasSeenOnboarding()
            .map<Boolean, Boolean?> { it }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null,
            )

    private val _form = MutableStateFlow(OnboardingForm())
    val form: StateFlow<OnboardingForm> = _form

    fun updateBirthYear(value: String) =
        _form.update { it.copy(birthYearText = value.filter(Char::isDigit).take(4)) }

    fun updateSex(value: BiologicalSex) = _form.update { it.copy(sex = value) }

    /**
     * Persists the reminders preference and schedules (or cancels) the default
     * morning/evening alarms. [ReminderScheduler] prefers exact alarms but falls back to
     * inexact when the exact-alarm permission isn't held, so this never throws; the user
     * can later grant exact alarms from the in-app Reminders screen for precise timing.
     */
    fun setRemindersEnabled(enabled: Boolean) {
        _form.update { it.copy(remindersEnabled = enabled) }
        viewModelScope.launch {
            val settings = ReminderSettings(isEnabled = enabled)   // default times
            reminderRepository.save(settings)
            if (enabled) {
                reminderScheduler.schedule(ReminderType.MORNING, settings.morningMinutes)
                reminderScheduler.schedule(ReminderType.EVENING, settings.eveningMinutes)
            } else {
                reminderScheduler.cancelAll()
            }
        }
    }

    /** Saves the optional profile fields (if any were provided) and marks onboarding done. */
    fun completeOnboarding() {
        viewModelScope.launch {
            val f = _form.value
            val birthYear = f.birthYearText.toIntOrNull()
            if (birthYear != null || f.sex != BiologicalSex.PREFER_NOT_TO_SAY) {
                val current = userProfileRepository.observe().first()
                userProfileRepository.save(
                    current.copy(
                        birthYear = birthYear ?: current.birthYear,
                        biologicalSex = f.sex,
                    ),
                )
            }
            onboardingRepository.setSeen()
        }
    }

    /** Dismisses onboarding without applying the optional profile fields. */
    fun skipOnboarding() {
        viewModelScope.launch { onboardingRepository.setSeen() }
    }
}

package com.zandaulion.bpdigitized.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zandaulion.bpdigitized.domain.repository.OnboardingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val onboardingRepository: OnboardingRepository,
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

    fun markSeen() {
        viewModelScope.launch { onboardingRepository.setSeen() }
    }
}

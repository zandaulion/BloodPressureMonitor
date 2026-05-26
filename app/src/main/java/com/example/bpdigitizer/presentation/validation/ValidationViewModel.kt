package com.example.bpdigitizer.presentation.validation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bpdigitizer.domain.model.BPReading
import com.example.bpdigitizer.domain.repository.BPRepository
import com.example.bpdigitizer.domain.usecase.CalculateBPZoneUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ValidationState(
    val systolic: String = "",
    val diastolic: String = "",
    val pulse: String = "",
    val notes: String = "",
    val imagePath: String? = null,
) {
    val isValid: Boolean
        get() {
            val s = systolic.toIntOrNull() ?: return false
            val d = diastolic.toIntOrNull() ?: return false
            return s > d
        }
}

sealed interface ValidationEvent {
    data object Saved : ValidationEvent
}

@HiltViewModel
class ValidationViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: BPRepository,
    private val calculateZone: CalculateBPZoneUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ValidationState(
            systolic = savedState.get<String>("sys").orEmpty(),
            diastolic = savedState.get<String>("dia").orEmpty(),
            pulse = savedState.get<String>("pulse").orEmpty(),
            imagePath = savedState.get<String>("img")?.takeIf { it.isNotBlank() },
        ),
    )
    val state: StateFlow<ValidationState> = _state

    private val _events = Channel<ValidationEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun updateSystolic(v: String) = _state.update { it.copy(systolic = v.filter(Char::isDigit).take(3)) }
    fun updateDiastolic(v: String) = _state.update { it.copy(diastolic = v.filter(Char::isDigit).take(3)) }
    fun updatePulse(v: String) = _state.update { it.copy(pulse = v.filter(Char::isDigit).take(3)) }
    fun updateNotes(v: String) = _state.update { it.copy(notes = v.take(200)) }

    fun save() {
        val current = _state.value
        if (!current.isValid) return
        val sys = current.systolic.toInt()
        val dia = current.diastolic.toInt()
        viewModelScope.launch {
            repository.insert(
                BPReading(
                    timestamp = System.currentTimeMillis(),
                    systolic = sys,
                    diastolic = dia,
                    pulse = current.pulse.toIntOrNull(),
                    category = calculateZone(sys, dia),
                    notes = current.notes.trim().takeIf { it.isNotEmpty() },
                ),
            )
            _events.send(ValidationEvent.Saved)
        }
    }
}

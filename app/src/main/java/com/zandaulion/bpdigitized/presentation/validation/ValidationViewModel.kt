package com.zandaulion.bpdigitized.presentation.validation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zandaulion.bpdigitized.domain.model.BPReading
import com.zandaulion.bpdigitized.domain.repository.BPRepository
import com.zandaulion.bpdigitized.domain.usecase.CalculateBPZoneUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ValidationState(
    val systolic: Int = 120,
    val diastolic: Int = 80,
    val pulse: Int = 60,
    val notes: String = "",
    val imagePath: String? = null,
    val isLoading: Boolean = true,
) {
    val isValid: Boolean get() = systolic > diastolic
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

    private val _state = MutableStateFlow(ValidationState())
    val state: StateFlow<ValidationState> = _state

    private val _events = Channel<ValidationEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        val argSys   = savedState.get<String>("sys").orEmpty()
        val argDia   = savedState.get<String>("dia").orEmpty()
        val argPulse = savedState.get<String>("pulse").orEmpty()
        val imagePath = savedState.get<String>("img")?.takeIf { it.isNotBlank() }

        if (argSys.isNotEmpty() && argDia.isNotEmpty()) {
            // OCR path — use detected values immediately
            _state.value = ValidationState(
                systolic  = argSys.toIntOrNull()?.coerceIn(SYS_RANGE) ?: DEFAULT_SYS,
                diastolic = argDia.toIntOrNull()?.coerceIn(DIA_RANGE) ?: DEFAULT_DIA,
                pulse     = argPulse.toIntOrNull()?.coerceIn(PUL_RANGE) ?: DEFAULT_PUL,
                imagePath = imagePath,
                isLoading = false,
            )
        } else {
            // Manual entry — pre-populate from last saved reading (or hardcoded defaults)
            viewModelScope.launch {
                val prev = repository.getLatest()
                _state.value = ValidationState(
                    systolic  = prev?.systolic?.coerceIn(SYS_RANGE) ?: DEFAULT_SYS,
                    diastolic = prev?.diastolic?.coerceIn(DIA_RANGE) ?: DEFAULT_DIA,
                    pulse     = prev?.pulse?.coerceIn(PUL_RANGE)    ?: DEFAULT_PUL,
                    imagePath = imagePath,
                    isLoading = false,
                )
            }
        }
    }

    fun updateSystolic(v: Int)  = _state.update { it.copy(systolic  = v.coerceIn(SYS_RANGE)) }
    fun updateDiastolic(v: Int) = _state.update { it.copy(diastolic = v.coerceIn(DIA_RANGE)) }
    fun updatePulse(v: Int)     = _state.update { it.copy(pulse     = v.coerceIn(PUL_RANGE)) }
    fun updateNotes(v: String)  = _state.update { it.copy(notes     = v.take(200)) }

    fun save() {
        val current = _state.value
        if (!current.isValid) return
        viewModelScope.launch {
            val reading = BPReading(
                timestamp = System.currentTimeMillis(),
                systolic  = current.systolic,
                diastolic = current.diastolic,
                pulse     = current.pulse,
                category  = calculateZone(current.systolic, current.diastolic),
                notes     = current.notes.trim().takeIf { it.isNotEmpty() },
            )
            val id = repository.insert(reading)
            _events.send(ValidationEvent.Saved)
        }
    }

    companion object {
        val SYS_RANGE = 60..250
        val DIA_RANGE = 40..160
        val PUL_RANGE = 30..200
        const val DEFAULT_SYS = 120
        const val DEFAULT_DIA = 80
        const val DEFAULT_PUL = 60
    }
}

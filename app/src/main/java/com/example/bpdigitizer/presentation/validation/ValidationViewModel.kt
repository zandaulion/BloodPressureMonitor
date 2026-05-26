package com.example.bpdigitizer.presentation.validation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.example.bpdigitizer.domain.repository.BPRepository
import com.example.bpdigitizer.domain.usecase.CalculateBPZoneUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

data class ValidationState(
    val systolic: String = "",
    val diastolic: String = "",
    val pulse: String = "",
    val imagePath: String? = null,
) {
    val isValid: Boolean
        get() {
            val s = systolic.toIntOrNull() ?: return false
            val d = diastolic.toIntOrNull() ?: return false
            return s > d
        }
}

@HiltViewModel
class ValidationViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val repository: BPRepository,
    private val calculateZone: CalculateBPZoneUseCase,
) : ViewModel()

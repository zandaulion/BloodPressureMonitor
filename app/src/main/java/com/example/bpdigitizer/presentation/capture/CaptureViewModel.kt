package com.example.bpdigitizer.presentation.capture

import androidx.lifecycle.ViewModel
import com.example.bpdigitizer.domain.usecase.ParseOcrTextUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val parseOcr: ParseOcrTextUseCase,
) : ViewModel()

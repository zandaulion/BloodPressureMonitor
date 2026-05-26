package com.example.bpdigitizer.domain.model

data class BPReading(
    val id: Long = 0L,
    val timestamp: Long,
    val systolic: Int,
    val diastolic: Int,
    val pulse: Int?,
    val category: BPCategory,
    val notes: String? = null,
)

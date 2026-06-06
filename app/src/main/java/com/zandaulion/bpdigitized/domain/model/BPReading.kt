package com.zandaulion.bpdigitized.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class BPReading(
    val id: Long = 0L,
    val timestamp: Long,
    val systolic: Int,
    val diastolic: Int,
    val pulse: Int?,
    val category: BPCategory,
    val notes: String? = null,
    // Default keeps older JSON exports (without this field) deserializable.
    val tags: List<BpTag> = emptyList(),
)

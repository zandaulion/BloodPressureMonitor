package com.example.bpdigitizer.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.bpdigitizer.domain.model.BPCategory
import com.example.bpdigitizer.domain.model.BPReading

@Entity(tableName = "bp_readings")
data class BPReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestamp: Long,
    val systolic: Int,
    val diastolic: Int,
    val pulse: Int?,
    val category: BPCategory,
    val notes: String?,
)

fun BPReadingEntity.toDomain() = BPReading(id, timestamp, systolic, diastolic, pulse, category, notes)
fun BPReading.toEntity() = BPReadingEntity(id, timestamp, systolic, diastolic, pulse, category, notes)

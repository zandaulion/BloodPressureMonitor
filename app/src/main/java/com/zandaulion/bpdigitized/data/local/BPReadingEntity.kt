package com.zandaulion.bpdigitized.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.zandaulion.bpdigitized.domain.model.BPCategory
import com.zandaulion.bpdigitized.domain.model.BPReading
import com.zandaulion.bpdigitized.domain.model.BpTag

@Entity(tableName = "bp_readings")
data class BPReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestamp: Long,
    val systolic: Int,
    val diastolic: Int,
    val pulse: Int?,
    val category: BPCategory,
    val notes: String?,
    // Comma-joined BpTag names, e.g. "CAFFEINE,STRESS". The SQL defaultValue
    // declared here MUST match the DEFAULT in MIGRATION_2_3 so Room's
    // post-migration schema validation passes (see BPDatabase.MIGRATION_2_3).
    @ColumnInfo(defaultValue = "''") val tags: String = "",
)

fun BPReadingEntity.toDomain() = BPReading(
    id = id,
    timestamp = timestamp,
    systolic = systolic,
    diastolic = diastolic,
    pulse = pulse,
    category = category,
    notes = notes,
    // Parse defensively: an unrecognised stored name (e.g. an enum value removed
    // in a future build) is skipped rather than crashing the whole list read.
    tags = tags.split(',')
        .filter { it.isNotBlank() }
        .mapNotNull { name -> runCatching { BpTag.valueOf(name) }.getOrNull() },
)

fun BPReading.toEntity() = BPReadingEntity(
    id = id,
    timestamp = timestamp,
    systolic = systolic,
    diastolic = diastolic,
    pulse = pulse,
    category = category,
    notes = notes,
    tags = tags.joinToString(",") { it.name },
)

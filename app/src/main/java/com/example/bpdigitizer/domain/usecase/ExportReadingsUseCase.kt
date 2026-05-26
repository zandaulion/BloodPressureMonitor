package com.example.bpdigitizer.domain.usecase

import android.content.Context
import com.example.bpdigitizer.domain.model.BPCategory
import com.example.bpdigitizer.domain.repository.BPRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class ExportReadingsUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: BPRepository,
    private val json: Json,
) {
    suspend operator fun invoke(): File {
        val readings = repository.getAll()
        val entries  = readings.map { r ->
            ExportEntry(
                id        = r.id,
                dateTime  = ISO_FORMAT.format(Date(r.timestamp)),
                timestamp = r.timestamp,
                systolic  = r.systolic,
                diastolic = r.diastolic,
                pulse     = r.pulse,
                category  = r.category,
                notes     = r.notes,
            )
        }
        val payload = json.encodeToString(entries)
        val stamp   = FILE_TIMESTAMP_FORMAT.format(Date())
        val file    = File(context.cacheDir, "bp_export_$stamp.json")
        file.writeText(payload)
        return file
    }

    /** JSON shape — includes both a human-readable dateTime and the raw epoch timestamp. */
    @Serializable
    private data class ExportEntry(
        val id: Long,
        val dateTime: String,
        val timestamp: Long,
        val systolic: Int,
        val diastolic: Int,
        val pulse: Int?,
        val category: BPCategory,
        val notes: String?,
    )

    private companion object {
        val ISO_FORMAT           = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        val FILE_TIMESTAMP_FORMAT = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    }
}

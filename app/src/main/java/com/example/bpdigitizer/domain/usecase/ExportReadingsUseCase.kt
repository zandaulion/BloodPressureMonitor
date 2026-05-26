package com.example.bpdigitizer.domain.usecase

import android.content.Context
import com.example.bpdigitizer.domain.repository.BPRepository
import dagger.hilt.android.qualifiers.ApplicationContext
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
        val payload = json.encodeToString(readings)
        val timestamp = FILE_TIMESTAMP_FORMAT.format(Date())
        val file = File(context.cacheDir, "bp_export_$timestamp.json")
        file.writeText(payload)
        return file
    }

    private companion object {
        val FILE_TIMESTAMP_FORMAT = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    }
}

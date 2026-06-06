package com.zandaulion.bpdigitized.domain.usecase

import android.content.Context
import com.zandaulion.bpdigitized.domain.model.meanArterialPressure
import com.zandaulion.bpdigitized.domain.model.pulsePressure
import com.zandaulion.bpdigitized.domain.repository.BPRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class ExportCsvUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: BPRepository,
) {
    suspend operator fun invoke(): File {
        val readings = repository.getAll()
        val stamp = FILE_TIMESTAMP_FORMAT.format(Date())
        val file  = File(context.cacheDir, "bp_export_$stamp.csv")

        file.bufferedWriter().use { w ->
            w.write("id,dateTime,timestamp,systolic,diastolic,pulse,map,pulsePressure,category,notes\n")
            readings.forEach { r ->
                w.write(
                    "${r.id}," +
                    "${ISO_FORMAT.format(Date(r.timestamp))}," +
                    "${r.timestamp}," +
                    "${r.systolic}," +
                    "${r.diastolic}," +
                    "${r.pulse ?: ""}," +
                    "${meanArterialPressure(r.systolic, r.diastolic)}," +
                    "${pulsePressure(r.systolic, r.diastolic)}," +
                    "${r.category}," +
                    "${csvQuote(r.notes)}\n"
                )
            }
        }
        return file
    }

    /**
     * Wraps [value] in double-quotes and escapes any internal double-quotes per RFC 4180.
     * Returns an empty string for null values.
     */
    private fun csvQuote(value: String?): String {
        if (value.isNullOrEmpty()) return ""
        // Only quote if the value contains commas, quotes, or newlines
        return if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    private companion object {
        val ISO_FORMAT            = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        val FILE_TIMESTAMP_FORMAT = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    }
}

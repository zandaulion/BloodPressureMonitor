package com.zandaulion.bpdigitized.domain.usecase

import android.content.Context
import android.net.Uri
import com.zandaulion.bpdigitized.domain.model.BPCategory
import com.zandaulion.bpdigitized.domain.model.BPReading
import com.zandaulion.bpdigitized.domain.model.BpTag
import com.zandaulion.bpdigitized.domain.repository.BPRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject

class ImportReadingsUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: BPRepository,
    private val calculateZone: CalculateBPZoneUseCase,
) {
    /**
     * Parses [uri] (a JSON file previously exported by this app) and inserts every reading
     * that does not already exist in the database (matched by exact timestamp).
     *
     * @return the number of newly inserted readings.
     */
    suspend operator fun invoke(uri: Uri): Int {
        val raw = context.contentResolver.openInputStream(uri)
            ?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("Cannot open file")

        val entries = LENIENT_JSON.decodeFromString<List<ImportEntry>>(raw)

        // Build a set of timestamps already in the DB to skip exact duplicates.
        val existing = repository.getAll().mapTo(HashSet()) { it.timestamp }

        var inserted = 0
        for (entry in entries) {
            if (entry.timestamp in existing) continue
            runCatching {
                repository.insert(
                    BPReading(
                        id        = 0L,   // let Room auto-assign
                        timestamp = entry.timestamp,
                        systolic  = entry.systolic,
                        diastolic = entry.diastolic,
                        pulse     = entry.pulse,
                        category  = entry.category
                            ?: calculateZone(entry.systolic, entry.diastolic),
                        notes     = entry.notes,
                        tags      = entry.tags,
                    ),
                )
                existing += entry.timestamp
                inserted++
            }
        }
        return inserted
    }

    /**
     * Import shape — tolerates both the original export (plain BPReading list) and the current
     * export (ExportEntry with an extra dateTime field), and any future additions, because
     * [LENIENT_JSON] is configured with ignoreUnknownKeys = true.
     */
    @Serializable
    private data class ImportEntry(
        val timestamp: Long,
        val systolic: Int,
        val diastolic: Int,
        val id: Long = 0L,
        val dateTime: String? = null,   // human-readable field written by ExportReadingsUseCase; ignored here
        val pulse: Int? = null,
        val category: BPCategory? = null,
        val notes: String? = null,
        val tags: List<BpTag> = emptyList(),
    )

    private companion object {
        val LENIENT_JSON = Json {
            ignoreUnknownKeys = true
            isLenient         = true
            coerceInputValues = true
        }
    }
}

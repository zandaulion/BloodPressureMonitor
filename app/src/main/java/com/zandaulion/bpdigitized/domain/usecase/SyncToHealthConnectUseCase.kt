package com.zandaulion.bpdigitized.domain.usecase

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Pressure
import com.zandaulion.bpdigitized.domain.repository.BPRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

class SyncToHealthConnectUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: BPRepository,
) {
    /**
     * Upserts all local readings into Health Connect.
     * Each record gets a stable [Metadata.clientRecordId] derived from the reading's DB id,
     * so calling this multiple times (or after a data migration) is idempotent — Health Connect
     * will update the existing record rather than creating a duplicate.
     *
     * @return the number of readings processed.
     */
    suspend operator fun invoke(): Int {
        val client   = HealthConnectClient.getOrCreate(context)
        val readings = repository.getAll()

        val records = buildList {
            for (r in readings) {
                val instant     = Instant.ofEpochMilli(r.timestamp)
                val zoneOffset  = ZoneId.systemDefault().rules.getOffset(instant)

                add(
                    BloodPressureRecord(
                        time       = instant,
                        zoneOffset = zoneOffset,
                        systolic   = Pressure.millimetersOfMercury(r.systolic.toDouble()),
                        diastolic  = Pressure.millimetersOfMercury(r.diastolic.toDouble()),
                        // manualEntry(clientRecordId) is the public factory for a manually-entered record
                        // with a stable ID — re-syncing will upsert, not duplicate.
                        metadata   = Metadata.manualEntry("bpdigitizer_bp_${r.id}"),
                    ),
                )

                r.pulse?.let { bpm ->
                    add(
                        HeartRateRecord(
                            startTime       = instant,
                            startZoneOffset = zoneOffset,
                            endTime         = instant.plusSeconds(60),
                            endZoneOffset   = zoneOffset,
                            samples         = listOf(HeartRateRecord.Sample(instant, bpm.toLong())),
                            metadata        = Metadata.manualEntry("bpdigitizer_hr_${r.id}"),
                        ),
                    )
                }
            }
        }

        if (records.isNotEmpty()) client.insertRecords(records)
        return readings.size
    }
}

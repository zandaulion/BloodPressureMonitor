package com.example.bpdigitizer.domain.usecase

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Pressure
import com.example.bpdigitizer.domain.model.BPReading
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/**
 * Pushes a single [BPReading] to Health Connect immediately after it is saved.
 *
 * This is a best-effort, fire-and-forget operation:
 *  - Silently does nothing if Health Connect is not installed or unavailable.
 *  - Silently does nothing if write permissions have not yet been granted.
 *  - Never throws — all exceptions are swallowed so the caller is never disrupted.
 *
 * Permission prompting is intentionally left to the manual-sync button on the
 * dashboard; this use case only piggybacks on already-granted permissions.
 */
class PushReadingToHealthConnectUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend operator fun invoke(reading: BPReading) {
        try {
            val status = HealthConnectClient.getSdkStatus(context)
            if (status != HealthConnectClient.SDK_AVAILABLE) return

            val client  = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            if (!granted.containsAll(REQUIRED_PERMISSIONS)) return

            val instant    = Instant.ofEpochMilli(reading.timestamp)
            val zoneOffset = ZoneId.systemDefault().rules.getOffset(instant)

            val records = buildList {
                add(
                    BloodPressureRecord(
                        time       = instant,
                        zoneOffset = zoneOffset,
                        systolic   = Pressure.millimetersOfMercury(reading.systolic.toDouble()),
                        diastolic  = Pressure.millimetersOfMercury(reading.diastolic.toDouble()),
                        metadata   = Metadata.manualEntry("bpdigitizer_bp_${reading.id}"),
                    ),
                )
                reading.pulse?.let { bpm ->
                    add(
                        HeartRateRecord(
                            startTime       = instant,
                            startZoneOffset = zoneOffset,
                            endTime         = instant.plusSeconds(60),
                            endZoneOffset   = zoneOffset,
                            samples         = listOf(HeartRateRecord.Sample(instant, bpm.toLong())),
                            metadata        = Metadata.manualEntry("bpdigitizer_hr_${reading.id}"),
                        ),
                    )
                }
            }

            client.insertRecords(records)
        } catch (_: Exception) {
            // Best-effort — never surface Health Connect errors to the user during save.
        }
    }

    private companion object {
        val REQUIRED_PERMISSIONS = setOf(
            HealthPermission.getWritePermission(BloodPressureRecord::class),
            HealthPermission.getWritePermission(HeartRateRecord::class),
        )
    }
}

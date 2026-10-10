package com.zandaulion.bpdigitized.domain.usecase

import com.zandaulion.bpdigitized.domain.backup.BackupData
import com.zandaulion.bpdigitized.domain.repository.BPRepository
import com.zandaulion.bpdigitized.domain.repository.UserProfileRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

data class ImportResult(val count: Int, val profileImported: Boolean)

class RestoreBackupUseCase @Inject constructor(
    private val readings: BPRepository,
    private val profiles: UserProfileRepository,
) {
    suspend operator fun invoke(backup: BackupData, includeProfile: Boolean): ImportResult {
        val existing = readings.getAll().mapTo(HashSet()) { it.timestamp }
        var count = 0
        for (reading in backup.readings) {
            if (reading.timestamp in existing) continue
            // Propagate storage errors and cancellation instead of reporting false success.
            readings.insert(reading.copy(id = 0))
            existing += reading.timestamp
            count++
        }
        val patch = backup.profile.takeIf { includeProfile }
        if (patch != null) profiles.save(patch.applyTo(profiles.observe().first()))
        return ImportResult(count, patch != null)
    }
}

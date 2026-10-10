package com.zandaulion.bpdigitized.domain.usecase

import android.content.Context
import com.zandaulion.bpdigitized.domain.backup.BackupCodec
import com.zandaulion.bpdigitized.domain.repository.BPRepository
import com.zandaulion.bpdigitized.domain.repository.UserProfileRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class ExportReadingsUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: BPRepository,
    private val profiles: UserProfileRepository,
) {
    suspend operator fun invoke(): File = withContext(Dispatchers.IO) {
        val payload = BackupCodec.encode(repository.getAll(), profiles.observe().first())
        File(context.cacheDir, "bp_export_${System.currentTimeMillis()}.json").apply { writeText(payload) }
    }
}

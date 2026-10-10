package com.zandaulion.bpdigitized.domain.usecase

import android.content.Context
import android.net.Uri
import com.zandaulion.bpdigitized.domain.backup.BackupCodec
import com.zandaulion.bpdigitized.domain.backup.BackupData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ImportReadingsUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val restore: RestoreBackupUseCase,
) {
    suspend fun read(uri: Uri): BackupData = withContext(Dispatchers.IO) {
        val raw = context.contentResolver.openInputStream(uri)
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: error("Cannot open file")
        BackupCodec.decode(raw)
    }

    suspend operator fun invoke(backup: BackupData, includeProfile: Boolean): ImportResult =
        restore(backup, includeProfile)
}

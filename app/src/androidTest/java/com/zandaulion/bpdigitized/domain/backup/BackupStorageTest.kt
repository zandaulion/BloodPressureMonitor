package com.zandaulion.bpdigitized.domain.backup

import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zandaulion.bpdigitized.data.repository.UserProfileRepositoryImpl
import com.zandaulion.bpdigitized.domain.model.*
import com.zandaulion.bpdigitized.domain.repository.BPRepository
import com.zandaulion.bpdigitized.domain.usecase.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Synthetic, isolated storage: never accesses the installed user's database/profile. */
@RunWith(AndroidJUnit4::class)
class BackupStorageTest {
    @Test fun fileImportAndExportRestoreProfileToAnIsolatedDataStore() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, "backup-test-${System.nanoTime()}").apply { mkdirs() }
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.IO)
        val profiles = UserProfileRepositoryImpl(PreferenceDataStoreFactory.create(scope = scope,
            produceFile = { File(folder, "profile.preferences_pb") }))
        val readings = object : BPRepository {
            val rows = MutableStateFlow(emptyList<BPReading>())
            override fun observeAll() = rows
            override suspend fun getAll() = rows.value
            override suspend fun getLatest() = rows.value.lastOrNull()
            override suspend fun insert(reading: BPReading): Long { rows.value += reading; return rows.value.size.toLong() }
            override suspend fun delete(id: Long) = error("Not used")
            override suspend fun updateTags(id: Long, tags: List<BpTag>) = error("Not used")
        }
        var exported: File? = null
        try {
            val fixture = File(folder, "synthetic.json").apply { writeText("""
                {"app":"bp-digitizer","version":1,"profile":{"birthYear":1985,"sex":"FEMALE","weightKg":62.5,"heightCm":167.5,"activity":"VERY_ACTIVE","smoker":true,"diabetes":false},
                "readings":[{"timestamp":1700000000000,"systolic":118,"diastolic":76,"pulse":65,"tags":"tag_resting,tag_on_waking"}]}
            """.trimIndent()) }
            val importer = ImportReadingsUseCase(context, RestoreBackupUseCase(readings, profiles))
            val backup = importer.read(Uri.fromFile(fixture))
            assertEquals(1, importer(backup, false).count)
            assertEquals(UserProfile(), profiles.observe().first())
            assertTrue(importer(backup, true).profileImported)
            val expected = UserProfile(1985, BiologicalSex.FEMALE, 62.5f, 167.5f, true, false, ActivityLevel.VERY_ACTIVE)
            assertEquals(expected, profiles.observe().first())
            exported = ExportReadingsUseCase(context, readings, profiles)()
            val roundTrip = importer.read(Uri.fromFile(exported))
            assertEquals(expected, roundTrip.profile!!.applyTo(UserProfile()))
            assertEquals(listOf(BpTag.RESTING, BpTag.ON_WAKING), roundTrip.readings.single().tags)
            assertEquals(0, importer(roundTrip, true).count)
        } finally {
            job.cancelAndJoin()
            exported?.delete()
            folder.deleteRecursively()
        }
    }
}

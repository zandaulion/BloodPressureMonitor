package com.zandaulion.bpdigitized.domain.backup

import com.zandaulion.bpdigitized.domain.model.*
import com.zandaulion.bpdigitized.domain.repository.*
import com.zandaulion.bpdigitized.domain.usecase.RestoreBackupUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Entirely synthetic records and profile; never copied from a user export. */
class BackupTest {
    private val row = BPReading(0, 1700000000000, 118, 76, 65, BPCategory.NORMAL, "Synthetic fixture", listOf(BpTag.RESTING))
    private val profile = UserProfile(1985, BiologicalSex.FEMALE, 62.5f, 167.5f, true, true, ActivityLevel.VERY_ACTIVE)
    private val legacy = """[{"timestamp":1700000000000,"systolic":118,"diastolic":76,"pulse":65,"category":"NORMAL","notes":"Synthetic fixture","tags":["RESTING"]}]"""

    @Test fun androidBackupRoundTripsEveryProfileAndReadingField() {
        val backup = BackupCodec.decode(BackupCodec.encode(listOf(row), profile))
        assertEquals(listOf(row), backup.readings)
        assertEquals(profile, backup.profile!!.applyTo(UserProfile()))
        val exported = Json.parseToJsonElement(BackupCodec.encode(listOf(row), profile)).jsonObject
        assertEquals("bp-digitizer", exported["app"]!!.jsonPrimitive.content)
        assertEquals("FEMALE", exported["profile"]!!.jsonObject["sex"]!!.jsonPrimitive.content)
        assertEquals("VERY_ACTIVE", exported["profile"]!!.jsonObject["activity"]!!.jsonPrimitive.content)
        assertTrue(exported["profile"]!!.jsonObject["smoker"]!!.jsonPrimitive.boolean)
    }

    @Test fun pwaEnvelopeAndPrefixedCommaTagsAreAccepted() {
        val raw = """{"app":"bp-digitizer","version":1,"exported":"2024-01-01T00:00:00Z","profile":{"birthYear":1985,"sex":"OTHER","weightKg":62.5,"heightCm":167.5,"activity":"SEDENTARY","smoker":false,"diabetes":true},"readings":[{"timestamp":1700000000000,"systolic":118,"diastolic":76,"tags":"tag_resting, tag_on_waking,tag_resting","source":"manual","id":99}]}"""
        val result = BackupCodec.decode(raw)
        assertEquals(listOf(BpTag.RESTING, BpTag.ON_WAKING), result.readings.single().tags)
        assertEquals(0L, result.readings.single().id)
        val restored = result.profile!!.applyTo(profile)
        assertEquals(BiologicalSex.PREFER_NOT_TO_SAY, restored.biologicalSex)
        assertEquals(ActivityLevel.SEDENTARY, restored.activityLevel)
        assertFalse(restored.isSmoker)
        assertTrue(restored.hasDiabetes)
    }

    @Test fun legacyArrayAndMissingOrEmptyProfileNeverReplaceProfile() {
        assertEquals(listOf(row), BackupCodec.decode(legacy).readings)
        for (raw in listOf(legacy, """{"readings":[],"profile":null}""", """{"readings":[],"profile":{}}""", """{"readings":[]}""")) {
            assertNull(BackupCodec.decode(raw).profile)
        }
    }

    @Test fun partialProfilePreservesAbsentFieldsAndHonorsNullAndFalse() {
        val backup = BackupCodec.decode("""{"readings":[],"profile":{"weightKg":null,"smoker":false}}""")
        assertEquals(profile.copy(weightKg = null, isSmoker = false), backup.profile!!.applyTo(profile))
    }

    @Test fun unspecifiedSexAndDefaultProfileRoundTrip() {
        val result = BackupCodec.decode(BackupCodec.encode(emptyList(), UserProfile()))
        assertEquals(UserProfile(), result.profile!!.applyTo(profile))
    }

    @Test fun missingNullAndEmptyTagsAreAcceptedAndUnknownCategoryRecomputed() {
        for (tags in listOf("", ",\"tags\":null", ",\"tags\":\"\"", ",\"tags\":[]")) {
            val result = BackupCodec.decode("""[{"timestamp":1,"systolic":118,"diastolic":76,"category":"future"$tags}]""")
            assertEquals(emptyList<BpTag>(), result.readings.single().tags)
            assertEquals(BPCategory.NORMAL, result.readings.single().category)
        }
    }

    @Test fun malformedBackupIsRejectedBeforeRestore() {
        val bad = listOf("{}", "null", """{"app":"other","readings":[]}""", """{"version":2,"readings":[]}""",
            """{"readings":[],"profile":{"sex":"unknown"}}""", """{"readings":[],"profile":{"weightKg":-1}}""",
            """{"readings":[],"profile":{"smoker":"false"}}""", """{"readings":[],"profile":{"birthYear":9999}}""",
            """[{"timestamp":1,"systolic":118,"diastolic":76,"tags":"tag_unknown"}]""",
            """[{"timestamp":1,"systolic":118}]""")
        for (raw in bad) {
            assertThrows(Exception::class.java) { BackupCodec.decode(raw) }
        }
    }

    @Test fun restoreMergesReadingsAndCanRestoreProfileWithZeroNewReadings() = runBlocking {
        val readings = FakeReadings()
        val profiles = FakeProfile()
        val restore = RestoreBackupUseCase(readings, profiles)
        val backup = BackupCodec.decode(BackupCodec.encode(listOf(row, row), profile))
        val first = restore(backup, includeProfile = false)
        assertEquals(1, first.count)
        assertFalse(first.profileImported)
        assertEquals(UserProfile(), profiles.state.value)
        val second = restore(backup, includeProfile = true)
        assertEquals(0, second.count)
        assertTrue(second.profileImported)
        assertEquals(profile, profiles.state.value)
        assertEquals(1, readings.state.value.size)
        restore(BackupCodec.decode(legacy), includeProfile = true)
        assertEquals(profile, profiles.state.value)
    }

    @Test fun profileOnlyBackupRestoresWithoutReadings() = runBlocking {
        val profiles = FakeProfile()
        val result = RestoreBackupUseCase(FakeReadings(), profiles)(
            BackupCodec.decode(BackupCodec.encode(emptyList(), profile)), true)
        assertEquals(0, result.count)
        assertTrue(result.profileImported)
        assertEquals(profile, profiles.state.value)
    }

    @Test fun failedInsertDoesNotReportSuccessOrOverwriteProfile() = runBlocking {
        val readings = FakeReadings().apply { failInsert = true }
        val profiles = FakeProfile()
        try {
            RestoreBackupUseCase(readings, profiles)(BackupCodec.decode(BackupCodec.encode(listOf(row), profile)), true)
            fail("Expected storage failure")
        } catch (_: IllegalStateException) { }
        assertEquals(UserProfile(), profiles.state.value)
    }

    private class FakeProfile : UserProfileRepository {
        val state = MutableStateFlow(UserProfile())
        override fun observe() = state
        override suspend fun save(profile: UserProfile) { state.value = profile }
    }
    private class FakeReadings : BPRepository {
        val state = MutableStateFlow(emptyList<BPReading>())
        var failInsert = false
        override fun observeAll() = state
        override suspend fun getAll() = state.value
        override suspend fun getLatest() = state.value.lastOrNull()
        override suspend fun insert(reading: BPReading): Long {
            check(!failInsert)
            state.value += reading
            return state.value.size.toLong()
        }
        override suspend fun delete(id: Long) { error("Not used") }
        override suspend fun updateTags(id: Long, tags: List<BpTag>) { error("Not used") }
    }
}

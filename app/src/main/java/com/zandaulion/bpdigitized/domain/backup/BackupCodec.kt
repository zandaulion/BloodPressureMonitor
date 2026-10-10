package com.zandaulion.bpdigitized.domain.backup

import com.zandaulion.bpdigitized.domain.model.*
import com.zandaulion.bpdigitized.domain.usecase.CalculateBPZoneUseCase
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.Year
import java.util.Locale

/** Shared PWA/Android JSON backup. This code never reads or writes device storage. */
object BackupCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val profileKeys = setOf("birthYear", "sex", "weightKg", "heightCm", "activity", "smoker", "diabetes")

    fun decode(raw: String): BackupData {
        val root = json.parseToJsonElement(raw.removePrefix("\uFEFF"))
        val rows: JsonArray
        var profile: ProfilePatch? = null
        when (root) {
            is JsonArray -> rows = root // Original Android exports.
            is JsonObject -> {
                require(root["app"] == null || root["app"] == JsonPrimitive("bp-digitizer")) { "Unsupported backup app" }
                require(root["version"] == null || root["version"] == JsonPrimitive(1)) { "Unsupported backup version" }
                rows = root["readings"] as? JsonArray ?: error("Backup requires a readings array")
                val value = root["profile"]
                if (value != null && value != JsonNull) {
                    val obj = value as? JsonObject ?: error("Profile must be an object")
                    val fields = obj.keys.intersect(profileKeys)
                    if (fields.isNotEmpty()) {
                        for (key in listOf("smoker", "diabetes")) {
                            obj[key]?.let { flag ->
                                require(flag is JsonPrimitive && !flag.isString && flag.booleanOrNull != null) {
                                    "Profile flags must be JSON booleans"
                                }
                            }
                        }
                        val entry = json.decodeFromJsonElement<ProfileEntry>(obj)
                        profile = ProfilePatch(entry.toProfile(), fields)
                    }
                }
            }
            else -> error("Expected a JSON backup or readings array")
        }
        // Decode and validate the complete file before any repository writes.
        val readings = rows.map { json.decodeFromJsonElement<ReadingEntry>(it).toReading() }
        return BackupData(readings, profile)
    }

    fun encode(readings: List<BPReading>, profile: UserProfile): String = json.encodeToString(
        Envelope(profile = ProfileEntry.from(profile), readings = readings.map(ReadingEntry::from)),
    )

    @Serializable private data class Envelope(
        val app: String = "bp-digitizer",
        val version: Int = 1,
        val exported: String = Instant.now().toString(),
        val profile: ProfileEntry,
        val readings: List<ReadingEntry>,
    )

    @Serializable private data class ProfileEntry(
        val birthYear: Int? = null,
        val sex: String? = null,
        val weightKg: Float? = null,
        val heightCm: Float? = null,
        val activity: String? = null,
        val smoker: Boolean = false,
        val diabetes: Boolean = false,
    ) {
        fun toProfile(): UserProfile {
            require(birthYear == null || birthYear in 1900..Year.now().value) { "Invalid birth year" }
            require(weightKg == null || (weightKg.isFinite() && weightKg > 0 && weightKg <= 500)) { "Invalid weight" }
            require(heightCm == null || (heightCm.isFinite() && heightCm > 0 && heightCm <= 280)) { "Invalid height" }
            val biologicalSex = when (sex) {
                null, "", "OTHER", "PREFER_NOT_TO_SAY" -> BiologicalSex.PREFER_NOT_TO_SAY
                else -> BiologicalSex.valueOf(sex)
            }
            return UserProfile(birthYear, biologicalSex, weightKg, heightCm, smoker, diabetes,
                activity?.let(ActivityLevel::valueOf) ?: ActivityLevel.MODERATELY_ACTIVE)
        }
        companion object {
            fun from(p: UserProfile) = ProfileEntry(p.birthYear,
                if (p.biologicalSex == BiologicalSex.PREFER_NOT_TO_SAY) "OTHER" else p.biologicalSex.name,
                p.weightKg, p.heightCm, p.activityLevel.name, p.isSmoker, p.hasDiabetes)
        }
    }

    @Serializable private data class ReadingEntry(
        val timestamp: Long,
        val systolic: Int,
        val diastolic: Int,
        val pulse: Int? = null,
        val category: String? = null,
        val notes: String? = null,
        val tags: JsonElement = JsonArray(emptyList()),
        val id: Long = 0,
        val dateTime: String? = null,
    ) {
        fun toReading(): BPReading {
            require(timestamp >= 0 && systolic > 0 && diastolic > 0 && (pulse == null || pulse > 0)) { "Invalid reading" }
            val tokens = when (tags) {
                JsonNull -> emptyList()
                is JsonArray -> tags.map { require(it is JsonPrimitive && it.isString); it.content }
                is JsonPrimitive -> { require(tags.isString); tags.content.split(',') }
                else -> error("Invalid tags")
            }
            val parsedTags = tokens.map(String::trim).filter(String::isNotEmpty).map {
                BpTag.valueOf(it.removePrefix("tag_").uppercase(Locale.ROOT))
            }.distinct()
            val zone = BPCategory.entries.find { it.name == category }
                ?: CalculateBPZoneUseCase()(systolic, diastolic)
            return BPReading(0, timestamp, systolic, diastolic, pulse, zone, notes, parsedTags)
        }
        companion object {
            fun from(r: BPReading) = ReadingEntry(r.timestamp, r.systolic, r.diastolic, r.pulse,
                r.category.name, r.notes, JsonArray(r.tags.map { JsonPrimitive(it.name) }),
                r.id, Instant.ofEpochMilli(r.timestamp).toString())
        }
    }
}

data class BackupData(val readings: List<BPReading>, val profile: ProfilePatch?)

/** Missing fields preserve local values; explicit null clears optional measurements. */
data class ProfilePatch(val values: UserProfile, val fields: Set<String>) {
    fun applyTo(current: UserProfile) = UserProfile(
        if ("birthYear" in fields) values.birthYear else current.birthYear,
        if ("sex" in fields) values.biologicalSex else current.biologicalSex,
        if ("weightKg" in fields) values.weightKg else current.weightKg,
        if ("heightCm" in fields) values.heightCm else current.heightCm,
        if ("smoker" in fields) values.isSmoker else current.isSmoker,
        if ("diabetes" in fields) values.hasDiabetes else current.hasDiabetes,
        if ("activity" in fields) values.activityLevel else current.activityLevel,
    )
}

package com.example.bpdigitizer.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.bpdigitizer.domain.model.ActivityLevel
import com.example.bpdigitizer.domain.model.BiologicalSex
import com.example.bpdigitizer.domain.model.UserProfile
import com.example.bpdigitizer.domain.repository.UserProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserProfileRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : UserProfileRepository {

    override fun observe(): Flow<UserProfile> = dataStore.data.map { prefs ->
        UserProfile(
            birthYear = prefs[Keys.BIRTH_YEAR],
            biologicalSex = prefs[Keys.SEX]
                ?.let { runCatching { BiologicalSex.valueOf(it) }.getOrNull() }
                ?: BiologicalSex.PREFER_NOT_TO_SAY,
            weightKg = prefs[Keys.WEIGHT_KG],
            heightCm = prefs[Keys.HEIGHT_CM],
            isSmoker = prefs[Keys.IS_SMOKER] ?: false,
            hasDiabetes = prefs[Keys.HAS_DIABETES] ?: false,
            activityLevel = prefs[Keys.ACTIVITY_LEVEL]
                ?.let { runCatching { ActivityLevel.valueOf(it) }.getOrNull() }
                ?: ActivityLevel.MODERATELY_ACTIVE,
        )
    }

    override suspend fun save(profile: UserProfile) {
        dataStore.edit { prefs ->
            if (profile.birthYear != null) prefs[Keys.BIRTH_YEAR] = profile.birthYear
            else prefs.remove(Keys.BIRTH_YEAR)

            prefs[Keys.SEX] = profile.biologicalSex.name

            if (profile.weightKg != null) prefs[Keys.WEIGHT_KG] = profile.weightKg
            else prefs.remove(Keys.WEIGHT_KG)

            if (profile.heightCm != null) prefs[Keys.HEIGHT_CM] = profile.heightCm
            else prefs.remove(Keys.HEIGHT_CM)

            prefs[Keys.IS_SMOKER] = profile.isSmoker
            prefs[Keys.HAS_DIABETES] = profile.hasDiabetes
            prefs[Keys.ACTIVITY_LEVEL] = profile.activityLevel.name
        }
    }

    private object Keys {
        val BIRTH_YEAR     = intPreferencesKey("profile_birth_year")
        val SEX            = stringPreferencesKey("profile_sex")
        val WEIGHT_KG      = floatPreferencesKey("profile_weight_kg")
        val HEIGHT_CM      = floatPreferencesKey("profile_height_cm")
        val IS_SMOKER      = booleanPreferencesKey("profile_is_smoker")
        val HAS_DIABETES   = booleanPreferencesKey("profile_has_diabetes")
        val ACTIVITY_LEVEL = stringPreferencesKey("profile_activity_level")
    }
}

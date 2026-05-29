package com.zandaulion.bpdigitized.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.zandaulion.bpdigitized.domain.model.ReminderSettings
import com.zandaulion.bpdigitized.domain.repository.ReminderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ReminderRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ReminderRepository {

    override fun observe(): Flow<ReminderSettings> = dataStore.data.map { prefs ->
        ReminderSettings(
            isEnabled      = prefs[KEY_ENABLED] ?: false,
            morningMinutes = prefs[KEY_MORNING]  ?: DEFAULT_MORNING,
            eveningMinutes = prefs[KEY_EVENING]  ?: DEFAULT_EVENING,
        )
    }

    override suspend fun save(settings: ReminderSettings) {
        dataStore.edit { prefs ->
            prefs[KEY_ENABLED] = settings.isEnabled
            prefs[KEY_MORNING] = settings.morningMinutes
            prefs[KEY_EVENING] = settings.eveningMinutes
        }
    }

    private companion object {
        val KEY_ENABLED = booleanPreferencesKey("reminder_is_enabled")
        val KEY_MORNING = intPreferencesKey("reminder_morning_minutes")
        val KEY_EVENING = intPreferencesKey("reminder_evening_minutes")

        const val DEFAULT_MORNING = 7 * 60 + 30  // 07:30
        const val DEFAULT_EVENING = 20 * 60       // 20:00
    }
}

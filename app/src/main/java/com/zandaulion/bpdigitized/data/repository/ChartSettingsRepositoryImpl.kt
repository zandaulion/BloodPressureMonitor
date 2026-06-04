package com.zandaulion.bpdigitized.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.zandaulion.bpdigitized.domain.repository.ChartSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChartSettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ChartSettingsRepository {

    override fun observeSmoothBursts(): Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_SMOOTH] ?: true
    }

    override suspend fun setSmoothBursts(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_SMOOTH] = enabled
        }
    }

    private companion object {
        val KEY_SMOOTH = booleanPreferencesKey("chart_smooth_bursts")
    }
}

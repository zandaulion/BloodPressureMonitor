package com.zandaulion.bpdigitized.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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

    override fun observeChartMode(): Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_CHART_MODE] ?: ""
    }

    override suspend fun setChartMode(mode: String) {
        dataStore.edit { prefs ->
            prefs[KEY_CHART_MODE] = mode
        }
    }

    private companion object {
        val KEY_SMOOTH     = booleanPreferencesKey("chart_smooth_bursts")
        val KEY_CHART_MODE = stringPreferencesKey("chart_mode")
    }
}

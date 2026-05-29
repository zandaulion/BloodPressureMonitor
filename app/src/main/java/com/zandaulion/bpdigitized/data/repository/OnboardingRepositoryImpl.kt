package com.zandaulion.bpdigitized.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.zandaulion.bpdigitized.domain.repository.OnboardingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class OnboardingRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : OnboardingRepository {

    override fun hasSeenOnboarding(): Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_SEEN] ?: false
    }

    override suspend fun setSeen() {
        dataStore.edit { prefs ->
            prefs[KEY_SEEN] = true
        }
    }

    private companion object {
        val KEY_SEEN = booleanPreferencesKey("onboarding_seen")
    }
}

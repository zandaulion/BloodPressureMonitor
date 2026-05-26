package com.example.bpdigitizer.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.bpdigitizer.domain.repository.ApiKeyRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiKeyRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : ApiKeyRepository {

    /**
     * Lazily initialised so that the Keystore key-generation (a blocking
     * operation) only happens on the first actual read/write, not at DI graph
     * construction time.
     */
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "secure_api_keys",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    // In-memory flow so the UI can react immediately without polling SharedPreferences.
    private val _hasKey = MutableStateFlow(false)

    init {
        // Initialise the in-memory flow once the object is created.
        // The lazy `prefs` init runs here the first time (on a background thread
        // in practice because the ViewModel's init is called from a coroutine).
        _hasKey.value = !prefs.getString(KEY, null).isNullOrBlank()
    }

    override fun observeHasKey(): Flow<Boolean> = _hasKey

    override fun getKey(): String? = prefs.getString(KEY, null)?.takeIf { it.isNotBlank() }

    override fun saveKey(key: String) {
        prefs.edit().putString(KEY, key.trim()).apply()
        _hasKey.value = key.isNotBlank()
    }

    override fun clearKey() {
        prefs.edit().remove(KEY).apply()
        _hasKey.value = false
    }

    private companion object {
        const val KEY = "gemini_api_key"
    }
}

package com.example.bpdigitizer.domain.repository

import kotlinx.coroutines.flow.Flow

interface ApiKeyRepository {
    /** Emits `true` whenever a non-blank key is stored, `false` otherwise. */
    fun observeHasKey(): Flow<Boolean>
    /** Returns the stored key, or `null` if none has been saved yet. */
    fun getKey(): String?
    fun saveKey(key: String)
    fun clearKey()
}

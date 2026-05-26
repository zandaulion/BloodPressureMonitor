package com.example.bpdigitizer.domain.repository

import com.example.bpdigitizer.domain.model.ReminderSettings
import kotlinx.coroutines.flow.Flow

interface ReminderRepository {
    fun observe(): Flow<ReminderSettings>
    suspend fun save(settings: ReminderSettings)
}

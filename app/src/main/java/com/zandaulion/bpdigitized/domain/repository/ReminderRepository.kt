package com.zandaulion.bpdigitized.domain.repository

import com.zandaulion.bpdigitized.domain.model.ReminderSettings
import kotlinx.coroutines.flow.Flow

interface ReminderRepository {
    fun observe(): Flow<ReminderSettings>
    suspend fun save(settings: ReminderSettings)
}

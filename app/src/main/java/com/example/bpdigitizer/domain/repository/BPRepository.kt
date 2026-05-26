package com.example.bpdigitizer.domain.repository

import com.example.bpdigitizer.domain.model.BPReading
import kotlinx.coroutines.flow.Flow

interface BPRepository {
    fun observeAll(): Flow<List<BPReading>>
    suspend fun getAll(): List<BPReading>
    suspend fun getLatest(): BPReading?
    suspend fun insert(reading: BPReading): Long
    suspend fun delete(id: Long)
}

package com.zandaulion.bpdigitized.domain.repository

import com.zandaulion.bpdigitized.domain.model.BPReading
import com.zandaulion.bpdigitized.domain.model.BpTag
import kotlinx.coroutines.flow.Flow

interface BPRepository {
    fun observeAll(): Flow<List<BPReading>>
    suspend fun getAll(): List<BPReading>
    suspend fun getLatest(): BPReading?
    suspend fun insert(reading: BPReading): Long
    suspend fun delete(id: Long)
    /** Overwrites the tags for an existing reading without touching any other field. */
    suspend fun updateTags(id: Long, tags: List<BpTag>)
}

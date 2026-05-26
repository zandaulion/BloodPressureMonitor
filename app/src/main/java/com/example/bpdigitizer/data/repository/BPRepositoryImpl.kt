package com.example.bpdigitizer.data.repository

import com.example.bpdigitizer.data.local.BPReadingDao
import com.example.bpdigitizer.data.local.toDomain
import com.example.bpdigitizer.data.local.toEntity
import com.example.bpdigitizer.domain.model.BPReading
import com.example.bpdigitizer.domain.repository.BPRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BPRepositoryImpl @Inject constructor(
    private val dao: BPReadingDao,
) : BPRepository {
    override fun observeAll(): Flow<List<BPReading>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getAll(): List<BPReading> =
        dao.getAll().map { it.toDomain() }

    override suspend fun insert(reading: BPReading): Long =
        dao.insert(reading.toEntity())

    override suspend fun delete(id: Long) = dao.delete(id)
}

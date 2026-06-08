package com.zandaulion.bpdigitized.data.repository

import com.zandaulion.bpdigitized.data.local.BPReadingDao
import com.zandaulion.bpdigitized.data.local.toDomain
import com.zandaulion.bpdigitized.data.local.toEntity
import com.zandaulion.bpdigitized.domain.model.BPReading
import com.zandaulion.bpdigitized.domain.model.BpTag
import com.zandaulion.bpdigitized.domain.repository.BPRepository
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

    override suspend fun getLatest(): BPReading? =
        dao.getLatest()?.toDomain()

    override suspend fun insert(reading: BPReading): Long =
        dao.insert(reading.toEntity())

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun updateTags(id: Long, tags: List<BpTag>) {
        dao.updateTags(id, tags.joinToString(",") { it.name })
    }
}

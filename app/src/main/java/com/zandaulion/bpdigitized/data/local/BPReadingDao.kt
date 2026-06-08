package com.zandaulion.bpdigitized.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BPReadingDao {
    @Query("SELECT * FROM bp_readings ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<BPReadingEntity>>

    @Query("SELECT * FROM bp_readings ORDER BY timestamp DESC")
    suspend fun getAll(): List<BPReadingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reading: BPReadingEntity): Long

    @Query("SELECT * FROM bp_readings ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(): BPReadingEntity?

    @Query("DELETE FROM bp_readings WHERE id = :id")
    suspend fun delete(id: Long)

    /** Replaces only the tags column without touching any other field. */
    @Query("UPDATE bp_readings SET tags = :tags WHERE id = :id")
    suspend fun updateTags(id: Long, tags: String)
}

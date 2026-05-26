package com.example.bpdigitizer.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BPReadingDao {
    @Query("SELECT * FROM bp_readings ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<BPReadingEntity>>

    @Query("SELECT * FROM bp_readings ORDER BY timestamp DESC")
    suspend fun getAll(): List<BPReadingEntity>

    @Insert
    suspend fun insert(reading: BPReadingEntity): Long

    @Query("DELETE FROM bp_readings WHERE id = :id")
    suspend fun delete(id: Long)
}

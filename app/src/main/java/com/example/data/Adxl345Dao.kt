package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface Adxl345Dao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReading(reading: Adxl345ReadingEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReadings(readings: List<Adxl345ReadingEntity>)

    @Query("SELECT * FROM adxl345_readings ORDER BY timestamp DESC")
    fun getAllReadings(): Flow<List<Adxl345ReadingEntity>>

    @Query("SELECT * FROM adxl345_readings ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentReadings(limit: Int): Flow<List<Adxl345ReadingEntity>>

    @Query("SELECT * FROM adxl345_readings WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getReadingsForSession(sessionId: String): Flow<List<Adxl345ReadingEntity>>

    @Query("SELECT COUNT(*) FROM adxl345_readings")
    fun getReadingCount(): Flow<Int>

    @Query("DELETE FROM adxl345_readings")
    suspend fun clearAllReadings()

    @Query("DELETE FROM adxl345_readings WHERE sessionId = :sessionId")
    suspend fun deleteReadingsForSession(sessionId: String)
}

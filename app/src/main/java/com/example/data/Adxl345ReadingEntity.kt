package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "adxl345_readings")
data class Adxl345ReadingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val x: Float,
    val y: Float,
    val z: Float,
    val pitch: Float,
    val roll: Float,
    val gForce: Float,
    val sessionId: String = "default"
)

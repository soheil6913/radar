package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a top-level ground scan session.
 * Stores spatial dimensions, soil properties, and metadata for 3D visualization.
 */
@Entity(tableName = "scan_sessions")
data class ScanSessionEntity(
    @PrimaryKey(autoGenerate = true) val sessionId: Long = 0,
    val sessionName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val gridWidth: Int,
    val gridLength: Int,
    val soilType: String = "خاک کشاورزی (Soil)",
    val scanPattern: String = "زیگزاگ (Zig-Zag)",
    val sensorType: String = "GOLD_RADAR_X20",
    val maxDepthMeters: Float = 3.5f,
    val operatorNotes: String = ""
) {
    val totalGridPoints: Int get() = gridWidth * gridLength
}

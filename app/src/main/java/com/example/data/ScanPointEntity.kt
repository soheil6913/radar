package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing an individual 3D scan point with spatial (X/Y),
 * signal strength, vertical depth estimation, phase shift, and classification metadata.
 */
@Entity(
    tableName = "scan_points",
    foreignKeys = [
        ForeignKey(
            entity = ScanSessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("sessionId"),
        Index("signalStrength"),
        Index(value = ["sessionId", "xIndex", "yIndex"], unique = true)
    ]
)
data class ScanPointEntity(
    @PrimaryKey(autoGenerate = true) val pointId: Long = 0,
    val sessionId: Long,
    val xIndex: Int,
    val yIndex: Int,
    val xCoordMeters: Float = 0f,
    val yCoordMeters: Float = 0f,
    val signalStrength: Float,
    val depthMeters: Float = 0f,
    val phaseShift: Int = 0,
    val targetClassification: String = "خاک عادی 🌱",
    val timestamp: Long = System.currentTimeMillis()
)

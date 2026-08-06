package com.example.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room @Entity representing a ground scan session point, storing spatial X/Y coordinates,
 * calculated depth, signal strength, timestamp, and metadata for 3D visualization.
 */
@Entity(tableName = "scan_session_records")
data class ScanSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "x_coordinate")
    val xCoordinate: Float = 0f,
    
    @ColumnInfo(name = "y_coordinate")
    val yCoordinate: Float = 0f,
    
    @ColumnInfo(name = "x_index")
    val xIndex: Int = 0,
    
    @ColumnInfo(name = "y_index")
    val yIndex: Int = 0,
    
    @ColumnInfo(name = "depth")
    val depth: Float = 0f,
    
    @ColumnInfo(name = "signal_strength")
    val signalStrength: Float = 0f,
    
    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "session_name")
    val sessionName: String = "Gold Radar Session",
    
    @ColumnInfo(name = "soil_type")
    val soilType: String = "Normal Soil",
    
    @ColumnInfo(name = "notes")
    val notes: String = ""
)

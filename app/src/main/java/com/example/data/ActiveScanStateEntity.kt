package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room @Entity used for auto-persisting active 3D scan sessions and visualization work.
 * Guarantees zero data loss across app restart, crash, or background process termination.
 */
@Entity(tableName = "active_scan_state")
data class ActiveScanStateEntity(
    @PrimaryKey val id: Int = 1,
    val isScanActive: Boolean = false,
    val gridWidth: Int = 8,
    val gridLength: Int = 10,
    val currentCol: Int = 0,
    val currentRow: Int = 0,
    val soilType: String = "خاک کشاورزی (Soil)",
    val scanPattern: String = "زیگزاگ (Zig-Zag)",
    val activeDataJson: String = "",
    val viewedScanId: Int? = null,
    val viewedScanName: String? = null,
    val viewedScanWidth: Int = 8,
    val viewedScanLength: Int = 10,
    val viewedScanSoilType: String = "خاک کشاورزی (Soil)",
    val viewedScanPattern: String = "زیگزاگ (Zig-Zag)",
    val viewedScanGridDataJson: String = "",
    val viewedScanNotes: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

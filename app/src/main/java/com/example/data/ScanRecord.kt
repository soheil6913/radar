package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_records")
data class ScanRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val timestamp: Long = System.currentTimeMillis(),
    val width: Int,
    val length: Int,
    val soilType: String,
    val scanPattern: String,
    val gridDataJson: String, // Space-separated floats, e.g. "23.4 45.2 -10.5..."
    val notes: String = ""
) {
    // Helper to get raw float data as List<Float>
    fun getGridData(): List<Float> {
        if (gridDataJson.isEmpty()) return emptyList()
        return try {
            gridDataJson.split(" ")
                .filter { it.isNotEmpty() }
                .map { it.toFloat() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        fun createGridDataJson(data: List<Float>): String {
            return data.joinToString(" ")
        }
    }
}

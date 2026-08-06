package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a user-defined custom marker or text annotation placed at a 3D coordinate (col, row).
 */
@Entity(tableName = "user_3d_annotations")
data class User3DAnnotation(
    @PrimaryKey
    val id: String = java.util.UUID.randomUUID().toString(),
    val scanId: Int? = null,
    val col: Int,
    val row: Int,
    val label: String,
    val note: String = "",
    val colorHex: String = "#FFD700",
    val timestamp: Long = System.currentTimeMillis()
) {
    fun createdAtFormatted(): String {
        val sdf = java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }
}

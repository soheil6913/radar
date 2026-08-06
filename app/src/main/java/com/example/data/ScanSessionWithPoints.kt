package com.example.data

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Relational data class embedding a [ScanSessionEntity] with its collection of 3D [ScanPointEntity] instances.
 * Used for detailed spatial rendering, depth layering, and 3D visualizer persistence.
 */
data class ScanSessionWithPoints(
    @Embedded val session: ScanSessionEntity,
    @Relation(
        parentColumn = "sessionId",
        entityColumn = "sessionId"
    )
    val points: List<ScanPointEntity>
) {
    /**
     * Converts structured relational session and 3D points into a legacy [ScanRecord].
     */
    fun toScanRecord(): ScanRecord {
        val totalCells = session.gridWidth * session.gridLength
        val dataArray = FloatArray(totalCells) { 0f }

        points.forEach { pt ->
            val idx = pt.yIndex * session.gridWidth + pt.xIndex
            if (idx in dataArray.indices) {
                dataArray[idx] = pt.signalStrength
            }
        }

        return ScanRecord(
            id = session.sessionId.toInt(),
            name = session.sessionName,
            timestamp = session.timestamp,
            width = session.gridWidth,
            length = session.gridLength,
            soilType = session.soilType,
            scanPattern = session.scanPattern,
            gridDataJson = ScanRecord.createGridDataJson(dataArray.toList()),
            notes = session.operatorNotes
        )
    }

    /**
     * Retrieves the peak positive (metal) anomaly point in this scan session.
     */
    fun getPeakMetalPoint(): ScanPointEntity? {
        return points.maxByOrNull { it.signalStrength }
    }

    /**
     * Retrieves the deepest cavity / void point in this scan session.
     */
    fun getDeepestCavityPoint(): ScanPointEntity? {
        return points.minByOrNull { it.signalStrength }
    }
}

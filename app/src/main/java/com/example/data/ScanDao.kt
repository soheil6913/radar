package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {
    // ==========================================
    // LEGENDARY / LEGACY SCAN RECORD QUERIES
    // ==========================================
    @Query("SELECT * FROM scan_records ORDER BY timestamp DESC")
    fun getAllScans(): Flow<List<ScanRecord>>

    @Query("SELECT * FROM scan_records WHERE id = :id LIMIT 1")
    suspend fun getScanById(id: Int): ScanRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScan(scan: ScanRecord): Long

    @Query("DELETE FROM scan_records WHERE id = :id")
    suspend fun deleteScanById(id: Int)

    @Query("DELETE FROM scan_records")
    suspend fun deleteAllScans()

    // ==========================================
    // RELATIONAL 3D SCAN SESSION QUERIES
    // ==========================================
    @Query("SELECT * FROM scan_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<ScanSessionEntity>>

    @Transaction
    @Query("SELECT * FROM scan_sessions ORDER BY timestamp DESC")
    fun getAllSessionsWithPoints(): Flow<List<ScanSessionWithPoints>>

    @Transaction
    @Query("SELECT * FROM scan_sessions WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getSessionWithPoints(sessionId: Long): ScanSessionWithPoints?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ScanSessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScanPoints(points: List<ScanPointEntity>)

    /**
     * Atomically saves a complete 3D scan session alongside all calculated coordinate points.
     */
    @Transaction
    suspend fun saveFull3DScanSession(session: ScanSessionEntity, points: List<ScanPointEntity>): Long {
        val insertedSessionId = insertSession(session)
        val preparedPoints = points.map { point ->
            point.copy(sessionId = insertedSessionId)
        }
        insertScanPoints(preparedPoints)
        return insertedSessionId
    }

    @Query("SELECT * FROM scan_points WHERE sessionId = :sessionId ORDER BY yIndex ASC, xIndex ASC")
    fun getPointsForSession(sessionId: Long): Flow<List<ScanPointEntity>>

    @Query("SELECT * FROM scan_points WHERE sessionId = :sessionId AND abs(signalStrength) >= :minSignalStrength")
    suspend fun getAnomaliesForSession(sessionId: Long, minSignalStrength: Float): List<ScanPointEntity>

    @Query("DELETE FROM scan_sessions WHERE sessionId = :sessionId")
    suspend fun deleteSessionById(sessionId: Long)

    @Query("DELETE FROM scan_sessions")
    suspend fun deleteAllSessions()

    // ==========================================
    // DIRECT SCAN SESSION ITEM QUERIES
    // ==========================================
    @Query("SELECT * FROM scan_session_records ORDER BY timestamp DESC")
    fun getAllScanSessions(): Flow<List<ScanSession>>

    @Query("SELECT * FROM scan_session_records WHERE id = :id LIMIT 1")
    suspend fun getScanSessionById(id: Long): ScanSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScanSession(session: ScanSession): Long

    @Query("DELETE FROM scan_session_records WHERE id = :id")
    suspend fun deleteScanSessionById(id: Long)

    // ==========================================
    // ACTIVE SCAN STATE AUTO-PERSISTENCE
    // ==========================================
    @Query("SELECT * FROM active_scan_state WHERE id = 1 LIMIT 1")
    fun getActiveScanStateFlow(): Flow<ActiveScanStateEntity?>

    @Query("SELECT * FROM active_scan_state WHERE id = 1 LIMIT 1")
    suspend fun getActiveScanState(): ActiveScanStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveActiveScanState(state: ActiveScanStateEntity)

    @Query("DELETE FROM active_scan_state")
    suspend fun clearActiveScanState()

    // ==========================================
    // 3D USER ANNOTATIONS & MARKERS
    // ==========================================
    @Query("SELECT * FROM user_3d_annotations ORDER BY timestamp DESC")
    fun getAllAnnotationsFlow(): Flow<List<User3DAnnotation>>

    @Query("SELECT * FROM user_3d_annotations WHERE scanId = :scanId ORDER BY timestamp DESC")
    fun getAnnotationsForScan(scanId: Int): Flow<List<User3DAnnotation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotation(annotation: User3DAnnotation)

    @Delete
    suspend fun deleteAnnotation(annotation: User3DAnnotation)

    @Query("DELETE FROM user_3d_annotations WHERE id = :id")
    suspend fun deleteAnnotationById(id: String)

    @Query("DELETE FROM user_3d_annotations")
    suspend fun deleteAllAnnotations()
}

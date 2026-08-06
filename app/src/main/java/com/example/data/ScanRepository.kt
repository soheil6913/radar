package com.example.data

import kotlinx.coroutines.flow.Flow

/**
 * Repository layer wrapping Room DAO queries for legacy [ScanRecord] items
 * as well as relational 3D [ScanSessionEntity] and [ScanPointEntity] spatial models.
 */
class ScanRepository(
    private val scanDao: ScanDao,
    private val adxl345Dao: Adxl345Dao? = null
) {
    // ==========================================
    // LEGACY / RECORD FLOWS
    // ==========================================
    val allScans: Flow<List<ScanRecord>> = scanDao.getAllScans()

    suspend fun getScanById(id: Int): ScanRecord? {
        return scanDao.getScanById(id)
    }

    suspend fun insertScan(scan: ScanRecord): Long {
        return scanDao.insertScan(scan)
    }

    suspend fun deleteScanById(id: Int) {
        scanDao.deleteScanById(id)
    }

    suspend fun deleteAllScans() {
        scanDao.deleteAllScans()
    }

    // ==========================================
    // RELATIONAL 3D SCAN SESSIONS & POINTS
    // ==========================================
    val allSessions: Flow<List<ScanSessionEntity>> = scanDao.getAllSessions()

    val allSessionsWithPoints: Flow<List<ScanSessionWithPoints>> = scanDao.getAllSessionsWithPoints()

    suspend fun getSessionWithPoints(sessionId: Long): ScanSessionWithPoints? {
        return scanDao.getSessionWithPoints(sessionId)
    }

    suspend fun saveFull3DScanSession(
        session: ScanSessionEntity,
        points: List<ScanPointEntity>
    ): Long {
        return scanDao.saveFull3DScanSession(session, points)
    }

    fun getPointsForSession(sessionId: Long): Flow<List<ScanPointEntity>> {
        return scanDao.getPointsForSession(sessionId)
    }

    suspend fun getAnomaliesForSession(sessionId: Long, minSignal: Float): List<ScanPointEntity> {
        return scanDao.getAnomaliesForSession(sessionId, minSignal)
    }

    suspend fun deleteSessionById(sessionId: Long) {
        scanDao.deleteSessionById(sessionId)
    }

    suspend fun deleteAllSessions() {
        scanDao.deleteAllSessions()
    }

    // ==========================================
    // ACTIVE SCAN STATE AUTO-PERSISTENCE
    // ==========================================
    val activeScanState: Flow<ActiveScanStateEntity?> = scanDao.getActiveScanStateFlow()

    suspend fun getActiveScanState(): ActiveScanStateEntity? {
        return scanDao.getActiveScanState()
    }

    suspend fun saveActiveScanState(state: ActiveScanStateEntity) {
        scanDao.saveActiveScanState(state)
    }

    suspend fun clearActiveScanState() {
        scanDao.clearActiveScanState()
    }

    // ==========================================
    // 3D USER ANNOTATIONS & MARKERS
    // ==========================================
    val allAnnotations: Flow<List<User3DAnnotation>> = scanDao.getAllAnnotationsFlow()

    suspend fun saveAnnotation(annotation: User3DAnnotation) {
        scanDao.insertAnnotation(annotation)
    }

    suspend fun deleteAnnotationById(id: String) {
        scanDao.deleteAnnotationById(id)
    }

    suspend fun clearAllAnnotations() {
        scanDao.deleteAllAnnotations()
    }

    // ==========================================
    // ADXL345 RAW SENSOR READINGS PERSISTENCE
    // ==========================================
    val allAdxlReadings: Flow<List<Adxl345ReadingEntity>> = adxl345Dao?.getAllReadings() ?: kotlinx.coroutines.flow.flowOf(emptyList())

    val adxlReadingCount: Flow<Int> = adxl345Dao?.getReadingCount() ?: kotlinx.coroutines.flow.flowOf(0)

    fun getRecentAdxlReadings(limit: Int = 100): Flow<List<Adxl345ReadingEntity>> {
        return adxl345Dao?.getRecentReadings(limit) ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }

    suspend fun insertAdxlReading(reading: Adxl345ReadingEntity): Long {
        return adxl345Dao?.insertReading(reading) ?: -1L
    }

    suspend fun insertAdxlReadings(readings: List<Adxl345ReadingEntity>) {
        adxl345Dao?.insertReadings(readings)
    }

    suspend fun clearAllAdxlReadings() {
        adxl345Dao?.clearAllReadings()
    }
}

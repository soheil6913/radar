package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ActiveScanStateEntity
import com.example.data.ScanDatabase
import com.example.data.ScanPointEntity
import com.example.data.ScanRecord
import com.example.data.ScanRepository
import com.example.data.ScanSessionEntity
import com.example.data.User3DAnnotation
import com.example.hardware.ConnectionMode
import com.example.hardware.SensorManager
import com.example.hardware.SensorType
import com.example.hardware.Adxl345LoggerService
import com.example.hardware.TtsManager
import com.example.hardware.FeedbackManager
import com.example.service.ScanLoggingService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

data class GroundPlaneStats(
    val baselineOffset: Float = 0f,
    val slopeX: Float = 0f,
    val slopeY: Float = 0f,
    val deadbandThreshold: Float = 12.0f
)

class VisualizerViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val database = ScanDatabase.getDatabase(application)
    private val repository = ScanRepository(database.scanDao(), database.adxl345Dao())
    val sensorManager = SensorManager(application)

    // ADXL345 Room Database Logging State
    val isAdxlLoggingActive = Adxl345LoggerService.isServiceRunning
    val adxlLoggedCount = Adxl345LoggerService.loggedCount
    val adxlReadingsFromRoom = repository.allAdxlReadings
    val adxlReadingCountFromRoom = repository.adxlReadingCount

    // Text To Speech (TTS) Audio Playback Manager
    val ttsManager = TtsManager.getInstance(application)
    val isTtsSpeaking = ttsManager.isSpeaking
    val ttsSpeakingText = ttsManager.currentlySpeakingText

    fun speakText(text: String) {
        ttsManager.speak(text)
    }

    fun stopSpeech() {
        ttsManager.stop()
    }

    fun startAdxlLogging() {
        Adxl345LoggerService.startLogging(getApplication())
    }

    fun stopAdxlLogging() {
        Adxl345LoggerService.stopLogging(getApplication())
    }

    fun clearAdxlRoomLogs() {
        viewModelScope.launch {
            repository.clearAllAdxlReadings()
        }
    }

    // Auto-stream scan recording state for live USB serial sensors
    private val _isAutoScanStreaming = MutableStateFlow(false)
    val isAutoScanStreaming = _isAutoScanStreaming.asStateFlow()

    fun toggleAutoScanStreaming() {
        _isAutoScanStreaming.value = !_isAutoScanStreaming.value
    }

    fun setAutoScanStreaming(enabled: Boolean) {
        _isAutoScanStreaming.value = enabled
    }

    private val prefs = application.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val _appLanguage = MutableStateFlow(prefs.getString("app_language", "fa") ?: "fa")
    val appLanguage = _appLanguage.asStateFlow()

    private val _soundEnabled = MutableStateFlow(prefs.getBoolean("sound_enabled", true))
    val soundEnabled = _soundEnabled.asStateFlow()

    private val _vibrationEnabled = MutableStateFlow(prefs.getBoolean("vibration_enabled", true))
    val vibrationEnabled = _vibrationEnabled.asStateFlow()

    private val _sensorBeepEnabled = MutableStateFlow(prefs.getBoolean("sensor_beep_enabled", true))
    val sensorBeepEnabled = _sensorBeepEnabled.asStateFlow()

    private val _measurementUnit = MutableStateFlow(prefs.getString("measurement_unit", "m") ?: "m")
    val measurementUnit = _measurementUnit.asStateFlow()

    private val _showCalibrationWizard = MutableStateFlow(false)
    val showCalibrationWizard = _showCalibrationWizard.asStateFlow()

    fun openCalibrationWizard() {
        _showCalibrationWizard.value = true
    }

    fun closeCalibrationWizard() {
        _showCalibrationWizard.value = false
    }

    fun setAppLanguage(lang: String) {
        _appLanguage.value = lang
        prefs.edit().putString("app_language", lang).apply()
    }

    fun setSoundEnabled(enabled: Boolean) {
        _soundEnabled.value = enabled
        prefs.edit().putBoolean("sound_enabled", enabled).apply()
    }

    fun setVibrationEnabled(enabled: Boolean) {
        _vibrationEnabled.value = enabled
        prefs.edit().putBoolean("vibration_enabled", enabled).apply()
    }

    fun setSensorBeepEnabled(enabled: Boolean) {
        _sensorBeepEnabled.value = enabled
        prefs.edit().putBoolean("sensor_beep_enabled", enabled).apply()
    }

    fun setMeasurementUnit(unit: String) {
        _measurementUnit.value = unit
        prefs.edit().putString("measurement_unit", unit).apply()
    }

    fun importV3DData(content: String): Boolean {
        try {
            val lines = content.lines()
            var name = "اسکن وارد شده v3d"
            var timestamp = System.currentTimeMillis()
            var width = 8
            var length = 10
            var soilType = "سنگلاخی (Rocky)"
            var scanPattern = "زیگزاگ (Zig-Zag)"
            var dataStr = ""
            var notes = "وارد شده از دستگاه دیگر (.v3d)"
            
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                
                val parts = trimmed.split(":", limit = 2)
                if (parts.size < 2) continue
                val key = parts[0].trim().uppercase()
                val value = parts[1].trim()
                
                when (key) {
                    "NAME" -> name = value
                    "TIMESTAMP" -> timestamp = value.toLongOrNull() ?: System.currentTimeMillis()
                    "WIDTH" -> width = value.toIntOrNull() ?: 8
                    "LENGTH" -> length = value.toIntOrNull() ?: 10
                    "SOIL" -> soilType = value
                    "PATTERN" -> scanPattern = value
                    "DATA" -> dataStr = value
                    "NOTES" -> notes = value
                }
            }
            
            if (dataStr.isEmpty()) return false
            
            viewModelScope.launch {
                val importedRecord = ScanRecord(
                    name = name,
                    timestamp = timestamp,
                    width = width,
                    length = length,
                    soilType = soilType,
                    scanPattern = scanPattern,
                    gridDataJson = dataStr,
                    notes = notes
                )
                repository.insertScan(importedRecord)
                _viewedScan.value = importedRecord
            }
            return true
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
            return false
        }
    }

    // Saved scans list from database
    val savedScans: StateFlow<List<ScanRecord>> = repository.allScans
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 3D User Markers & Text Annotations
    val userAnnotations: StateFlow<List<User3DAnnotation>> = repository.allAnnotations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addUserAnnotation(col: Int, row: Int, label: String, note: String = "", colorHex: String = "#FFD700") {
        viewModelScope.launch {
            val scanId = _viewedScan.value?.id
            val annotation = User3DAnnotation(
                scanId = if (scanId != null && scanId > 0) scanId else null,
                col = col,
                row = row,
                label = label.ifBlank { "نشانگر (${col + 1}, ${row + 1})" },
                note = note,
                colorHex = colorHex
            )
            repository.saveAnnotation(annotation)
        }
    }

    fun deleteUserAnnotation(id: String) {
        viewModelScope.launch {
            repository.deleteAnnotationById(id)
        }
    }

    fun clearUserAnnotations() {
        viewModelScope.launch {
            repository.clearAllAnnotations()
        }
    }

    // Currently viewed scan data (for 3D visualizer)
    private val _viewedScan = MutableStateFlow<ScanRecord?>(null)
    val viewedScan = _viewedScan.asStateFlow()

    // Room Database Auto-Restoration Banner State
    private val _sessionRestoredFromRoom = MutableStateFlow(false)
    val sessionRestoredFromRoom = _sessionRestoredFromRoom.asStateFlow()

    fun dismissSessionRestoredNotice() {
        _sessionRestoredFromRoom.value = false
    }

    /**
     * Persists active scan session state and 3D visualization work to Room Database.
     * Prevents data loss on app close, background kill, or crash.
     */
    fun persistActiveStateToDb() {
        viewModelScope.launch {
            try {
                val viewed = _viewedScan.value
                val state = ActiveScanStateEntity(
                    id = 1,
                    isScanActive = _isScanActive.value,
                    gridWidth = _gridWidth.value,
                    gridLength = _gridLength.value,
                    currentCol = _currentCol.value,
                    currentRow = _currentRow.value,
                    soilType = _soilType.value,
                    scanPattern = _scanPattern.value,
                    activeDataJson = ScanRecord.createGridDataJson(_activeScanData.value),
                    viewedScanId = viewed?.id,
                    viewedScanName = viewed?.name,
                    viewedScanWidth = viewed?.width ?: _gridWidth.value,
                    viewedScanLength = viewed?.length ?: _gridLength.value,
                    viewedScanSoilType = viewed?.soilType ?: _soilType.value,
                    viewedScanPattern = viewed?.scanPattern ?: _scanPattern.value,
                    viewedScanGridDataJson = viewed?.gridDataJson ?: "",
                    viewedScanNotes = viewed?.notes ?: "",
                    timestamp = System.currentTimeMillis()
                )
                repository.saveActiveScanState(state)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Real-time digital filtering strength (-1.0 to 1.0)
    // -1.0 to < 0.0: High-pass (accentuate small targets/edges)
    // 0.0: Raw (unfiltered)
    // > 0.0 to 1.0: Low-pass (smooth noise/ground clutter)
    private val _filterStrength = MutableStateFlow(0.0f)
    val filterStrength = _filterStrength.asStateFlow()

    private val _selectedDepthLayer = MutableStateFlow("All") // "All", "Surface", "Subsurface", "Deep", "Bedrock"
    val selectedDepthLayer = _selectedDepthLayer.asStateFlow()

    private val _soilNoiseFilterEnabled = MutableStateFlow(false)
    val soilNoiseFilterEnabled = _soilNoiseFilterEnabled.asStateFlow()

    private val _colorAutoScaleEnabled = MutableStateFlow(true) // True by default so it's active immediately
    val colorAutoScaleEnabled = _colorAutoScaleEnabled.asStateFlow()

    private val _flattenBaseEnabled = MutableStateFlow(true) // Flatten ground plane base by default for maximum clarity
    val flattenBaseEnabled = _flattenBaseEnabled.asStateFlow()

    private val _groundPlaneStats = MutableStateFlow(GroundPlaneStats())
    val groundPlaneStats = _groundPlaneStats.asStateFlow()

    fun setFilterStrength(strength: Float) {
        _filterStrength.value = strength.coerceIn(-1.0f, 1.0f)
    }

    fun setSelectedDepthLayer(layer: String) {
        _selectedDepthLayer.value = layer
    }

    fun setSoilNoiseFilterEnabled(enabled: Boolean) {
        _soilNoiseFilterEnabled.value = enabled
    }

    fun setColorAutoScaleEnabled(enabled: Boolean) {
        _colorAutoScaleEnabled.value = enabled
    }

    fun setFlattenBaseEnabled(enabled: Boolean) {
        _flattenBaseEnabled.value = enabled
    }

    private fun applySoilNoiseRemoval(data: List<Float>, width: Int, length: Int): List<Float> {
        if (data.isEmpty() || width <= 0 || length <= 0) return data
        val size = data.size
        val result = ArrayList<Float>(size)
        
        for (i in 0 until size) {
            val x = i % width
            val y = i / width
            
            // Gather 3x3 neighborhood values for hybrid filtering
            val neighbors = ArrayList<Float>()
            for (dy in -1..1) {
                for (dx in -1..1) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx in 0 until width && ny in 0 until length) {
                        val nIdx = ny * width + nx
                        if (nIdx in data.indices) {
                            neighbors.add(data[nIdx])
                        }
                    }
                }
            }
            
            // Rejects extreme mineralization spikes (typical soil pollution)
            neighbors.sort()
            val median = if (neighbors.isNotEmpty()) neighbors[neighbors.size / 2] else data[i]
            
            // Smooths the surrounding texture
            val avg = if (neighbors.isNotEmpty()) neighbors.sum() / neighbors.size else data[i]
            
            // 70% median filter to reject spikes + 30% average filter for visual interpolation smoothness
            val denoised = median * 0.7f + avg * 0.3f
            result.add(denoised)
        }
        return result
    }

    private fun applyDigitalFilter(data: List<Float>, width: Int, length: Int, strength: Float): List<Float> {
        if (data.isEmpty() || width <= 0 || length <= 0) return data
        if (kotlin.math.abs(strength) < 0.01f) return data

        val size = data.size
        val result = ArrayList<Float>(size)
        
        for (i in 0 until size) {
            val x = i % width
            val y = i / width
            
            // 3x3 Box blur for smoothing / low-pass component
            var sum = 0f
            var count = 0
            for (dy in -1..1) {
                for (dx in -1..1) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx in 0 until width && ny in 0 until length) {
                        val nIdx = ny * width + nx
                        if (nIdx in data.indices) {
                            sum += data[nIdx]
                            count++
                        }
                    }
                }
            }
            val smoothed = if (count > 0) sum / count else data[i]
            val raw = data[i]
            
            val filtered = if (strength > 0f) {
                // Low-pass Filter: Linear interpolation between raw and smoothed
                raw * (1f - strength) + smoothed * strength
            } else {
                // High-pass Filter: Amplify local difference from local average (high-boost filter)
                val boost = -strength
                val highPass = raw - smoothed
                raw + boost * highPass * 2.5f
            }
            result.add(filtered)
        }
        return result
    }

    fun calculateAndApplyGroundPlaneFlattening(
        data: List<Float>,
        width: Int,
        length: Int,
        deadbandThreshold: Float = 12.0f
    ): Pair<List<Float>, GroundPlaneStats> {
        if (data.isEmpty() || width <= 0 || length <= 0) {
            return Pair(data, GroundPlaneStats())
        }

        val n = data.size

        // 1. Calculate robust trimmed mean baseline (15th-85th percentile neutral soil response)
        val sortedData = data.sorted()
        val startIndex = (n * 0.15f).toInt().coerceIn(0, n - 1)
        val endIndex = (n * 0.85f).toInt().coerceIn(startIndex + 1, n)
        val trimmedSlice = sortedData.subList(startIndex, endIndex)
        val meanBaseline = if (trimmedSlice.isNotEmpty()) trimmedSlice.average().toFloat() else sortedData[n / 2]

        // 2. Fit 2D Least-Squares Plane: Z(x, y) = A*x + B*y + C across neutral ground points
        var sumX = 0.0; var sumY = 0.0; var sumZ = 0.0
        var sumXX = 0.0; var sumYY = 0.0; var sumXY = 0.0
        var sumXZ = 0.0; var sumYZ = 0.0
        var validCount = 0

        val centerX = (width - 1) / 2.0
        val centerY = (length - 1) / 2.0

        for (i in 0 until n) {
            val valZ = data[i]
            if (abs(valZ - meanBaseline) < 160.0f) {
                val x = (i % width) - centerX
                val y = (i / width) - centerY
                sumX += x
                sumY += y
                sumZ += valZ
                sumXX += x * x
                sumYY += y * y
                sumXY += x * y
                sumXZ += x * valZ
                sumYZ += y * valZ
                validCount++
            }
        }

        var a = 0.0
        var b = 0.0
        var c = meanBaseline.toDouble()

        if (validCount > 3) {
            val det = sumXX * (sumYY * validCount - sumY * sumY) -
                    sumXY * (sumXY * validCount - sumX * sumY) +
                    sumX * (sumXY * sumY - sumYY * sumX)

            if (abs(det) > 1e-5) {
                a = ((sumXZ * (sumYY * validCount - sumY * sumY) -
                        sumXY * (sumYZ * validCount - sumY * sumZ) +
                        sumX * (sumYZ * sumY - sumYY * sumZ))) / det

                b = ((sumXX * (sumYZ * validCount - sumY * sumZ) -
                        sumXZ * (sumXY * validCount - sumX * sumY) +
                        sumX * (sumXY * sumZ - sumYZ * sumX))) / det

                c = ((sumXX * (sumYY * sumZ - sumYZ * sumY) -
                        sumXY * (sumXY * sumZ - sumXZ * sumY) +
                        sumXZ * (sumXY * sumY - sumYY * sumX))) / det
            }
        }

        val stats = GroundPlaneStats(
            baselineOffset = c.toFloat(),
            slopeX = a.toFloat(),
            slopeY = b.toFloat(),
            deadbandThreshold = deadbandThreshold
        )

        // 3. Detrend ground slope & flatten baseline noise into a clean 0 level base plane
        val flattened = ArrayList<Float>(n)
        for (i in 0 until n) {
            val gx = (i % width) - centerX
            val gy = (i / width) - centerY
            val groundEst = a * gx + b * gy + c
            val rawZ = data[i]
            val detrended = (rawZ - groundEst).toFloat()

            val absVal = abs(detrended)
            val finalZ = if (absVal <= deadbandThreshold) {
                0.0f
            } else {
                val sign = if (detrended > 0f) 1.0f else -1.0f
                sign * (absVal - deadbandThreshold)
            }
            flattened.add(finalZ)
        }

        return Pair(flattened, stats)
    }

    // Reactively filtered scan data flow
    val filteredScan: StateFlow<ScanRecord?> = combine(
        viewedScan,
        filterStrength,
        soilNoiseFilterEnabled,
        flattenBaseEnabled
    ) { scan, strength, noiseFilter, flattenBase ->
        if (scan == null) {
            null
        } else if (kotlin.math.abs(strength) < 0.01f && !noiseFilter && !flattenBase) {
            scan
        } else {
            val rawData = scan.getGridData()
            var processedData = rawData
            
            // First apply the geophysical ground mineral rejection filter
            if (noiseFilter) {
                processedData = applySoilNoiseRemoval(processedData, scan.width, scan.length)
            }
            
            // Apply automatic ground plane calculation and base flattening
            if (flattenBase) {
                val (flattenedData, stats) = calculateAndApplyGroundPlaneFlattening(processedData, scan.width, scan.length)
                processedData = flattenedData
                _groundPlaneStats.value = stats
            }
            
            // Then apply any digital filters if specified
            if (kotlin.math.abs(strength) >= 0.01f) {
                processedData = applyDigitalFilter(processedData, scan.width, scan.length, strength)
            }
            
            val filteredJson = ScanRecord.createGridDataJson(processedData)
            scan.copy(gridDataJson = filteredJson)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // --- SCAN PLAYBACK STATES ---
    private val _isPlaybackActive = MutableStateFlow(false)
    val isPlaybackActive = _isPlaybackActive.asStateFlow()

    private val _isPlaybackPlaying = MutableStateFlow(false)
    val isPlaybackPlaying = _isPlaybackPlaying.asStateFlow()

    private val _playbackPointIndex = MutableStateFlow(0) // 1..N
    val playbackPointIndex = _playbackPointIndex.asStateFlow()

    private val _playbackSpeedMultiplier = MutableStateFlow(1.0f) // 0.5x, 1x, 2x, 4x, 8x
    val playbackSpeedMultiplier = _playbackSpeedMultiplier.asStateFlow()

    private var playbackJob: kotlinx.coroutines.Job? = null

    // Combine filteredScan with playback mask for active 3D re-rendering point-by-point
    val displayScan: StateFlow<ScanRecord?> = combine(
        filteredScan,
        isPlaybackActive,
        playbackPointIndex
    ) { scan, isPlayback, ptIndex ->
        if (scan == null) null
        else if (!isPlayback) scan
        else {
            val fullData = scan.getGridData()
            if (fullData.isEmpty()) scan
            else {
                val safeCount = ptIndex.coerceIn(0, fullData.size)
                val maskedData = fullData.mapIndexed { idx, valAtIdx ->
                    if (idx < safeCount) valAtIdx else 0.0f
                }
                scan.copy(gridDataJson = ScanRecord.createGridDataJson(maskedData))
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun togglePlaybackMode() {
        val newActive = !_isPlaybackActive.value
        _isPlaybackActive.value = newActive
        if (newActive) {
            val total = _viewedScan.value?.getGridData()?.size ?: 0
            _playbackPointIndex.value = if (total > 0) 1 else 0
            _selectedNodeIndex.value = 0
            startPlayback()
        } else {
            pausePlayback()
        }
    }

    fun startPlayback() {
        val totalPoints = _viewedScan.value?.getGridData()?.size ?: 0
        if (totalPoints <= 0) return

        if (_playbackPointIndex.value >= totalPoints) {
            _playbackPointIndex.value = 1
        }

        _isPlaybackActive.value = true
        _isPlaybackPlaying.value = true
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            while (_isPlaybackPlaying.value && _playbackPointIndex.value < totalPoints) {
                val delayMs = (200f / _playbackSpeedMultiplier.value).toLong().coerceAtLeast(15)
                delay(delayMs)
                _playbackPointIndex.value += 1
                _selectedNodeIndex.value = _playbackPointIndex.value - 1
            }
            if (_playbackPointIndex.value >= totalPoints) {
                _isPlaybackPlaying.value = false
            }
        }
    }

    fun pausePlayback() {
        _isPlaybackPlaying.value = false
        playbackJob?.cancel()
        playbackJob = null
    }

    fun stepPlaybackForward() {
        pausePlayback()
        val totalPoints = _viewedScan.value?.getGridData()?.size ?: 0
        if (_playbackPointIndex.value < totalPoints) {
            _playbackPointIndex.value += 1
            _selectedNodeIndex.value = _playbackPointIndex.value - 1
        }
    }

    fun stepPlaybackBackward() {
        pausePlayback()
        if (_playbackPointIndex.value > 1) {
            _playbackPointIndex.value -= 1
            _selectedNodeIndex.value = _playbackPointIndex.value - 1
        }
    }

    fun seekPlayback(index: Int) {
        val totalPoints = _viewedScan.value?.getGridData()?.size ?: 0
        _playbackPointIndex.value = index.coerceIn(1, totalPoints)
        _selectedNodeIndex.value = _playbackPointIndex.value - 1
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeedMultiplier.value = speed
        if (_isPlaybackPlaying.value) {
            startPlayback()
        }
    }

    fun exitPlayback() {
        pausePlayback()
        _isPlaybackActive.value = false
    }

    // --- AI ANALYSIS & COPILOT STATES ---
    private val _aiAnalysisResult = MutableStateFlow<String?>(null)
    val aiAnalysisResult = _aiAnalysisResult.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading = _isAiLoading.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages = _chatMessages.asStateFlow()

    fun clearAiState() {
        _aiAnalysisResult.value = null
        _chatMessages.value = emptyList()
    }

    fun analyzeCurrentScanWithAi() {
        viewModelScope.launch {
            _isAiLoading.value = true
            _aiAnalysisResult.value = null
            
            val currentScan = _viewedScan.value
            val soil = currentScan?.soilType ?: _soilType.value
            val pattern = currentScan?.scanPattern ?: _scanPattern.value
            val width = currentScan?.width ?: _gridWidth.value
            val length = currentScan?.length ?: _gridLength.value
            val data = currentScan?.getGridData() ?: _activeScanData.value.toList()

            val response = AiTacticalClient.getTacticalAnalysis(
                soilType = soil,
                scanPattern = pattern,
                gridWidth = width,
                gridLength = length,
                gridData = data
            )

            _aiAnalysisResult.value = response
            _chatMessages.value = listOf(
                ChatMessage(
                    text = "🚨 ارزیابی ژئوفیزیک با موفقیت به پایان رسید. بر اساس تحلیل هوش مصنوعی تاکتیکال، جزئیات آنومالی در پایین ثبت شده است. اکنون می‌توانید در همین بخش به شکل زنده با مشاور هوش مصنوعی گفتگو کنید:",
                    isUser = false
                ),
                ChatMessage(text = response, isUser = false)
            )
            _isAiLoading.value = false
        }
    }

    fun sendMessageToAi(messageText: String) {
        if (messageText.isBlank()) return
        val currentList = _chatMessages.value.toMutableList()
        currentList.add(ChatMessage(text = messageText, isUser = true))
        _chatMessages.value = currentList

        viewModelScope.launch {
            _isAiLoading.value = true
            val currentScan = _viewedScan.value
            val soil = currentScan?.soilType ?: _soilType.value
            val pattern = currentScan?.scanPattern ?: _scanPattern.value
            val width = currentScan?.width ?: _gridWidth.value
            val length = currentScan?.length ?: _gridLength.value
            val data = currentScan?.getGridData() ?: _activeScanData.value.toList()

            val response = AiTacticalClient.getTacticalAnalysis(
                soilType = soil,
                scanPattern = pattern,
                gridWidth = width,
                gridLength = length,
                gridData = data,
                userMessage = messageText
            )

            val updatedList = _chatMessages.value.toMutableList()
            updatedList.add(ChatMessage(text = response, isUser = false))
            _chatMessages.value = updatedList
            _isAiLoading.value = false
        }
    }

    // Real-time Scan configuration
    private val _gridWidth = MutableStateFlow(8) // Number of columns (impulses)
    val gridWidth = _gridWidth.asStateFlow()

    private val _gridLength = MutableStateFlow(10) // Number of rows (lines)
    val gridLength = _gridLength.asStateFlow()

    private val _soilType = MutableStateFlow("خاک کشاورزی (Soil)")
    val soilType = _soilType.asStateFlow()

    private val _scanPattern = MutableStateFlow("موازی (Parallel)") // "موازی (Parallel)" or "زیگزاگ (Zig-Zag)"
    val scanPattern = _scanPattern.asStateFlow()

    // Scanning status
    private val _isScanActive = MutableStateFlow(false)
    val isScanActive = _isScanActive.asStateFlow()

    private val _currentCol = MutableStateFlow(0) // current X cursor
    val currentCol = _currentCol.asStateFlow()

    private val _currentRow = MutableStateFlow(0) // current Y cursor
    val currentRow = _currentRow.asStateFlow()

    // Data gathered during active scan
    private val _activeScanData = MutableStateFlow<MutableList<Float>>(mutableListOf())
    val activeScanData = _activeScanData.asStateFlow()

    // 3D Visualizer view settings
    private val _yaw = MutableStateFlow(45f)
    val yaw = _yaw.asStateFlow()

    private val _pitch = MutableStateFlow(35f)
    val pitch = _pitch.asStateFlow()

    private val _zoom = MutableStateFlow(1.0f)
    val zoom = _zoom.asStateFlow()

    private val _panX = MutableStateFlow(0f)
    val panX = _panX.asStateFlow()

    private val _panY = MutableStateFlow(0f)
    val panY = _panY.asStateFlow()

    private val _zScale = MutableStateFlow(1.0f) // height amplification
    val zScale = _zScale.asStateFlow()

    private val _isAutoScaleEnabled = MutableStateFlow(true) // true by default
    val isAutoScaleEnabled = _isAutoScaleEnabled.asStateFlow()

    private val _colorThreshold = MutableStateFlow(0.0f) // 0 to 1
    val colorThreshold = _colorThreshold.asStateFlow()

    private val _renderStyle = MutableStateFlow("Solid") // "Solid", "Wireframe", "Points", "Heatmap"
    val renderStyle = _renderStyle.asStateFlow()

    private val _isRgbAnalysis = MutableStateFlow(false)
    val isRgbAnalysis = _isRgbAnalysis.asStateFlow()

    private val _isOutdoorSunlightMode = MutableStateFlow(prefs.getBoolean("outdoor_sunlight_mode", true))
    val isOutdoorSunlightMode = _isOutdoorSunlightMode.asStateFlow()

    private val _showHelpTutorial = MutableStateFlow(false)
    val showHelpTutorial = _showHelpTutorial.asStateFlow()

    fun openHelpTutorial() {
        _showHelpTutorial.value = true
    }

    fun closeHelpTutorial() {
        _showHelpTutorial.value = false
    }

    fun toggleOutdoorSunlightMode() {
        val next = !_isOutdoorSunlightMode.value
        setOutdoorSunlightMode(next)
    }

    fun setOutdoorSunlightMode(enabled: Boolean) {
        _isOutdoorSunlightMode.value = enabled
        prefs.edit().putBoolean("outdoor_sunlight_mode", enabled).apply()
        if (enabled) {
            _colorPalette.value = "OutdoorSunlight"
        } else {
            _colorPalette.value = "Thermal"
        }
    }

    private val _colorPalette = MutableStateFlow(if (prefs.getBoolean("outdoor_sunlight_mode", true)) "OutdoorSunlight" else "Thermal")
    val colorPalette = _colorPalette.asStateFlow()

    private val _selectedNodeIndex = MutableStateFlow<Int?>(null)
    val selectedNodeIndex = _selectedNodeIndex.asStateFlow()

    private val _resetTrigger = MutableStateFlow(0)
    val resetTrigger = _resetTrigger.asStateFlow()

    private val _cameraPresetTrigger = MutableStateFlow<Pair<String, Int>?>(null)
    val cameraPresetTrigger = _cameraPresetTrigger.asStateFlow()

    private var presetCounter = 0

    init {
        // Automatically record incoming hardware sensor updates if auto-streaming is enabled
        viewModelScope.launch {
            sensorManager.adcValue.collect {
                if (_isScanActive.value && _isAutoScanStreaming.value) {
                    recordCurrentStep()
                }
            }
        }

        viewModelScope.launch {
            // Check if database is empty, if so, seed default template scans
            try {
                val currentList = repository.allScans.first()
                if (currentList.isEmpty()) {
                    val demo1 = createPredefinedScanRecord("buried_gold_chest")
                    val demo2 = createPredefinedScanRecord("deep_cave")
                    val demo3 = createPredefinedScanRecord("ancient_grave_and_tunnel")
                    val demo4 = createPredefinedScanRecord("pipeline")
                    
                    repository.insertScan(demo1)
                    repository.insertScan(demo2)
                    repository.insertScan(demo3)
                    repository.insertScan(demo4)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Room Database Auto-Restoration: Restore previous active scan session or 3D visualization work
            try {
                val savedState = repository.getActiveScanState()
                if (savedState != null) {
                    var restoredSomething = false
                    if (savedState.isScanActive) {
                        _gridWidth.value = savedState.gridWidth
                        _gridLength.value = savedState.gridLength
                        _soilType.value = savedState.soilType
                        _scanPattern.value = savedState.scanPattern
                        _currentCol.value = savedState.currentCol
                        _currentRow.value = savedState.currentRow
                        if (savedState.activeDataJson.isNotEmpty()) {
                            val parsedList = savedState.activeDataJson.split(" ")
                                .filter { it.isNotEmpty() }
                                .mapNotNull { it.toFloatOrNull() }
                                .toMutableList()
                            _activeScanData.value = parsedList
                        }
                        _isScanActive.value = true
                        restoredSomething = true
                    }
                    if (savedState.viewedScanName != null && savedState.viewedScanGridDataJson.isNotEmpty()) {
                        val restoredScan = ScanRecord(
                            id = savedState.viewedScanId ?: -1,
                            name = savedState.viewedScanName,
                            width = savedState.viewedScanWidth,
                            length = savedState.viewedScanLength,
                            soilType = savedState.viewedScanSoilType,
                            scanPattern = savedState.viewedScanPattern,
                            gridDataJson = savedState.viewedScanGridDataJson,
                            notes = savedState.viewedScanNotes,
                            timestamp = savedState.timestamp
                        )
                        _viewedScan.value = restoredScan
                        restoredSomething = true
                    }
                    if (restoredSomething) {
                        _sessionRestoredFromRoom.value = true
                    } else {
                        loadPredefinedScan("buried_gold_chest")
                    }
                } else {
                    loadPredefinedScan("buried_gold_chest")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                loadPredefinedScan("buried_gold_chest")
            }
        }

        viewModelScope.launch {
            combine(viewedScan, isAutoScaleEnabled) { scan, autoScale ->
                Pair(scan, autoScale)
            }.collect { (scan, autoScale) ->
                if (autoScale && scan != null) {
                    val data = scan.getGridData()
                    if (data.isNotEmpty()) {
                        val maxAbs = data.maxOfOrNull { kotlin.math.abs(it) } ?: 1.0f
                        if (maxAbs > 0f) {
                            val optimalScale = 300f / maxAbs
                            _zScale.value = optimalScale.coerceIn(0.1f, 3.0f)
                        }
                    }
                }
            }
        }
    }

    fun updateGridWidth(width: Int) {
        _gridWidth.value = width.coerceIn(3, 20)
        persistActiveStateToDb()
    }

    fun updateGridLength(length: Int) {
        _gridLength.value = length.coerceIn(3, 30)
        persistActiveStateToDb()
    }

    fun updateSoilType(type: String) {
        _soilType.value = type
        persistActiveStateToDb()
    }

    fun updateScanPattern(pattern: String) {
        _scanPattern.value = pattern
        persistActiveStateToDb()
    }

    // Start a new scan session
    fun startNewScan() {
        val totalPoints = _gridWidth.value * _gridLength.value
        val list = ArrayList<Float>(totalPoints)
        for (i in 0 until totalPoints) {
            list.add(0.0f) // initialize with 0 (neutral)
        }
        _activeScanData.value = list
        _currentCol.value = 0
        _currentRow.value = 0
        _isScanActive.value = true
        _selectedNodeIndex.value = null
        
        // Disconnect simulator and let user connect real hardware or start manual entries
        if (sensorManager.connectionState.value == ConnectionMode.DISCONNECTED) {
            sensorManager.startSimulator() // Auto-start simulator for easy trial
        }

        persistActiveStateToDb()
    }

    // Stop and discard
    fun cancelScan() {
        _isScanActive.value = false
        _activeScanData.value = mutableListOf()
        persistActiveStateToDb()
    }

    // Live Scan Telemetry Logger State
    private val _isLiveLogging = MutableStateFlow(false)
    val isLiveLogging = _isLiveLogging.asStateFlow()
    private var activeLiveLogger: ScanLoggingService.LiveTelemetryLogger? = null

    fun startLiveLogging(context: Context, sessionTitle: String, format: ScanLoggingService.ExportFormat) {
        activeLiveLogger = ScanLoggingService.LiveTelemetryLogger(context, sessionTitle, format)
        _isLiveLogging.value = true
    }

    fun stopLiveLogging(): ScanLoggingService.ExportResult? {
        val result = activeLiveLogger?.finalizeAndExport()
        activeLiveLogger = null
        _isLiveLogging.value = false
        return result
    }

    fun exportScan(context: Context, scan: ScanRecord, format: ScanLoggingService.ExportFormat): ScanLoggingService.ExportResult {
        val content = if (format == ScanLoggingService.ExportFormat.JSON) {
            ScanLoggingService.exportScanRecordToJson(scan)
        } else {
            ScanLoggingService.exportScanRecordToCsv(scan)
        }
        return ScanLoggingService.saveAndShare(context, scan.name, content, format)
    }

    fun exportViewedScan(context: Context, format: ScanLoggingService.ExportFormat): ScanLoggingService.ExportResult? {
        val scan = _viewedScan.value ?: return null
        return exportScan(context, scan, format)
    }

    // Record value for the current cell
    fun recordCurrentStep() {
        if (!_isScanActive.value) return

        val w = _gridWidth.value
        val l = _gridLength.value
        val x = _currentCol.value
        val y = _currentRow.value
        
        val index = y * w + x
        if (index >= 0 && index < _activeScanData.value.size) {
            // Get current value from hardware sensor (Phase and ADC combined or Phase or ADXL345 tilt/accel)
            val rawStrength = sensorManager.signalStrength.value
            val pShift = sensorManager.phaseShift.value
            
            val valueToRecord = when (sensorManager.sensorType.value) {
                SensorType.ADXL345 -> {
                    val gForce = sensorManager.adxlGForce.value
                    val pitch = sensorManager.adxlPitch.value
                    val roll = sensorManager.adxlRoll.value
                    val tiltAngle = kotlin.math.sqrt(pitch * pitch + roll * roll)
                    val gDev = kotlin.math.abs(gForce - 1.0f)

                    when {
                        gDev > 0.15f -> (gDev * 1500f * (if (pitch >= 0) 1f else -1f)).coerceIn(-980f, 980f)
                        tiltAngle > 3f -> (tiltAngle * 35f * (if (roll >= 0) 1f else -1f)).coerceIn(-900f, 900f)
                        rawStrength > 10 -> (rawStrength.toFloat() * 8f) - 100f
                        else -> (rawStrength.toFloat() - 20f) * 4f
                    }
                }
                else -> {
                    when {
                        pShift > 30 -> rawStrength.toFloat() * 8.0f // Golden peak
                        pShift in 15..30 -> rawStrength.toFloat() * 4.0f // Silver peak
                        pShift < -20 -> -rawStrength.toFloat() * 3.0f // Iron dip
                        else -> (rawStrength.toFloat() - 30f) * 2f // Soil background
                    }
                }
            }
            
            val currentList = _activeScanData.value.toMutableList()
            currentList[index] = valueToRecord
            _activeScanData.value = currentList

            // Log point in active live telemetry logger if running
            if (_isLiveLogging.value) {
                val estimatedDepth = (abs(valueToRecord) / 180f).coerceIn(0.2f, 8.5f)
                activeLiveLogger?.logPoint(x = x, y = y, signal = valueToRecord, depthMeters = estimatedDepth, phaseShift = pShift)
            }
            
            // Audio/Haptic Feedback
            FeedbackManager.playClick(getApplication())
            FeedbackManager.vibrate(getApplication(), 80)
            if (rawStrength > 45) {
                FeedbackManager.playBuzzer(getApplication(), sensorManager.adcValue.value)
            }
            
            // Advance cursor based on scan pattern
            advanceCursor()
            persistActiveStateToDb()
        }
    }

    private fun advanceCursor() {
        val w = _gridWidth.value
        val l = _gridLength.value
        val x = _currentCol.value
        val y = _currentRow.value
        
        val isZigZag = _scanPattern.value.contains("زیگزاگ") || _scanPattern.value.contains("Zig-Zag")

        if (isZigZag) {
            val goingRight = y % 2 == 0
            if (goingRight) {
                if (x < w - 1) {
                    _currentCol.value = x + 1
                } else {
                    if (y < l - 1) {
                        _currentRow.value = y + 1
                        _currentCol.value = w - 1
                    } else {
                        finishScan()
                    }
                }
            } else {
                if (x > 0) {
                    _currentCol.value = x - 1
                } else {
                    if (y < l - 1) {
                        _currentRow.value = y + 1
                        _currentCol.value = 0
                    } else {
                        finishScan()
                    }
                }
            }
        } else {
            if (x < w - 1) {
                _currentCol.value = x + 1
            } else {
                if (y < l - 1) {
                    _currentRow.value = y + 1
                    _currentCol.value = 0
                } else {
                    finishScan()
                }
            }
        }
    }

    private fun finishScan() {
        _isScanActive.value = false
        val record = ScanRecord(
            id = -1,
            name = "اسکن جدید (${System.currentTimeMillis() % 10000})",
            width = _gridWidth.value,
            length = _gridLength.value,
            soilType = _soilType.value,
            scanPattern = _scanPattern.value,
            gridDataJson = ScanRecord.createGridDataJson(_activeScanData.value),
            notes = "اسکن زنده ضبط شده توسط دستگاه"
        )
        _viewedScan.value = record
        _selectedNodeIndex.value = null

        viewModelScope.launch {
            try {
                val sessionEntity = ScanSessionEntity(
                    sessionName = record.name,
                    timestamp = System.currentTimeMillis(),
                    gridWidth = record.width,
                    gridLength = record.length,
                    soilType = record.soilType,
                    scanPattern = record.scanPattern,
                    sensorType = sensorManager.sensorType.value.name,
                    operatorNotes = record.notes
                )
                val dataList = _activeScanData.value
                val pointsList = ArrayList<ScanPointEntity>()
                val w = record.width
                val l = record.length
                for (y in 0 until l) {
                    for (x in 0 until w) {
                        val idx = y * w + x
                        val sig = dataList.getOrNull(idx) ?: 0f
                        val estDepth = (abs(sig) / 180f).coerceIn(0.2f, 8.5f)
                        val classif = when {
                            sig > 150f -> "طلا / فلز باارزش 🪙"
                            sig > 50f -> "فلز / مگنتیک ⚡"
                            sig < -50f -> "حفره / اتاقک 🕳️"
                            else -> "خاک عادی 🌱"
                        }
                        pointsList.add(
                            ScanPointEntity(
                                sessionId = 0,
                                xIndex = x,
                                yIndex = y,
                                xCoordMeters = x * 0.5f,
                                yCoordMeters = y * 0.5f,
                                signalStrength = sig,
                                depthMeters = estDepth,
                                phaseShift = 0,
                                targetClassification = classif
                            )
                        )
                    }
                }
                repository.saveFull3DScanSession(sessionEntity, pointsList)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        persistActiveStateToDb()
    }

    // Save scan to database
    fun saveScan(name: String, notes: String) {
        val recordToSave = _viewedScan.value ?: return
        viewModelScope.launch {
            val record = ScanRecord(
                name = name.ifEmpty { "اسکن بدون نام" },
                width = recordToSave.width,
                length = recordToSave.length,
                soilType = recordToSave.soilType,
                scanPattern = recordToSave.scanPattern,
                gridDataJson = recordToSave.gridDataJson,
                notes = notes
            )
            val newId = repository.insertScan(record)
            _viewedScan.value = record.copy(id = newId.toInt())
            persistActiveStateToDb()
        }
    }

    // Load scan from history
    fun loadScan(scan: ScanRecord) {
        pausePlayback()
        _isPlaybackActive.value = false
        _viewedScan.value = scan
        _selectedNodeIndex.value = null
        persistActiveStateToDb()
    }

    // Delete scan
    fun deleteScan(id: Int) {
        viewModelScope.launch {
            repository.deleteScanById(id)
            if (_viewedScan.value?.id == id) {
                loadPredefinedScan("buried_gold_chest")
            }
            persistActiveStateToDb()
        }
    }

    // 3D View Manipulation
    fun rotate(deltaYaw: Float, deltaPitch: Float) {
        _yaw.value = (_yaw.value + deltaYaw) % 360f
        _pitch.value = (_pitch.value + deltaPitch).coerceIn(-89f, 89f)
    }

    fun changeZoom(scale: Float) {
        _zoom.value = (_zoom.value * scale).coerceIn(0.2f, 4.0f)
    }

    fun pan(dx: Float, dy: Float) {
        _panX.value += dx
        _panY.value += dy
    }

    fun resetView() {
        _yaw.value = 45f
        _pitch.value = 35f
        _zoom.value = 1.0f
        _panX.value = 0f
        _panY.value = 0f
        _zScale.value = 1.0f
        _colorThreshold.value = 0.0f
        _selectedNodeIndex.value = null
        _resetTrigger.value += 1
        _cameraPresetTrigger.value = null
    }

    fun setCameraPreset(preset: String) {
        presetCounter++
        _cameraPresetTrigger.value = Pair(preset, presetCounter)
        
        when (preset) {
            "Top-Down" -> {
                _yaw.value = 0f
                _pitch.value = 89f
                _zoom.value = 1.0f
                _panX.value = 0f
                _panY.value = 0f
            }
            "Isometric" -> {
                _yaw.value = 45f
                _pitch.value = 35f
                _zoom.value = 1.0f
                _panX.value = 0f
                _panY.value = 0f
            }
            "Front Cross-Section" -> {
                _yaw.value = 0f
                _pitch.value = 0f
                _zoom.value = 1.0f
                _panX.value = 0f
                _panY.value = 0f
            }
            "Side Cross-Section" -> {
                _yaw.value = 90f
                _pitch.value = 0f
                _zoom.value = 1.0f
                _panX.value = 0f
                _panY.value = 0f
            }
        }
    }

    fun setZScale(scale: Float) {
        _isAutoScaleEnabled.value = false // disable auto-scale if manually adjusted
        _zScale.value = scale.coerceIn(0.1f, 3.0f)
    }

    fun setAutoScaleEnabled(enabled: Boolean) {
        _isAutoScaleEnabled.value = enabled
    }

    fun setColorThreshold(thresh: Float) {
        _colorThreshold.value = thresh.coerceIn(0.0f, 0.9f)
    }

    fun setRenderStyle(style: String) {
        _renderStyle.value = style
        if (style == "Heatmap") {
            _yaw.value = 0f
            _pitch.value = 90f
            _zoom.value = 1.0f
            _panX.value = 0f
            _panY.value = 0f
        } else if (style == "Solid" && _yaw.value == 0f && _pitch.value == 90f) {
            _yaw.value = 45f
            _pitch.value = 35f
        }
    }

    fun setIsRgbAnalysis(enabled: Boolean) {
        _isRgbAnalysis.value = enabled
    }

    fun setColorPalette(palette: String) {
        _colorPalette.value = palette
    }

    fun selectNode(index: Int?) {
        _selectedNodeIndex.value = index
    }

    // Preloaded Scans Factory
    fun loadPredefinedScan(key: String) {
        val width = 10
        val length = 12
        val data = ArrayList<Float>(width * length)
        
        for (y in 0 until length) {
            for (x in 0 until width) {
                // Background soil noise
                val noise = ((Math.sin(x.toDouble() * 1.3) + Math.cos(y.toDouble() * 0.9)) * 25).toFloat()
                data.add(noise)
            }
        }

        val name: String
        val description: String
        val soil = "خاک معدنی (Mineral)"
        
        when (key.trim().lowercase()) {
            "buried_gold_chest", " buried_gold_chest" -> {
                name = "صندوقچه طلای مدفون (Gold Chest)"
                description = "یک سیگنال هدف فلزی بسیار قوی و فشرده (قرمز روشن) در مرکز اسکن در عمق ۲.۸ متری."
                // Inject metallic gold chest at center (x: 4..5, y: 5..6)
                for (cy in 4..6) {
                    for (cx in 4..6) {
                        val dist = (cx - 5) * (cx - 5) + (cy - 5) * (cy - 5)
                        val valAdd = (850f * exp(-dist.toFloat() * 0.8f))
                        val idx = cy * width + cx
                        data[idx] = data[idx] + valAdd
                    }
                }
            }
            "deep_cave" -> {
                name = "حفره و اتاقک زیرزمینی (Deep Chamber)"
                description = "یک آنومالی حفره‌ای بزرگ (آبی پررنگ) که نشان‌دهنده یک اتاقک یا غار خالی در عمق ۵ متری است."
                // Inject large negative cavity at bottom-left (center x:3, y:7)
                for (cy in 5..9) {
                    for (cx in 1..5) {
                        val dist = (cx - 3) * (cx - 3) + (cy - 7) * (cy - 7)
                        val valAdd = (-650f * exp(-dist.toFloat() * 0.4f))
                        val idx = cy * width + cx
                        data[idx] = data[idx] + valAdd
                    }
                }
            }
            "ancient_grave_and_tunnel" -> {
                name = "تونل باستانی و قبر (Tunnel & Gold Grave)"
                description = "یک راهرو زیرزمینی (خط آبی قطری) که یک سیگنال فلزی ارزشمند (نقطه قرمز) درون آن قرار دارد."
                // Inject diagonal tunnel: x - y = constant
                for (y in 0 until length) {
                    for (x in 0 until width) {
                        // Diagonal line: x approx y - 1
                        val dist = Math.abs(x - (y - 1))
                        if (dist <= 1) {
                            val idx = y * width + x
                            data[idx] = data[idx] - 380f + (dist * 100)
                        }
                    }
                }
                // Inject golden crown inside the tunnel at (4, 5)
                data[5 * width + 4] = 800f
                data[5 * width + 5] = 450f
                data[6 * width + 4] = 480f
            }
            else -> {
                // Pipeline / iron profile
                name = "لوله آهنی یا خطوط لوله (Ferrous Pipeline)"
                description = "یک آنومالی خطی فلزی از شمال به جنوب اسکن که ناشی از لوله‌های خدمات شهری است."
                // Inject linear iron anomaly at x = 3
                for (y in 0 until length) {
                    data[y * width + 3] = -400f
                    data[y * width + 4] = 600f // Ferrous response has positive/negative polarity dipoles
                    data[y * width + 5] = -200f
                }
            }
        }

        val record = ScanRecord(
            id = -100, // Predefined flag
            name = name,
            width = width,
            length = length,
            soilType = soil,
            scanPattern = "زیگزاگ (Zig-Zag)",
            gridDataJson = ScanRecord.createGridDataJson(data),
            notes = description
        )
        _viewedScan.value = record
        _selectedNodeIndex.value = null
    }

    private fun createPredefinedScanRecord(key: String): ScanRecord {
        val width = 10
        val length = 12
        val data = ArrayList<Float>(width * length)
        
        for (y in 0 until length) {
            for (x in 0 until width) {
                // Background soil noise
                val noise = ((Math.sin(x.toDouble() * 1.3) + Math.cos(y.toDouble() * 0.9)) * 25).toFloat()
                data.add(noise)
            }
        }

        val name: String
        val description: String
        val soil = "خاک معدنی (Mineral)"
        
        when (key.trim().lowercase()) {
            "buried_gold_chest" -> {
                name = "صندوقچه طلای مدفون (Gold Chest)"
                description = "یک سیگنال هدف فلزی بسیار قوی و فشرده (قرمز روشن) در مرکز اسکن در عمق ۲.۸ متری."
                for (cy in 4..6) {
                    for (cx in 4..6) {
                        val dist = (cx - 5) * (cx - 5) + (cy - 5) * (cy - 5)
                        val valAdd = (850f * exp(-dist.toFloat() * 0.8f))
                        val idx = cy * width + cx
                        data[idx] = data[idx] + valAdd
                    }
                }
            }
            "deep_cave" -> {
                name = "حفره و اتاقک زیرزمینی (Deep Chamber)"
                description = "یک آنومالی حفره‌ای بزرگ (آبی پررنگ) که نشان‌دهنده یک اتاقک یا غار خالی در عمق ۵ متری است."
                for (cy in 5..9) {
                    for (cx in 1..5) {
                        val dist = (cx - 3) * (cx - 3) + (cy - 7) * (cy - 7)
                        val valAdd = (-650f * exp(-dist.toFloat() * 0.4f))
                        val idx = cy * width + cx
                        data[idx] = data[idx] + valAdd
                    }
                }
            }
            "ancient_grave_and_tunnel" -> {
                name = "تونل باستانی و قبر (Tunnel & Gold Grave)"
                description = "یک راهرو زیرزمینی (خط آبی قطری) که یک سیگنال فلزی ارزشمند (نقطه قرمز) درون آن قرار دارد."
                for (y in 0 until length) {
                    for (x in 0 until width) {
                        val dist = Math.abs(x - (y - 1))
                        if (dist <= 1) {
                            val idx = y * width + x
                            data[idx] = data[idx] - 380f + (dist * 100)
                        }
                    }
                }
                data[5 * width + 4] = 800f
                data[5 * width + 5] = 450f
                data[6 * width + 4] = 480f
            }
            else -> {
                name = "لوله آهنی یا خطوط لوله (Ferrous Pipeline)"
                description = "یک آنومالی خطی فلزی از شمال به جنوب اسکن که ناشی از لوله‌های خدمات شهری است."
                for (y in 0 until length) {
                    data[y * width + 3] = -400f
                    data[y * width + 4] = 600f
                    data[y * width + 5] = -200f
                }
            }
        }

        return ScanRecord(
            id = 0, // Auto-generate
            name = name,
            width = width,
            length = length,
            soilType = soil,
            scanPattern = "زیگزاگ (Zig-Zag)",
            gridDataJson = ScanRecord.createGridDataJson(data),
            notes = description
        )
    }

    // ADXL345 Accelerometer Driver Controls
    fun calibrateAdxlZeroG() {
        sensorManager.adxlDriver.calibrateZeroG()
    }

    fun resetAdxlCalibration() {
        sensorManager.adxlDriver.resetCalibration()
    }

    fun setAdxlLowPassAlpha(alpha: Float) {
        sensorManager.adxlDriver.setLowPassAlpha(alpha)
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.shutdown()
        sensorManager.disconnect()
    }

    companion object {
        fun calculateAndApplyGroundPlaneFlattening(
            data: List<Float>,
            width: Int,
            length: Int,
            deadbandThreshold: Float = 12.0f
        ): Pair<List<Float>, GroundPlaneStats> {
            if (data.isEmpty() || width <= 0 || length <= 0) {
                return Pair(data, GroundPlaneStats())
            }

            val n = data.size

            // 1. Calculate robust trimmed mean baseline (15th-85th percentile neutral soil response)
            val sortedData = data.sorted()
            val startIndex = (n * 0.15f).toInt().coerceIn(0, n - 1)
            val endIndex = (n * 0.85f).toInt().coerceIn(startIndex + 1, n)
            val trimmedSlice = sortedData.subList(startIndex, endIndex)
            val meanBaseline = if (trimmedSlice.isNotEmpty()) trimmedSlice.average().toFloat() else sortedData[n / 2]

            // 2. Fit 2D Least-Squares Plane: Z(x, y) = A*x + B*y + C across neutral ground points
            var sumX = 0.0; var sumY = 0.0; var sumZ = 0.0
            var sumXX = 0.0; var sumYY = 0.0; var sumXY = 0.0
            var sumXZ = 0.0; var sumYZ = 0.0
            var count = 0

            val centerX = (width - 1) / 2.0
            val centerY = (length - 1) / 2.0

            for (y in 0 until length) {
                for (x in 0 until width) {
                    val idx = y * width + x
                    val z = data.getOrNull(idx) ?: 0f

                    // Filter extreme anomaly peaks/dips to isolate true ground tilt plane
                    val diffFromBase = abs(z - meanBaseline)
                    if (diffFromBase < 250f) {
                        val gx = x - centerX
                        val gy = y - centerY

                        sumX += gx
                        sumY += gy
                        sumZ += z
                        sumXX += gx * gx
                        sumYY += gy * gy
                        sumXY += gx * gy
                        sumXZ += gx * z
                        sumYZ += gy * z
                        count++
                    }
                }
            }

            if (count < 4) {
                // Not enough neutral points; baseline shift subtraction only
                val flattened = data.map { z ->
                    val detrended = z - meanBaseline
                    if (abs(detrended) <= deadbandThreshold) 0f else detrended
                }
                return Pair(flattened, GroundPlaneStats(meanBaseline, 0f, 0f, meanBaseline))
            }

            // Matrix inversion for plane fitting: A*gx + B*gy + C = z
            val det = count * (sumXX * sumYY - sumXY * sumXY) -
                    sumX * (sumX * sumYY - sumY * sumXY) +
                    sumY * (sumX * sumXY - sumY * sumXX)

            var a = 0.0
            var b = 0.0
            var c = meanBaseline.toDouble()

            if (abs(det) > 1e-6) {
                a = ((sumXZ * (count * sumYY - sumY * sumY) - sumYZ * (count * sumXY - sumX * sumY) + sumZ * (sumX * sumYY - sumY * sumXY)) / det)
                b = ((count * (sumXZ * sumXY - sumYZ * sumXX) - sumX * (sumXZ * sumY - sumYZ * sumX) + sumZ * (sumX * sumYZ - sumY * sumXZ)) / det)
                c = ((sumZ * (sumXX * sumYY - sumXY * sumXY) - sumX * (sumXZ * sumYY - sumYZ * sumXY) + sumY * (sumXZ * sumXY - sumYZ * sumXX)) / det)
            }

            val pitchTilt = Math.toDegrees(kotlin.math.atan(b)).toFloat()
            val rollTilt = Math.toDegrees(kotlin.math.atan(a)).toFloat()
            val stats = GroundPlaneStats(meanBaseline, pitchTilt, rollTilt, c.toFloat())

            // 3. Subtract Ground Plane Elevation & Apply Neutral Noise Deadband
            val flattened = ArrayList<Float>(n)
            for (i in 0 until n) {
                val gx = (i % width) - centerX
                val gy = (i / width) - centerY
                val groundEst = a * gx + b * gy + c
                val rawZ = data[i]
                val detrended = (rawZ - groundEst).toFloat()

                val absVal = abs(detrended)
                val finalZ = if (absVal <= deadbandThreshold) {
                    0.0f
                } else {
                    val sign = if (detrended > 0f) 1.0f else -1.0f
                    sign * (absVal - deadbandThreshold)
                }
                flattened.add(finalZ)
            }

            return Pair(flattened, stats)
        }
    }
}

class VisualizerViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VisualizerViewModel::class.java)) {
            return VisualizerViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

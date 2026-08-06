package com.example.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import com.example.data.ScanPointEntity
import com.example.data.ScanRecord
import com.example.data.ScanSessionEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Professional Logging & Data Export Service for Ground Radar & 3D Topographical Scans.
 * Supports CSV and JSON format generation, telemetry logging, file export to MediaStore,
 * clipboard copy, and native Android share intents for external analysis software (e.g. Surfer, Voxler, Excel, MatLab).
 */
object ScanLoggingService {

    enum class ExportFormat {
        CSV, JSON
    }

    data class ExportResult(
        val success: Boolean,
        val filePath: String?,
        val fileName: String,
        val format: ExportFormat,
        val message: String
    )

    // ==========================================
    // 1. SCAN RECORD EXPORT (CSV / JSON)
    // ==========================================

    /**
     * Convert a ScanRecord into a professional multi-section CSV string with metadata headers,
     * coordinate long-table format, and 2D matrix visual block.
     */
    fun exportScanRecordToCsv(scan: ScanRecord): String {
        val dataList = scan.getGridData()
        val totalPoints = scan.width * scan.length
        val nonZeroData = dataList.filter { it != 0f }
        val maxVal = if (nonZeroData.isNotEmpty()) nonZeroData.maxOrNull() ?: 0f else 0f
        val minVal = if (nonZeroData.isNotEmpty()) nonZeroData.minOrNull() ?: 0f else 0f
        val avgVal = if (nonZeroData.isNotEmpty()) nonZeroData.average().toFloat() else 0f

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val dateString = dateFormat.format(Date(scan.timestamp))

        val sb = StringBuilder()

        // --- Metadata Header Block ---
        sb.append("# ==================================================\n")
        sb.append("# OKM / GOLD RADAR X20 - GROUND SCAN LOGGING SERVICE\n")
        sb.append("# ==================================================\n")
        sb.append("# Scan Name      : ${scan.name}\n")
        sb.append("# Scan ID        : ${scan.id}\n")
        sb.append("# Timestamp      : ${scan.timestamp}\n")
        sb.append("# Date/Time      : $dateString\n")
        sb.append("# Grid Dimensions: ${scan.width} Cols (X) x ${scan.length} Rows (Y)\n")
        sb.append("# Total Points   : $totalPoints\n")
        sb.append("# Target Soil    : ${scan.soilType}\n")
        sb.append("# Scan Pattern   : ${scan.scanPattern}\n")
        sb.append("# Max Signal     : ${String.format(Locale.US, "%.2f", maxVal)}\n")
        sb.append("# Min Signal     : ${String.format(Locale.US, "%.2f", minVal)}\n")
        sb.append("# Mean Signal    : ${String.format(Locale.US, "%.2f", avgVal)}\n")
        if (scan.notes.isNotBlank()) {
            sb.append("# Notes          : ${scan.notes.replace("\n", " ")}\n")
        }
        sb.append("# ==================================================\n\n")

        // --- Part 1: Long Table Format (GIS / Excel / Surfer Compatible) ---
        sb.append("# --- DATA TABLE (LONG FORMAT: INDEX, X, Y, SIGNAL, DEPTH_M) ---\n")
        sb.append("PointIndex,X_Column,Y_Row,Signal_ADC,Estimated_Depth_Meters\n")
        for (y in 0 until scan.length) {
            for (x in 0 until scan.width) {
                val idx = y * scan.width + x
                val signalVal = dataList.getOrNull(idx) ?: 0f
                // Estimate depth in meters based on signal magnitude and soil type factor
                val estimatedDepth = (abs(signalVal) / 180f).coerceIn(0.2f, 8.5f)
                sb.append("$idx,$x,$y,${String.format(Locale.US, "%.2f", signalVal)},${String.format(Locale.US, "%.2f", estimatedDepth)}\n")
            }
        }

        sb.append("\n")

        // --- Part 2: 2D Matrix Grid Representation ---
        sb.append("# --- 2D VISUAL MATRIX GRID (Y-ROWS x X-COLUMNS) ---\n")
        sb.append("# ")
        for (x in 0 until scan.width) {
            sb.append("Col_$x${if (x < scan.width - 1) "," else ""}")
        }
        sb.append("\n")

        for (y in 0 until scan.length) {
            val rowValues = mutableListOf<String>()
            for (x in 0 until scan.width) {
                val idx = y * scan.width + x
                val valAt = dataList.getOrNull(idx) ?: 0f
                rowValues.add(String.format(Locale.US, "%.2f", valAt))
            }
            sb.append(rowValues.joinToString(",") + "\n")
        }

        return sb.toString()
    }

    /**
     * Convert a ScanRecord into a structured JSON string formatted for external REST APIs or web visualizers.
     */
    fun exportScanRecordToJson(scan: ScanRecord): String {
        val dataList = scan.getGridData()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        val isoDate = dateFormat.format(Date(scan.timestamp))

        val nonZero = dataList.filter { it != 0f }
        val maxVal = if (nonZero.isNotEmpty()) nonZero.maxOrNull() ?: 0f else 0f
        val minVal = if (nonZero.isNotEmpty()) nonZero.minOrNull() ?: 0f else 0f
        val avgVal = if (nonZero.isNotEmpty()) nonZero.average().toFloat() else 0f

        val pointsJsonArray = StringBuilder()
        for (y in 0 until scan.length) {
            for (x in 0 until scan.width) {
                val idx = y * scan.width + x
                val valAt = dataList.getOrNull(idx) ?: 0f
                val estimatedDepth = (abs(valAt) / 180f).coerceIn(0.2f, 8.5f)
                if (idx > 0) pointsJsonArray.append(",\n    ")
                pointsJsonArray.append(
                    """{"index": $idx, "x": $x, "y": $y, "signal": ${String.format(Locale.US, "%.2f", valAt)}, "estimatedDepthMeters": ${String.format(Locale.US, "%.2f", estimatedDepth)}}"""
                )
            }
        }

        return """
        {
          "header": {
            "application": "OKM Gold Radar X20 Visualizer",
            "version": "2.4.0",
            "exportTimestamp": ${System.currentTimeMillis()},
            "exportDateIso": "$isoDate"
          },
          "session": {
            "id": ${scan.id},
            "name": "${escapeJson(scan.name)}",
            "timestamp": ${scan.timestamp},
            "width": ${scan.width},
            "length": ${scan.length},
            "totalPoints": ${scan.width * scan.length},
            "soilType": "${escapeJson(scan.soilType)}",
            "scanPattern": "${escapeJson(scan.scanPattern)}",
            "notes": "${escapeJson(scan.notes)}"
          },
          "statistics": {
            "maxSignal": ${String.format(Locale.US, "%.2f", maxVal)},
            "minSignal": ${String.format(Locale.US, "%.2f", minVal)},
            "averageSignal": ${String.format(Locale.US, "%.2f", avgVal)},
            "rawPointsCount": ${dataList.size}
          },
          "rawMatrixData": [${dataList.joinToString(", ")}],
          "points": [
            $pointsJsonArray
          ]
        }
        """.trimIndent()
    }

    // ==========================================
    // 2. SCAN SESSION & SCAN POINTS ENTITY EXPORT
    // ==========================================

    /**
     * Export a Room ScanSessionEntity with associated list of ScanPointEntity into CSV.
     */
    fun exportSessionToCsv(session: ScanSessionEntity, points: List<ScanPointEntity>): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val dateString = dateFormat.format(Date(session.timestamp))
        val sb = StringBuilder()

        sb.append("# ==================================================\n")
        sb.append("# OKM RADAR - GROUND SESSION LOGGING REPORT\n")
        sb.append("# Session ID   : ${session.sessionId}\n")
        sb.append("# Session Name : ${session.sessionName}\n")
        sb.append("# Date/Time    : $dateString\n")
        sb.append("# Dimensions   : ${session.gridWidth} W x ${session.gridLength} L\n")
        sb.append("# Soil Type    : ${session.soilType}\n")
        sb.append("# Pattern      : ${session.scanPattern}\n")
        sb.append("# Sensor Type  : ${session.sensorType}\n")
        sb.append("# Notes        : ${session.operatorNotes}\n")
        sb.append("# ==================================================\n\n")

        sb.append("PointID,SessionID,X_Index,Y_Index,X_Coord_M,Y_Coord_M,Signal_Strength,Depth_Meters,Phase_Shift,Target_Classification,Timestamp\n")
        for (pt in points) {
            sb.append("${pt.pointId},${pt.sessionId},${pt.xIndex},${pt.yIndex},${pt.xCoordMeters},${pt.yCoordMeters},${pt.signalStrength},${pt.depthMeters},${pt.phaseShift},\"${pt.targetClassification}\",${pt.timestamp}\n")
        }

        return sb.toString()
    }

    /**
     * Export a Room ScanSessionEntity with associated list of ScanPointEntity into JSON.
     */
    fun exportSessionToJson(session: ScanSessionEntity, points: List<ScanPointEntity>): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        val pointsJson = points.joinToString(",\n    ") { pt ->
            """{"pointId": ${pt.pointId}, "xIndex": ${pt.xIndex}, "yIndex": ${pt.yIndex}, "xMeters": ${pt.xCoordMeters}, "yMeters": ${pt.yCoordMeters}, "signalStrength": ${pt.signalStrength}, "depthMeters": ${pt.depthMeters}, "phaseShift": ${pt.phaseShift}, "classification": "${escapeJson(pt.targetClassification)}", "timestamp": ${pt.timestamp}}"""
        }

        return """
        {
          "sessionId": ${session.sessionId},
          "sessionName": "${escapeJson(session.sessionName)}",
          "timestamp": ${session.timestamp},
          "dateIso": "${dateFormat.format(Date(session.timestamp))}",
          "gridWidth": ${session.gridWidth},
          "gridLength": ${session.gridLength},
          "soilType": "${escapeJson(session.soilType)}",
          "scanPattern": "${escapeJson(session.scanPattern)}",
          "sensorType": "${escapeJson(session.sensorType)}",
          "maxDepthMeters": ${session.maxDepthMeters},
          "notes": "${escapeJson(session.operatorNotes)}",
          "pointCount": ${points.size},
          "points": [
            $pointsJson
          ]
        }
        """.trimIndent()
    }

    // ==========================================
    // 3. STORAGE & SHARING PIPELINE
    // ==========================================

    /**
     * Save content string to system Downloads/GoldRadarX20 folder, copy to clipboard, and open Share intent.
     */
    fun saveAndShare(
        context: Context,
        scanName: String,
        content: String,
        format: ExportFormat
    ): ExportResult {
        val extension = if (format == ExportFormat.JSON) "json" else "csv"
        val mimeType = if (format == ExportFormat.JSON) "application/json" else "text/csv"
        val cleanName = scanName.trim().replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        val timestamp = System.currentTimeMillis()
        val fileName = "${cleanName}_export_$timestamp.$extension"

        var savedPath: String? = null
        var isSuccess = false

        try {
            // 1. Try MediaStore.Downloads for Android 10+ (API 29+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/GoldRadarX20")
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { os ->
                        os.write(content.toByteArray(Charsets.UTF_8))
                        savedPath = "${Environment.DIRECTORY_DOWNLOADS}/GoldRadarX20/$fileName"
                        isSuccess = true
                    }
                }
            }

            // 2. Fallback to App External Files directory
            if (!isSuccess) {
                val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                if (downloadDir != null) {
                    val folder = File(downloadDir, "GoldRadarX20")
                    if (!folder.exists()) folder.mkdirs()
                    val targetFile = File(folder, fileName)
                    targetFile.writeText(content, Charsets.UTF_8)
                    savedPath = targetFile.absolutePath
                    isSuccess = true
                }
            }

            // 3. Copy output to Clipboard
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Scan Data $fileName", content)
            clipboard.setPrimaryClip(clip)

            // 4. Launch Share Intent
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "خروجی اسکن زمین: $scanName ($extension)")
                putExtra(Intent.EXTRA_TEXT, content)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(Intent.createChooser(shareIntent, "اشتراک‌گذاری خروجی داده اسکن"))

            val message = if (isSuccess) {
                "✅ فایل $extension با موفقیت صادر شد و در پوشه Downloads ذخیره گردید.\nمسیر: $savedPath"
            } else {
                "📋 متن خروجی $extension در حافظه کپی شد."
            }

            Toast.makeText(context, message, Toast.LENGTH_LONG).show()

            return ExportResult(
                success = isSuccess,
                filePath = savedPath,
                fileName = fileName,
                format = format,
                message = message
            )
        } catch (e: Exception) {
            val errMsg = "خطا در خروجی داده: ${e.localizedMessage}"
            Toast.makeText(context, errMsg, Toast.LENGTH_SHORT).show()
            return ExportResult(
                success = false,
                filePath = null,
                fileName = fileName,
                format = format,
                message = errMsg
            )
        }
    }

    // ==========================================
    // 4. REAL-TIME LIVE TELEMETRY LOGGER
    // ==========================================

    class LiveTelemetryLogger(
        private val context: Context,
        val sessionTitle: String,
        val format: ExportFormat
    ) {
        private val startTime = System.currentTimeMillis()
        private val logEntries = mutableListOf<String>()
        var isLoggingActive = true
            private set

        init {
            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(startTime))
            if (format == ExportFormat.CSV) {
                logEntries.add("# ==================================================")
                logEntries.add("# LIVE SCAN TELEMETRY LOG: $sessionTitle")
                logEntries.add("# Start Time: $dateStr ($startTime)")
                logEntries.add("# ==================================================")
                logEntries.add("Timestamp_MS,X_Col,Y_Row,Signal_ADC,Depth_Meters,Phase_Shift")
            }
        }

        fun logPoint(x: Int, y: Int, signal: Float, depthMeters: Float = 0f, phaseShift: Int = 0) {
            if (!isLoggingActive) return
            val now = System.currentTimeMillis()
            if (format == ExportFormat.CSV) {
                logEntries.add("$now,$x,$y,${String.format(Locale.US, "%.2f", signal)},${String.format(Locale.US, "%.2f", depthMeters)},$phaseShift")
            } else {
                logEntries.add(
                    """{"timestamp": $now, "x": $x, "y": $y, "signal": ${String.format(Locale.US, "%.2f", signal)}, "depthMeters": ${String.format(Locale.US, "%.2f", depthMeters)}, "phaseShift": $phaseShift}"""
                )
            }
        }

        fun finalizeAndExport(): ExportResult {
            isLoggingActive = false
            val fullContent = if (format == ExportFormat.CSV) {
                logEntries.joinToString("\n")
            } else {
                """
                {
                  "sessionTitle": "${escapeJson(sessionTitle)}",
                  "startTime": $startTime,
                  "endTime": ${System.currentTimeMillis()},
                  "logPointsCount": ${logEntries.size},
                  "logs": [
                    ${logEntries.joinToString(",\n    ")}
                  ]
                }
                """.trimIndent()
            }

            return saveAndShare(context, "${sessionTitle}_live_log", fullContent, format)
        }
    }

    private fun escapeJson(input: String): String {
        return input.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\u000C", "\\f")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}

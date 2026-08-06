package com.example.hardware

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * ADXL345 3-Axis Digital Accelerometer Low-Level Driver & Signal Processor.
 *
 * Implements hardware register abstraction, binary frame parsing, ASCII line decoding,
 * digital low-pass filtering, zero-G offset calibration, tilt angle calculation,
 * vibration RMS analysis, and scan stability telemetry for 3D ground scanning data acquisition.
 */
class Adxl345Driver {

    companion object {
        private const val TAG = "Adxl345Driver"

        // ADXL345 Hardware Register Map (I2C Address 0x53 / 0x1D)
        const val REG_DEVID = 0x00          // Device ID (Expected: 0xE5 / 229)
        const val REG_THRESH_TAP = 0x1D     // Tap threshold
        const val REG_OFSX = 0x20           // X-axis offset
        const val REG_OFSY = 0x21           // Y-axis offset
        const val REG_OFSZ = 0x22           // Z-axis offset
        const val REG_DUR = 0x21            // Tap duration
        const val REG_BW_RATE = 0x2C        // Data rate & power mode
        const val REG_POWER_CTL = 0x2D      // Power-saving features control
        const val REG_INT_ENABLE = 0x2E     // Interrupt enable control
        const val REG_INT_MAP = 0x2F        // Interrupt mapping control
        const val REG_INT_SOURCE = 0x30     // Source of interrupts
        const val REG_DATA_FORMAT = 0x31    // Data format control (+/-2g, 4g, 8g, 16g, Full Res)
        const val REG_DATAX0 = 0x32         // X-Axis Data 0 (LSB)
        const val REG_DATAX1 = 0x33         // X-Axis Data 1 (MSB)
        const val REG_DATAY0 = 0x34         // Y-Axis Data 0 (LSB)
        const val REG_DATAY1 = 0x35         // Y-Axis Data 1 (MSB)
        const val REG_DATAZ0 = 0x36         // Z-Axis Data 0 (LSB)
        const val REG_DATAZ1 = 0x37         // Z-Axis Data 1 (MSB)
        const val REG_FIFO_CTL = 0x38       // FIFO control
        const val REG_FIFO_STATUS = 0x39    // FIFO status

        // Expected ADXL345 Device Identification Byte
        const val ADXL345_DEVID_VALUE = 0xE5

        // Scale factors per LSB for Full-Resolution Mode (4 mg/LSB = 0.0039 g/LSB = 0.03825 m/s²/LSB)
        const val SCALE_FULL_RES_G_PER_LSB = 0.0039f
        const val GRAVITY_EARTH = 9.80665f // m/s²
    }

    enum class Range(val formatBits: Byte, val gRange: Float) {
        RANGE_2G(0x00, 2.0f),
        RANGE_4G(0x01, 4.0f),
        RANGE_8G(0x02, 8.0f),
        RANGE_16G(0x03, 16.0f)
    }

    enum class OutputDataRate(val rateBits: Byte, val hz: Float) {
        RATE_12_5_HZ(0x07, 12.5f),
        RATE_25_HZ(0x08, 25.0f),
        RATE_50_HZ(0x09, 50.0f),
        RATE_100_HZ(0x0A, 100.0f),
        RATE_200_HZ(0x0B, 200.0f),
        RATE_400_HZ(0x0C, 400.0f),
        RATE_800_HZ(0x0D, 800.0f)
    }

    // Configuration Settings
    private var currentRange = Range.RANGE_16G
    private var currentDataRate = OutputDataRate.RATE_100_HZ
    private var isFullResolution = true

    // Low-pass Filter Alpha (0.0 = max smoothing, 1.0 = raw pass-through)
    private var lpfAlpha = 0.25f

    // Zero-G Calibration Offsets (in G)
    private var offsetX = 0.0f
    private var offsetY = 0.0f
    private var offsetZ = 0.0f

    // Raw Sensor Telemetry StateFlows
    private val _rawX = MutableStateFlow(0f)
    val rawX: StateFlow<Float> = _rawX.asStateFlow()

    private val _rawY = MutableStateFlow(0f)
    val rawY: StateFlow<Float> = _rawY.asStateFlow()

    private val _rawZ = MutableStateFlow(GRAVITY_EARTH)
    val rawZ: StateFlow<Float> = _rawZ.asStateFlow()

    // Filtered Acceleration StateFlows (in m/s²)
    private val _accelX = MutableStateFlow(0f)
    val accelX: StateFlow<Float> = _accelX.asStateFlow()

    private val _accelY = MutableStateFlow(0f)
    val accelY: StateFlow<Float> = _accelY.asStateFlow()

    private val _accelZ = MutableStateFlow(GRAVITY_EARTH)
    val accelZ: StateFlow<Float> = _accelZ.asStateFlow()

    // Computed Spatial Orientation StateFlows
    private val _pitch = MutableStateFlow(0f)      // Degrees (-180 to +180)
    val pitch: StateFlow<Float> = _pitch.asStateFlow()

    private val _roll = MutableStateFlow(0f)       // Degrees (-180 to +180)
    val roll: StateFlow<Float> = _roll.asStateFlow()

    private val _tiltAngle = MutableStateFlow(0f)  // Degrees of tilt from pure vertical earth axis
    val tiltAngle: StateFlow<Float> = _tiltAngle.asStateFlow()

    private val _gForce = MutableStateFlow(1.0f)   // Total normalized Earth dynamic gravity Gs
    val gForce: StateFlow<Float> = _gForce.asStateFlow()

    // Quality Control & Stability Metrics
    private val _vibrationRms = MutableStateFlow(0f)
    val vibrationRms: StateFlow<Float> = _vibrationRms.asStateFlow()

    private val _isCalibrated = MutableStateFlow(false)
    val isCalibrated: StateFlow<Boolean> = _isCalibrated.asStateFlow()

    private val _scanStabilityScore = MutableStateFlow(100) // 0-100% stability score
    val scanStabilityScore: StateFlow<Int> = _scanStabilityScore.asStateFlow()

    private val _statusText = MutableStateFlow("سنسور ADXL345 در حالت نرمال")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    // Sliding window buffer for vibration and calibration calculation
    private val historyX = ArrayList<Float>()
    private val historyY = ArrayList<Float>()
    private val historyZ = ArrayList<Float>()
    private val windowSize = 20

    /**
     * Resets calibration offsets to default zero baseline.
     */
    fun resetCalibration() {
        offsetX = 0.0f
        offsetY = 0.0f
        offsetZ = 0.0f
        _isCalibrated.value = false
        _statusText.value = "کالیبراسیون ADXL345 بازنشانی شد"
    }

    /**
     * PerformsZero-G Calibration based on current static position holding the sensor level on ground.
     */
    fun calibrateZeroG() {
        if (historyX.size < 5) {
            _statusText.value = "اطلاعات کافی برای کالیبراسیون موجود نیست"
            return
        }

        val avgX = historyX.average().toFloat()
        val avgY = historyY.average().toFloat()
        val avgZ = historyZ.average().toFloat()

        // Expect Z = 9.80665 m/s² when resting horizontally, X=0, Y=0
        offsetX = avgX
        offsetY = avgY
        offsetZ = avgZ - GRAVITY_EARTH

        _isCalibrated.value = true
        _statusText.value = "کالیبراسیون Zero-G با موفقیت انجام شد (افست: X=%.2f, Y=%.2f, Z=%.2f)".format(offsetX, offsetY, offsetZ)
        Log.d(TAG, "Zero-G Calibration set: Offsets X=$offsetX, Y=$offsetY, Z=$offsetZ")
    }

    /**
     * Configures low-pass filter alpha factor for noise rejection.
     * @param alpha Value between 0.05 (heavy filtering) and 1.0 (no filtering).
     */
    fun setLowPassAlpha(alpha: Float) {
        lpfAlpha = alpha.coerceIn(0.01f, 1.0f)
    }

    /**
     * Decodes a 6-byte raw binary payload read directly from registers DATAX0..DATAZ1 (0x32..0x37).
     * @param buffer Byte array containing at least 6 bytes: [X_LSB, X_MSB, Y_LSB, Y_MSB, Z_LSB, Z_MSB]
     */
    fun processRawRegisterBytes(buffer: ByteArray, offset: Int = 0): Boolean {
        if (buffer.size - offset < 6) return false

        // ADXL345 stores data as 16-bit 2's complement little-endian values
        val xRaw = (buffer[offset].toInt() and 0xFF) or (buffer[offset + 1].toInt() shl 8)
        val yRaw = (buffer[offset + 2].toInt() and 0xFF) or (buffer[offset + 3].toInt() shl 8)
        val zRaw = (buffer[offset + 4].toInt() and 0xFF) or (buffer[offset + 5].toInt() shl 8)

        // Convert to signed 16-bit short
        val shortX = xRaw.toShort()
        val shortY = yRaw.toShort()
        val shortZ = zRaw.toShort()

        // Convert LSBs to acceleration in m/s²
        val scale = if (isFullResolution) {
            SCALE_FULL_RES_G_PER_LSB * GRAVITY_EARTH
        } else {
            (currentRange.gRange / 512.0f) * GRAVITY_EARTH
        }

        val ax = shortX * scale
        val ay = shortY * scale
        val az = shortZ * scale

        processAccelData(ax, ay, az)
        return true
    }

    /**
     * Decodes ASCII lines streamed over USB Serial / UART from microcontroller connected to ADXL345.
     * Supports multiple standard packet formats:
     * - `ADXL_RAW: x, y, z`
     * - `ADXL_XYZ: x, y, z` (in m/s² or g)
     * - `ACCEL: x, y, z`
     * - `ADXL: x, y, z`
     * - `PITCH: pitch, ROLL: roll, GFORCE: g`
     */
    fun parseStreamLine(line: String): Boolean {
        try {
            // Pattern 1: ADXL_XYZ: x, y, z
            val xyzRegex = Regex("(?:ADXL_XYZ|ADXL_RAW|ACCEL|ADXL):\\s*(-?[0-9.]+)\\s*,\\s*(-?[0-9.]+)\\s*,\\s*(-?[0-9.]+)")
            val match = xyzRegex.find(line)
            if (match != null) {
                var x = match.groupValues[1].toFloatOrNull() ?: 0f
                var y = match.groupValues[2].toFloatOrNull() ?: 0f
                var z = match.groupValues[3].toFloatOrNull() ?: GRAVITY_EARTH

                // If values are in Gs (small float e.g. 0.05, 1.0), convert to m/s²
                if (abs(z) < 3.0f && abs(x) < 3.0f && abs(y) < 3.0f) {
                    x *= GRAVITY_EARTH
                    y *= GRAVITY_EARTH
                    z *= GRAVITY_EARTH
                }

                processAccelData(x, y, z)
                return true
            }

            // Pattern 2: PITCH: pitch, ROLL: roll
            val prRegex = Regex("(?:PITCH|P):\\s*(-?[0-9.]+).*?(?:ROLL|R):\\s*(-?[0-9.]+)")
            val prMatch = prRegex.find(line)
            if (prMatch != null) {
                val p = prMatch.groupValues[1].toFloatOrNull() ?: 0f
                val r = prMatch.groupValues[2].toFloatOrNull() ?: 0f
                
                _pitch.value = p
                _roll.value = r
                _tiltAngle.value = sqrt(p * p + r * r)
                _gForce.value = 1.0f
                _statusText.value = "دریافت زاویه شیب ADXL345: P=%.1f°, R=%.1f°".format(p, r)
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing ADXL345 line: $line", e)
        }
        return false
    }

    /**
     * Core signal processing engine for raw (X, Y, Z) acceleration inputs in m/s².
     * Applies Zero-G calibration, EMA exponential moving average low-pass filter,
     * calculates orientation angles (Pitch/Roll), dynamic Earth G-Force, and vibration RMS.
     */
    fun processAccelData(rawXIn: Float, rawYIn: Float, rawZIn: Float) {
        _rawX.value = rawXIn
        _rawY.value = rawYIn
        _rawZ.value = rawZIn

        // 1. Apply Zero-G Calibration Offsets
        val calX = rawXIn - offsetX
        val calY = rawYIn - offsetY
        val calZ = rawZIn - offsetZ

        // 2. Apply Exponential Moving Average (EMA) Low-Pass Filter
        val filtX = _accelX.value + lpfAlpha * (calX - _accelX.value)
        val filtY = _accelY.value + lpfAlpha * (calY - _accelY.value)
        val filtZ = _accelZ.value + lpfAlpha * (calZ - _accelZ.value)

        _accelX.value = filtX
        _accelY.value = filtY
        _accelZ.value = filtZ

        // 3. Compute Pitch and Roll Angles (Euler Angles from Acceleration)
        // Pitch = atan2(Y, sqrt(X^2 + Z^2)) in degrees
        // Roll = atan2(-X, Z) in degrees
        val pitchRad = atan2(filtY.toDouble(), sqrt((filtX * filtX + filtZ * filtZ).toDouble()))
        val rollRad = atan2((-filtX).toDouble(), filtZ.toDouble())

        val pitchDeg = Math.toDegrees(pitchRad).toFloat()
        val rollDeg = Math.toDegrees(rollRad).toFloat()

        _pitch.value = pitchDeg
        _roll.value = rollDeg

        val tilt = sqrt(pitchDeg * pitchDeg + rollDeg * rollDeg)
        _tiltAngle.value = tilt

        // 4. Compute Total Dynamic G-Force
        val totalMagnitude = sqrt(filtX * filtX + filtY * filtY + filtZ * filtZ)
        val gValue = totalMagnitude / GRAVITY_EARTH
        _gForce.value = gValue

        // 5. Update Sliding Window & Vibration Analysis
        synchronized(historyX) {
            historyX.add(filtX)
            historyY.add(filtY)
            historyZ.add(filtZ)

            if (historyX.size > windowSize) {
                historyX.removeAt(0)
                historyY.removeAt(0)
                historyZ.removeAt(0)
            }

            if (historyX.size >= 5) {
                val meanX = historyX.average().toFloat()
                val meanY = historyY.average().toFloat()
                val meanZ = historyZ.average().toFloat()

                var varSum = 0f
                for (i in historyX.indices) {
                    val dx = historyX[i] - meanX
                    val dy = historyY[i] - meanY
                    val dz = historyZ[i] - meanZ
                    varSum += (dx * dx + dy * dy + dz * dz)
                }
                val rms = sqrt(varSum / historyX.size)
                _vibrationRms.value = rms

                // Calculate Scan Stability Score (100% = perfectly steady, <50% = unstable motion/shake)
                val stability = (100 - (rms * 15f + abs(gValue - 1.0f) * 40f + tilt * 1.5f)).toInt().coerceIn(0, 100)
                _scanStabilityScore.value = stability

                _statusText.value = when {
                    stability > 85 -> "دستگاه کاملاً تراز و پایدار 🎯 (شیب: %.1f°)".format(tilt)
                    stability in 60..85 -> "ثبات مناسب اسکن (شیب: %.1f°)".format(tilt)
                    else -> "تکان/لرزش دست شناسایی شد ⚠️ (پایداری: $stability%)"
                }
            }
        }
    }

    /**
     * Encodes register configuration command for ADXL345 to send over I2C/SPI bridge or serial.
     */
    fun createRegisterWriteCommand(reg: Int, value: Byte): ByteArray {
        return byteArrayOf(0xAA.toByte(), 0x55.toByte(), reg.toByte(), value)
    }

    /**
     * Generates initialization sequence bytes for setting up ADXL345 into Measurement Mode,
     * Full Resolution, 100Hz Output Data Rate.
     */
    fun getInitCommandSequence(): List<ByteArray> {
        val commands = mutableListOf<ByteArray>()
        // 1. Set BW_RATE = 100Hz (0x0A)
        commands.add(createRegisterWriteCommand(REG_BW_RATE, OutputDataRate.RATE_100_HZ.rateBits))
        // 2. Set DATA_FORMAT = Full Resolution, +/-16g range (0x0B)
        commands.add(createRegisterWriteCommand(REG_DATA_FORMAT, (0x08 or Range.RANGE_16G.formatBits.toInt()).toByte()))
        // 3. Set POWER_CTL = Measure Mode (0x08)
        commands.add(createRegisterWriteCommand(REG_POWER_CTL, 0x08.toByte()))
        return commands
    }
}

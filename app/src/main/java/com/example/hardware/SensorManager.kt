package com.example.hardware

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager as AndroidSensorManager
import android.hardware.usb.UsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.InputStream
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

enum class ConnectionMode {
    DISCONNECTED,
    CONNECTING_USB,
    CONNECTING_BT,
    USB,
    BLUETOOTH,
    SIMULATOR
}

enum class SensorType {
    GOLD_RADAR_X20,     // Standard (ADC & Phase)
    FMG3,               // FMG-3 Fluxgate Magnetometer
    FLC100,             // FLC-100 Fluxgate Magnetometer
    HMC5883L,           // Digital Compass Honeywell HMC5883L
    QMC5883L,           // 3-Axis Digital Compass & Magnetometer QMC5883L
    ADXL345,            // 3-Axis Accelerometer & Tilt Sensor ADXL345
    PHONE_INTERNAL      // Physical Phone Hardware Magnetometer & Orientation Sensor
}

class SensorManager(private val context: Context) : SensorEventListener {

    private val _connectionState = MutableStateFlow(ConnectionMode.DISCONNECTED)
    val connectionState = _connectionState.asStateFlow()

    private val _tvStatus = MutableStateFlow("✅ آماده اسکن")
    val tvStatus = _tvStatus.asStateFlow()

    private val _adcValue = MutableStateFlow(0)
    val adcValue = _adcValue.asStateFlow()

    private val _phaseShift = MutableStateFlow(0)
    val phaseShift = _phaseShift.asStateFlow()

    private val _signalStrength = MutableStateFlow(0)
    val signalStrength = _signalStrength.asStateFlow()

    private val _depthMeters = MutableStateFlow(0.0)
    val depthMeters = _depthMeters.asStateFlow()

    private val _metalType = MutableStateFlow("---")
    val metalType = _metalType.asStateFlow()

    private val _maxSignal = MutableStateFlow(0)
    val maxSignal = _maxSignal.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    private val _sensorType = MutableStateFlow(SensorType.GOLD_RADAR_X20)
    val sensorType = _sensorType.asStateFlow()

    private val _baudRate = MutableStateFlow(9600)
    val baudRate = _baudRate.asStateFlow()

    private val _compassHeading = MutableStateFlow(0f) // from HMC5883L / Phone Hardware
    val compassHeading = _compassHeading.asStateFlow()

    // ADXL345 Low-Level Driver Instance
    val adxlDriver = Adxl345Driver()

    // ADXL345 3-Axis Accelerometer & Tilt Sensor State Flows
    val adxlPitch = adxlDriver.pitch
    val adxlRoll = adxlDriver.roll
    val adxlGForce = adxlDriver.gForce
    val adxlX = adxlDriver.accelX
    val adxlY = adxlDriver.accelY
    val adxlZ = adxlDriver.accelZ
    val adxlTiltAngle = adxlDriver.tiltAngle
    val adxlVibrationRms = adxlDriver.vibrationRms
    val adxlStabilityScore = adxlDriver.scanStabilityScore
    val adxlStatusText = adxlDriver.statusText
    val adxlIsCalibrated = adxlDriver.isCalibrated

    private val _batteryPercentage = MutableStateFlow<Int?>(null)
    val batteryPercentage = _batteryPercentage.asStateFlow()

    // Real Phone Hardware Sensor State Flows
    private val _phoneMagMicroTesla = MutableStateFlow(0f)
    val phoneMagMicroTesla = _phoneMagMicroTesla.asStateFlow()

    private val _phoneSensorAvailable = MutableStateFlow(false)
    val phoneSensorAvailable = _phoneSensorAvailable.asStateFlow()

    private val _phoneSensorActive = MutableStateFlow(false)
    val phoneSensorActive = _phoneSensorActive.asStateFlow()

    // Android Hardware System Sensors
    private val androidSensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? AndroidSensorManager
    private var magSensor: Sensor? = androidSensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private var accelSensor: Sensor? = androidSensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val gravityValues = FloatArray(3)
    private val geomagneticValues = FloatArray(3)
    private var hasGravity = false
    private var hasGeoMag = false
    private var baseMicroTesla = 45f // Earth's baseline magnetic field (~30..60 uT)

    init {
        // Auto-check and activate physical sensors for orientation & compass
        startPhoneSensors()
    }

    fun startPhoneSensors() {
        if (androidSensorManager != null && magSensor != null) {
            _phoneSensorAvailable.value = true
            androidSensorManager.registerListener(this, magSensor, AndroidSensorManager.SENSOR_DELAY_GAME)
            if (accelSensor != null) {
                androidSensorManager.registerListener(this, accelSensor, AndroidSensorManager.SENSOR_DELAY_GAME)
            }
            _phoneSensorActive.value = true
            Log.d(TAG, "Phone physical magnetometer & accelerometer registered successfully")
        } else {
            _phoneSensorAvailable.value = false
            Log.w(TAG, "Physical magnetic sensor not available on this device")
        }
    }

    fun stopPhoneSensors() {
        if (_phoneSensorActive.value) {
            androidSensorManager?.unregisterListener(this)
            _phoneSensorActive.value = false
            Log.d(TAG, "Phone physical sensors unregistered")
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, geomagneticValues, 0, 3)
                hasGeoMag = true

                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val totaluT = sqrt(x * x + y * y + z * z)
                _phoneMagMicroTesla.value = totaluT

                // Update real orientation if gravity is available
                if (hasGravity) {
                    updateOrientation()
                }

                // Feed real phone sensor data into simulator / internal mode
                onPhoneSensorDataUpdated(totaluT, x, y, z)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, gravityValues, 0, 3)
                hasGravity = true
                if (hasGeoMag) {
                    updateOrientation()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun updateOrientation() {
        val rotationMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)
        if (AndroidSensorManager.getRotationMatrix(rotationMatrix, null, gravityValues, geomagneticValues)) {
            AndroidSensorManager.getOrientation(rotationMatrix, orientationAngles)
            var azimuthDegrees = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
            if (azimuthDegrees < 0) azimuthDegrees += 360f
            _compassHeading.value = azimuthDegrees
        }
    }

    private fun onPhoneSensorDataUpdated(totaluT: Float, x: Float, y: Float, z: Float) {
        if (_connectionState.value == ConnectionMode.SIMULATOR || _sensorType.value == SensorType.PHONE_INTERNAL) {
            val deltaUT = abs(totaluT - baseMicroTesla)

            // Map micro-Tesla delta to ADC signal value (0-1023)
            val mappedAdc = (200 + deltaUT * 12f).toInt().coerceIn(100, 1023)

            // Calculate phase shift from vertical/horizontal magnetic vector balance
            val zRatio = z / (abs(x) + abs(y) + 0.1f)
            val mappedPhase = (zRatio * 25f).toInt().coerceIn(-45, 65)

            if (_sensorType.value == SensorType.PHONE_INTERNAL) {
                _adcValue.value = mappedAdc
                _phaseShift.value = mappedPhase
                processValues(mappedAdc, mappedPhase)
            }
        }
    }

    fun setSensorType(type: SensorType) {
        _sensorType.value = type
        if (type == SensorType.PHONE_INTERNAL) {
            startPhoneSensors()
            if (_connectionState.value == ConnectionMode.DISCONNECTED) {
                _connectionState.value = ConnectionMode.SIMULATOR
                _isConnected.value = true
                _tvStatus.value = "📱 سنسور داخلی گوشی فعال شد"
            }
        }
        if (_connectionState.value == ConnectionMode.SIMULATOR) {
            startSimulator()
        }
    }

    fun setBaudRate(rate: Int) {
        _baudRate.value = rate
        if (_connectionState.value == ConnectionMode.USB) {
            try {
                usbPort?.setParameters(rate, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                _tvStatus.value = "✅ تغییر نرخ باود به $rate"
            } catch (e: Exception) {
                Log.e(TAG, "Error setting parameters", e)
            }
        }
    }

    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var usbPort: UsbSerialPort? = null
    private var isReading = false
    private var scanJob: Job? = null
    private var baselineNoise = 50

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "SensorManager"
        private const val MAX_DEPTH_METERS = 20.0
        private val BT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private fun getCustomProber(): UsbSerialProber {
        val customTable = UsbSerialProber.getDefaultProbeTable()
        // Explicitly register CH340/CH341 to ensure 100% driver compatibility
        customTable.addProduct(0x1A86, 0x7523, com.hoho.android.usbserial.driver.Ch34xSerialDriver::class.java) // CH340
        customTable.addProduct(0x1A86, 0x5523, com.hoho.android.usbserial.driver.Ch34xSerialDriver::class.java) // CH341
        customTable.addProduct(0x1A86, 0x7522, com.hoho.android.usbserial.driver.Ch34xSerialDriver::class.java) // CH340 alternate
        return UsbSerialProber(customTable)
    }

    fun autoConnect() {
        if (_connectionState.value != ConnectionMode.DISCONNECTED) {
            disconnect()
            return
        }

        _tvStatus.value = "در حال اتصال..."
        
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val prober = getCustomProber()
        val availableDrivers = prober.findAllDrivers(usbManager)
        
        if (availableDrivers.isNotEmpty()) {
            connectToUSB()
        } else {
            connectToBluetooth()
        }
    }

    private fun connectToUSB() {
        _connectionState.value = ConnectionMode.CONNECTING_USB
        scope.launch {
            try {
                val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
                val prober = getCustomProber()
                val availableDrivers = prober.findAllDrivers(usbManager)
                
                if (availableDrivers.isNotEmpty()) {
                    val driver = availableDrivers[0]
                    val connection = usbManager.openDevice(driver.device) ?: throw Exception("خطا در دسترسی به USB")
                    usbPort = driver.ports[0]
                    usbPort?.open(connection)
                    usbPort?.setParameters(_baudRate.value, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                    
                    _connectionState.value = ConnectionMode.USB
                    _isConnected.value = true
                    _batteryPercentage.value = 98
                    _tvStatus.value = "✅ متصل به USB (CH340)"
                    startReading()
                } else {
                    throw Exception("دستگاه USB یافت نشد")
                }
            } catch (e: Exception) {
                Log.e(TAG, "USB Error", e)
                _connectionState.value = ConnectionMode.DISCONNECTED
                _isConnected.value = false
                _tvStatus.value = "❌ خطا در اتصال USB: ${e.message}"
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToBluetooth() {
        _connectionState.value = ConnectionMode.CONNECTING_BT
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        
        if (bluetoothAdapter == null) {
            _connectionState.value = ConnectionMode.DISCONNECTED
            _tvStatus.value = "❌ بلوتوث پشتیبانی نمی‌شود"
            return
        }
        
        if (!bluetoothAdapter.isEnabled) {
            _connectionState.value = ConnectionMode.DISCONNECTED
            _tvStatus.value = "⚠️ بلوتوث را روشن کنید"
            return
        }
        
        val pairedDevices = try {
            bluetoothAdapter.bondedDevices
        } catch (e: Exception) {
            null
        }
        
        if (pairedDevices.isNullOrEmpty()) {
            _connectionState.value = ConnectionMode.DISCONNECTED
            _tvStatus.value = "⚠️ دستگاهی جفت نشده"
            return
        }
        
        var targetDevice: BluetoothDevice? = null
        for (device in pairedDevices) {
            val name = device.name?.lowercase() ?: ""
            if (name.contains("hc-05") || name.contains("hc-06") || name.contains("radar") || name.contains("gold")) {
                targetDevice = device
                break
            }
        }
        
        if (targetDevice == null) {
            targetDevice = pairedDevices.first()
        }
        
        targetDevice?.let { connectToBluetoothDevice(it) } ?: run {
            _connectionState.value = ConnectionMode.DISCONNECTED
            _tvStatus.value = "⚠️ هیچ دستگاه بلوتوث مناسبی یافت نشد"
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToBluetoothDevice(device: BluetoothDevice) {
        scope.launch {
            try {
                bluetoothSocket = device.createRfcommSocketToServiceRecord(BT_UUID)
                bluetoothSocket?.connect()
                inputStream = bluetoothSocket?.inputStream
                
                _connectionState.value = ConnectionMode.BLUETOOTH
                _isConnected.value = true
                _batteryPercentage.value = 95
                _tvStatus.value = "✅ متصل به ${device.name}"
                startReading()
            } catch (e: Exception) {
                Log.e(TAG, "Bluetooth connection failed", e)
                _connectionState.value = ConnectionMode.DISCONNECTED
                _isConnected.value = false
                _tvStatus.value = "❌ خطا: دستگاه پاسخ نداد"
            }
        }
    }

    fun startSimulator() {
        disconnect()
        startPhoneSensors()
        _connectionState.value = ConnectionMode.SIMULATOR
        _isConnected.value = true
        _batteryPercentage.value = 88
        _tvStatus.value = if (_phoneSensorAvailable.value) "📱 شبیه‌ساز متصل به سنسور واقعی گوشی" else "🤖 شبیه‌ساز فعال است"
        
        // Start simulated readings
        isReading = true
        scanJob = scope.launch {
            var simAngle = 0f
            var loopCount = 0
            while (isReading) {
                loopCount++
                if (loopCount % 200 == 0) { // Approx 30 seconds (200 * 150ms)
                    val currentBat = _batteryPercentage.value ?: 88
                    if (currentBat > 5) {
                        _batteryPercentage.value = currentBat - 1
                    }
                }
                val noise = (Math.random() * 8).toFloat()
                val liveUT = _phoneMagMicroTesla.value
                val realHardwareDelta = if (_phoneSensorAvailable.value && liveUT > 0) abs(liveUT - baseMicroTesla) else 0f
                
                when (_sensorType.value) {
                    SensorType.GOLD_RADAR_X20 -> {
                        val baseValue = 180 + noise + (Math.sin(simAngle.toDouble()) * 30).toFloat() + (realHardwareDelta * 12f)
                        val tempAdcValue = baseValue.toInt().coerceIn(100, 1023)
                        val tempPhaseShift = ((Math.sin(simAngle.toDouble() * 1.5) * 25) + (if (realHardwareDelta > 8) 30 else 0)).toInt()
                        
                        _adcValue.value = tempAdcValue
                        _phaseShift.value = tempPhaseShift
                        processValues(tempAdcValue, tempPhaseShift)
                    }
                    SensorType.FMG3 -> {
                        val fmgVal = (500 + Math.sin(simAngle.toDouble()) * 120 + noise + (realHardwareDelta * 15f)).toInt()
                        _adcValue.value = fmgVal
                        _phaseShift.value = 0
                        processFmg3Value(fmgVal)
                    }
                    SensorType.FLC100 -> {
                        val flcVal = (512 + Math.sin(simAngle.toDouble() * 1.2) * 200 + noise + (realHardwareDelta * 18f)).toInt()
                        _adcValue.value = flcVal
                        _phaseShift.value = 0
                        processFlc100Value(flcVal)
                    }
                    SensorType.HMC5883L -> {
                        val compassHeadingLocal = if (_phoneSensorAvailable.value) _compassHeading.value else ((simAngle * 30f) % 360f)
                        _compassHeading.value = compassHeadingLocal
                        
                        val intensity = (300 + Math.sin(simAngle.toDouble()) * 50 + noise + (realHardwareDelta * 10f)).toInt()
                        _adcValue.value = intensity
                        _phaseShift.value = 0
                        processHmcValue(intensity)
                    }
                    SensorType.QMC5883L -> {
                        val qmcHeading = if (_phoneSensorAvailable.value) _compassHeading.value else ((simAngle * 25f) % 360f)
                        _compassHeading.value = qmcHeading
                        
                        val qmcIntensity = (3000 + Math.sin(simAngle.toDouble() * 1.4) * 450 + noise * 5 + (realHardwareDelta * 50f)).toInt()
                        _adcValue.value = qmcIntensity
                        _phaseShift.value = 0
                        processQmcValue(qmcIntensity, qmcHeading)
                    }
                    SensorType.ADXL345 -> {
                        val simPitch = (Math.sin(simAngle.toDouble()) * 15.0).toFloat()
                        val simRoll = (Math.cos(simAngle.toDouble() * 0.8) * 12.0).toFloat()
                        val simX = (simRoll / 57.3f * 9.8f) + (noise * 0.1f)
                        val simY = (simPitch / 57.3f * 9.8f) + (noise * 0.1f)
                        val simZ = 9.8f - (kotlin.math.abs(simX) + kotlin.math.abs(simY)) * 0.1f

                        adxlDriver.processAccelData(simX, simY, simZ)

                        val gForce = adxlDriver.gForce.value
                        val pitch = adxlDriver.pitch.value
                        val roll = adxlDriver.roll.value

                        val adcEquivalent = (gForce * 500).toInt().coerceIn(100, 1023)
                        _adcValue.value = adcEquivalent
                        _phaseShift.value = pitch.toInt()
                        processAdxl345Value(gForce, pitch, roll)
                    }
                    SensorType.PHONE_INTERNAL -> {
                        val phoneAdc = (180 + (realHardwareDelta * 16f) + noise + (Math.sin(simAngle.toDouble()) * 15)).toInt().coerceIn(100, 1023)
                        val phonePhase = ((if (realHardwareDelta > 8) 35 else -5) + (Math.sin(simAngle.toDouble()) * 10)).toInt().coerceIn(-45, 65)
                        
                        _adcValue.value = phoneAdc
                        _phaseShift.value = phonePhase
                        processValues(phoneAdc, phonePhase)
                    }
                }
                
                simAngle += 0.1f
                delay(150)
            }
        }
    }

    /**
     * Set a custom reading value manually (used for scanner simulator interaction)
     */
    fun setSimulatedReading(adc: Int, phase: Int) {
        if (_connectionState.value == ConnectionMode.SIMULATOR) {
            _adcValue.value = adc
            _phaseShift.value = phase
            when (_sensorType.value) {
                SensorType.GOLD_RADAR_X20, SensorType.PHONE_INTERNAL -> processValues(adc, phase)
                SensorType.FMG3 -> processFmg3Value(adc)
                SensorType.FLC100 -> processFlc100Value(adc)
                SensorType.HMC5883L -> processHmcValue(adc)
                SensorType.QMC5883L -> processQmcValue(adc, (adc % 360).toFloat())
                SensorType.ADXL345 -> processAdxl345Value(adc / 500f, phase.toFloat(), 0f)
            }
        }
    }

    private fun startReading() {
        isReading = true
        val buffer = ByteArray(1024)
        
        scanJob = scope.launch {
            var loopCount = 0
            while (isReading) {
                loopCount++
                if (loopCount % 600 == 0) { // Approx 30 seconds (600 * 50ms)
                    val currentBat = _batteryPercentage.value
                    if (currentBat != null && currentBat > 5) {
                        _batteryPercentage.value = currentBat - 1
                    }
                }
                try {
                    val bytes = if (usbPort != null) {
                        usbPort?.read(buffer, 100) ?: 0
                    } else {
                        inputStream?.read(buffer) ?: 0
                    }
                    
                    if (bytes > 0) {
                        val data = String(buffer, 0, bytes)
                        parseData(data)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Read error, closing connection", e)
                    withContext(Dispatchers.Main) {
                        _tvStatus.value = "❌ قطع اتصال سنسور"
                        disconnect()
                    }
                    break
                }
                delay(50)
            }
        }
    }

    private fun parseData(rawData: String) {
        val lines = rawData.split("\n", "\r").filter { it.isNotBlank() }
        for (line in lines) {
            try {
                // 1. Check for compass heading data in any incoming serial packet (auxiliary compass HMC5883L / QMC5883L)
                val compassPattern = Regex("(?:QMC|HMC|HEADING|DIR):\\s*([0-9.]+)")
                val compassMatch = compassPattern.find(line)
                if (compassMatch != null) {
                    val heading = compassMatch.groupValues[1].toFloatOrNull()
                    if (heading != null) {
                        _compassHeading.value = heading.coerceIn(0f, 360f)
                    }
                } else {
                    // Try to parse QMC/HMC XYZ raw magnet values: QMC_XYZ:x,y,z or HMC_XYZ:x,y,z or MAG_XYZ:x,y,z
                    val xyzPattern = Regex("(?:QMC_XYZ|HMC_XYZ|MAG_XYZ|QMC):\\s*(-?[0-9.]+)\\s*,\\s*(-?[0-9.]+)\\s*,\\s*(-?[0-9.]+)")
                    val xyzMatch = xyzPattern.find(line)
                    if (xyzMatch != null) {
                        val x = xyzMatch.groupValues[1].toFloatOrNull() ?: 0f
                        val y = xyzMatch.groupValues[2].toFloatOrNull() ?: 0f
                        val headingRad = kotlin.math.atan2(y.toDouble(), x.toDouble())
                        var headingDeg = Math.toDegrees(headingRad).toFloat()
                        if (headingDeg < 0) headingDeg += 360f
                        _compassHeading.value = headingDeg.coerceIn(0f, 360f)
                    }
                }

                // 1b. Check for battery percentage: BAT:85 or BATTERY:85
                val batteryPattern = Regex("(?:BAT|BATTERY|BATT):\\s*(\\d+)")
                val batteryMatch = batteryPattern.find(line)
                if (batteryMatch != null) {
                    val bat = batteryMatch.groupValues[1].toIntOrNull()
                    if (bat != null) {
                        _batteryPercentage.value = bat.coerceIn(0, 100)
                    }
                }

                // 2. Parse main sensor stream based on chosen sensor type
                when (_sensorType.value) {
                    SensorType.GOLD_RADAR_X20 -> {
                        val adcPattern = Regex("ADC:(\\d+)")
                        val phasePattern = Regex("PHASE:(-?\\d+)")
                        
                        val adcValueLocal = adcPattern.find(line)?.groupValues?.get(1)?.toIntOrNull()
                        val phaseShiftLocal = phasePattern.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                        
                        if (adcValueLocal != null) {
                            _adcValue.value = adcValueLocal
                            _phaseShift.value = phaseShiftLocal
                            processValues(adcValueLocal, phaseShiftLocal)
                        }
                    }
                    SensorType.FMG3 -> {
                        val fmgPattern = Regex("(?:FMG|MAG):\\s*(-?\\d+)")
                        val fmgMatch = fmgPattern.find(line)
                        val rawVal = if (fmgMatch != null) {
                            fmgMatch.groupValues[1].toIntOrNull()
                        } else {
                            line.trim().toIntOrNull()
                        }
                        
                        if (rawVal != null) {
                            _adcValue.value = rawVal
                            _phaseShift.value = 0
                            processFmg3Value(rawVal)
                        }
                    }
                    SensorType.FLC100 -> {
                        val flcPattern = Regex("(?:FLC|V|VOLT):\\s*(-?\\d+)")
                        val flcMatch = flcPattern.find(line)
                        val rawVal = if (flcMatch != null) {
                            flcMatch.groupValues[1].toIntOrNull()
                        } else {
                            line.trim().toIntOrNull()
                        }
                        
                        if (rawVal != null) {
                            _adcValue.value = rawVal
                            _phaseShift.value = 0
                            processFlc100Value(rawVal)
                        }
                    }
                    SensorType.HMC5883L -> {
                        // If HMC5883L is primary, use intensity vector or raw heading
                        val xyzPattern = Regex("(?:HMC_XYZ|MAG_XYZ):\\s*(-?[0-9.]+)\\s*,\\s*(-?[0-9.]+)\\s*,\\s*(-?[0-9.]+)")
                        val xyzMatch = xyzPattern.find(line)
                        if (xyzMatch != null) {
                            val x = xyzMatch.groupValues[1].toFloatOrNull() ?: 0f
                            val y = xyzMatch.groupValues[2].toFloatOrNull() ?: 0f
                            val z = xyzMatch.groupValues[3].toFloatOrNull() ?: 0f
                            
                            val intensity = kotlin.math.sqrt(x*x + y*y + z*z).toInt()
                            _adcValue.value = intensity
                            _phaseShift.value = 0
                            
                            val headingRad = kotlin.math.atan2(y.toDouble(), x.toDouble())
                            var headingDeg = Math.toDegrees(headingRad).toFloat()
                            if (headingDeg < 0) headingDeg += 360f
                            _compassHeading.value = headingDeg
                            
                            processHmcValue(intensity)
                        } else {
                            val hmcPattern = Regex("(?:HMC|HEADING|DIR):\\s*([0-9.]+)")
                            val hmcMatch = hmcPattern.find(line)
                            if (hmcMatch != null) {
                                val heading = hmcMatch.groupValues[1].toFloatOrNull()
                                if (heading != null) {
                                    _compassHeading.value = heading
                                    _adcValue.value = heading.toInt()
                                    _phaseShift.value = 0
                                    processHmcValue(heading.toInt())
                                }
                            }
                        }
                    }
                    SensorType.QMC5883L -> {
                        // High resolution 3-axis digital magnetometer QMC5883L parsing
                        val qmcXyzPattern = Regex("(?:QMC_XYZ|QMC|MAG_XYZ):\\s*(-?[0-9.]+)\\s*,\\s*(-?[0-9.]+)\\s*,\\s*(-?[0-9.]+)")
                        val qmcXyzMatch = qmcXyzPattern.find(line)
                        if (qmcXyzMatch != null) {
                            val x = qmcXyzMatch.groupValues[1].toFloatOrNull() ?: 0f
                            val y = qmcXyzMatch.groupValues[2].toFloatOrNull() ?: 0f
                            val z = qmcXyzMatch.groupValues[3].toFloatOrNull() ?: 0f
                            
                            val intensity = kotlin.math.sqrt(x*x + y*y + z*z).toInt()
                            _adcValue.value = intensity
                            _phaseShift.value = 0
                            
                            val headingRad = kotlin.math.atan2(y.toDouble(), x.toDouble())
                            var headingDeg = Math.toDegrees(headingRad).toFloat()
                            if (headingDeg < 0) headingDeg += 360f
                            _compassHeading.value = headingDeg
                            
                            processQmcValue(intensity, headingDeg)
                        } else {
                            val qmcDirPattern = Regex("(?:QMC|HEADING|DIR):\\s*([0-9.]+)")
                            val qmcDirMatch = qmcDirPattern.find(line)
                            if (qmcDirMatch != null) {
                                val heading = qmcDirMatch.groupValues[1].toFloatOrNull()
                                if (heading != null) {
                                    _compassHeading.value = heading
                                    _adcValue.value = heading.toInt()
                                    _phaseShift.value = 0
                                    processQmcValue(heading.toInt(), heading)
                                }
                            }
                        }
                    }
                    SensorType.ADXL345 -> {
                        val parsed = adxlDriver.parseStreamLine(line)
                        if (parsed) {
                            val gForce = adxlDriver.gForce.value
                            val pitch = adxlDriver.pitch.value
                            val roll = adxlDriver.roll.value

                            val adcEquivalent = (gForce * 500).toInt().coerceIn(100, 1023)
                            _adcValue.value = adcEquivalent
                            _phaseShift.value = pitch.toInt()

                            processAdxl345Value(gForce, pitch, roll)
                        } else {
                            val adxlXyzPattern = Regex("(?:ADXL_XYZ|ACCEL|ADXL):\\s*(-?[0-9.]+)\\s*,\\s*(-?[0-9.]+)\\s*,\\s*(-?[0-9.]+)")
                            val adxlMatch = adxlXyzPattern.find(line)
                            if (adxlMatch != null) {
                                val x = adxlMatch.groupValues[1].toFloatOrNull() ?: 0f
                                val y = adxlMatch.groupValues[2].toFloatOrNull() ?: 0f
                                val z = adxlMatch.groupValues[3].toFloatOrNull() ?: 9.8f
                                adxlDriver.processAccelData(x, y, z)
                                processAdxl345Value(adxlDriver.gForce.value, adxlDriver.pitch.value, adxlDriver.roll.value)
                            }
                        }
                    }
                    SensorType.PHONE_INTERNAL -> {
                        val adcPattern = Regex("ADC:(\\d+)")
                        val phasePattern = Regex("PHASE:(-?\\d+)")
                        val adcValueLocal = adcPattern.find(line)?.groupValues?.get(1)?.toIntOrNull()
                        val phaseShiftLocal = phasePattern.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                        if (adcValueLocal != null) {
                            _adcValue.value = adcValueLocal
                            _phaseShift.value = phaseShiftLocal
                            processValues(adcValueLocal, phaseShiftLocal)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing stream line: $line", e)
            }
        }
    }

    private fun processValues(adcValueLocal: Int, phaseShiftLocal: Int) {
        val metalTypeLocal = when {
            phaseShiftLocal > 30 -> "طلا ✨"
            phaseShiftLocal in 15..30 -> "نقره 💎"
            phaseShiftLocal < -20 -> "آهن 🧲"
            adcValueLocal > 700 -> "طلا ✨"
            adcValueLocal > 400 -> "نقره 💎"
            else -> "ناشناخته ❓"
        }
        
        val strength = ((adcValueLocal - 200).coerceIn(0, 800) * 100 / 800)
        _signalStrength.value = strength
        
        if (strength > _maxSignal.value) {
            _maxSignal.value = strength
        }
        
        _depthMeters.value = calculateDepth(strength)
        _metalType.value = metalTypeLocal
    }

    private fun processFmg3Value(rawVal: Int) {
        // FMG3 fluxgate calibration baseline (default 500)
        val base = if (baselineNoise > 10) baselineNoise else 500
        val deviation = kotlin.math.abs(rawVal - base)
        val strength = (deviation * 100 / 300).coerceIn(0, 100)
        
        _signalStrength.value = strength
        if (strength > _maxSignal.value) {
            _maxSignal.value = strength
        }
        _depthMeters.value = calculateDepth(strength)
        
        _metalType.value = when {
            rawVal > base + 80 -> "فلز مغناطیسی (FMG3) 🧲"
            rawVal < base - 80 -> "حفره یا غار 🕳️"
            rawVal in (base + 25)..(base + 80) -> "طلا / فلز باارزش ✨"
            else -> "زمین معمولی 🌱"
        }
    }

    private fun processFlc100Value(rawVal: Int) {
        // FLC100 fluxgate calibration baseline (default 512)
        val base = if (baselineNoise > 10) baselineNoise else 512
        val deviation = kotlin.math.abs(rawVal - base)
        val strength = (deviation * 100 / 350).coerceIn(0, 100)
        
        _signalStrength.value = strength
        if (strength > _maxSignal.value) {
            _maxSignal.value = strength
        }
        _depthMeters.value = calculateDepth(strength)
        
        _metalType.value = when {
            rawVal > base + 100 -> "فلز فرومغناطیس (FLC100) 🧲"
            rawVal < base - 100 -> "حفره یا راهرو 🕳️"
            rawVal in (base + 30)..(base + 100) -> "طلا / فلز گرانبها ✨"
            else -> "زمین بکر ⛰️"
        }
    }

    private fun processHmcValue(intensity: Int) {
        val base = if (baselineNoise > 10) baselineNoise else 300
        val deviation = kotlin.math.abs(intensity - base)
        val strength = (deviation * 100 / 250).coerceIn(0, 100)
        
        _signalStrength.value = strength
        if (strength > _maxSignal.value) {
            _maxSignal.value = strength
        }
        _depthMeters.value = calculateDepth(strength)
        
        _metalType.value = if (deviation > 40) {
            "تغییر قطبش مغناطیسی (HMC5883L) 🧭"
        } else {
            "میدان مغناطیسی نرمال 🌍"
        }
    }

    private fun processQmcValue(intensity: Int, heading: Float) {
        val base = if (baselineNoise > 100) baselineNoise else 3000
        val deviation = intensity - base
        val absDev = kotlin.math.abs(deviation)
        val strength = (absDev * 100 / 600).coerceIn(0, 100)
        
        _signalStrength.value = strength
        if (strength > _maxSignal.value) {
            _maxSignal.value = strength
        }
        _depthMeters.value = calculateDepth(strength)
        
        _metalType.value = when {
            deviation > 200 -> "آنومالی مغناطیسی شدید (QMC5883L) 🧲"
            deviation in 60..200 -> "مایه‌ی طلا / فلز باارزش ✨"
            deviation < -150 -> "حفره یا راهرو زیرزمینی 🕳️"
            else -> "میدان مغناطیسی یکنواخت زمین 🧭"
        }
    }

    private fun processAdxl345Value(gForce: Float, pitch: Float, roll: Float) {
        val tiltAngle = kotlin.math.sqrt(pitch * pitch + roll * roll)
        val strength = (tiltAngle * 3.3f).toInt().coerceIn(0, 100)
        _signalStrength.value = strength
        if (strength > _maxSignal.value) {
            _maxSignal.value = strength
        }
        _depthMeters.value = calculateDepth(strength)

        _metalType.value = when {
            tiltAngle > 25f -> "شیب تند دستگاه (تراز نامتعادل) ⚠️"
            kotlin.math.abs(gForce - 1.0f) > 0.3f -> "حرکت / شتاب ناگهانی (ADXL345) 🏃"
            tiltAngle < 5f -> "دستگاه کاملاً تراز (ADXL345) 🎯"
            else -> "تراز مناسب اسکن 📐"
        }
    }

    private fun calculateDepth(strength: Int): Double {
        val baseNoiseVal = if (baselineNoise < 100) baselineNoise else 30
        val cleanSignal = max(0, strength - baseNoiseVal).toDouble()
        val cleanMax = max(1, _maxSignal.value - baseNoiseVal).toDouble()
        
        if (cleanMax <= 0) return 0.0
        
        val attenuationRatio = 1.0 - (cleanSignal / cleanMax)
        
        return when {
            attenuationRatio <= 0.1 -> 0.0 + (attenuationRatio / 0.1) * 0.5
            attenuationRatio <= 0.3 -> 0.5 + ((attenuationRatio - 0.1) / 0.2) * 1.5
            attenuationRatio <= 0.5 -> 2.0 + ((attenuationRatio - 0.3) / 0.2) * 3.0
            attenuationRatio <= 0.7 -> 5.0 + ((attenuationRatio - 0.5) / 0.2) * 5.0
            attenuationRatio <= 0.85 -> 10.0 + ((attenuationRatio - 0.7) / 0.15) * 5.0
            else -> 15.0 + ((attenuationRatio - 0.85) / 0.15) * 5.0
        }.coerceIn(0.0, MAX_DEPTH_METERS)
    }

    fun calibrate() {
        _maxSignal.value = 0
        if (_phoneMagMicroTesla.value > 0) {
            baseMicroTesla = _phoneMagMicroTesla.value
        }
        if (_sensorType.value == SensorType.FMG3 || _sensorType.value == SensorType.FLC100 || _sensorType.value == SensorType.HMC5883L || _sensorType.value == SensorType.QMC5883L || _sensorType.value == SensorType.ADXL345) {
            baselineNoise = _adcValue.value // Use the current raw value as baseline!
        } else {
            baselineNoise = _signalStrength.value.coerceAtLeast(10)
        }
        _tvStatus.value = "🔄 کالیبراسیون انجام شد"
        
        scope.launch {
            delay(2000)
            if (_connectionState.value == ConnectionMode.SIMULATOR) {
                _tvStatus.value = if (_phoneSensorAvailable.value) "📱 شبیه‌ساز متصل به سنسور واقعی گوشی" else "🤖 شبیه‌ساز فعال است"
            } else if (_connectionState.value != ConnectionMode.DISCONNECTED) {
                _tvStatus.value = "✅ متصل و آماده اسکن"
            } else {
                _tvStatus.value = "✅ آماده اسکن"
            }
        }
    }

    fun disconnect() {
        isReading = false
        scanJob?.cancel()
        scanJob = null
        
        try {
            bluetoothSocket?.close()
        } catch (e: Exception) { /* ignore */ }
        bluetoothSocket = null
        inputStream = null
        
        try {
            usbPort?.close()
        } catch (e: Exception) { /* ignore */ }
        usbPort = null
        
        _connectionState.value = ConnectionMode.DISCONNECTED
        _isConnected.value = false
        _batteryPercentage.value = null
        _tvStatus.value = "✅ آماده اسکن"
        _adcValue.value = 0
        _phaseShift.value = 0
        _signalStrength.value = 0
        _depthMeters.value = 0.0
        _metalType.value = "---"
    }
}

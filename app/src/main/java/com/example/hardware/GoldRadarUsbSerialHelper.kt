package com.example.hardware

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.Ch34xSerialDriver
import com.hoho.android.usbserial.driver.Cp21xxSerialDriver
import com.hoho.android.usbserial.driver.FtdiSerialDriver
import com.hoho.android.usbserial.driver.ProbeTable
import com.hoho.android.usbserial.driver.ProlificSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * Data model for a parsed Gold Radar X20 serial data packet.
 */
data class GoldRadarPacket(
    val rawText: String,
    val timestamp: Long = System.currentTimeMillis(),
    val adcValue: Int? = null,
    val phaseShift: Int? = null,
    val compassHeading: Float? = null,
    val batteryLevel: Int? = null,
    val temperatureCelsius: Float? = null,
    val gainSetting: Int? = null,
    val signalStrengthPercentage: Int? = null,
    val targetClassification: String? = null
)

/**
 * Connection states for Gold Radar X20 USB Hardware.
 */
enum class GoldRadarConnectionStatus {
    DISCONNECTED,
    PERMISSION_PENDING,
    CONNECTING,
    CONNECTED,
    AUTO_BAUD_TESTING,
    ERROR
}

/**
 * Metadata for connected USB-Serial hardware chip.
 */
data class UsbHardwareInfo(
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val driverType: String,
    val manufacturerName: String?,
    val productName: String?,
    val serialNumber: String?
)

/**
 * Dedicated helper and manager class to initialize, configure, and maintain
 * the high-speed USB-Serial connection to Gold Radar X20 hardware using the
 * usb-serial-for-android library.
 */
class GoldRadarUsbSerialHelper(private val context: Context) {

    companion object {
        private const val TAG = "GoldRadarUsbHelper"
        const val ACTION_USB_PERMISSION = "com.soheil.federal.USB_PERMISSION"

        // Standard Gold Radar X20 Baud Rates
        val SUPPORTED_BAUD_RATES = listOf(115200, 9600, 57600, 38400, 19200)

        /**
         * Builds a comprehensive UsbSerialProber table registered with known
         * CH340/CH341, FTDI, CP2102/CP2104, Prolific, and CDC/ACM USB-to-Serial chips
         * used in Gold Radar X20 detectors and ground scanner probes.
         */
        fun buildCustomProber(): UsbSerialProber {
            val customTable = UsbSerialProber.getDefaultProbeTable()

            // CH340 / CH341 / CH342 / CH343 / CH340G / CH340T
            customTable.addProduct(0x1A86, 0x7523, Ch34xSerialDriver::class.java)
            customTable.addProduct(0x1A86, 0x5523, Ch34xSerialDriver::class.java)
            customTable.addProduct(0x1A86, 0x7522, Ch34xSerialDriver::class.java)
            customTable.addProduct(0x1A86, 0x5512, Ch34xSerialDriver::class.java)

            // FTDI FT232R / FT232H / FT2232H / FT4232H
            customTable.addProduct(0x0403, 0x6001, FtdiSerialDriver::class.java)
            customTable.addProduct(0x0403, 0x6010, FtdiSerialDriver::class.java)
            customTable.addProduct(0x0403, 0x6011, FtdiSerialDriver::class.java)
            customTable.addProduct(0x0403, 0x6014, FtdiSerialDriver::class.java)

            // CP2102 / CP2104 / CP2105 / CP2108 / CP2109
            customTable.addProduct(0x10C4, 0xEA60, Cp21xxSerialDriver::class.java)
            customTable.addProduct(0x10C4, 0xEA70, Cp21xxSerialDriver::class.java)
            customTable.addProduct(0x10C4, 0xEA71, Cp21xxSerialDriver::class.java)

            // Prolific PL2303
            customTable.addProduct(0x067B, 0x2303, ProlificSerialDriver::class.java)

            // Arduino / STM32 / ESP32 CDC ACM
            customTable.addProduct(0x2341, 0x0043, CdcAcmSerialDriver::class.java) // Uno
            customTable.addProduct(0x2341, 0x0001, CdcAcmSerialDriver::class.java) // Uno R3
            customTable.addProduct(0x2341, 0x0010, CdcAcmSerialDriver::class.java) // Mega 2560
            customTable.addProduct(0x2E8A, 0x000A, CdcAcmSerialDriver::class.java) // Raspberry Pi Pico
            customTable.addProduct(0x303A, 0x1001, CdcAcmSerialDriver::class.java) // ESP32-S3

            return UsbSerialProber(customTable)
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private var serialPort: UsbSerialPort? = null
    private var usbConnection: UsbDeviceConnection? = null
    private var readJob: Job? = null

    @Volatile
    private var isListening = false

    // State Flows
    private val _connectionStatus = MutableStateFlow(GoldRadarConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<GoldRadarConnectionStatus> = _connectionStatus.asStateFlow()

    private val _statusMessage = MutableStateFlow("منتظر اتصال دستگاه Gold Radar X20...")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _hardwareInfo = MutableStateFlow<UsbHardwareInfo?>(null)
    val hardwareInfo: StateFlow<UsbHardwareInfo?> = _hardwareInfo.asStateFlow()

    private val _currentBaudRate = MutableStateFlow(115200)
    val currentBaudRate: StateFlow<Int> = _currentBaudRate.asStateFlow()

    private val _latestPacket = MutableStateFlow<GoldRadarPacket?>(null)
    val latestPacket: StateFlow<GoldRadarPacket?> = _latestPacket.asStateFlow()

    private val _totalBytesRx = MutableStateFlow(0L)
    val totalBytesRx: StateFlow<Long> = _totalBytesRx.asStateFlow()

    private val _totalBytesTx = MutableStateFlow(0L)
    val totalBytesTx: StateFlow<Long> = _totalBytesTx.asStateFlow()

    private val _dataRateBytesPerSec = MutableStateFlow(0)
    val dataRateBytesPerSec: StateFlow<Int> = _dataRateBytesPerSec.asStateFlow()

    // Shared Flow Streams
    private val _rawStream = MutableSharedFlow<String>(extraBufferCapacity = 512)
    val rawStream: SharedFlow<String> = _rawStream.asSharedFlow()

    private val _packetStream = MutableSharedFlow<GoldRadarPacket>(extraBufferCapacity = 512)
    val packetStream: SharedFlow<GoldRadarPacket> = _packetStream.asSharedFlow()

    private var isReceiverRegistered = false

    private val usbBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    synchronized(this) {
                        val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        }
                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        if (granted && device != null) {
                            _statusMessage.value = "✅ مجوز USB تأیید شد. در حال باز کردن پورت..."
                            openDevicePort(device)
                        } else {
                            _connectionStatus.value = GoldRadarConnectionStatus.ERROR
                            _statusMessage.value = "❌ مجوز دسترسی به USB لغو شد."
                        }
                    }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    Log.d(TAG, "USB Device Detached event received")
                    val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    if (device != null && device.deviceName == _hardwareInfo.value?.deviceName) {
                        _statusMessage.value = "⚠️ دستگاه Gold Radar X20 جدا شد"
                        disconnect()
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    Log.d(TAG, "USB Device Attached event received")
                    _statusMessage.value = "🔌 دستگاه USB جدید شناسايی شد"
                    connect()
                }
            }
        }
    }

    init {
        registerUsbReceiver()
    }

    private fun registerUsbReceiver() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(ACTION_USB_PERMISSION)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(usbBroadcastReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(usbBroadcastReceiver, filter)
            }
            isReceiverRegistered = true
        }
    }

    /**
     * Automatically scans for connected USB devices and initiates connection.
     */
    fun connect(preferredBaudRate: Int = _currentBaudRate.value): Boolean {
        if (_connectionStatus.value == GoldRadarConnectionStatus.CONNECTED) {
            Log.d(TAG, "Already connected to Gold Radar X20 serial port.")
            return true
        }

        _currentBaudRate.value = preferredBaudRate
        _connectionStatus.value = GoldRadarConnectionStatus.CONNECTING
        _statusMessage.value = "در حال جستجوی درایور سنسور Gold Radar X20..."

        val prober = buildCustomProber()
        val availableDrivers = prober.findAllDrivers(usbManager)

        if (availableDrivers.isEmpty()) {
            _connectionStatus.value = GoldRadarConnectionStatus.DISCONNECTED
            _statusMessage.value = "❌ هیچ سخت‌افزار Gold Radar X20 یا درایور USB-Serial یافت نشد"
            return false
        }

        val driver = availableDrivers[0]
        val device = driver.device

        if (!usbManager.hasPermission(device)) {
            _connectionStatus.value = GoldRadarConnectionStatus.PERMISSION_PENDING
            _statusMessage.value = "🔑 درخواست مجوز دسترسی به دستگاه USB..."

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val permissionIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(ACTION_USB_PERMISSION),
                flags
            )
            usbManager.requestPermission(device, permissionIntent)
            return false
        }

        return openDevicePort(device)
    }

    /**
     * Opens the USB device connection and sets port parameters.
     */
    private fun openDevicePort(device: UsbDevice): Boolean {
        val prober = buildCustomProber()
        val driver = prober.probeDevice(device) ?: run {
            _connectionStatus.value = GoldRadarConnectionStatus.ERROR
            _statusMessage.value = "❌ درایور پشتیبانی‌نشده برای VID:${device.vendorId} PID:${device.productId}"
            return false
        }

        val connection = usbManager.openDevice(device) ?: run {
            _connectionStatus.value = GoldRadarConnectionStatus.ERROR
            _statusMessage.value = "❌ خطا در ایجاد UsbDeviceConnection"
            return false
        }

        return try {
            val port = driver.ports[0]
            port.open(connection)
            port.setParameters(
                _currentBaudRate.value,
                UsbSerialPort.DATABITS_8,
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE
            )

            // DTR / RTS signal toggle
            try {
                port.dtr = true
                port.rts = true
            } catch (e: Exception) {
                Log.w(TAG, "DTR/RTS set ignored: ${e.message}")
            }

            this.serialPort = port
            this.usbConnection = connection

            val info = UsbHardwareInfo(
                deviceName = device.deviceName,
                vendorId = device.vendorId,
                productId = device.productId,
                driverType = driver.javaClass.simpleName,
                manufacturerName = device.manufacturerName,
                productName = device.productName,
                serialNumber = device.serialNumber
            )
            _hardwareInfo.value = info
            _connectionStatus.value = GoldRadarConnectionStatus.CONNECTED
            _statusMessage.value = "✅ متصل به ${info.productName ?: info.driverType} (${_currentBaudRate.value} bps)"

            startAsynchronousReading()
            
            // Send initial ping/handshake to Gold Radar X20 hardware
            scope.launch {
                delay(300)
                sendGoldRadarCommand("PING")
                delay(200)
                sendGoldRadarCommand("INFO")
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure USB Serial port", e)
            _connectionStatus.value = GoldRadarConnectionStatus.ERROR
            _statusMessage.value = "❌ خطا در تنظیم پورت: ${e.message}"
            disconnect()
            false
        }
    }

    /**
     * Changes baud rate on the active connection.
     */
    fun setBaudRate(newBaudRate: Int) {
        if (newBaudRate <= 0) return
        _currentBaudRate.value = newBaudRate
        serialPort?.let { port ->
            try {
                port.setParameters(
                    newBaudRate,
                    UsbSerialPort.DATABITS_8,
                    UsbSerialPort.STOPBITS_1,
                    UsbSerialPort.PARITY_NONE
                )
                _statusMessage.value = "✅ سرعت باود به $newBaudRate bps بروزرسانی شد"
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update baud rate", e)
            }
        }
    }

    /**
     * Tests multiple standard baud rates to find the active speed of the Gold Radar hardware.
     */
    fun autoDetectBaudRate() {
        if (serialPort == null) {
            if (!connect()) return
        }

        scope.launch {
            _connectionStatus.value = GoldRadarConnectionStatus.AUTO_BAUD_TESTING
            for (rate in SUPPORTED_BAUD_RATES) {
                _statusMessage.value = "🔍 تست سرعت $rate bps..."
                setBaudRate(rate)
                delay(150)
                sendGoldRadarCommand("PING")
                delay(350)

                // If valid packet received during wait, keep this baud rate
                if (_latestPacket.value != null && System.currentTimeMillis() - (_latestPacket.value?.timestamp ?: 0) < 500) {
                    _statusMessage.value = "🎯 سرعت مناسب پیدا شد: $rate bps"
                    _connectionStatus.value = GoldRadarConnectionStatus.CONNECTED
                    return@launch
                }
            }
            _statusMessage.value = "✅ تست باود پایان یافت (سرعت فعلی: ${_currentBaudRate.value} bps)"
            _connectionStatus.value = GoldRadarConnectionStatus.CONNECTED
        }
    }

    /**
     * Continuous asynchronous reading loop using I/O coroutines dispatcher.
     */
    private fun startAsynchronousReading() {
        stopReading()
        isListening = true

        readJob = scope.launch {
            val buffer = ByteArray(4096)
            val lineAccumulator = StringBuilder()
            var secondBytesRx = 0

            Log.d(TAG, "Asynchronous USB Serial reader thread started")

            while (isListening && _connectionStatus.value == GoldRadarConnectionStatus.CONNECTED) {
                val port = serialPort ?: break
                try {
                    val bytesRead = port.read(buffer, 200) // 200ms read timeout
                    if (bytesRead > 0) {
                        _totalBytesRx.value += bytesRead
                        secondBytesRx += bytesRead
                        _dataRateBytesPerSec.value = secondBytesRx

                        val textChunk = String(buffer, 0, bytesRead, StandardCharsets.UTF_8)
                        lineAccumulator.append(textChunk)

                        // Process CRLF or LF delimited packets
                        var lineBreakIndex: Int
                        while (true) {
                            val idxN = lineAccumulator.indexOf("\n")
                            val idxR = lineAccumulator.indexOf("\r")
                            lineBreakIndex = when {
                                idxN != -1 && idxR != -1 -> Math.min(idxN, idxR)
                                idxN != -1 -> idxN
                                else -> idxR
                            }

                            if (lineBreakIndex == -1) break

                            val rawLine = lineAccumulator.substring(0, lineBreakIndex).trim()

                            if (lineBreakIndex < lineAccumulator.length - 1 &&
                                (lineAccumulator[lineBreakIndex] == '\r' && lineAccumulator[lineBreakIndex + 1] == '\n')) {
                                lineAccumulator.delete(0, lineBreakIndex + 2)
                            } else {
                                lineAccumulator.delete(0, lineBreakIndex + 1)
                            }

                            if (rawLine.isNotEmpty()) {
                                _rawStream.emit(rawLine)
                                parseAndProcessPacket(rawLine)
                            }
                        }
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Serial Read IO error", e)
                    withContext(Dispatchers.Main) {
                        _statusMessage.value = "❌ قطع ارتباط سخت‌افزاری"
                        _connectionStatus.value = GoldRadarConnectionStatus.ERROR
                        disconnect()
                    }
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Unexpected error in USB serial loop", e)
                    delay(100)
                }
            }
            Log.d(TAG, "USB Serial reader loop terminated")
        }
    }

    /**
     * Parses incoming serial strings into structured GoldRadarPacket objects.
     */
    private fun parseAndProcessPacket(line: String) {
        val adcRegex = Regex("(?:ADC|VAL|RAW|QMC_RAW):\\s*(-?\\d+)")
        val phaseRegex = Regex("(?:PHASE|SHIFT):\\s*(-?\\d+)")
        val compassRegex = Regex("(?:QMC|HMC|HEADING|DIR):\\s*([0-9.]+)")
        val batRegex = Regex("(?:BAT|BATTERY|BATT):\\s*(\\d+)")
        val tempRegex = Regex("(?:TEMP|CELSIUS):\\s*([0-9.]+)")
        val gainRegex = Regex("(?:GAIN):\\s*(\\d+)")

        val adc = adcRegex.find(line)?.groupValues?.get(1)?.toIntOrNull()
        val phase = phaseRegex.find(line)?.groupValues?.get(1)?.toIntOrNull()
        var compass = compassRegex.find(line)?.groupValues?.get(1)?.toFloatOrNull()
        val bat = batRegex.find(line)?.groupValues?.get(1)?.toIntOrNull()
        val temp = tempRegex.find(line)?.groupValues?.get(1)?.toFloatOrNull()
        val gain = gainRegex.find(line)?.groupValues?.get(1)?.toIntOrNull()

        if (compass == null) {
            val xyzRegex = Regex("(?:QMC_XYZ|HMC_XYZ|MAG_XYZ):\\s*(-?[0-9.]+)\\s*,\\s*(-?[0-9.]+)\\s*,\\s*(-?[0-9.]+)")
            val xyzMatch = xyzRegex.find(line)
            if (xyzMatch != null) {
                val x = xyzMatch.groupValues[1].toFloatOrNull() ?: 0f
                val y = xyzMatch.groupValues[2].toFloatOrNull() ?: 0f
                var deg = Math.toDegrees(kotlin.math.atan2(y.toDouble(), x.toDouble())).toFloat()
                if (deg < 0) deg += 360f
                compass = deg
            }
        }

        var calcSignal: Int? = null
        var classification: String? = null

        if (adc != null) {
            calcSignal = ((adc - 180).coerceIn(0, 800) * 100 / 800)

            val pVal = phase ?: 0
            classification = when {
                pVal > 30 -> "طلا ✨ (Gold Peak)"
                pVal in 15..30 -> "نقره 💎 (Silver Peak)"
                pVal < -20 -> "آهن 🧲 (Ferrous Anomaly)"
                adc > 700 -> "آنومالی باارزش ✨"
                adc < 200 -> "حفره / دالان 🕳️"
                else -> "خاک عادی 🌱"
            }
        }

        val packet = GoldRadarPacket(
            rawText = line,
            adcValue = adc,
            phaseShift = phase,
            compassHeading = compass,
            batteryLevel = bat,
            temperatureCelsius = temp,
            gainSetting = gain,
            signalStrengthPercentage = calcSignal,
            targetClassification = classification
        )

        _latestPacket.value = packet
        scope.launch {
            _packetStream.emit(packet)
        }
    }

    /**
     * Transmits raw ByteArray to the Gold Radar X20 hardware.
     */
    fun sendBytes(data: ByteArray): Boolean {
        val port = serialPort ?: run {
            Log.w(TAG, "Cannot send bytes: serialPort is null")
            return false
        }
        return try {
            port.write(data, 1000)
            _totalBytesTx.value += data.size
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error writing bytes to USB serial port", e)
            false
        }
    }

    /**
     * Transmits command string with CRLF delimiter.
     */
    fun sendGoldRadarCommand(command: String): Boolean {
        val cmdWithLf = if (command.endsWith("\r\n")) command else "$command\r\n"
        val bytes = cmdWithLf.toByteArray(StandardCharsets.UTF_8)
        return sendBytes(bytes)
    }

    // Standard Gold Radar X20 Command Shortcuts
    fun startScan(): Boolean = sendGoldRadarCommand("START_SCAN")
    fun stopScan(): Boolean = sendGoldRadarCommand("STOP_SCAN")
    fun calibrateSensor(): Boolean = sendGoldRadarCommand("CALIBRATE")
    fun setGain(gainLevel: Int): Boolean = sendGoldRadarCommand("GAIN:$gainLevel")
    fun setSamplingRateMs(rateMs: Int): Boolean = sendGoldRadarCommand("RATE:$rateMs")
    fun requestBatteryStatus(): Boolean = sendGoldRadarCommand("BATTERY")

    private fun stopReading() {
        isListening = false
        readJob?.cancel()
        readJob = null
    }

    /**
     * Closes the active USB connection and releases all resources.
     */
    fun disconnect() {
        stopReading()

        try {
            serialPort?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing serialPort", e)
        }
        serialPort = null

        try {
            usbConnection?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing usbConnection", e)
        }
        usbConnection = null

        _connectionStatus.value = GoldRadarConnectionStatus.DISCONNECTED
        _hardwareInfo.value = null
        _dataRateBytesPerSec.value = 0
        _statusMessage.value = "قطع ارتباط کامل Gold Radar X20"
    }

    /**
     * Unregisters broadcast receivers and cancels jobs.
     */
    fun destroy() {
        disconnect()
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(usbBroadcastReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "Unregistering receiver error", e)
            }
            isReceiverRegistered = false
        }
    }
}

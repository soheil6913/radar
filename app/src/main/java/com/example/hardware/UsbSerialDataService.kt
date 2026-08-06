package com.example.hardware

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.hoho.android.usbserial.driver.Ch34xSerialDriver
import com.hoho.android.usbserial.driver.FtdiSerialDriver
import com.hoho.android.usbserial.driver.ProbeTable
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

enum class UsbServiceConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class UsbDeviceDetails(
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val driverName: String,
    val portNumber: Int
)

data class ParsedSensorPacket(
    val rawString: String,
    val timestamp: Long = System.currentTimeMillis(),
    val adcValue: Int? = null,
    val phaseShift: Int? = null,
    val compassHeading: Float? = null,
    val batteryPercentage: Int? = null,
    val signalStrength: Int? = null
)

/**
 * Foreground Service that continuously listens for incoming raw serial data
 * from USB sensor hardware (e.g., CH340, FTDI, CP210x) attached to the Android device.
 */
class UsbSerialDataService : Service() {

    companion object {
        private const val TAG = "UsbSerialDataService"
        private const val CHANNEL_ID = "usb_sensor_stream_channel"
        private const val NOTIFICATION_ID = 4001
        const val ACTION_USB_PERMISSION = "com.example.hardware.USB_PERMISSION"

        // Custom Prober registering all common USB-to-Serial chips
        fun getCustomProber(): UsbSerialProber {
            val probeTable = UsbSerialProber.getDefaultProbeTable()
            // Explicitly add common CH340 / CH341 vendor & product IDs
            probeTable.addProduct(0x1A86, 0x7523, Ch34xSerialDriver::class.java)
            probeTable.addProduct(0x1A86, 0x5523, Ch34xSerialDriver::class.java)
            probeTable.addProduct(0x1A86, 0x7522, Ch34xSerialDriver::class.java)
            probeTable.addProduct(0x0403, 0x6001, FtdiSerialDriver::class.java)   // FT232R
            return UsbSerialProber(probeTable)
        }
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var usbPort: UsbSerialPort? = null
    private var usbConnection: UsbDeviceConnection? = null
    private var readJob: Job? = null

    @Volatile
    private var isListening = false

    private val _connectionState = MutableStateFlow(UsbServiceConnectionState.DISCONNECTED)
    val connectionState: StateFlow<UsbServiceConnectionState> = _connectionState.asStateFlow()

    private val _statusMessage = MutableStateFlow("منتظر اتصال به سنسور USB...")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _usbDeviceDetails = MutableStateFlow<UsbDeviceDetails?>(null)
    val usbDeviceDetails: StateFlow<UsbDeviceDetails?> = _usbDeviceDetails.asStateFlow()

    private val _baudRate = MutableStateFlow(9600)
    val baudRate: StateFlow<Int> = _baudRate.asStateFlow()

    // SharedFlows for streaming high-frequency incoming data to multiple subscribers
    private val _rawByteStream = MutableSharedFlow<ByteArray>(extraBufferCapacity = 256)
    val rawByteStream: SharedFlow<ByteArray> = _rawByteStream.asSharedFlow()

    private val _packetStream = MutableSharedFlow<String>(extraBufferCapacity = 256)
    val packetStream: SharedFlow<String> = _packetStream.asSharedFlow()

    private val _parsedPacketStream = MutableSharedFlow<ParsedSensorPacket>(extraBufferCapacity = 256)
    val parsedPacketStream: SharedFlow<ParsedSensorPacket> = _parsedPacketStream.asSharedFlow()

    private val _latestParsedData = MutableStateFlow<ParsedSensorPacket?>(null)
    val latestParsedData: StateFlow<ParsedSensorPacket?> = _latestParsedData.asStateFlow()

    private val _rxByteCount = MutableStateFlow(0L)
    val rxByteCount: StateFlow<Long> = _rxByteCount.asStateFlow()

    private val _txByteCount = MutableStateFlow(0L)
    val txByteCount: StateFlow<Long> = _txByteCount.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): UsbSerialDataService = this@UsbSerialDataService
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    Log.d(TAG, "USB Device Detached")
                    val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    if (device != null && device.deviceName == _usbDeviceDetails.value?.deviceName) {
                        _statusMessage.value = "⚠️ دستگاه USB جدا شد"
                        disconnectUsb()
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    Log.d(TAG, "USB Device Attached")
                    _statusMessage.value = "🔌 دستگاه USB جدید شناسايی شد"
                    connectUsb()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("در حال گوش دادن به سنسور USB..."))

        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        }
        registerReceiver(usbReceiver, filter)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val requestedBaud = intent?.getIntExtra("baud_rate", 9600) ?: 9600
        setBaudRate(requestedBaud)
        connectUsb()
        return START_STICKY
    }

    /**
     * Scan available USB drivers and open the first matching serial port.
     */
    fun connectUsb(): Boolean {
        if (_connectionState.value == UsbServiceConnectionState.CONNECTED) {
            Log.d(TAG, "Already connected to USB serial port.")
            return true
        }

        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val prober = getCustomProber()
        val drivers = prober.findAllDrivers(usbManager)

        if (drivers.isEmpty()) {
            _connectionState.value = UsbServiceConnectionState.DISCONNECTED
            _statusMessage.value = "❌ هیچ دستگاه USB Serial پیدا نشد"
            return false
        }

        _connectionState.value = UsbServiceConnectionState.CONNECTING
        _statusMessage.value = "در حال برقراری ارتباط با سخت‌افزار USB..."

        val driver = drivers[0]
        val device = driver.device
        val connection = usbManager.openDevice(device)

        if (connection == null) {
            _connectionState.value = UsbServiceConnectionState.ERROR
            _statusMessage.value = "❌ مجوز یا دسترسی به USB داده نشد"
            return false
        }

        return try {
            val port = driver.ports[0]
            port.open(connection)
            port.setParameters(
                _baudRate.value,
                UsbSerialPort.DATABITS_8,
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE
            )

            // DTR / RTS signals to trigger microcontrollers like Arduino / ESP32 / STM32
            try {
                port.dtr = true
                port.rts = true
            } catch (e: Exception) {
                Log.w(TAG, "Could not set DTR/RTS", e)
            }

            this.usbPort = port
            this.usbConnection = connection

            val details = UsbDeviceDetails(
                deviceName = device.deviceName,
                vendorId = device.vendorId,
                productId = device.productId,
                driverName = driver.javaClass.simpleName,
                portNumber = port.portNumber
            )
            _usbDeviceDetails.value = details
            _connectionState.value = UsbServiceConnectionState.CONNECTED
            _statusMessage.value = "✅ متصل به سنسور (${details.driverName} - ${details.vendorId}:${details.productId})"

            updateNotification("سنسور فعال: ${details.driverName}")
            startReadingLoop()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error opening USB port", e)
            _connectionState.value = UsbServiceConnectionState.ERROR
            _statusMessage.value = "❌ خطا در باز کردن پورت: ${e.message}"
            disconnectUsb()
            false
        }
    }

    /**
     * Changes baud rate on the active port dynamically.
     */
    fun setBaudRate(newRate: Int) {
        if (newRate <= 0) return
        _baudRate.value = newRate
        usbPort?.let { port ->
            try {
                port.setParameters(
                    newRate,
                    UsbSerialPort.DATABITS_8,
                    UsbSerialPort.STOPBITS_1,
                    UsbSerialPort.PARITY_NONE
                )
                _statusMessage.value = "✅ تغییر Baud Rate به $newRate"
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update baud rate", e)
            }
        }
    }

    /**
     * Continuous asynchronous reading loop operating on I/O Coroutine dispatcher.
     */
    private fun startReadingLoop() {
        stopReadingLoop()
        isListening = true

        readJob = serviceScope.launch {
            val readBuffer = ByteArray(4096)
            val lineBuffer = StringBuilder()

            Log.d(TAG, "Continuous USB Serial reading loop started")

            while (isListening && _connectionState.value == UsbServiceConnectionState.CONNECTED) {
                val port = usbPort ?: break
                try {
                    val bytesRead = port.read(readBuffer, 200) // 200ms read timeout
                    if (bytesRead > 0) {
                        _rxByteCount.value += bytesRead

                        val chunkBytes = readBuffer.copyOf(bytesRead)
                        _rawByteStream.emit(chunkBytes)

                        val chunkText = String(chunkBytes, StandardCharsets.UTF_8)
                        lineBuffer.append(chunkText)

                        // Delimit lines by \n or \r
                        var newLineIndex: Int
                        while (true) {
                            val idxN = lineBuffer.indexOf("\n")
                            val idxR = lineBuffer.indexOf("\r")
                            newLineIndex = when {
                                idxN != -1 && idxR != -1 -> Math.min(idxN, idxR)
                                idxN != -1 -> idxN
                                else -> idxR
                            }

                            if (newLineIndex == -1) break

                            val completeLine = lineBuffer.substring(0, newLineIndex).trim()
                            // Skip the delimiter character(s)
                            if (newLineIndex < lineBuffer.length - 1 && 
                                (lineBuffer[newLineIndex] == '\r' && lineBuffer[newLineIndex + 1] == '\n')) {
                                lineBuffer.delete(0, newLineIndex + 2)
                            } else {
                                lineBuffer.delete(0, newLineIndex + 1)
                            }

                            if (completeLine.isNotEmpty()) {
                                _packetStream.emit(completeLine)
                                parseAndEmitPacket(completeLine)
                            }
                        }
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Read IO exception", e)
                    withContext(Dispatchers.Main) {
                        _statusMessage.value = "❌ قطع ارتباط سخت‌افزاری"
                        _connectionState.value = UsbServiceConnectionState.ERROR
                        disconnectUsb()
                    }
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Unexpected read loop error", e)
                    delay(100)
                }
            }
            Log.d(TAG, "USB Reading loop stopped")
        }
    }

    private fun parseAndEmitPacket(line: String) {
        val adcPattern = Regex("(?:ADC|VAL|RAW|QMC_RAW):\\s*(-?\\d+)")
        val phasePattern = Regex("(?:PHASE|SHIFT):\\s*(-?\\d+)")
        val compassPattern = Regex("(?:QMC|HMC|HEADING|DIR):\\s*([0-9.]+)")
        val batPattern = Regex("(?:BAT|BATTERY):\\s*(\\d+)")

        val adc = adcPattern.find(line)?.groupValues?.get(1)?.toIntOrNull()
        val phase = phasePattern.find(line)?.groupValues?.get(1)?.toIntOrNull()
        var compass = compassPattern.find(line)?.groupValues?.get(1)?.toFloatOrNull()
        val bat = batPattern.find(line)?.groupValues?.get(1)?.toIntOrNull()

        if (compass == null) {
            val qmcXyzPattern = Regex("(?:QMC_XYZ|HMC_XYZ|MAG_XYZ):\\s*(-?[0-9.]+)\\s*,\\s*(-?[0-9.]+)\\s*,\\s*(-?[0-9.]+)")
            val xyzMatch = qmcXyzPattern.find(line)
            if (xyzMatch != null) {
                val x = xyzMatch.groupValues[1].toFloatOrNull() ?: 0f
                val y = xyzMatch.groupValues[2].toFloatOrNull() ?: 0f
                var headingDeg = Math.toDegrees(kotlin.math.atan2(y.toDouble(), x.toDouble())).toFloat()
                if (headingDeg < 0) headingDeg += 360f
                compass = headingDeg
            }
        }

        var calculatedSignal: Int? = null
        if (adc != null) {
            calculatedSignal = ((adc - 200).coerceIn(0, 800) * 100 / 800)
        }

        val packet = ParsedSensorPacket(
            rawString = line,
            adcValue = adc,
            phaseShift = phase,
            compassHeading = compass,
            batteryPercentage = bat,
            signalStrength = calculatedSignal
        )

        _latestParsedData.value = packet
        serviceScope.launch {
            _parsedPacketStream.emit(packet)
        }
    }

    /**
     * Transmits raw bytes or ASCII command strings to the connected USB sensor hardware.
     */
    fun sendData(data: ByteArray): Boolean {
        val port = usbPort ?: return false
        return try {
            port.write(data, 1000)
            _txByteCount.value += data.size
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error writing data to USB serial port", e)
            false
        }
    }

    fun sendCommand(command: String): Boolean {
        val bytes = (command + "\r\n").toByteArray(StandardCharsets.UTF_8)
        return sendData(bytes)
    }

    private fun stopReadingLoop() {
        isListening = false
        readJob?.cancel()
        readJob = null
    }

    fun disconnectUsb() {
        stopReadingLoop()
        try {
            usbPort?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing usbPort", e)
        }
        usbPort = null

        try {
            usbConnection?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing usbConnection", e)
        }
        usbConnection = null

        _connectionState.value = UsbServiceConnectionState.DISCONNECTED
        _usbDeviceDetails.value = null
        _statusMessage.value = "قطع اتصال کامل USB"
        updateNotification("سنسور USB غیرفعال است")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "USB Sensor Service Channel"
            val descriptionText = "نمایش وضعیت دریافت داده‌های زنده سنسور USB"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(contentText: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("سرویس دریافت داده‌های سنسور OKM")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(contentText))
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbReceiver)
        disconnectUsb()
        serviceScope.cancel()
    }
}

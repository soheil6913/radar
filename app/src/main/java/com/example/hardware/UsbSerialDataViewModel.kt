package com.example.hardware

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SerialLogEntry(
    val timestamp: String,
    val text: String,
    val isOutgoing: Boolean = false,
    val parsedData: ParsedSensorPacket? = null
)

/**
 * ViewModel component that binds to UsbSerialDataService and provides continuous
 * live serial data updates, statistics, transmission capabilities, and log buffering.
 */
class UsbSerialDataViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "UsbSerialViewModel"
        private const val MAX_LOG_CAPACITY = 200
    }

    private var usbService: UsbSerialDataService? = null
    private var isBound = false
    private var logCollectJob: Job? = null

    private val _connectionState = MutableStateFlow(UsbServiceConnectionState.DISCONNECTED)
    val connectionState: StateFlow<UsbServiceConnectionState> = _connectionState.asStateFlow()

    private val _statusMessage = MutableStateFlow("سرویس USB راه‌اندازی نشده است")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _deviceDetails = MutableStateFlow<UsbDeviceDetails?>(null)
    val deviceDetails: StateFlow<UsbDeviceDetails?> = _deviceDetails.asStateFlow()

    private val _baudRate = MutableStateFlow(9600)
    val baudRate: StateFlow<Int> = _baudRate.asStateFlow()

    private val _logEntries = MutableStateFlow<List<SerialLogEntry>>(emptyList())
    val logEntries: StateFlow<List<SerialLogEntry>> = _logEntries.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _latestSensorPacket = MutableStateFlow<ParsedSensorPacket?>(null)
    val latestSensorPacket: StateFlow<ParsedSensorPacket?> = _latestSensorPacket.asStateFlow()

    private val _totalBytesReceived = MutableStateFlow(0L)
    val totalBytesReceived: StateFlow<Long> = _totalBytesReceived.asStateFlow()

    private val _totalPacketsReceived = MutableStateFlow(0L)
    val totalPacketsReceived: StateFlow<Long> = _totalPacketsReceived.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? UsbSerialDataService.LocalBinder
            if (binder != null) {
                usbService = binder.getService()
                isBound = true
                observeServiceData()
                Log.d(TAG, "Successfully bound to UsbSerialDataService")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            usbService = null
            isBound = false
            _connectionState.value = UsbServiceConnectionState.DISCONNECTED
            _statusMessage.value = "⚠️ سرویس USB قطع شد"
            Log.d(TAG, "Unbound from UsbSerialDataService")
        }
    }

    init {
        bindAndStartService()
    }

    fun bindAndStartService() {
        val context = getApplication<Application>()
        val intent = Intent(context, UsbSerialDataService::class.java).apply {
            putExtra("baud_rate", _baudRate.value)
        }

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting/binding UsbSerialDataService", e)
            _statusMessage.value = "❌ خطا در اجرای سرویس: ${e.message}"
        }
    }

    private fun observeServiceData() {
        val service = usbService ?: return

        logCollectJob?.cancel()
        logCollectJob = viewModelScope.launch {
            // Collect connection states
            launch {
                service.connectionState.collect { state ->
                    _connectionState.value = state
                }
            }

            // Collect status messages
            launch {
                service.statusMessage.collect { msg ->
                    _statusMessage.value = msg
                }
            }

            // Collect USB device metadata
            launch {
                service.usbDeviceDetails.collect { details ->
                    _deviceDetails.value = details
                }
            }

            // Collect byte counts
            launch {
                service.rxByteCount.collect { bytes ->
                    _totalBytesReceived.value = bytes
                }
            }

            // Collect parsed packets continuously
            launch {
                service.latestParsedData.collect { packet ->
                    if (packet != null) {
                        _latestSensorPacket.value = packet
                    }
                }
            }

            // Collect raw packet string lines continuously
            launch {
                service.packetStream.collect { rawLine ->
                    if (!_isPaused.value) {
                        _totalPacketsReceived.value += 1
                        addLogEntry(
                            SerialLogEntry(
                                timestamp = timeFormat.format(Date()),
                                text = rawLine,
                                isOutgoing = false,
                                parsedData = service.latestParsedData.value
                            )
                        )
                    }
                }
            }
        }
    }

    private fun addLogEntry(entry: SerialLogEntry) {
        val current = _logEntries.value.toMutableList()
        current.add(entry)
        if (current.size > MAX_LOG_CAPACITY) {
            current.removeAt(0)
        }
        _logEntries.value = current
    }

    fun toggleConnect() {
        val service = usbService
        if (service != null) {
            if (service.connectionState.value == UsbServiceConnectionState.CONNECTED) {
                service.disconnectUsb()
            } else {
                service.connectUsb()
            }
        } else {
            bindAndStartService()
        }
    }

    fun setBaudRate(newRate: Int) {
        _baudRate.value = newRate
        usbService?.setBaudRate(newRate)
    }

    fun togglePauseListening() {
        _isPaused.value = !_isPaused.value
    }

    fun sendCommand(cmd: String): Boolean {
        val service = usbService ?: return false
        val success = service.sendCommand(cmd)
        if (success) {
            addLogEntry(
                SerialLogEntry(
                    timestamp = timeFormat.format(Date()),
                    text = "> $cmd",
                    isOutgoing = true
                )
            )
        }
        return success
    }

    fun clearLogs() {
        _logEntries.value = emptyList()
        _totalPacketsReceived.value = 0L
    }

    override fun onCleared() {
        super.onCleared()
        if (isBound) {
            try {
                getApplication<Application>().unbindService(serviceConnection)
            } catch (e: Exception) {
                Log.e(TAG, "Error unbinding service", e)
            }
            isBound = false
        }
    }
}

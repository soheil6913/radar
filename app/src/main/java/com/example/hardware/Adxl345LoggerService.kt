package com.example.hardware

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.Adxl345ReadingEntity
import com.example.data.ScanDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Background Service that continuously reads raw XYZ, pitch, roll, and gForce
 * telemetry from the ADXL345 3-Axis sensor via USB Serial / SensorManager
 * and persists them directly into the Room database for later 3D visualization.
 */
class Adxl345LoggerService : Service() {

    companion object {
        private const val TAG = "Adxl345LoggerService"
        private const val CHANNEL_ID = "adxl345_logger_channel"
        private const val NOTIFICATION_ID = 4002

        const val ACTION_START_LOGGING = "com.example.hardware.START_ADXL_LOGGING"
        const val ACTION_STOP_LOGGING = "com.example.hardware.STOP_ADXL_LOGGING"

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        private val _loggedCount = MutableStateFlow(0)
        val loggedCount: StateFlow<Int> = _loggedCount.asStateFlow()

        fun startLogging(context: Context) {
            val intent = Intent(context, Adxl345LoggerService::class.java).apply {
                action = ACTION_START_LOGGING
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopLogging(context: Context) {
            val intent = Intent(context, Adxl345LoggerService::class.java).apply {
                action = ACTION_STOP_LOGGING
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var loggerJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_LOGGING -> startLoggingProcess()
            ACTION_STOP_LOGGING -> stopLoggingProcess()
            else -> startLoggingProcess()
        }
        return START_STICKY
    }

    private fun startLoggingProcess() {
        if (_isServiceRunning.value) return

        _isServiceRunning.value = true
        startForeground(NOTIFICATION_ID, createNotification("ثبت‌کننده داده‌های ADXL345 فعال است"))

        val db = ScanDatabase.getDatabase(applicationContext)
        val adxlDao = db.adxl345Dao()

        loggerJob?.cancel()
        loggerJob = serviceScope.launch {
            val sensorManager = SensorManager(applicationContext)
            var count = 0

            Log.d(TAG, "Started continuous ADXL345 background logging to Room DB")

            while (_isServiceRunning.value) {
                try {
                    val sensorType = sensorManager.sensorType.value
                    if (sensorType == SensorType.ADXL345) {
                        val x = sensorManager.adxlX.value
                        val y = sensorManager.adxlY.value
                        val z = sensorManager.adxlZ.value
                        val pitch = sensorManager.adxlPitch.value
                        val roll = sensorManager.adxlRoll.value
                        val gForce = sensorManager.adxlGForce.value

                        val entity = Adxl345ReadingEntity(
                            timestamp = System.currentTimeMillis(),
                            x = x,
                            y = y,
                            z = z,
                            pitch = pitch,
                            roll = roll,
                            gForce = gForce,
                            sessionId = "adxl_session_${System.currentTimeMillis() / 100000}"
                        )

                        adxlDao.insertReading(entity)
                        count++
                        _loggedCount.value = count

                        if (count % 10 == 0) {
                            updateNotification("تعداد ذخیره شده در دیتابیس Room: $count رکورد")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving ADXL345 reading to Room DB", e)
                }

                delay(250) // Read & log 4 times per second (250ms interval)
            }
        }
    }

    private fun stopLoggingProcess() {
        _isServiceRunning.value = false
        loggerJob?.cancel()
        loggerJob = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "ADXL345 Sensor Logger"
            val descriptionText = "ذخیره‌سازی پس‌زمینه داده‌های خام سنسور ADXL345 در دیتابیس Room"
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
            .setContentTitle("سرویس ذخیره‌سازی سنسور ADXL345")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
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
        _isServiceRunning.value = false
        loggerJob?.cancel()
        serviceScope.cancel()
    }
}

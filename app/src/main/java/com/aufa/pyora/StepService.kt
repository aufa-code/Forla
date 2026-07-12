package com.aufa.pyora

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.aufa.pyora.data.AppDatabase
import com.aufa.pyora.data.DailySummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalDate

class StepService : Service(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val CHANNEL_ID = "forla_steps"
        const val NOTIF_ID = 1001
        const val PREFS = "forla"
        const val KEY_LAST_RAW = "walk_last_raw"
        fun stepsKey(date: String) = "walk_steps_$date"
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification(currentSteps()))
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------- HITUNG LANGKAH ----------
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_STEP_COUNTER) return
        val raw = event.values[0].toInt()
        val today = LocalDate.now().toString()
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val lastRaw = prefs.getInt(KEY_LAST_RAW, -1)

        // Bacaan pertama, atau HP baru restart (counter hardware balik 0) -> baseline ulang.
        if (lastRaw < 0 || raw < lastRaw) {
            prefs.edit().putInt(KEY_LAST_RAW, raw).apply()
            return
        }

        val delta = raw - lastRaw
        prefs.edit().putInt(KEY_LAST_RAW, raw).apply()

        if (delta > 0) {
            val key = stepsKey(today)
            val newSteps = prefs.getInt(key, 0) + delta
            prefs.edit().putInt(key, newSteps).apply()
            scope.launch {
                AppDatabase.getInstance(applicationContext)
                    .dailySummaryDao()
                    .upsert(DailySummary(date = today, totalSteps = newSteps))
            }
            updateNotification(newSteps)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // ---------- NOTIFIKASI ----------
    private fun currentSteps(): Int {
        val today = LocalDate.now().toString()
        return getSharedPreferences(PREFS, MODE_PRIVATE).getInt(stepsKey(today), 0)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "Penghitung Langkah",
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
    }

    private fun buildNotification(steps: Int): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Forla • $steps langkah hari ini")
            .setContentText("Menghitung langkah…")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(steps: Int) {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID, buildNotification(steps))
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        scope.cancel()
    }
}
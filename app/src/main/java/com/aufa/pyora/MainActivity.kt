package com.aufa.pyora

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.aufa.pyora.data.AppDatabase
import com.aufa.pyora.data.DailySummary
import com.aufa.pyora.data.DailySummaryDao
import com.aufa.pyora.ui.theme.PyoraTheme
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    private lateinit var dao: DailySummaryDao

    private var todaySteps by mutableIntStateOf(0)
    private var sensorAvailable by mutableStateOf(true)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startStepSensor()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Siapkan database + sensor
        dao = AppDatabase.getInstance(this).dailySummaryDao()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        sensorAvailable = stepSensor != null

        setContent {
            PyoraTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Baca riwayat dari database (auto-update kalau ada data baru)
                    val history by remember { dao.getAll() }.collectAsState(initial = emptyList())
                    StepScreen(
                        steps = todaySteps,
                        sensorAvailable = sensorAvailable,
                        history = history
                    )
                }
            }
        }

        ensurePermissionAndStart()
    }

    private fun ensurePermissionAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) startStepSensor()
            else permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        } else {
            startStepSensor()
        }
    }

    private fun startStepSensor() {
        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onResume() {
        super.onResume()
        startStepSensor()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            val totalSinceBoot = event.values[0].toInt()
            val today = LocalDate.now().toString() // contoh: 2026-07-04

            // Simpan "titik awal" langkah hari ini biar bisa hitung langkah HARI INI
            val prefs = getSharedPreferences("pyora", MODE_PRIVATE)
            val baselineKey = "baseline_$today"
            if (!prefs.contains(baselineKey)) {
                prefs.edit().putInt(baselineKey, totalSinceBoot).apply()
            }
            val baseline = prefs.getInt(baselineKey, totalSinceBoot)
            val steps = (totalSinceBoot - baseline).coerceAtLeast(0)

            todaySteps = steps
            saveSteps(today, steps)
        }
    }

    private fun saveSteps(date: String, steps: Int) {
        lifecycleScope.launch {
            dao.upsert(DailySummary(date = date, totalSteps = steps))
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

@Composable
fun StepScreen(
    steps: Int,
    sensorAvailable: Boolean,
    history: List<DailySummary>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        Text(text = "👟 Pyora", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(text = "Langkah hari ini", fontSize = 16.sp)
        Spacer(Modifier.height(16.dp))

        if (sensorAvailable) {
            Text(text = "$steps", fontSize = 72.sp, fontWeight = FontWeight.Bold)
            Text(text = "langkah", fontSize = 14.sp)
        } else {
            Text(text = "⚠️ HP ini nggak punya sensor step counter.", fontSize = 16.sp)
        }

        Spacer(Modifier.height(32.dp))
        Text(text = "📊 Riwayat (dari database)", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        if (history.isEmpty()) {
            Text(text = "Belum ada data tersimpan.", fontSize = 14.sp)
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(history) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = item.date, fontSize = 16.sp)
                        Text(text = "${item.totalSteps} langkah", fontSize = 16.sp)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
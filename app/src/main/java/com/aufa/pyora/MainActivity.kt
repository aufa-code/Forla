package com.aufa.pyora

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.aufa.pyora.data.ActivityDao
import com.aufa.pyora.data.ActivityRecord
import com.aufa.pyora.data.AppDatabase
import com.aufa.pyora.data.DailySummary
import com.aufa.pyora.data.DailySummaryDao
import com.aufa.pyora.data.Memory
import com.aufa.pyora.data.MemoryDao
import com.aufa.pyora.data.MoneyTransaction
import com.aufa.pyora.data.Place
import com.aufa.pyora.data.PlaceDao
import com.aufa.pyora.data.TransactionDao
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput


// ================= WARNA & KONSTAN =================
private val DarkBg = Color(0xFF0E0E0E)
private val CardBg = Color(0xFF1A1A1A)
private val Neon = Color(0xFFB4FF39)
private val TextGray = Color(0xFF9E9E9E)
private val ExpenseRed = Color(0xFFFF6B6B)
private const val DAILY_GOAL = 6000
private const val DWELL_RADIUS_M = 40f
private const val CHANNEL_ID = "pyora_expense"
private const val DWELL_TIME_MS = 5L * 60L * 1000L // 5 menit

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    private lateinit var summaryDao: DailySummaryDao
    private lateinit var txDao: TransactionDao
    private lateinit var memoryDao: MemoryDao
    private lateinit var placeDao: PlaceDao
    private lateinit var activityDao: ActivityDao

    private var todaySteps by mutableIntStateOf(0)
    private var sensorAvailable by mutableStateOf(true)

    // ---- Lokasi ----
    private lateinit var fusedClient: FusedLocationProviderClient
    private var wantTracking = false
    private var isTracking by mutableStateOf(false)
    private var currentPlace by mutableStateOf("Belum terdeteksi")
    private var currentCoords by mutableStateOf("—")
    private var currentPlaceId: Long? = null
    private var confirmedVisitIds by mutableStateOf<Set<Long>>(emptySet())
    private var activeVisitId: Long? = null
    private var activeVisitPlaceId: Long? = null
    private var activeVisitPlaceName: String? = null

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == "confirmed_visits") {
            val stored = prefs.getStringSet("confirmed_visits", emptySet())?.toSet() ?: emptySet()
            val ids = stored.mapNotNull { it.toLongOrNull() }.toSet()
            runOnUiThread { confirmedVisitIds = ids }
        }
    }

    // ---- Deteksi kunjungan ----
    private var recognizedPlace by mutableStateOf<String?>(null)
    private var pendingLat by mutableStateOf<Double?>(null)
    private var pendingLng by mutableStateOf<Double?>(null)
    private var visitInfo by mutableStateOf("")

    private var cachedPlaces: List<Place> = emptyList()
    private var anchorLat: Double? = null
    private var anchorLng: Double? = null
    private var anchorTime: Long = 0L
    private var visitHandledForAnchor = false
    private var lastLat: Double? = null
    private var lastLng: Double? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) startStepSensor() }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) beginLocationUpdates()
        else {
            currentPlace = "Izin lokasi ditolak"
            isTracking = false
            wantTracking = false
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            lastLat = loc.latitude
            lastLng = loc.longitude
            currentCoords = "%.5f, %.5f  (±%.0f m)".format(loc.latitude, loc.longitude, loc.accuracy)
            reverseGeocode(loc.latitude, loc.longitude)
            processDwell(loc.latitude, loc.longitude)
            checkKnownPlace(loc.latitude, loc.longitude)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = AppDatabase.getInstance(this)
        summaryDao = db.dailySummaryDao()
        txDao = db.transactionDao()
        memoryDao = db.memoryDao()
        placeDao = db.placeDao()
        activityDao = db.activityDao()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        sensorAvailable = stepSensor != null

        fusedClient = LocationServices.getFusedLocationProviderClient(this)

        lifecycleScope.launch {
            placeDao.getAll().collect { cachedPlaces = it }
        }
        val storedConfirmed = getSharedPreferences("pyora", MODE_PRIVATE)
            .getStringSet("confirmed_visits", emptySet()) ?: emptySet()
        confirmedVisitIds = storedConfirmed.mapNotNull { it.toLongOrNull() }.toSet()

        createNotifChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = DarkBg, surface = DarkBg)) {
                Surface(modifier = Modifier.fillMaxSize(), color = DarkBg) {
                    var tab by remember { mutableStateOf(0) }
                    val stepHistory by remember { summaryDao.getAll() }.collectAsState(initial = emptyList())
                    val txHistory by remember { txDao.getAll() }.collectAsState(initial = emptyList())
                    val memoryHistory by remember { memoryDao.getAll() }.collectAsState(initial = emptyList())
                    val places by remember { placeDao.getAll() }.collectAsState(initial = emptyList())
                    val activities by remember { activityDao.getAll() }.collectAsState(initial = emptyList())

                    Column(modifier = Modifier.fillMaxSize()) {
                        TabBar(selected = tab, onSelect = { tab = it })
                        when (tab) {
                            0 -> StepScreen(
                                steps = todaySteps,
                                sensorAvailable = sensorAvailable,
                                history = stepHistory
                            )
                            1 -> FinanceScreen(
                                transactions = txHistory,
                                currentPlaceName = recognizedPlace,
                                places = places,
                                onAdd = { amount, type, category, note ->
                                    addTransaction(amount, type, category, note)
                                }
                            )
                            2 -> MemoryScreen(
                                memories = memoryHistory,
                                onAdd = { note, photoPath -> addMemory(note, photoPath) }
                            )
                            3 -> LocationScreen(
                                place = currentPlace,
                                coords = currentCoords,
                                isTracking = isTracking,
                                recognizedPlace = recognizedPlace,
                                pendingNewPlace = pendingLat != null,
                                visitInfo = visitInfo,
                                savedPlaces = places,
                                onStart = { startTracking() },
                                onStop = { stopTracking() },
                                onMarkNow = { markHere() },
                                onTestNotif = { testDepartureNotification() },
                                onSavePlace = { name -> savePlace(name) }
                            )
                            else -> TimelineScreen(
                                activities = activities,
                                places = places,
                                transactions = txHistory,
                                confirmedVisitIds = confirmedVisitIds,
                                onConfirm = { visitId, placeId, amount ->
                                    confirmVisitExpense(visitId, placeId, amount)
                                }
                            )
                        }
                    }
                }
            }
        }

        ensurePermissionAndStart()
    }

    // ================= LOKASI =================
    private fun startTracking() {
        wantTracking = true
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) beginLocationUpdates()
        else locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun beginLocationUpdates() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMinUpdateIntervalMillis(3000L)
            .build()
        fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        isTracking = true
        currentPlace = "Mencari lokasi..."
    }

    private fun stopTracking() {
        wantTracking = false
        fusedClient.removeLocationUpdates(locationCallback)
        isTracking = false
        currentPlace = "Belum terdeteksi"
        currentCoords = "—"
        anchorLat = null
        anchorLng = null
        visitHandledForAnchor = false
        visitInfo = ""
        recognizedPlace = null
        pendingLat = null
        pendingLng = null
    }

    private fun reverseGeocode(lat: Double, lng: Double) {
        lifecycleScope.launch(Dispatchers.IO) {
            val name = try {
                val geocoder = Geocoder(this@MainActivity, Locale("id", "ID"))
                @Suppress("DEPRECATION")
                val results = geocoder.getFromLocation(lat, lng, 1)
                val addr = results?.firstOrNull()
                addr?.getAddressLine(0) ?: "Lokasi tak dikenal"
            } catch (e: Exception) {
                "Gagal baca nama tempat"
            }
            withContext(Dispatchers.Main) { currentPlace = name }
        }
    }

    private fun processDwell(lat: Double, lng: Double) {
        val aLat = anchorLat
        val aLng = anchorLng
        if (aLat == null || aLng == null) {
            anchorLat = lat
            anchorLng = lng
            anchorTime = System.currentTimeMillis()
            visitHandledForAnchor = false
            return
        }
        val dist = distanceMeters(aLat, aLng, lat, lng)
        if (dist > DWELL_RADIUS_M) {
            val vid = activeVisitId
            val vpid = activeVisitPlaceId
            val vname = activeVisitPlaceName
            if (vid != null && vname != null) {
                notifyDeparture(vid, vpid, vname)
            }
            activeVisitId = null
            activeVisitPlaceId = null
            activeVisitPlaceName = null

            anchorLat = lat
            anchorLng = lng
            anchorTime = System.currentTimeMillis()
            visitHandledForAnchor = false
            recognizedPlace = null
            pendingLat = null
            pendingLng = null
            visitInfo = "Sedang bergerak..."
        } else {
            val elapsed = System.currentTimeMillis() - anchorTime
            if (elapsed >= DWELL_TIME_MS && !visitHandledForAnchor) {
                visitHandledForAnchor = true
                handleVisit(aLat, aLng)
            } else if (!visitHandledForAnchor) {
                val mins = elapsed / 60000
                visitInfo = "Diam di titik ini ~$mins menit (perlu 5)"
            }
        }
    }

    private fun checkKnownPlace(lat: Double, lng: Double) {
        val match = cachedPlaces.firstOrNull { p ->
            distanceMeters(p.latitude, p.longitude, lat, lng) <= p.radiusMeters.toFloat()
        }
        if (match != null) {
            recognizedPlace = match.name
            currentPlaceId = match.id
            visitInfo = "✅ Kamu sedang di ${match.name}"
        } else if (pendingLat == null) {
            recognizedPlace = null
            currentPlaceId = null
        }
    }

    private fun handleVisit(lat: Double, lng: Double) {
        val match = cachedPlaces.firstOrNull { p ->
            distanceMeters(p.latitude, p.longitude, lat, lng) <= p.radiusMeters.toFloat()
        }
        if (match != null) {
            recognizedPlace = match.name
            currentPlaceId = match.id
            pendingLat = null
            pendingLng = null
            visitInfo = "✅ Dikenali: ${match.name}"
            recordVisit(match.id, match.name, anchorTime)
        } else {
            recognizedPlace = null
            pendingLat = lat
            pendingLng = lng
            visitInfo = "🆕 Tempat baru terdeteksi — kasih nama!"
        }
    }

    private fun markHere() {
        val lat = lastLat
        val lng = lastLng
        if (lat != null && lng != null) {
            if (anchorTime == 0L) anchorTime = System.currentTimeMillis()
            handleVisit(lat, lng)
        } else {
            visitInfo = "Lokasi belum siap, tunggu sebentar..."
        }
    }

    private fun savePlace(name: String) {
        val lat = pendingLat ?: lastLat ?: return
        val lng = pendingLng ?: lastLng ?: return
        lifecycleScope.launch {
            val id = placeDao.insert(Place(name = name, latitude = lat, longitude = lng, radiusMeters = 100.0))
            val visitId = activityDao.insert(
                ActivityRecord(
                    type = "visit",
                    startTime = if (anchorTime > 0L) anchorTime else System.currentTimeMillis(),
                    endTime = System.currentTimeMillis(),
                    placeId = id,
                    stepCount = todaySteps
                )
            )
            activeVisitId = visitId
            activeVisitPlaceId = id
            activeVisitPlaceName = name
            withContext(Dispatchers.Main) {
                recognizedPlace = name
                currentPlaceId = id
                pendingLat = null
                pendingLng = null
                visitInfo = "💾 Tersimpan: $name"
            }
        }
    }

    private fun recordVisit(placeId: Long, placeName: String, startTime: Long) {
        lifecycleScope.launch {
            val id = activityDao.insert(
                ActivityRecord(
                    type = "visit",
                    startTime = startTime,
                    endTime = System.currentTimeMillis(),
                    placeId = placeId,
                    stepCount = todaySteps
                )
            )
            activeVisitId = id
            activeVisitPlaceId = placeId
            activeVisitPlaceName = placeName
        }
    }

    private fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
        val out = FloatArray(1)
        Location.distanceBetween(lat1, lng1, lat2, lng2, out)
        return out[0]
    }

    // ================= TRANSAKSI & MEMORI =================

    private fun confirmVisitExpense(visitId: Long, placeId: Long?, amount: Double?) {
        val newSet = confirmedVisitIds + visitId
        confirmedVisitIds = newSet
        getSharedPreferences("pyora", MODE_PRIVATE).edit()
            .putStringSet("confirmed_visits", newSet.map { it.toString() }.toSet())
            .apply()
        if (amount != null && amount > 0 && placeId != null) {
            lifecycleScope.launch {
                txDao.insert(
                    MoneyTransaction(
                        amount = amount,
                        type = "expense",
                        category = null,
                        placeId = placeId,
                        timestamp = System.currentTimeMillis(),
                        note = "Konfirmasi kunjungan",
                        source = "auto"
                    )
                )
            }
        }
    }
    private fun addTransaction(amount: Double, type: String, category: String?, note: String?) {
        val placeId = currentPlaceId
        lifecycleScope.launch {
            txDao.insert(
                MoneyTransaction(
                    amount = amount,
                    type = type,
                    category = category,
                    placeId = placeId,
                    timestamp = System.currentTimeMillis(),
                    note = note,
                    source = if (placeId != null) "auto" else "manual"
                )
            )
        }
    }

    private fun addMemory(note: String?, photoPath: String?) {
        lifecycleScope.launch {
            memoryDao.insert(
                Memory(
                    timestamp = System.currentTimeMillis(),
                    photoUri = photoPath,
                    note = note
                )
            )
        }
    }

    // ================= SENSOR LANGKAH =================
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

    private fun createNotifChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Konfirmasi Pengeluaran",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Notifikasi saat kamu meninggalkan suatu tempat" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun expensePendingIntent(
        visitId: Long, placeId: Long?, amount: Double, requestCode: Int, mutable: Boolean
    ): PendingIntent {
        val intent = Intent(this, ExpenseReceiver::class.java).apply {
            action = ExpenseReceiver.ACTION_LOG
            putExtra(ExpenseReceiver.EXTRA_VISIT_ID, visitId)
            putExtra(ExpenseReceiver.EXTRA_PLACE_ID, placeId ?: -1L)
            putExtra(ExpenseReceiver.EXTRA_AMOUNT, amount)
            putExtra(ExpenseReceiver.EXTRA_NOTIF_ID, visitId.toInt())
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE)
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(this, requestCode, intent, flags)
    }

    private fun notifyDeparture(visitId: Long, placeId: Long?, placeName: String) {
        createNotifChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val notifId = visitId.toInt()
        val base = notifId * 10

        val remoteInput = RemoteInput.Builder(ExpenseReceiver.KEY_REPLY)
            .setLabel("Nominal (Rp)")
            .build()
        val replyAction = NotificationCompat.Action.Builder(
            0, "Isi nominal", expensePendingIntent(visitId, placeId, -1.0, base + 0, mutable = true)
        ).addRemoteInput(remoteInput).setAllowGeneratedReplies(false).build()

        val a5 = NotificationCompat.Action.Builder(
            0, "Rp5rb", expensePendingIntent(visitId, placeId, 5000.0, base + 1, false)
        ).build()
        val a10 = NotificationCompat.Action.Builder(
            0, "Rp10rb", expensePendingIntent(visitId, placeId, 10000.0, base + 2, false)
        ).build()
        val aNone = NotificationCompat.Action.Builder(
            0, "Gak ada", expensePendingIntent(visitId, placeId, 0.0, base + 3, false)
        ).build()

        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Habis berapa di $placeName?")
            .setContentText("Catat pengeluaranmu tadi langsung dari sini.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(replyAction)
            .addAction(a5)
            .addAction(a10)
            .addAction(aNone)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(notifId, notif)
        } catch (e: SecurityException) {
            // abaikan kalau izin notif belum ada
        }
    }

    private fun testDepartureNotification() {
        val pid = currentPlaceId
        val pname = recognizedPlace
        if (pid == null || pname == null) {
            visitInfo = "Harus di tempat tersimpan dulu (mis. rumah)"
            return
        }
        lifecycleScope.launch {
            val id = activityDao.insert(
                ActivityRecord(
                    type = "visit",
                    startTime = System.currentTimeMillis() - DWELL_TIME_MS,
                    endTime = System.currentTimeMillis(),
                    placeId = pid,
                    stepCount = todaySteps
                )
            )
            notifyDeparture(id, pid, pname)
        }
    }

    override fun onResume() {
        super.onResume()
        startStepSensor()
        if (wantTracking) beginLocationUpdates()
        getSharedPreferences("pyora", MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(prefsListener)
        val stored = getSharedPreferences("pyora", MODE_PRIVATE)
            .getStringSet("confirmed_visits", emptySet()) ?: emptySet()
        confirmedVisitIds = stored.mapNotNull { it.toLongOrNull() }.toSet()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        fusedClient.removeLocationUpdates(locationCallback)
        getSharedPreferences("pyora", MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(prefsListener)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            val totalSinceBoot = event.values[0].toInt()
            val today = LocalDate.now().toString()

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
            summaryDao.upsert(DailySummary(date = date, totalSteps = steps))
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

// ================= HELPER =================

private fun copyImageToInternal(context: Context, uri: Uri): String? {
    return try {
        val input = context.contentResolver.openInputStream(uri) ?: return null
        val file = File(context.filesDir, "memory_${System.currentTimeMillis()}.jpg")
        input.use { inp -> file.outputStream().use { out -> inp.copyTo(out) } }
        file.absolutePath
    } catch (e: Exception) {
        null
    }
}

private fun loadBitmap(path: String): Bitmap? {
    return try {
        val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
        BitmapFactory.decodeFile(path, opts)
    } catch (e: Exception) {
        null
    }
}

private fun millisToLocalDate(ms: Long): LocalDate =
    java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalDate()

private fun formatHourMinute(ms: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

private fun formatDuration(ms: Long): String {
    val totalMin = ms / 60000
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "$h jam $m menit" else "$m menit"
}

private fun formatDurationShort(ms: Long): String {
    val totalMin = ms / 60000
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "${h}j ${m}m" else "${m}m"
}

private fun formatDateHeader(date: LocalDate): String =
    date.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy", Locale("id", "ID")))

private fun formatTime(millis: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    return sdf.format(Date(millis))
}

private fun formatRupiah(amount: Double): String {
    val symbols = DecimalFormatSymbols(Locale.getDefault()).apply { groupingSeparator = '.' }
    val formatter = DecimalFormat("#,###", symbols)
    val absVal = if (amount < 0) -amount else amount
    val sign = if (amount < 0) "-" else ""
    return "${sign}Rp${formatter.format(absVal.toLong())}"
}

// ================= KOMPONEN REUSABLE =================

@Composable
fun PyoraCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .padding(16.dp),
        content = content
    )
}

@Composable
fun StatCard(modifier: Modifier = Modifier, label: String, value: String) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Neon)
        Spacer(Modifier.height(4.dp))
        Text(text = label, fontSize = 11.sp, color = TextGray)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
    )
}

@Composable
private fun pyoraFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = Neon,
    unfocusedBorderColor = TextGray,
    focusedLabelColor = Neon,
    unfocusedLabelColor = TextGray,
    cursorColor = Neon,
    focusedContainerColor = CardBg,
    unfocusedContainerColor = CardBg
)

@Composable
fun NeonButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Neon, contentColor = Color.Black),
        modifier = modifier
    ) {
        Text(text = text, fontWeight = FontWeight.Bold)
    }
}

// ================= TAB =================

@Composable
fun TabBar(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TabButton(label = "👟", active = selected == 0) { onSelect(0) }
        TabButton(label = "💰", active = selected == 1) { onSelect(1) }
        TabButton(label = "📸", active = selected == 2) { onSelect(2) }
        TabButton(label = "📍", active = selected == 3) { onSelect(3) }
        TabButton(label = "🗓️", active = selected == 4) { onSelect(4) }
    }
}

@Composable
fun RowScope.TabButton(label: String, active: Boolean, onClick: () -> Unit) {
    if (active) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = Neon, contentColor = Color.Black),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
            modifier = Modifier.weight(1f)
        ) { Text(label, fontSize = 18.sp) }
    } else {
        OutlinedButton(
            onClick = onClick,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Neon),
            border = BorderStroke(1.dp, Neon),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
            modifier = Modifier.weight(1f)
        ) { Text(label, fontSize = 18.sp) }
    }
}

// ================= LANGKAH =================

@Composable
fun StepScreen(
    steps: Int,
    sensorAvailable: Boolean,
    history: List<DailySummary>
) {
    val last7 = history.take(7)
    val weeklyTotal = last7.sumOf { it.totalSteps }
    val avgPerDay = if (last7.isEmpty()) 0 else weeklyTotal / last7.size
    val bestDay = history.maxByOrNull { it.totalSteps }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))

        if (sensorAvailable) {
            StepRing(steps = steps, goal = DAILY_GOAL)
        } else {
            Text(text = "⚠️ HP ini nggak punya sensor step counter.", color = Color.White)
        }

        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(Modifier.weight(1f), "Total 7 hari", "$weeklyTotal")
            StatCard(Modifier.weight(1f), "Rata-rata", "$avgPerDay")
            StatCard(Modifier.weight(1f), "Terbaik", if (bestDay != null) "${bestDay.totalSteps}" else "-")
        }

        Spacer(Modifier.height(24.dp))
        SectionTitle("📊 Riwayat")

        if (history.isEmpty()) {
            Text(text = "Belum ada data tersimpan.", color = TextGray)
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(history) { item ->
                    PyoraCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = item.date, color = Color.White, fontSize = 15.sp)
                            Text(text = "${item.totalSteps} langkah", color = Neon, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StepRing(steps: Int, goal: Int) {
    val progress = (steps.toFloat() / goal).coerceIn(0f, 1f)
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(230.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = 22.dp.toPx()
            val inset = strokePx / 2
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            drawArc(
                color = Color(0xFF2A2A2A),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
            drawArc(
                color = Neon,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "$steps", fontSize = 52.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(text = "langkah", fontSize = 14.sp, color = TextGray)
            Spacer(Modifier.height(6.dp))
            Text(text = "${(progress * 100).toInt()}% dari $goal", fontSize = 12.sp, color = Neon)
        }
    }
}

// ================= KEUANGAN =================

@Composable
fun FinanceScreen(
    transactions: List<MoneyTransaction>,
    currentPlaceName: String?,
    places: List<Place>,
    onAdd: (Double, String, String?, String?) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("expense") }

    val placeMap = remember(places) { places.associateBy { it.id } }
    val totalIncome = transactions.filter { it.type == "income" }.sumOf { it.amount }
    val totalExpense = transactions.filter { it.type == "expense" }.sumOf { it.amount }
    val balance = totalIncome - totalExpense

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(24.dp)
    ) {
        Text(text = "💰 Keuangan", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(Modifier.weight(1f), "Pemasukan", formatRupiah(totalIncome))
            StatCard(Modifier.weight(1f), "Pengeluaran", formatRupiah(totalExpense))
            StatCard(Modifier.weight(1f), "Saldo", formatRupiah(balance))
        }
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = amountText,
            onValueChange = { input -> amountText = input.filter { it.isDigit() } },
            label = { Text("Nominal (Rp)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = pyoraFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = category,
            onValueChange = { category = it },
            label = { Text("Kategori (mis. makan, transport)") },
            colors = pyoraFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Catatan (opsional)") },
            colors = pyoraFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TypeButton(label = "Pengeluaran", active = type == "expense") { type = "expense" }
            TypeButton(label = "Pemasukan", active = type == "income") { type = "income" }
        }
        Spacer(Modifier.height(12.dp))

        if (currentPlaceName != null) {
            Text(
                text = "📍 Otomatis ditandai di: $currentPlaceName",
                color = Neon,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
        }

        NeonButton(text = "Tambah Transaksi", modifier = Modifier.fillMaxWidth()) {
            val amount = amountText.toDoubleOrNull()
            if (amount != null && amount > 0) {
                onAdd(amount, type, category.ifBlank { null }, note.ifBlank { null })
                amountText = ""
                category = ""
                note = ""
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionTitle("📋 Riwayat Transaksi")

        if (transactions.isEmpty()) {
            Text(text = "Belum ada transaksi.", color = TextGray)
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(transactions) { tx ->
                    val isIncome = tx.type == "income"
                    PyoraCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(text = tx.category ?: "Lainnya", color = Color.White, fontSize = 15.sp)
                                if (!tx.note.isNullOrBlank()) {
                                    Text(text = tx.note, color = TextGray, fontSize = 12.sp)
                                }
                                tx.placeId?.let { pid ->
                                    Text(
                                        text = "📍 ${placeMap[pid]?.name ?: "Tempat"}",
                                        color = Neon,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Text(
                                text = (if (isIncome) "+ " else "- ") + formatRupiah(tx.amount),
                                color = if (isIncome) Neon else ExpenseRed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TypeButton(label: String, active: Boolean, onClick: () -> Unit) {
    if (active) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = Neon, contentColor = Color.Black)
        ) { Text(label) }
    } else {
        OutlinedButton(
            onClick = onClick,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Neon),
            border = BorderStroke(1.dp, Neon)
        ) { Text(label) }
    }
}

// ================= MEMORI / KENANGAN =================

@Composable
fun MemoryScreen(
    memories: List<Memory>,
    onAdd: (String?, String?) -> Unit
) {
    val context = LocalContext.current
    var note by remember { mutableStateOf("") }
    var photoPath by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val path = copyImageToInternal(context, uri)
            if (path != null) photoPath = path
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(24.dp)
    ) {
        Text(text = "📸 Kenangan", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Tulis kenangan / catatan...") },
            colors = pyoraFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Neon),
                border = BorderStroke(1.dp, Neon)
            ) {
                Text(if (photoPath == null) "📷 Pilih Foto" else "✅ Foto siap")
            }
            NeonButton(text = "Simpan") {
                if (note.isNotBlank() || photoPath != null) {
                    onAdd(note.ifBlank { null }, photoPath)
                    note = ""
                    photoPath = null
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionTitle("🗂️ Galeri Kenangan")

        if (memories.isEmpty()) {
            Text(text = "Belum ada kenangan tersimpan.", color = TextGray)
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(memories) { memory ->
                    MemoryCard(memory)
                }
            }
        }
    }
}

@Composable
fun MemoryCard(memory: Memory) {
    PyoraCard {
        Text(text = formatTime(memory.timestamp), fontSize = 12.sp, color = Neon)
        if (!memory.note.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(text = memory.note, fontSize = 16.sp, color = Color.White)
        }
        memory.photoUri?.let { path ->
            val bitmap = remember(path) { loadBitmap(path) }
            if (bitmap != null) {
                Spacer(Modifier.height(10.dp))
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            }
        }
    }
}

// ================= LOKASI / TEMPAT =================

@Composable
fun LocationScreen(
    place: String,
    coords: String,
    isTracking: Boolean,
    recognizedPlace: String?,
    pendingNewPlace: Boolean,
    visitInfo: String,
    savedPlaces: List<Place>,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onMarkNow: () -> Unit,
    onTestNotif: () -> Unit,
    onSavePlace: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(text = "📍 Tempat Saat Ini", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(20.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(CardBg)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isTracking) "🟢 Melacak lokasi..." else "⚪ Pelacakan nonaktif",
                color = if (isTracking) Neon else TextGray,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(14.dp))
            if (recognizedPlace != null) {
                Text(
                    text = "✅ $recognizedPlace",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Neon,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(text = place, fontSize = 13.sp, color = TextGray, textAlign = TextAlign.Center)
            } else {
                Text(
                    text = place,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(text = coords, fontSize = 13.sp, color = TextGray)
            if (visitInfo.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(text = visitInfo, fontSize = 12.sp, color = Neon, textAlign = TextAlign.Center)
            }
        }

        Spacer(Modifier.height(16.dp))

        if (pendingNewPlace) {
            var name by remember { mutableStateOf("") }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardBg)
                    .padding(16.dp)
            ) {
                Text(text = "🆕 Tempat baru terdeteksi", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                Text(text = "Kasih nama biar Pyora inget selamanya:", color = TextGray, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama tempat (mis. Rumah, Kampus)") },
                    colors = pyoraFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                NeonButton(text = "Simpan Tempat", modifier = Modifier.fillMaxWidth()) {
                    if (name.isNotBlank()) {
                        onSavePlace(name.trim())
                        name = ""
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (!isTracking) {
            NeonButton(text = "▶️  Mulai Lacak Lokasi", modifier = Modifier.fillMaxWidth()) { onStart() }
        } else {
            OutlinedButton(
                onClick = onStop,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Neon),
                border = BorderStroke(1.dp, Neon),
                modifier = Modifier.fillMaxWidth()
            ) { Text("⏹️  Stop Pelacakan") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onMarkNow,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Neon),
                border = BorderStroke(1.dp, Neon),
                modifier = Modifier.fillMaxWidth()
            ) { Text("🔖  Tandai Tempat Ini Sekarang") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onTestNotif,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Neon),
                border = BorderStroke(1.dp, Neon),
                modifier = Modifier.fillMaxWidth()
            ) { Text("🔔  Tes Notifikasi Pergi") }
        }

        Spacer(Modifier.height(20.dp))
        SectionTitle("🗺️ Tempat Tersimpan (${savedPlaces.size})")
        if (savedPlaces.isEmpty()) {
            Text(text = "Belum ada tempat tersimpan.", color = TextGray)
        } else {
            savedPlaces.forEach { p ->
                PyoraCard {
                    Text(text = p.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "%.5f, %.5f".format(p.latitude, p.longitude),
                        color = TextGray,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = "Pyora otomatis mendeteksi saat kamu diam ≥5 menit di suatu tempat. Tempat baru akan diminta namanya, lalu dikenali otomatis di kunjungan berikutnya.",
            fontSize = 13.sp,
            color = TextGray
        )
    }
}
// ================= LINIMASA / TIMELINE =================

@Composable
fun TimelineScreen(
    activities: List<ActivityRecord>,
    places: List<Place>,
    transactions: List<MoneyTransaction>,
    confirmedVisitIds: Set<Long>,
    onConfirm: (Long, Long?, Double?) -> Unit
) {
    val today = LocalDate.now()
    val placeMap = remember(places) { places.associateBy { it.id } }
    val todayVisits = activities
        .filter { it.type == "visit" && millisToLocalDate(it.startTime) == today }
        .sortedBy { it.startTime }
    val todayExpenses = transactions
        .filter { it.type == "expense" && millisToLocalDate(it.timestamp) == today }

    val pending = todayVisits.filter { it.id !in confirmedVisitIds }.reversed()

    val totalPlaces = todayVisits.mapNotNull { it.placeId }.distinct().size
    val totalDurationMs = todayVisits.sumOf { (it.endTime - it.startTime).coerceAtLeast(0L) }

    val placeIdsToday = todayVisits.mapNotNull { it.placeId }.distinct()
    val perPlace = placeIdsToday.map { pid ->
        val name = placeMap[pid]?.name ?: "Tempat tak dikenal"
        val dur = todayVisits.filter { it.placeId == pid }.sumOf { (it.endTime - it.startTime).coerceAtLeast(0L) }
        val exp = todayExpenses.filter { it.placeId == pid }.sumOf { it.amount }
        Triple(name, dur, exp)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(text = "🗓️ Linimasa Hari Ini", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(6.dp))
        Text(text = formatDateHeader(today), fontSize = 13.sp, color = TextGray)
        Spacer(Modifier.height(18.dp))

        if (pending.isNotEmpty()) {
            SectionTitle("⏳ Perlu Dikonfirmasi (${pending.size})")
            pending.forEach { visit ->
                PendingExpenseCard(
                    placeName = placeMap[visit.placeId]?.name ?: "Tempat tak dikenal",
                    visit = visit,
                    onConfirm = onConfirm
                )
            }
            Spacer(Modifier.height(22.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(Modifier.weight(1f), "Tempat", "$totalPlaces")
            StatCard(Modifier.weight(1f), "Durasi", formatDurationShort(totalDurationMs))
            StatCard(Modifier.weight(1f), "Kunjungan", "${todayVisits.size}")
        }

        Spacer(Modifier.height(22.dp))
        SectionTitle("💰 Rekap per Tempat")
        if (perPlace.isEmpty()) {
            Text(text = "Belum ada data.", fontSize = 14.sp, color = TextGray)
        } else {
            perPlace.forEach { (name, dur, exp) ->
                PyoraCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(2.dp))
                            Text(text = "⏱️ ${formatDuration(dur)}", color = TextGray, fontSize = 12.sp)
                        }
                        Text(
                            text = if (exp > 0) "- ${formatRupiah(exp)}" else "Rp0",
                            color = if (exp > 0) ExpenseRed else TextGray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        SectionTitle("📍 Perjalananmu")
        if (todayVisits.isEmpty()) {
            Text(
                text = "Belum ada kunjungan tercatat hari ini.\n\nDiam ≥5 menit di suatu tempat, atau buka tab 📍 lalu tap \"Tandai Tempat Ini Sekarang\" buat nyoba.",
                fontSize = 14.sp,
                color = TextGray
            )
        } else {
            todayVisits.forEach { visit ->
                TimelineItem(
                    visit = visit,
                    placeName = placeMap[visit.placeId]?.name ?: "Tempat tak dikenal"
                )
            }
        }
    }
}

@Composable
fun PendingExpenseCard(
    placeName: String,
    visit: ActivityRecord,
    onConfirm: (Long, Long?, Double?) -> Unit
) {
    var custom by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .padding(16.dp)
    ) {
        Text(text = "Habis berapa di $placeName?", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(
            text = "🕐 ${formatHourMinute(visit.startTime)} – ${formatHourMinute(visit.endTime)}",
            color = TextGray,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChipButton("Rp5rb", Modifier.weight(1f)) { onConfirm(visit.id, visit.placeId, 5000.0) }
            ChipButton("Rp10rb", Modifier.weight(1f)) { onConfirm(visit.id, visit.placeId, 10000.0) }
            ChipButton("Rp20rb", Modifier.weight(1f)) { onConfirm(visit.id, visit.placeId, 20000.0) }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = custom,
                onValueChange = { input -> custom = input.filter { it.isDigit() } },
                label = { Text("Nominal lain") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = pyoraFieldColors(),
                modifier = Modifier.weight(1f)
            )
            NeonButton(text = "OK") {
                val amt = custom.toDoubleOrNull()
                if (amt != null && amt > 0) {
                    onConfirm(visit.id, visit.placeId, amt)
                    custom = ""
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { onConfirm(visit.id, visit.placeId, null) },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextGray),
            border = BorderStroke(1.dp, TextGray),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Gak ada pengeluaran") }
    }
}

@Composable
fun RowScope.ChipButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Neon),
        border = BorderStroke(1.dp, Neon),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
        modifier = modifier
    ) { Text(label, fontSize = 13.sp) }
}

@Composable
fun TimelineItem(visit: ActivityRecord, placeName: String) {
    val durMs = (visit.endTime - visit.startTime).coerceAtLeast(0L)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
    ) {
        Column(
            modifier = Modifier.width(52.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = formatHourMinute(visit.startTime), color = Neon, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Neon)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardBg)
                .padding(16.dp)
        ) {
            Text(text = placeName, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "🕐 ${formatHourMinute(visit.startTime)} – ${formatHourMinute(visit.endTime)} · ${formatDuration(durMs)}",
                color = TextGray,
                fontSize = 13.sp
            )
        }
    }
}
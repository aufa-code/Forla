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
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.aufa.pyora.data.AppDatabase
import com.aufa.pyora.data.DailySummary
import com.aufa.pyora.data.DailySummaryDao
import com.aufa.pyora.data.Memory
import com.aufa.pyora.data.MemoryDao
import com.aufa.pyora.data.MoneyTransaction
import com.aufa.pyora.data.TransactionDao
import com.aufa.pyora.ui.theme.PyoraTheme
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    private lateinit var summaryDao: DailySummaryDao
    private lateinit var txDao: TransactionDao
    private lateinit var memoryDao: MemoryDao

    private var todaySteps by mutableIntStateOf(0)
    private var sensorAvailable by mutableStateOf(true)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startStepSensor()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = AppDatabase.getInstance(this)
        summaryDao = db.dailySummaryDao()
        txDao = db.transactionDao()
        memoryDao = db.memoryDao()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        sensorAvailable = stepSensor != null

        setContent {
            PyoraTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var tab by remember { mutableStateOf(0) }
                    val stepHistory by remember { summaryDao.getAll() }.collectAsState(initial = emptyList())
                    val txHistory by remember { txDao.getAll() }.collectAsState(initial = emptyList())
                    val memoryHistory by remember { memoryDao.getAll() }.collectAsState(initial = emptyList())

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
                                onAdd = { amount, type, category, note ->
                                    addTransaction(amount, type, category, note)
                                }
                            )
                            else -> MemoryScreen(
                                memories = memoryHistory,
                                onAdd = { note, photoPath ->
                                    addMemory(note, photoPath)
                                }
                            )
                        }
                    }
                }
            }
        }

        ensurePermissionAndStart()
    }

    private fun addTransaction(amount: Double, type: String, category: String?, note: String?) {
        lifecycleScope.launch {
            txDao.insert(
                MoneyTransaction(
                    amount = amount,
                    type = type,
                    category = category,
                    timestamp = System.currentTimeMillis(),
                    note = note
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

private fun formatTime(millis: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    return sdf.format(Date(millis))
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
        TabButton(label = "👟 Langkah", active = selected == 0) { onSelect(0) }
        TabButton(label = "💰 Uang", active = selected == 1) { onSelect(1) }
        TabButton(label = "📸 Memori", active = selected == 2) { onSelect(2) }
    }
}

@Composable
fun RowScope.TabButton(label: String, active: Boolean, onClick: () -> Unit) {
    if (active) {
        Button(onClick = onClick, modifier = Modifier.weight(1f)) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = Modifier.weight(1f)) { Text(label) }
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
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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

        Spacer(Modifier.height(28.dp))

        Text(
            text = "📈 Insight Mingguan",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            InsightItem(label = "Total 7 hari", value = "$weeklyTotal")
            InsightItem(label = "Rata-rata/hari", value = "$avgPerDay")
            InsightItem(
                label = "Hari terbaik",
                value = if (bestDay != null) "${bestDay.totalSteps}" else "-"
            )
        }

        Spacer(Modifier.height(28.dp))

        Text(
            text = "📊 Riwayat",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start)
        )
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

@Composable
fun InsightItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(text = label, fontSize = 12.sp)
    }
}

// ================= KEUANGAN =================

@Composable
fun FinanceScreen(
    transactions: List<MoneyTransaction>,
    onAdd: (Double, String, String?, String?) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("expense") }

    val totalIncome = transactions.filter { it.type == "income" }.sumOf { it.amount }
    val totalExpense = transactions.filter { it.type == "expense" }.sumOf { it.amount }
    val balance = totalIncome - totalExpense

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(text = "💰 Keuangan", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            InsightItem(label = "Pemasukan", value = "Rp${totalIncome.toLong()}")
            InsightItem(label = "Pengeluaran", value = "Rp${totalExpense.toLong()}")
            InsightItem(label = "Saldo", value = "Rp${balance.toLong()}")
        }
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = amountText,
            onValueChange = { input -> amountText = input.filter { it.isDigit() } },
            label = { Text("Nominal (Rp)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = category,
            onValueChange = { category = it },
            label = { Text("Kategori (mis. makan, transport)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Catatan (opsional)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TypeButton(label = "Pengeluaran", active = type == "expense") { type = "expense" }
            TypeButton(label = "Pemasukan", active = type == "income") { type = "income" }
        }
        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                val amount = amountText.toDoubleOrNull()
                if (amount != null && amount > 0) {
                    onAdd(amount, type, category.ifBlank { null }, note.ifBlank { null })
                    amountText = ""
                    category = ""
                    note = ""
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Tambah Transaksi")
        }

        Spacer(Modifier.height(20.dp))
        Text(text = "📋 Riwayat Transaksi", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        if (transactions.isEmpty()) {
            Text(text = "Belum ada transaksi.", fontSize = 14.sp)
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(transactions) { tx ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = tx.category ?: "Lainnya", fontSize = 16.sp)
                            if (!tx.note.isNullOrBlank()) {
                                Text(text = tx.note, fontSize = 12.sp)
                            }
                        }
                        Text(
                            text = (if (tx.type == "income") "+ Rp" else "- Rp") + "${tx.amount.toLong()}",
                            fontSize = 16.sp
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun TypeButton(label: String, active: Boolean, onClick: () -> Unit) {
    if (active) {
        Button(onClick = onClick) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
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
            .padding(24.dp)
    ) {
        Text(text = "📸 Kenangan", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Tulis kenangan / catatan...") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                picker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }) {
                Text(if (photoPath == null) "📷 Pilih Foto" else "✅ Foto siap")
            }
            Button(
                onClick = {
                    if (note.isNotBlank() || photoPath != null) {
                        onAdd(note.ifBlank { null }, photoPath)
                        note = ""
                        photoPath = null
                    }
                }
            ) {
                Text("Simpan Kenangan")
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(text = "🗂️ Galeri Kenangan", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        if (memories.isEmpty()) {
            Text(text = "Belum ada kenangan tersimpan.", fontSize = 14.sp)
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(text = formatTime(memory.timestamp), fontSize = 12.sp)
        if (!memory.note.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(text = memory.note, fontSize = 16.sp)
        }
        memory.photoUri?.let { path ->
            val bitmap = remember(path) { loadBitmap(path) }
            if (bitmap != null) {
                Spacer(Modifier.height(8.dp))
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
    }
}
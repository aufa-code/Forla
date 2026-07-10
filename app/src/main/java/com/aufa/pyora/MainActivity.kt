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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.aufa.pyora.data.AppDatabase
import com.aufa.pyora.data.DailySummary
import com.aufa.pyora.data.DailySummaryDao
import com.aufa.pyora.data.FoodDao
import com.aufa.pyora.data.FoodEntry
import com.aufa.pyora.data.ProfileDao
import com.aufa.pyora.data.UserProfile
import com.aufa.pyora.data.WeightDao
import com.aufa.pyora.data.WeightEntry
import com.aufa.pyora.ui.theme.ForlaBg
import com.aufa.pyora.ui.theme.ForlaCalorie
import com.aufa.pyora.ui.theme.ForlaCarbs
import com.aufa.pyora.ui.theme.ForlaCard
import com.aufa.pyora.ui.theme.ForlaCardAlt
import com.aufa.pyora.ui.theme.ForlaDeficit
import com.aufa.pyora.ui.theme.ForlaFat
import com.aufa.pyora.ui.theme.ForlaPrimary
import com.aufa.pyora.ui.theme.ForlaProtein
import com.aufa.pyora.ui.theme.ForlaSurplus
import com.aufa.pyora.ui.theme.ForlaText
import com.aufa.pyora.ui.theme.ForlaTextSub
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.composed
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast

// ================= KONSTAN =================
private const val STEP_GOAL = 8000
private const val STEP_KCAL = 0.04          // ~0.04 kkal per langkah
private const val DEFAULT_BMR = 1600.0
private const val DEFAULT_TARGET_CAL = 2500

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null

    private lateinit var summaryDao: DailySummaryDao
    private lateinit var foodDao: FoodDao
    private lateinit var weightDao: WeightDao
    private lateinit var profileDao: ProfileDao

    private var todaySteps by mutableIntStateOf(0)
    private var sensorAvailable by mutableStateOf(true)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) startStepSensor() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AppDatabase.getInstance(this)
        summaryDao = db.dailySummaryDao()
        foodDao = db.foodDao()
        weightDao = db.weightDao()
        profileDao = db.profileDao()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        sensorAvailable = stepSensor != null

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = ForlaBg, surface = ForlaBg)) {
                Surface(modifier = Modifier.fillMaxSize(), color = ForlaBg) {
                    var tab by remember { mutableStateOf(0) }

                    val zone = ZoneId.systemDefault()
                    val today = LocalDate.now()
                    val startOfDay = today.atStartOfDay(zone).toInstant().toEpochMilli()
                    val endOfDay = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

                    val todayFoods by remember { foodDao.getBetween(startOfDay, endOfDay) }
                        .collectAsState(initial = emptyList())
                    val allFoods by remember { foodDao.getAll() }.collectAsState(initial = emptyList())
                    val profile by remember { profileDao.get() }.collectAsState(initial = null)
                    val weights by remember { weightDao.getAll() }.collectAsState(initial = emptyList())

                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(1f)) {
                            when (tab) {
                                0 -> DashboardScreen(todayFoods, profile, todaySteps, sensorAvailable)
                                1 -> AddFoodScreen(todayFoods, onAdd = { addFood(it) }, onDelete = { deleteFood(it) })
                                2 -> ActivityScreen(todaySteps, sensorAvailable, profile)
                                3 -> ProgressScreen(weights, allFoods, onAddWeight = { addWeight(it) })
                                else -> ProfileScreen(profile, onSave = { saveProfile(it) })
                            }
                        }
                        ForlaTabBar(selected = tab, onSelect = { tab = it })
                    }
                }
            }
        }

        ensurePermissionAndStart()
    }

    // ================= AKSI DB =================
    private fun addFood(entry: FoodEntry) { lifecycleScope.launch { foodDao.insert(entry) } }
    private fun deleteFood(entry: FoodEntry) { lifecycleScope.launch { foodDao.delete(entry) } }
    private fun addWeight(entry: WeightEntry) { lifecycleScope.launch { weightDao.insert(entry) } }
    private fun saveProfile(p: UserProfile) { lifecycleScope.launch { profileDao.upsert(p) } }

    // ================= SENSOR LANGKAH =================
    private fun ensurePermissionAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) startStepSensor()
            else permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        } else startStepSensor()
    }

    private fun startStepSensor() {
        stepSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
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
            val prefs = getSharedPreferences("forla", MODE_PRIVATE)
            val baselineKey = "baseline_$today"
            if (!prefs.contains(baselineKey)) {
                prefs.edit().putInt(baselineKey, totalSinceBoot).apply()
            }
            val baseline = prefs.getInt(baselineKey, totalSinceBoot)
            val steps = (totalSinceBoot - baseline).coerceAtLeast(0)
            todaySteps = steps
            lifecycleScope.launch { summaryDao.upsert(DailySummary(date = today, totalSteps = steps)) }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

// ================= HITUNGAN =================
fun computeBmr(p: UserProfile): Double {
    val base = 10 * p.weightKg + 6.25 * p.heightCm - 5 * p.age
    return if (p.gender == "female") base - 161 else base + 5
}

fun activityFactor(level: String): Double = when (level) {
    "sedentary" -> 1.2
    "light" -> 1.375
    "moderate" -> 1.55
    "active" -> 1.725
    "veryActive" -> 1.9
    else -> 1.55
}

// ================= KOMPONEN REUSABLE =================
@Composable
fun ForlaCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ForlaCard)
            .padding(16.dp),
        content = content
    )
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = ForlaText,
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp, top = 4.dp)
    )
}

@Composable
fun forlaFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = ForlaText,
    unfocusedTextColor = ForlaText,
    focusedBorderColor = ForlaPrimary,
    unfocusedBorderColor = ForlaTextSub,
    focusedLabelColor = ForlaPrimary,
    unfocusedLabelColor = ForlaTextSub,
    cursorColor = ForlaPrimary,
    focusedContainerColor = ForlaCard,
    unfocusedContainerColor = ForlaCard
)

@Composable
fun ForlaButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = ForlaPrimary, contentColor = Color.Black),
        modifier = modifier
    ) { Text(text, fontWeight = FontWeight.Bold) }
}

@Composable
fun StatCard(modifier: Modifier = Modifier, label: String, value: String, color: Color = ForlaPrimary) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).background(ForlaCard)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
        Spacer(Modifier.height(4.dp))
        Text(text = label, fontSize = 11.sp, color = ForlaTextSub)
    }
}

@Composable
fun ChoiceChip(label: String, active: Boolean, onClick: () -> Unit) {
    if (active) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = ForlaPrimary, contentColor = Color.Black),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) { Text(label, fontSize = 13.sp) }
    } else {
        OutlinedButton(
            onClick = onClick,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ForlaPrimary),
            border = BorderStroke(1.dp, ForlaPrimary),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) { Text(label, fontSize = 13.sp) }
    }
}

// ================= TAB BAR =================
@Composable
fun ForlaTabBar(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(ForlaCard).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TabButton("🎯", "Beranda", selected == 0) { onSelect(0) }
        TabButton("📷", "Catat", selected == 1) { onSelect(1) }
        TabButton("👟", "Aktivitas", selected == 2) { onSelect(2) }
        TabButton("📈", "Progress", selected == 3) { onSelect(3) }
        TabButton("⚙️", "Profil", selected == 4) { onSelect(4) }
    }
}

@Composable
fun RowScope.TabButton(icon: String, label: String, active: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
            .background(if (active) ForlaPrimary else Color.Transparent)
            .clickableNoRipple(onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(icon, fontSize = 18.sp)
        Text(
            label,
            fontSize = 10.sp,
            color = if (active) Color.Black else ForlaTextSub,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// helper klik tanpa ripple biar simpel
@Composable
// helper klik tanpa ripple biar simpel
fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )
}

// ================= 0. DASHBOARD =================
@Composable
fun DashboardScreen(
    todayFoods: List<FoodEntry>,
    profile: UserProfile?,
    steps: Int,
    sensorAvailable: Boolean
) {
    val caloriesIn = todayFoods.sumOf { it.calories }
    val proteinIn = todayFoods.sumOf { it.protein }
    val fatIn = todayFoods.sumOf { it.fat }
    val carbsIn = todayFoods.sumOf { it.carbs }

    val targetCal = profile?.targetCalories?.takeIf { it > 0 }?.toInt() ?: DEFAULT_TARGET_CAL
    val targetP = profile?.targetProtein?.takeIf { it > 0 } ?: 150.0
    val targetF = profile?.targetFat?.takeIf { it > 0 } ?: 70.0
    val targetC = profile?.targetCarbs?.takeIf { it > 0 } ?: 300.0

    val bmr = if (profile != null && profile.weightKg > 0) computeBmr(profile) else DEFAULT_BMR
    val stepBurn = steps * STEP_KCAL
    val caloriesOut = bmr + stepBurn
    val net = caloriesIn - caloriesOut

    Column(
        modifier = Modifier.fillMaxSize().background(ForlaBg)
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        Text("Forla", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ForlaPrimary)
        Text("Macro & Calorie Tracker", fontSize = 12.sp, color = ForlaTextSub)
        Spacer(Modifier.height(16.dp))

        CalorieRing(caloriesIn.toInt(), targetCal)
        Spacer(Modifier.height(20.dp))

        // Net surplus / defisit
        val netInt = net.toInt()
        val isSurplus = netInt >= 0
        ForlaCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(if (isSurplus) "Surplus Hari Ini" else "Defisit Hari Ini",
                        color = ForlaTextSub, fontSize = 13.sp)
                    Text(
                        (if (isSurplus) "+" else "") + "$netInt kkal",
                        color = if (isSurplus) ForlaSurplus else ForlaDeficit,
                        fontSize = 22.sp, fontWeight = FontWeight.Bold
                    )
                }
                Text(if (isSurplus) "💪" else "🔻", fontSize = 30.sp)
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(Modifier.weight(1f), "Masuk", "${caloriesIn.toInt()} kkal", ForlaCalorie)
            StatCard(Modifier.weight(1f), "Keluar", "${caloriesOut.toInt()} kkal", ForlaTextSub)
            StatCard(Modifier.weight(1f), "Langkah", if (sensorAvailable) "$steps" else "-", ForlaPrimary)
        }

        Spacer(Modifier.height(20.dp))
        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(ForlaCard).padding(16.dp)) {
            Text("Makronutrien", color = ForlaText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            MacroBar("Protein", proteinIn, targetP, ForlaProtein)
            MacroBar("Karbohidrat", carbsIn, targetC, ForlaCarbs)
            MacroBar("Lemak", fatIn, targetF, ForlaFat)
        }

        Spacer(Modifier.height(16.dp))
        if (todayFoods.isEmpty()) {
            Text("Belum ada makanan tercatat hari ini.\nBuka tab 📷 Catat buat mulai.",
                color = ForlaTextSub, fontSize = 13.sp)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun CalorieRing(caloriesIn: Int, target: Int) {
    val progress = if (target > 0) (caloriesIn.toFloat() / target).coerceIn(0f, 1f) else 0f
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = 22.dp.toPx()
            val inset = strokePx / 2
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            drawArc(ForlaCardAlt, -90f, 360f, false, Offset(inset, inset), arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round))
            drawArc(ForlaCalorie, -90f, 360f * progress, false, Offset(inset, inset), arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$caloriesIn", fontSize = 46.sp, fontWeight = FontWeight.Bold, color = ForlaText)
            Text("dari $target kkal", fontSize = 13.sp, color = ForlaTextSub)
            Spacer(Modifier.height(4.dp))
            Text("${(progress * 100).toInt()}%", fontSize = 12.sp, color = ForlaPrimary)
        }
    }
}

@Composable
fun MacroBar(label: String, current: Double, target: Double, color: Color) {
    val pct = if (target > 0) (current / target).coerceIn(0.0, 1.0).toFloat() else 0f
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = ForlaText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text("${current.toInt()} / ${target.toInt()} g", color = ForlaTextSub, fontSize = 12.sp)
        }
        Spacer(Modifier.height(4.dp))
        Box(modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(ForlaCardAlt)) {
            Box(modifier = Modifier.fillMaxWidth(pct).height(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
        }
    }
}

// ================= 1. CATAT MAKAN (foto + AI) =================
@Composable
fun AddFoodScreen(
    todayFoods: List<FoodEntry>,
    onAdd: (FoodEntry) -> Unit,
    onDelete: (FoodEntry) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var takaran by remember { mutableStateOf("") }
    var isDrink by remember { mutableStateOf(false) }
    var volume by remember { mutableStateOf("") }
    var cal by remember { mutableStateOf("") }
    var pro by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var carb by remember { mutableStateOf("") }
    var meal by remember { mutableStateOf("breakfast") }

    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var analyzing by remember { mutableStateOf(false) }
    var aiMsg by remember { mutableStateOf<String?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bmp -> if (bmp != null) { photo = bmp; aiMsg = null } }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) { photo = uriToBitmap(context, uri); aiMsg = null } }

    fun runAi() {
        val bmp = photo ?: return
        analyzing = true; aiMsg = null
        scope.launch {
            try {
                val res = GeminiHelper.analyzeFood(BuildConfig.GEMINI_API_KEY, bmp, takaran)
                if (name.isBlank()) name = res.name
                cal = res.calories.toInt().toString()
                pro = res.protein.toInt().toString()
                fat = res.fat.toInt().toString()
                carb = res.carbs.toInt().toString()
                aiMsg = "✅ Terisi dari AI — cek & koreksi kalau perlu."
            } catch (e: Exception) {
                aiMsg = "⚠️ Gagal analisis: ${e.message}"
            } finally {
                analyzing = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(ForlaBg)
            .verticalScroll(rememberScrollState()).padding(20.dp)
    ) {
        Text("📷 Catat Makanan", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ForlaText)
        Text("Foto + takaran → AI isi otomatis.", fontSize = 12.sp, color = ForlaTextSub)
        Spacer(Modifier.height(16.dp))

        // ---- Foto ----
        if (photo != null) {
            Image(
                bitmap = photo!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp))
            )
            Spacer(Modifier.height(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { cameraLauncher.launch(null) },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ForlaPrimary),
                border = BorderStroke(1.dp, ForlaPrimary),
                modifier = Modifier.weight(1f)
            ) { Text("📷 Kamera") }
            OutlinedButton(
                onClick = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ForlaPrimary),
                border = BorderStroke(1.dp, ForlaPrimary),
                modifier = Modifier.weight(1f)
            ) { Text("🖼️ Galeri") }
        }

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(takaran, { takaran = it },
            label = { Text("Takaran (mis. 3 centong nasi, 2 dada ayam)") },
            colors = forlaFieldColors(), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))

        if (photo != null) {
            if (analyzing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = ForlaPrimary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Menganalisis makanan...", color = ForlaTextSub, fontSize = 13.sp)
                }
            } else {
                ForlaButton("🤖 Analisis dengan AI", modifier = Modifier.fillMaxWidth()) { runAi() }
            }
        }
        aiMsg?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = if (it.startsWith("✅")) ForlaSurplus else ForlaDeficit, fontSize = 12.sp)
        }

        Spacer(Modifier.height(14.dp))
        OutlinedTextField(name, { name = it }, label = { Text("Nama makanan") },
            colors = forlaFieldColors(), modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip("🍚 Makanan", !isDrink) { isDrink = false }
            ChoiceChip("🥤 Minuman", isDrink) { isDrink = true }
        }
        if (isDrink) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(volume, { v -> volume = v.filter { it.isDigit() } },
                label = { Text("Volume (ml)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = forlaFieldColors(), modifier = Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(12.dp))
        Text("Waktu makan", color = ForlaTextSub, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip("Sarapan", meal == "breakfast") { meal = "breakfast" }
            ChoiceChip("Siang", meal == "lunch") { meal = "lunch" }
            ChoiceChip("Malam", meal == "dinner") { meal = "dinner" }
            ChoiceChip("Snack", meal == "snack") { meal = "snack" }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(cal, { v -> cal = v.filter { it.isDigit() } }, label = { Text("Kalori") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = forlaFieldColors(), modifier = Modifier.weight(1f))
            OutlinedTextField(pro, { v -> pro = v.filter { it.isDigit() } }, label = { Text("Protein (g)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = forlaFieldColors(), modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(carb, { v -> carb = v.filter { it.isDigit() } }, label = { Text("Karbo (g)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = forlaFieldColors(), modifier = Modifier.weight(1f))
            OutlinedTextField(fat, { v -> fat = v.filter { it.isDigit() } }, label = { Text("Lemak (g)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = forlaFieldColors(), modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(14.dp))
        ForlaButton("Simpan Makanan", modifier = Modifier.fillMaxWidth()) {
            if (name.isNotBlank() && cal.toDoubleOrNull() != null) {
                onAdd(
                    FoodEntry(
                        timestamp = System.currentTimeMillis(),
                        name = name.trim(),
                        takaranText = takaran.ifBlank { null },
                        volumeMl = if (isDrink) volume.toIntOrNull() else null,
                        photoUri = null,
                        calories = cal.toDoubleOrNull() ?: 0.0,
                        protein = pro.toDoubleOrNull() ?: 0.0,
                        fat = fat.toDoubleOrNull() ?: 0.0,
                        carbs = carb.toDoubleOrNull() ?: 0.0,
                        mealType = meal,
                        isDrink = isDrink
                    )
                )
                name = ""; takaran = ""; volume = ""; cal = ""; pro = ""; fat = ""; carb = ""
                photo = null; aiMsg = null
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionTitle("🍽️ Makanan Hari Ini (${todayFoods.size})")
        if (todayFoods.isEmpty()) {
            Text("Belum ada. Yuk catat makan pertamamu!", color = ForlaTextSub, fontSize = 13.sp)
        } else {
            todayFoods.forEach { food ->
                ForlaCard {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(food.name, color = ForlaText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            if (!food.takaranText.isNullOrBlank())
                                Text(food.takaranText, color = ForlaTextSub, fontSize = 12.sp)
                            Text("P ${food.protein.toInt()}  •  K ${food.carbs.toInt()}  •  L ${food.fat.toInt()} (g)",
                                color = ForlaTextSub, fontSize = 11.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${food.calories.toInt()} kkal", color = ForlaCalorie,
                                fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("Hapus", color = ForlaDeficit, fontSize = 12.sp,
                                modifier = Modifier.clickableNoRipple { onDelete(food) })
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// helper: Uri galeri -> Bitmap
fun uriToBitmap(context: Context, uri: Uri): Bitmap? = try {
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
} catch (e: Exception) { null }

// ================= 2. AKTIVITAS =================
@Composable
fun ActivityScreen(steps: Int, sensorAvailable: Boolean, profile: UserProfile?) {
    val stepBurn = (steps * STEP_KCAL).toInt()
    val bmr = if (profile != null && profile.weightKg > 0) computeBmr(profile).toInt() else DEFAULT_BMR.toInt()
    Column(
        modifier = Modifier.fillMaxSize().background(ForlaBg).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(20.dp))
        Text("👟 Aktivitas", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ForlaText)
        Spacer(Modifier.height(20.dp))
        if (sensorAvailable) StepRing(steps, STEP_GOAL)
        else Text("⚠️ HP ini nggak punya sensor step counter.", color = ForlaText)
        Spacer(Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(Modifier.weight(1f), "Langkah", "$steps", ForlaPrimary)
            StatCard(Modifier.weight(1f), "Bakar (langkah)", "$stepBurn kkal", ForlaCalorie)
            StatCard(Modifier.weight(1f), "BMR", "$bmr kkal", ForlaTextSub)
        }
        Spacer(Modifier.height(20.dp))
        ForlaCard {
            Text("Kalori keluar total ≈ ${bmr + stepBurn} kkal", color = ForlaText, fontSize = 15.sp,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Dihitung dari BMR + kalori langkah. Angka BMR pakai data di tab Profil.",
                color = ForlaTextSub, fontSize = 12.sp)
        }
    }
}

@Composable
fun StepRing(steps: Int, goal: Int) {
    val progress = (steps.toFloat() / goal).coerceIn(0f, 1f)
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = 22.dp.toPx()
            val inset = strokePx / 2
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            drawArc(ForlaCardAlt, -90f, 360f, false, Offset(inset, inset), arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round))
            drawArc(ForlaPrimary, -90f, 360f * progress, false, Offset(inset, inset), arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$steps", fontSize = 46.sp, fontWeight = FontWeight.Bold, color = ForlaText)
            Text("langkah", fontSize = 14.sp, color = ForlaTextSub)
            Spacer(Modifier.height(6.dp))
            Text("${(progress * 100).toInt()}% dari $goal", fontSize = 12.sp, color = ForlaPrimary)
        }
    }
}

// ================= 3. PROGRESS =================
@Composable
fun ProgressScreen(
    weights: List<WeightEntry>,
    allFoods: List<FoodEntry>,
    onAddWeight: (WeightEntry) -> Unit
) {
    var w by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().background(ForlaBg)
            .verticalScroll(rememberScrollState()).padding(20.dp)
    ) {
        Text("📈 Progress", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ForlaText)
        Spacer(Modifier.height(16.dp))
        SectionTitle("⚖️ Catat Berat Badan")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(w, { v -> w = v.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Berat (kg)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = forlaFieldColors(), modifier = Modifier.weight(1f))
            ForlaButton("Simpan") {
                val kg = w.toDoubleOrNull()
                if (kg != null && kg > 0) {
                    onAddWeight(WeightEntry(date = LocalDate.now().toString(), weightKg = kg))
                    w = ""
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        SectionTitle("📊 Riwayat Berat")
        if (weights.isEmpty()) {
            Text("Belum ada data berat badan.", color = ForlaTextSub, fontSize = 13.sp)
        } else {
            weights.forEach { entry ->
                ForlaCard {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(entry.date, color = ForlaText, fontSize = 15.sp)
                        Text("${entry.weightKg} kg", color = ForlaPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ================= 4. PROFIL & TARGET =================
@Composable
fun ProfileScreen(profile: UserProfile?, onSave: (UserProfile) -> Unit) {
    var weight by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("male") }
    var activity by remember { mutableStateOf("moderate") }
    var tCal by remember { mutableStateOf("") }
    var tPro by remember { mutableStateOf("") }
    var tFat by remember { mutableStateOf("") }
    var tCarb by remember { mutableStateOf("") }
    val context = LocalContext.current

    LaunchedEffect(profile) {
        profile?.let {
            if (it.weightKg > 0) weight = it.weightKg.toString()
            if (it.heightCm > 0) height = it.heightCm.toString()
            if (it.age > 0) age = it.age.toString()
            gender = it.gender
            activity = it.activityLevel
            if (it.targetCalories > 0) tCal = it.targetCalories.toInt().toString()
            if (it.targetProtein > 0) tPro = it.targetProtein.toInt().toString()
            if (it.targetFat > 0) tFat = it.targetFat.toInt().toString()
            if (it.targetCarbs > 0) tCarb = it.targetCarbs.toInt().toString()
        }
    }

    val wKg = weight.toDoubleOrNull() ?: 0.0
    val hCm = height.toDoubleOrNull() ?: 0.0
    val ageI = age.toIntOrNull() ?: 0
    val tdee = if (wKg > 0 && hCm > 0 && ageI > 0) {
        val base = 10 * wKg + 6.25 * hCm - 5 * ageI + (if (gender == "female") -161 else 5)
        (base * activityFactor(activity)).toInt()
    } else 0

    Column(
        modifier = Modifier.fillMaxSize().background(ForlaBg)
            .verticalScroll(rememberScrollState()).padding(20.dp)
    ) {
        Text("⚙️ Profil & Target", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ForlaText)
        Spacer(Modifier.height(16.dp))

        SectionTitle("👤 Data Diri")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(weight, { v -> weight = v.filter { it.isDigit() || it == '.' } },
                label = { Text("Berat (kg)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = forlaFieldColors(), modifier = Modifier.weight(1f))
            OutlinedTextField(height, { v -> height = v.filter { it.isDigit() || it == '.' } },
                label = { Text("Tinggi (cm)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = forlaFieldColors(), modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(age, { v -> age = v.filter { it.isDigit() } }, label = { Text("Umur") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = forlaFieldColors(), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip("Pria", gender == "male") { gender = "male" }
            ChoiceChip("Wanita", gender == "female") { gender = "female" }
        }
        Spacer(Modifier.height(10.dp))
        Text("Tingkat aktivitas", color = ForlaTextSub, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip("Rebahan", activity == "sedentary") { activity = "sedentary" }
            ChoiceChip("Ringan", activity == "light") { activity = "light" }
            ChoiceChip("Sedang", activity == "moderate") { activity = "moderate" }
            ChoiceChip("Aktif", activity == "active") { activity = "active" }
            ChoiceChip("Sangat", activity == "veryActive") { activity = "veryActive" }
        }
        if (tdee > 0) {
            Spacer(Modifier.height(14.dp))
            ForlaCard {
                Text("TDEE kamu ≈ $tdee kkal/hari", color = ForlaPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("Buat bulking, target ≈ ${tdee + 300}–${tdee + 500} kkal (surplus).",
                    color = ForlaTextSub, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("🎯 Target Harian")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(tCal, { v -> tCal = v.filter { it.isDigit() } }, label = { Text("Kalori") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = forlaFieldColors(), modifier = Modifier.weight(1f))
            OutlinedTextField(tPro, { v -> tPro = v.filter { it.isDigit() } }, label = { Text("Protein (g)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = forlaFieldColors(), modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(tCarb, { v -> tCarb = v.filter { it.isDigit() } }, label = { Text("Karbo (g)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = forlaFieldColors(), modifier = Modifier.weight(1f))
            OutlinedTextField(tFat, { v -> tFat = v.filter { it.isDigit() } }, label = { Text("Lemak (g)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = forlaFieldColors(), modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(16.dp))
        ForlaButton("Simpan Profil", modifier = Modifier.fillMaxWidth()) {
            val autoCal = if (tdee > 0) tdee + 400 else 2500
            val autoPro = if (wKg > 0) (wKg * 2).toInt() else 150
            val autoFat = ((autoCal * 0.25) / 9).toInt()
            val autoCarb = ((autoCal - autoPro * 4 - autoFat * 9) / 4).coerceAtLeast(0)

            val finalCal = tCal.toDoubleOrNull() ?: autoCal.toDouble()
            val finalPro = tPro.toDoubleOrNull() ?: autoPro.toDouble()
            val finalFat = tFat.toDoubleOrNull() ?: autoFat.toDouble()
            val finalCarb = tCarb.toDoubleOrNull() ?: autoCarb.toDouble()

            tCal = finalCal.toInt().toString()
            tPro = finalPro.toInt().toString()
            tFat = finalFat.toInt().toString()
            tCarb = finalCarb.toInt().toString()

            onSave(
                UserProfile(
                    id = 1,
                    targetCalories = finalCal,
                    targetProtein = finalPro,
                    targetFat = finalFat,
                    targetCarbs = finalCarb,
                    weightKg = wKg,
                    heightCm = hCm,
                    age = ageI,
                    gender = gender,
                    activityLevel = activity
                )
            )
            Toast.makeText(context, "✅ Profil & target tersimpan!", Toast.LENGTH_SHORT).show()
        }
        Spacer(Modifier.height(24.dp))
    }
}
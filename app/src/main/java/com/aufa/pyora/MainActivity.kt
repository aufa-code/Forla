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
import android.content.Intent
import kotlin.math.roundToInt
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

// ================= KONSTAN =================
private const val STEP_GOAL = 8000
private const val STEP_KCAL = 0.04          // ~0.04 kkal per langkah
private const val DEFAULT_BMR = 1600.0
private const val DEFAULT_TARGET_CAL = 2500

class MainActivity : ComponentActivity() {

    private lateinit var summaryDao: DailySummaryDao
    private lateinit var foodDao: FoodDao
    private lateinit var weightDao: WeightDao
    private lateinit var profileDao: ProfileDao

    private var sensorAvailable by mutableStateOf(true)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { startStepService() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AppDatabase.getInstance(this)
        summaryDao = db.dailySummaryDao()
        foodDao = db.foodDao()
        weightDao = db.weightDao()
        profileDao = db.profileDao()

        sensorAvailable =
            packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_STEP_COUNTER)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = ForlaBg, surface = ForlaBg)) {
                Surface(modifier = Modifier.fillMaxSize(), color = ForlaBg) {

                    val prefs = remember { getSharedPreferences("forla", Context.MODE_PRIVATE) }
                    var onboardingDone by remember {
                        mutableStateOf(prefs.getBoolean("onboarding_done", false))
                    }

                    if (!onboardingDone) {
                        OnboardingFlow(onFinish = { p ->
                            saveProfile(p)
                            prefs.edit().putBoolean("onboarding_done", true).apply()
                            onboardingDone = true
                        })
                    } else {
                        var tab by remember { mutableStateOf(0) }

                        val zone = ZoneId.systemDefault()
                        val today = LocalDate.now()
                        val startOfDay = today.atStartOfDay(zone).toInstant().toEpochMilli()
                        val endOfDay =
                            today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

                        val todayFoods by remember { foodDao.getBetween(startOfDay, endOfDay) }
                            .collectAsState(initial = emptyList())

                        // --- Tanggal yang dipilih di Beranda ---
                        var selectedDate by remember { mutableStateOf(LocalDate.now()) }
                        val selStart = selectedDate.atStartOfDay(zone).toInstant().toEpochMilli()
                        val selEnd = selectedDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
                        val selectedFoods by remember(selectedDate) { foodDao.getBetween(selStart, selEnd) }
                            .collectAsState(initial = emptyList())
                        val isToday = selectedDate == LocalDate.now()
                        val allFoods by remember { foodDao.getAll() }.collectAsState(initial = emptyList())
                        val profile by remember { profileDao.get() }.collectAsState(initial = null)
                        val weights by remember { weightDao.getAll() }.collectAsState(initial = emptyList())

                        val todaySummary by remember { summaryDao.getByDateFlow(today.toString()) }
                            .collectAsState(initial = null)
                        val todaySteps = todaySummary?.totalSteps ?: 0

                        Column(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.weight(1f)) {
                                when (tab) {
                                    0 -> DashboardScreen(
                                        todayFoods = selectedFoods,
                                        profile = profile,
                                        steps = if (isToday) todaySteps else 0,
                                        sensorAvailable = sensorAvailable,
                                        selectedDate = selectedDate,
                                        onDateSelected = { selectedDate = it }
                                    )
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
        }

        ensurePermissionAndStart()
    }

    // ================= AKSI DB =================
    private fun addFood(entry: FoodEntry) {
        lifecycleScope.launch { foodDao.insert(entry) }
    }

    private fun deleteFood(entry: FoodEntry) {
        lifecycleScope.launch { foodDao.delete(entry) }
    }

    private fun addWeight(entry: WeightEntry) {
        lifecycleScope.launch { weightDao.insert(entry) }
    }

    private fun saveProfile(p: UserProfile) {
        lifecycleScope.launch { profileDao.upsert(p) }
    }

    // ================= START STEP SERVICE =================
    private fun ensurePermissionAndStart() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
            != PackageManager.PERMISSION_GRANTED
        ) needed.add(Manifest.permission.ACTIVITY_RECOGNITION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) needed.add(Manifest.permission.POST_NOTIFICATIONS)

        if (needed.isEmpty()) startStepService()
        else permissionLauncher.launch(needed.toTypedArray())
    }

    private fun startStepService() {
        ContextCompat.startForegroundService(this, Intent(this, StepService::class.java))
    }
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
fun HeroStat(label: String, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = ForlaText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(3.dp))
            Text(
                unit, color = ForlaTextSub, fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(label, color = ForlaTextSub, fontSize = 12.sp)
    }
}

@Composable
fun CalorieHero(caloriesIn: Int, targetCal: Int, netInt: Int, isSurplus: Boolean) {
    val remaining = (targetCal - caloriesIn).coerceAtLeast(0)
    val progress = if (targetCal > 0) (caloriesIn.toFloat() / targetCal).coerceIn(0f, 1f) else 0f
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "$remaining",
                color = ForlaText, fontSize = 46.sp,
                fontWeight = FontWeight.Bold, letterSpacing = (-1).sp
            )
            Text("kkal tersisa", color = ForlaTextSub, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background((if (isSurplus) ForlaSurplus else ForlaDeficit).copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    (if (isSurplus) "Surplus " else "Defisit ") + "${kotlin.math.abs(netInt)} kkal",
                    color = if (isSurplus) ForlaSurplus else ForlaDeficit,
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                )
            }
        }
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val stroke = 12.dp.toPx()
                val d = size.minDimension - stroke
                val tl = androidx.compose.ui.geometry.Offset((size.width - d) / 2f, (size.height - d) / 2f)
                val sz = androidx.compose.ui.geometry.Size(d, d)
                drawArc(
                    ForlaCardAlt, -90f, 360f, false, tl, sz,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                )
                drawArc(
                    ForlaCalorie, -90f, 360f * progress, false, tl, sz,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                )
            }
            Text("🔥", fontSize = 30.sp)
        }
    }
}

@Composable
fun MacroRing(
    label: String,
    current: Double,
    target: Double,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val progress = if (target > 0) (current / target).toFloat().coerceIn(0f, 1f) else 0f
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(88.dp)) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 9.dp.toPx()
                val d = size.minDimension - stroke
                val topLeft = androidx.compose.ui.geometry.Offset(
                    (size.width - d) / 2f, (size.height - d) / 2f
                )
                val arcSize = androidx.compose.ui.geometry.Size(d, d)
                // track (background ring)
                drawArc(
                    color = ForlaCardAlt,
                    startAngle = -90f, sweepAngle = 360f, useCenter = false,
                    topLeft = topLeft, size = arcSize,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                )
                // progress ring
                drawArc(
                    color = color,
                    startAngle = -90f, sweepAngle = 360f * progress, useCenter = false,
                    topLeft = topLeft, size = arcSize,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${current.roundToInt()}",
                    color = ForlaText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
                Text(
                    text = "/${target.roundToInt()}g",
                    color = ForlaTextSub,
                    fontSize = 10.sp
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = ForlaTextSub, fontSize = 13.sp, fontWeight = FontWeight.Medium)
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ===== PILL NAV (4 tab, melayang) =====
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(50))
                .background(ForlaCard.copy(alpha = 0.4f))
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TabButton(Icons.Filled.Home, "Beranda", selected == 0) { onSelect(0) }
            TabButton(Icons.Filled.DirectionsRun, "Aktivitas", selected == 2) { onSelect(2) }
            TabButton(Icons.Filled.BarChart, "Progress", selected == 3) { onSelect(3) }
            TabButton(Icons.Filled.Person, "Profil", selected == 4) { onSelect(4) }
        }

        // ===== FAB "Catat/Scan" =====
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(50))
                .background(ForlaPrimary)
                .clickableNoRipple { onSelect(1) },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Catat",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
fun RowScope.TabButton(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.weight(1f).clip(RoundedCornerShape(50))
            .background(if (active) ForlaPrimary else Color.Transparent)
            .clickableNoRipple(onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) Color.Black else ForlaTextSub,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(2.dp))
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

@Composable
fun DateStrip(selectedDate: LocalDate, onSelect: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    val idLocale = Locale("id", "ID")
    val days = (-5..1).map { today.plusDays(it.toLong()) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        days.forEach { date ->
            val isSelected = date == selectedDate
            val isFuture = date.isAfter(today)
            val dayName = date.dayOfWeek.getDisplayName(TextStyle.SHORT, idLocale)
                .replaceFirstChar { it.uppercase() }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) ForlaPrimary else ForlaCard.copy(alpha = 0.5f))
                    .then(
                        if (isFuture) Modifier
                        else Modifier.clickableNoRipple { onSelect(date) }
                    )
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    dayName,
                    fontSize = 11.sp,
                    color = when {
                        isSelected -> Color.Black
                        isFuture -> ForlaTextSub.copy(alpha = 0.4f)
                        else -> ForlaTextSub
                    }
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    date.dayOfMonth.toString(),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        isSelected -> Color.Black
                        isFuture -> ForlaTextSub.copy(alpha = 0.4f)
                        else -> ForlaText
                    }
                )
            }
        }
    }
}

// ================= 0. DASHBOARD =================
@Composable
fun DashboardScreen(
    todayFoods: List<FoodEntry>,
    profile: UserProfile?,
    steps: Int,
    sensorAvailable: Boolean,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit
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
    val netInt = net.toInt()
    val isSurplus = netInt >= 0

    Box(modifier = Modifier.fillMaxSize()) {
        ForlaAnimatedBg()
        Column(
            modifier = Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))
            Text("Forla", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ForlaPrimary)
            Text("Macro & Calorie Tracker", fontSize = 12.sp, color = ForlaTextSub)
            Spacer(Modifier.height(16.dp))
            DateStrip(selectedDate, onDateSelected)

            // ===== HERO CARD =====
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(ForlaCard.copy(alpha = 0.5f))
                    .padding(20.dp)
            ) {
                CalorieHero(caloriesIn.toInt(), targetCal, netInt, isSurplus)

                Spacer(Modifier.height(18.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ForlaCardAlt))
                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    HeroStat("Masuk", "${caloriesIn.toInt()}", "kkal")
                    HeroStat("Keluar", "${caloriesOut.toInt()}", "kkal")
                    HeroStat("Langkah", if (sensorAvailable) "$steps" else "-", "langkah")
                }
            }

            Spacer(Modifier.height(14.dp))

            // ===== MAKRONUTRIEN =====
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(ForlaCard.copy(alpha = 0.5f))
                    .padding(16.dp)
            ) {
                Text("Makronutrien", color = ForlaText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MacroRing("Protein", proteinIn, targetP, ForlaPrimary)
                    MacroRing("Karbo", carbsIn, targetC, ForlaPrimary)
                    MacroRing("Lemak", fatIn, targetF, ForlaPrimary)
                }
            }

            Spacer(Modifier.height(14.dp))
            if (todayFoods.isEmpty()) {
                Text(
                    "Belum ada makanan tercatat hari ini.",
                    color = ForlaTextSub, fontSize = 13.sp
                )
            }
            Spacer(Modifier.height(24.dp))
        }
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
fun ForlaAnimatedBg(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "bg")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "t"
    )
    androidx.compose.foundation.Canvas(
        modifier = modifier.fillMaxSize().background(ForlaBg)
    ) {
        val w = size.width
        val h = size.height
        // glow oranye (atas-kanan, gerak pelan)
        val c1 = androidx.compose.ui.geometry.Offset(w * (0.85f - 0.12f * t), h * (0.12f + 0.06f * t))
        drawCircle(
            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                colors = listOf(ForlaPrimary.copy(alpha = 0.20f), androidx.compose.ui.graphics.Color.Transparent),
                center = c1, radius = w * 0.75f
            ),
            radius = w * 0.75f, center = c1
        )
        // glow hijau (bawah-kiri)
        val c2 = androidx.compose.ui.geometry.Offset(w * (0.12f + 0.10f * t), h * (0.82f - 0.06f * t))
        drawCircle(
            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                colors = listOf(ForlaSurplus.copy(alpha = 0.12f), androidx.compose.ui.graphics.Color.Transparent),
                center = c2, radius = w * 0.65f
            ),
            radius = w * 0.65f, center = c2
        )
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
                aiMsg = "Terisi dari AI — cek & koreksi kalau perlu."
            } catch (e: Exception) {
                aiMsg = "Gagal analisis: ${e.message}"
            } finally {
                analyzing = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ForlaAnimatedBg()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(24.dp))
            Text("Catat Makanan", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = ForlaText)
            Text("Foto + takaran → AI isi otomatis", fontSize = 13.sp, color = ForlaTextSub)
            Spacer(Modifier.height(16.dp))

            // ===== Kartu Foto & AI =====
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(ForlaCard.copy(alpha = 0.5f))
                    .padding(16.dp)
            ) {
                if (photo != null) {
                    Image(
                        bitmap = photo!!.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp))
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { cameraLauncher.launch(null) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ForlaPrimary),
                        border = BorderStroke(1.dp, ForlaPrimary),
                        modifier = Modifier.weight(1f)
                    ) { Text("Kamera") }
                    OutlinedButton(
                        onClick = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ForlaPrimary),
                        border = BorderStroke(1.dp, ForlaPrimary),
                        modifier = Modifier.weight(1f)
                    ) { Text("Galeri") }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(takaran, { takaran = it },
                    label = { Text("Takaran (mis. 3 centong nasi, 2 dada ayam)") },
                    colors = forlaFieldColors(), modifier = Modifier.fillMaxWidth())
                if (photo != null) {
                    Spacer(Modifier.height(10.dp))
                    if (analyzing) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = ForlaPrimary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Menganalisis makanan...", color = ForlaTextSub, fontSize = 13.sp)
                        }
                    } else {
                        ForlaButton("Analisis dengan AI", modifier = Modifier.fillMaxWidth()) { runAi() }
                    }
                }
                aiMsg?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = if (it.startsWith("Gagal")) ForlaDeficit else ForlaSurplus, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(16.dp))

            // ===== Kartu Detail Makanan =====
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(ForlaCard.copy(alpha = 0.5f))
                    .padding(16.dp)
            ) {
                OutlinedTextField(name, { name = it }, label = { Text("Nama makanan") },
                    colors = forlaFieldColors(), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("Makanan", !isDrink) { isDrink = false }
                    ChoiceChip("Minuman", isDrink) { isDrink = true }
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
            }

            Spacer(Modifier.height(16.dp))
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
            SectionTitle("Makanan Hari Ini (${todayFoods.size})")
            Spacer(Modifier.height(8.dp))
            if (todayFoods.isEmpty()) {
                Text("Belum ada. Yuk catat makan pertamamu!", color = ForlaTextSub, fontSize = 13.sp)
            } else {
                todayFoods.forEach { food ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(ForlaCard.copy(alpha = 0.5f))
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
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
            Spacer(Modifier.height(24.dp))
        }
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

    Box(modifier = Modifier.fillMaxSize()) {
        ForlaAnimatedBg()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            // Header (tanpa emoji, ala Cal AI)
            Text("Aktivitas", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = ForlaText)
            Text("Pantau langkah & pembakaran kalori", fontSize = 13.sp, color = ForlaTextSub)
            Spacer(Modifier.height(24.dp))

            if (sensorAvailable) {
                StepRing(steps, STEP_GOAL)
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(ForlaCard.copy(alpha = 0.5f))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Sensor langkah nggak tersedia", color = ForlaText, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "HP ini nggak punya step counter, jadi langkah nggak bisa dihitung.",
                        color = ForlaTextSub, fontSize = 12.sp
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(Modifier.weight(1f), "Langkah", "$steps", ForlaPrimary)
                StatCard(Modifier.weight(1f), "Bakar", "$stepBurn kkal", ForlaCalorie)
                StatCard(Modifier.weight(1f), "BMR", "$bmr kkal", ForlaTextSub)
            }

            Spacer(Modifier.height(16.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(ForlaCard.copy(alpha = 0.5f))
                    .padding(16.dp)
            ) {
                Text(
                    "Kalori keluar total ≈ ${bmr + stepBurn} kkal",
                    color = ForlaText, fontSize = 15.sp, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Dihitung dari BMR + kalori langkah. Angka BMR pakai data di tab Profil.",
                    color = ForlaTextSub, fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(24.dp))
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
    val latest = weights.maxByOrNull { it.date }
    val oldest = weights.minByOrNull { it.date }

    Box(modifier = Modifier.fillMaxSize()) {
        ForlaAnimatedBg()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(24.dp))
            // Header (tanpa emoji)
            Text("Progress", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = ForlaText)
            Text("Pantau perkembangan berat badanmu", fontSize = 13.sp, color = ForlaTextSub)
            Spacer(Modifier.height(20.dp))

            // Kartu berat terkini
            if (latest != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(ForlaCard.copy(alpha = 0.5f))
                        .padding(20.dp)
                ) {
                    Text("Berat terkini", color = ForlaTextSub, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("${latest.weightKg} kg", color = ForlaText, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    if (oldest != null && weights.size > 1) {
                        val d = latest.weightKg - oldest.weightKg
                        val label = when {
                            d < 0 -> "Turun ${"%.1f".format(-d)} kg dari awal"
                            d > 0 -> "Naik ${"%.1f".format(d)} kg dari awal"
                            else -> "Stabil dari awal"
                        }
                        val col = when {
                            d < 0 -> ForlaSurplus
                            d > 0 -> ForlaDeficit
                            else -> ForlaTextSub
                        }
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(col.copy(alpha = 0.15f))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(label, color = col, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            // Catat berat
            SectionTitle("Catat Berat Badan")
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    w, { v -> w = v.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Berat (kg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = forlaFieldColors(), modifier = Modifier.weight(1f)
                )
                ForlaButton("Simpan") {
                    val kg = w.toDoubleOrNull()
                    if (kg != null && kg > 0) {
                        onAddWeight(WeightEntry(date = LocalDate.now().toString(), weightKg = kg))
                        w = ""
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionTitle("Riwayat Berat")
            Spacer(Modifier.height(8.dp))
            if (weights.isEmpty()) {
                Text("Belum ada data berat badan.", color = ForlaTextSub, fontSize = 13.sp)
            } else {
                weights.sortedByDescending { it.date }.forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(ForlaCard.copy(alpha = 0.5f))
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(entry.date, color = ForlaText, fontSize = 15.sp)
                        Text("${entry.weightKg} kg", color = ForlaPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
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

    Box(modifier = Modifier.fillMaxSize()) {
        ForlaAnimatedBg()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(24.dp))
            // Header + avatar (tanpa emoji)
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(50))
                        .background(ForlaPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Person, contentDescription = "Profil",
                        tint = ForlaPrimary, modifier = Modifier.size(38.dp))
                }
                Spacer(Modifier.height(10.dp))
                Text("Profil", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ForlaText)
                Text("Atur data diri & target harian", fontSize = 13.sp, color = ForlaTextSub)
            }
            Spacer(Modifier.height(20.dp))

            // ===== Data Diri =====
            SectionTitle("Data Diri")
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(ForlaCard.copy(alpha = 0.5f))
                    .padding(16.dp)
            ) {
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
            }

            if (tdee > 0) {
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(ForlaPrimary.copy(alpha = 0.12f))
                        .padding(16.dp)
                ) {
                    Text("TDEE kamu ≈ $tdee kkal/hari", color = ForlaPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(2.dp))
                    Text("Buat bulking, target ≈ ${tdee + 300}–${tdee + 500} kkal (surplus).",
                        color = ForlaTextSub, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(20.dp))

            // ===== Target Harian =====
            SectionTitle("Target Harian")
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(ForlaCard.copy(alpha = 0.5f))
                    .padding(16.dp)
            ) {
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
            }

            Spacer(Modifier.height(20.dp))
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
                Toast.makeText(context, "Profil & target tersimpan!", Toast.LENGTH_SHORT).show()
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
package com.aufa.pyora

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aufa.pyora.data.UserProfile
import com.aufa.pyora.ui.theme.ForlaBg
import com.aufa.pyora.ui.theme.ForlaCalorie
import com.aufa.pyora.ui.theme.ForlaCard
import com.aufa.pyora.ui.theme.ForlaCardAlt
import com.aufa.pyora.ui.theme.ForlaCarbs
import com.aufa.pyora.ui.theme.ForlaFat
import com.aufa.pyora.ui.theme.ForlaPrimary
import com.aufa.pyora.ui.theme.ForlaProtein
import com.aufa.pyora.ui.theme.ForlaText
import com.aufa.pyora.ui.theme.ForlaTextSub
import kotlinx.coroutines.delay

// ================= ONBOARDING (Cal AI style) =================
// Alur langkah:
// 0 welcome | 1 tujuan | 2 gender | 3 umur | 4 tinggi | 5 berat | 6 aktivitas | 7 loading | 8 hasil
@Composable
fun OnboardingFlow(onFinish: (UserProfile) -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var goal by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var activity by remember { mutableStateOf("") }

    val firstQ = 1
    val lastQ = 6

    fun canProceed(): Boolean = when (step) {
        1 -> goal.isNotEmpty()
        2 -> gender.isNotEmpty()
        3 -> (age.toIntOrNull() ?: 0) in 5..120
        4 -> (height.toDoubleOrNull() ?: 0.0) in 80.0..250.0
        5 -> (weight.toDoubleOrNull() ?: 0.0) in 20.0..400.0
        6 -> activity.isNotEmpty()
        else -> true
    }

    Column(modifier = Modifier.fillMaxSize().background(ForlaBg).padding(24.dp)) {
        if (step in firstQ..lastQ) {
            OnbTopBar(current = step, first = firstQ, last = lastQ) { step-- }
            Spacer(Modifier.height(28.dp))
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (step) {
                0 -> OnbWelcome()
                1 -> OnbGoal(goal) { goal = it }
                2 -> OnbGender(gender) { gender = it }
                3 -> OnbNumber("Berapa umur kamu?", "Buat ngitung kebutuhan kalori harian.",
                    age, "tahun", KeyboardType.Number) { v -> age = v.filter { it.isDigit() } }
                4 -> OnbNumber("Berapa tinggi kamu?", "Dalam sentimeter (cm).",
                    height, "cm", KeyboardType.Decimal) { v -> height = v.filter { it.isDigit() || it == '.' } }
                5 -> OnbNumber("Berapa berat kamu sekarang?", "Dalam kilogram (kg).",
                    weight, "kg", KeyboardType.Decimal) { v -> weight = v.filter { it.isDigit() || it == '.' } }
                6 -> OnbActivity(activity) { activity = it }
                7 -> OnbLoading { step = 8 }
                8 -> {
                    val plan = remember {
                        buildPlan(gender, age.toIntOrNull() ?: 0, height.toDoubleOrNull() ?: 0.0,
                            weight.toDoubleOrNull() ?: 0.0, activity, goal)
                    }
                    OnbResult(plan) { onFinish(plan) }
                }
            }
        }

        when (step) {
            0 -> ForlaButton("Mulai 🚀", modifier = Modifier.fillMaxWidth()) { step = 1 }
            in firstQ..lastQ -> OnbNextButton(
                text = if (step == lastQ) "Lihat Rencanaku ✨" else "Lanjut",
                enabled = canProceed()
            ) { step = if (step == lastQ) 7 else step + 1 }
            else -> {}
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun OnbTopBar(current: Int, first: Int, last: Int, onBack: () -> Unit) {
    val total = last - first + 1
    val done = current - first + 1
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("‹", color = ForlaText, fontSize = 30.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { onBack() }.padding(end = 14.dp))
        Box(modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(ForlaCardAlt)) {
            Box(modifier = Modifier.fillMaxWidth(done.toFloat() / total).height(8.dp)
                .clip(RoundedCornerShape(4.dp)).background(ForlaPrimary))
        }
        Spacer(Modifier.width(12.dp))
        Text("$done/$total", color = ForlaTextSub, fontSize = 13.sp)
    }
}

@Composable
private fun OnbWelcome() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🔥", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text("Selamat datang di Forla", color = ForlaText, fontSize = 26.sp,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            "Asisten kalori & makro kamu. Foto makanan, biar AI yang ngitung. " +
                    "Yuk atur target dulu — cuma butuh 1 menit.",
            color = ForlaTextSub, fontSize = 15.sp, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

@Composable
private fun OnbQuestion(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(title, color = ForlaText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = ForlaTextSub, fontSize = 14.sp)
        Spacer(Modifier.height(20.dp))
        content()
    }
}

@Composable
private fun OnbGoal(selected: String, onSelect: (String) -> Unit) {
    OnbQuestion("Apa tujuan kamu?", "Kita sesuaikan target kalorinya.") {
        OnbOption("📉", "Turunin berat", "Defisit kalori (~500 kkal)", selected == "lose") { onSelect("lose") }
        OnbOption("⚖️", "Jaga berat", "Kalori sesuai kebutuhan", selected == "maintain") { onSelect("maintain") }
        OnbOption("📈", "Naikin berat", "Surplus kalori (~400 kkal)", selected == "gain") { onSelect("gain") }
    }
}

@Composable
private fun OnbGender(selected: String, onSelect: (String) -> Unit) {
    OnbQuestion("Jenis kelamin kamu?", "Berpengaruh ke perhitungan metabolisme.") {
        OnbOption("👨", "Pria", null, selected == "male") { onSelect("male") }
        OnbOption("👩", "Wanita", null, selected == "female") { onSelect("female") }
    }
}

@Composable
private fun OnbActivity(selected: String, onSelect: (String) -> Unit) {
    OnbQuestion("Seberapa aktif kamu?", "Pilih yang paling mendekati keseharianmu.") {
        OnbOption("🛋️", "Rebahan", "Jarang olahraga", selected == "sedentary") { onSelect("sedentary") }
        OnbOption("🚶", "Ringan", "Olahraga 1–3x/minggu", selected == "light") { onSelect("light") }
        OnbOption("🏃", "Sedang", "Olahraga 3–5x/minggu", selected == "moderate") { onSelect("moderate") }
        OnbOption("💪", "Aktif", "Olahraga 6–7x/minggu", selected == "active") { onSelect("active") }
        OnbOption("🔥", "Sangat aktif", "Kerja fisik / atlet", selected == "veryActive") { onSelect("veryActive") }
    }
}

@Composable
private fun OnbOption(emoji: String, title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) ForlaPrimary else ForlaCard)
            .border(BorderStroke(1.dp, if (selected) ForlaPrimary else ForlaCardAlt), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 26.sp)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, color = if (selected) Color.Black else ForlaText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            if (subtitle != null) Text(subtitle,
                color = if (selected) Color.Black.copy(alpha = 0.7f) else ForlaTextSub, fontSize = 12.sp)
        }
    }
}

@Composable
private fun OnbNumber(title: String, subtitle: String, value: String, suffix: String,
                      keyboard: KeyboardType, onChange: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(title, color = ForlaText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = ForlaTextSub, fontSize = 14.sp)
        Spacer(Modifier.height(40.dp))
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                textStyle = TextStyle(color = ForlaText, fontSize = 34.sp,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                singleLine = true,
                colors = forlaFieldColors(),
                modifier = Modifier.width(170.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(suffix, color = ForlaTextSub, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun OnbLoading(onDone: () -> Unit) {
    LaunchedEffect(Unit) { delay(1900); onDone() }
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = ForlaPrimary, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(24.dp))
        Text("Menyusun rencana kamu…", color = ForlaText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Menghitung kebutuhan kalori & makro", color = ForlaTextSub, fontSize = 13.sp)
    }
}

@Composable
private fun OnbResult(plan: UserProfile, onStart: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("✨", fontSize = 46.sp)
        Spacer(Modifier.height(8.dp))
        Text("Rencana harian kamu siap!", color = ForlaText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Bisa diubah kapan aja di tab Profil.", color = ForlaTextSub, fontSize = 14.sp)
        Spacer(Modifier.height(24.dp))

        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(ForlaCard).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Target Kalori Harian", color = ForlaTextSub, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Text("${plan.targetCalories.toInt()}", color = ForlaCalorie, fontSize = 52.sp, fontWeight = FontWeight.Bold)
            Text("kkal / hari", color = ForlaTextSub, fontSize = 13.sp)
        }
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OnbMacroCard(Modifier.weight(1f), "Protein", "${plan.targetProtein.toInt()} g", ForlaProtein)
            OnbMacroCard(Modifier.weight(1f), "Karbo", "${plan.targetCarbs.toInt()} g", ForlaCarbs)
            OnbMacroCard(Modifier.weight(1f), "Lemak", "${plan.targetFat.toInt()} g", ForlaFat)
        }
        Spacer(Modifier.height(24.dp))
        ForlaButton("Mulai Pakai Forla 🎉", modifier = Modifier.fillMaxWidth()) { onStart() }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun OnbMacroCard(modifier: Modifier, label: String, value: String, color: Color) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).background(ForlaCard).padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(label, color = ForlaTextSub, fontSize = 12.sp)
    }
}

@Composable
private fun OnbNextButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = ForlaPrimary,
            contentColor = Color.Black,
            disabledContainerColor = ForlaCardAlt,
            disabledContentColor = ForlaTextSub
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) { Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
}

// Hitung target pakai rumus yang sama kayak halaman Profil (Mifflin-St Jeor + TDEE)
private fun buildPlan(gender: String, age: Int, height: Double, weight: Double,
                      activity: String, goal: String): UserProfile {
    val base = 10 * weight + 6.25 * height - 5 * age + (if (gender == "female") -161 else 5)
    val tdee = (base * activityFactor(activity)).toInt()
    val adjust = when (goal) {
        "lose" -> -500
        "gain" -> 400
        else -> 0
    }
    val cal = (tdee + adjust).coerceAtLeast(1200)
    val pro = if (weight > 0) (weight * 2).toInt() else 120
    val fat = ((cal * 0.25) / 9).toInt()
    val carb = ((cal - pro * 4 - fat * 9) / 4).coerceAtLeast(0)
    return UserProfile(
        id = 1,
        targetCalories = cal.toDouble(),
        targetProtein = pro.toDouble(),
        targetFat = fat.toDouble(),
        targetCarbs = carb.toDouble(),
        weightKg = weight,
        heightCm = height,
        age = age,
        gender = gender,
        activityLevel = activity
    )
}
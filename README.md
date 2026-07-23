# 🍎 Forla — Macro & Calorie Tracker

> **Cal AI versi lokal — full Bahasa Indonesia.**
> Aplikasi Android untuk melacak kalori & makronutrien harian dengan bantuan AI, dibuat khusus buat pengguna Indonesia.

![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)
![Language](https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white)
![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)
![minSdk](https://img.shields.io/badge/minSdk-26-orange)

---

## 📖 Tentang Aplikasi

**Forla** adalah aplikasi pelacak kalori & makronutrien (protein, karbohidrat, lemak) yang terinspirasi dari **Cal AI**, tapi dirancang **sepenuhnya dalam Bahasa Indonesia** dengan AI yang mengerti makanan lokal Indonesia.

Cukup **foto makananmu**, lalu AI akan otomatis mengisi estimasi kalori & nutrisinya. Forla juga melacak **langkah harian** lewat sensor perangkat dan memantau **progres berat badan** dari waktu ke waktu.

---

## ✨ Fitur Utama

- 📸 **Catat Makanan + AI** — foto makanan, AI (Gemini) otomatis mengisi kalori, protein, karbo, & lemak
- 🔥 **Dashboard Kalori** — ringkasan kalori masuk vs target harian + cincin makronutrien
- 📅 **Navigasi Tanggal** — klik tanggal untuk melihat progres hari-hari sebelumnya
- 👟 **Pelacak Langkah** — menghitung langkah harian lewat sensor perangkat
- ⚖️ **Progres Berat Badan** — catat & pantau riwayat berat badan
- 👤 **Profil & TDEE** — hitung kebutuhan kalori harian (TDEE) & target otomatis berdasarkan data diri
- 🎯 **Onboarding** — kuis awal untuk personalisasi target

---

## 🧩 Pemenuhan Syarat Project Akhir

| Syarat | Implementasi di Forla |
|---|---|
| **Minimal 1 sensor perangkat** | ✅ **Step Counter Sensor** — menghitung langkah harian pengguna secara real-time |
| **Minimal SQLite** | ✅ **Room (SQLite)** — menyimpan data makanan, berat badan, profil, & ringkasan harian |

---

## 🛠️ Teknologi

- **Bahasa:** Kotlin
- **UI:** Jetpack Compose (Material 3)
- **Database:** Room 2.7.1 (SQLite) + KSP
- **AI:** Google Gemini API (analisis foto makanan)
- **Sensor:** Android Step Counter Sensor
- **Arsitektur:** Single-Activity + Composable screens
- **minSdk:** 26 (Android 8.0) · **targetSdk:** 36

---

## 📱 Layar Aplikasi

1. **Beranda** — hero kalori harian, cincin makronutrien, strip tanggal interaktif
2. **Catat Makanan** — foto + AI, input manual, daftar makanan hari ini
3. **Aktivitas** — cincin langkah, estimasi kalori terbakar, BMR
4. **Progress** — berat badan terkini, input & riwayat berat
5. **Profil** — data diri, kalkulasi TDEE, target harian

---

## 🚀 Cara Build & Install

### Prasyarat
- Android Studio (versi terbaru)
- JDK 17+
- Perangkat/emulator Android 8.0 (API 26) ke atas

### Langkah
1. Clone repository ini:
   ```bash
   git clone https://github.com/aufa-code/Forla.git
   ```
2. Buka project di **Android Studio**.
3. Tambahkan **Gemini API Key** ke file `local.properties` (di root project):
   ```properties
   GEMINI_API_KEY=masukkan_api_key_kamu_di_sini
   ```
   > Dapatkan API key gratis di [Google AI Studio](https://aistudio.google.com/app/apikey).
4. Klik **Sync Project with Gradle Files**.
5. Jalankan lewat tombol **Run ▶️** atau build APK: **Build → Build APK(s)**.

### Install APK langsung (tanpa Android Studio)
File APK ada di:
```
app/build/outputs/apk/debug/app-debug.apk
```
Kirim file ini ke HP, lalu install (aktifkan "Install from unknown sources" bila diminta).

---

## 📸 Screenshot

> _Tambahkan screenshot aplikasi di sini (Beranda, Catat Makanan, Progress, dll)._

---

## 👥 Tim Pengembang

> _Tambahkan nama & NIM anggota kelompok di sini._

- Nama — NIM
- Nama — NIM

---

## 📄 Lisensi

Proyek ini dibuat untuk keperluan **Project Akhir** mata kuliah dan bersifat edukatif.

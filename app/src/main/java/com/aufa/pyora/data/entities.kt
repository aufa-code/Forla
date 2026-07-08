package com.aufa.pyora.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "places")
data class Place(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double = 100.0,
    val category: String? = null,
    val isFavorite: Boolean = false
)

@Entity(tableName = "activities")
data class ActivityRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,               // still / walking / running / vehicle
    val startTime: Long,
    val endTime: Long,
    val placeId: Long? = null,
    val distanceMeters: Double = 0.0,
    val stepCount: Int = 0
)

@Entity(tableName = "transactions")
data class MoneyTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val type: String,               // expense / income
    val category: String? = null,
    val placeId: Long? = null,
    val timestamp: Long,
    val note: String? = null,
    val source: String = "manual"   // manual / geofence / ocr
)

@Entity(tableName = "memories")
data class Memory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val photoUri: String? = null,
    val voiceNoteUri: String? = null,
    val note: String? = null,
    val placeId: Long? = null
)

@Entity(tableName = "daily_summary")
data class DailySummary(
    @PrimaryKey val date: String,   // format YYYY-MM-DD
    val totalSteps: Int = 0,
    val totalDistance: Double = 0.0,
    val totalExpense: Double = 0.0,
    val totalIncome: Double = 0.0,
    val summaryText: String? = null
)

// ===== FORLA: Macro & Calorie Tracker =====

@Entity(tableName = "food_entries")
data class FoodEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val name: String,
    val takaranText: String? = null,   // "3 centong nasi, 2 dada ayam"
    val volumeMl: Int? = null,         // buat minuman
    val photoUri: String? = null,
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val carbs: Double = 0.0,
    val mealType: String = "other",    // breakfast / lunch / dinner / snack
    val isDrink: Boolean = false
)

@Entity(tableName = "weight_entries")
data class WeightEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,                  // YYYY-MM-DD
    val weightKg: Double
)

@Entity(tableName = "profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1,       // selalu 1 baris (single row)
    val targetCalories: Double = 0.0,
    val targetProtein: Double = 0.0,
    val targetFat: Double = 0.0,
    val targetCarbs: Double = 0.0,
    val weightKg: Double = 0.0,
    val heightCm: Double = 0.0,
    val age: Int = 0,
    val gender: String = "male",       // male / female
    val activityLevel: String = "moderate" // sedentary/light/moderate/active/veryActive
)
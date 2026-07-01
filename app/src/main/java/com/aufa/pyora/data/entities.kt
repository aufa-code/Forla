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
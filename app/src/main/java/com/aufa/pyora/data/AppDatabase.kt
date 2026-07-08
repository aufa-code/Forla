package com.aufa.pyora.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Place::class,
        ActivityRecord::class,
        MoneyTransaction::class,
        Memory::class,
        DailySummary::class,
        // ===== FORLA =====
        FoodEntry::class,
        WeightEntry::class,
        UserProfile::class
    ],
    version = 2,               // ⬆️ dibump dari 1 → 2
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun placeDao(): PlaceDao
    abstract fun activityDao(): ActivityDao
    abstract fun transactionDao(): TransactionDao
    abstract fun memoryDao(): MemoryDao
    abstract fun dailySummaryDao(): DailySummaryDao
    // ===== FORLA =====
    abstract fun foodDao(): FoodDao
    abstract fun weightDao(): WeightDao
    abstract fun profileDao(): ProfileDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "forla.db"                 // 🔄 ganti nama DB → fresh start
                )
                    .fallbackToDestructiveMigration()  // aman buat fase development
                    .build().also { INSTANCE = it }
            }
        }
    }
}
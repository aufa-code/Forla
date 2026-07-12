package com.aufa.pyora.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DailySummary::class,
        FoodEntry::class,
        WeightEntry::class,
        UserProfile::class
    ],
    version = 3,           // ⬆️ WAJIB bump 2 → 3 (skema berubah)
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dailySummaryDao(): DailySummaryDao
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
                    "forla.db"
                )
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}
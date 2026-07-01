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
        DailySummary::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun placeDao(): PlaceDao
    abstract fun activityDao(): ActivityDao
    abstract fun transactionDao(): TransactionDao
    abstract fun memoryDao(): MemoryDao
    abstract fun dailySummaryDao(): DailySummaryDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pyora.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
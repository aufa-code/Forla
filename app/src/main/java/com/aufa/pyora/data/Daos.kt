package com.aufa.pyora.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Update
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DailySummaryDao {
    @Upsert
    suspend fun upsert(summary: DailySummary)

    @Query("SELECT * FROM daily_summary WHERE date = :date")
    suspend fun getByDate(date: String): DailySummary?

    @Query("SELECT * FROM daily_summary ORDER BY date DESC")
    fun getAll(): Flow<List<DailySummary>>
}

// ===== FORLA DAOs =====

@Dao
interface FoodDao {
    @Insert
    suspend fun insert(entry: FoodEntry): Long

    @Update
    suspend fun update(entry: FoodEntry)

    @Delete
    suspend fun delete(entry: FoodEntry)

    @Query("SELECT * FROM food_entries ORDER BY timestamp DESC")
    fun getAll(): Flow<List<FoodEntry>>

    @Query("SELECT * FROM food_entries WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp DESC")
    fun getBetween(start: Long, end: Long): Flow<List<FoodEntry>>
}

@Dao
interface WeightDao {
    @Insert
    suspend fun insert(entry: WeightEntry): Long

    @Delete
    suspend fun delete(entry: WeightEntry)

    @Query("SELECT * FROM weight_entries ORDER BY date DESC")
    fun getAll(): Flow<List<WeightEntry>>
}

@Dao
interface ProfileDao {
    @Upsert
    suspend fun upsert(profile: UserProfile)

    @Query("SELECT * FROM profile WHERE id = 1")
    fun get(): Flow<UserProfile?>

    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun getOnce(): UserProfile?
}
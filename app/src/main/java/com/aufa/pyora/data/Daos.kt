package com.aufa.pyora.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(place: Place): Long

    @Query("SELECT * FROM places")
    fun getAll(): Flow<List<Place>>
}

@Dao
interface ActivityDao {
    @Insert
    suspend fun insert(activity: ActivityRecord): Long

    @Query("SELECT * FROM activities ORDER BY startTime DESC")
    fun getAll(): Flow<List<ActivityRecord>>
}

@Dao
interface TransactionDao {
    @Insert
    suspend fun insert(transaction: MoneyTransaction): Long

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAll(): Flow<List<MoneyTransaction>>
}

@Dao
interface MemoryDao {
    @Insert
    suspend fun insert(memory: Memory): Long

    @Query("SELECT * FROM memories ORDER BY timestamp DESC")
    fun getAll(): Flow<List<Memory>>
}

@Dao
interface DailySummaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(summary: DailySummary)

    @Query("SELECT * FROM daily_summary WHERE date = :date")
    suspend fun getByDate(date: String): DailySummary?

    @Query("SELECT * FROM daily_summary ORDER BY date DESC")
    fun getAll(): Flow<List<DailySummary>>
}
package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Query("SELECT * FROM level_progress ORDER BY levelNumber ASC")
    fun observeAllProgress(): Flow<List<LevelProgressEntity>>

    @Query("SELECT * FROM level_progress ORDER BY levelNumber ASC")
    suspend fun getAllProgressSnapshot(): List<LevelProgressEntity>

    @Query("SELECT * FROM level_progress WHERE levelNumber = :levelNumber LIMIT 1")
    suspend fun getProgressForLevel(levelNumber: Int): LevelProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(entity: LevelProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAllProgress(entities: List<LevelProgressEntity>)

    @Query("SELECT * FROM active_session WHERE id = 1 LIMIT 1")
    suspend fun getActiveSession(): ActiveSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveActiveSession(session: ActiveSessionEntity)

    @Query("SELECT * FROM daily_challenges ORDER BY dateKey DESC")
    fun observeAllDailyChallenges(): Flow<List<DailyChallengeEntity>>

    @Query("SELECT * FROM daily_challenges ORDER BY dateKey DESC")
    suspend fun getAllDailyChallengesSnapshot(): List<DailyChallengeEntity>

    @Query("SELECT * FROM daily_challenges WHERE dateKey = :dateKey LIMIT 1")
    suspend fun getDailyChallenge(dateKey: String): DailyChallengeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyChallenge(entity: DailyChallengeEntity)
}

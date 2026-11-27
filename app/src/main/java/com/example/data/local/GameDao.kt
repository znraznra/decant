package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    @Query("DELETE FROM level_progress")
    suspend fun clearAllProgress()

    @Query("SELECT * FROM active_session WHERE id = 1 LIMIT 1")
    suspend fun getActiveSession(): ActiveSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveActiveSession(session: ActiveSessionEntity)

    @Query("DELETE FROM active_session")
    suspend fun clearActiveSession()

    @Query("SELECT * FROM daily_challenges ORDER BY dateKey DESC")
    fun observeAllDailyChallenges(): Flow<List<DailyChallengeEntity>>

    @Query("SELECT * FROM daily_challenges ORDER BY dateKey DESC")
    suspend fun getAllDailyChallengesSnapshot(): List<DailyChallengeEntity>

    @Query("SELECT * FROM daily_challenges WHERE dateKey = :dateKey LIMIT 1")
    suspend fun getDailyChallenge(dateKey: String): DailyChallengeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyChallenge(entity: DailyChallengeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAllDailyChallenges(entities: List<DailyChallengeEntity>)

    @Query("DELETE FROM daily_challenges")
    suspend fun clearAllDailyChallenges()

    @Query("SELECT * FROM tier_records ORDER BY tierOrdinal ASC")
    fun observeAllTierRecords(): Flow<List<DifficultyRecordEntity>>

    @Query("SELECT * FROM tier_records ORDER BY tierOrdinal ASC")
    suspend fun getAllTierRecordsSnapshot(): List<DifficultyRecordEntity>

    @Query("SELECT * FROM tier_records WHERE tierOrdinal = :tierOrdinal LIMIT 1")
    suspend fun getTierRecord(tierOrdinal: Int): DifficultyRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTierRecord(record: DifficultyRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAllTierRecords(records: List<DifficultyRecordEntity>)

    @Query("DELETE FROM tier_records")
    suspend fun clearAllTierRecords()

    @Transaction
    suspend fun replaceDatabaseState(
        progress: List<LevelProgressEntity>,
        activeSession: ActiveSessionEntity?,
        dailyChallenges: List<DailyChallengeEntity>,
        tierRecords: List<DifficultyRecordEntity> = emptyList()
    ) {
        clearAllProgress()
        clearActiveSession()
        clearAllDailyChallenges()
        clearAllTierRecords()
        if (progress.isNotEmpty()) {
            upsertAllProgress(progress)
        }
        if (activeSession != null) {
            saveActiveSession(activeSession)
        }
        if (dailyChallenges.isNotEmpty()) {
            upsertAllDailyChallenges(dailyChallenges)
        }
        if (tierRecords.isNotEmpty()) {
            upsertAllTierRecords(tierRecords)
        }
    }
}

package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.MAX_UNDO_PER_STAGE

@Entity(tableName = "level_progress")
data class LevelProgressEntity(
    @PrimaryKey val levelNumber: Int,
    val isCompleted: Boolean,
    val bestMoves: Int?,
    val completedAtMs: Long
)

@Entity(tableName = "active_session")
data class ActiveSessionEntity(
    @PrimaryKey val id: Int = 1,
    val currentLevel: Int,
    val themeOrdinal: Int,
    val tubesJson: String,
    val initialTubesJson: String = "",
    val undoStackJson: String,
    val remainingUndos: Int = MAX_UNDO_PER_STAGE,
    val extraTubeUsed: Boolean,
    val moveCount: Int,
    val updatedAtMs: Long
)

@Entity(tableName = "daily_challenges")
data class DailyChallengeEntity(
    @PrimaryKey val dateKey: String,
    val tierOrdinal: Int,
    val colorCount: Int,
    val themeOrdinal: Int,
    val initialTubesJson: String,
    val currentTubesJson: String,
    val undoStackJson: String,
    val remainingUndos: Int = MAX_UNDO_PER_STAGE,
    val extraTubeUsed: Boolean = false,
    val moveCount: Int = 0,
    val isCompleted: Boolean = false,
    val bestMoves: Int? = null,
    val completedAtMs: Long? = null,
    val updatedAtMs: Long = 0L
)

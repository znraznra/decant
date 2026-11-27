package com.example.domain

import kotlinx.serialization.Serializable

const val TUBE_CAPACITY = 4
const val MAX_COLOR_COUNT = 12
const val MAX_UNDO_PER_STAGE = 5

@Serializable
data class TubeModel(
    val id: Int,
    val segments: List<Int>,
    val isExtraTube: Boolean = false
) {
    val isEmpty: Boolean get() = segments.isEmpty()
    val isFull: Boolean get() = segments.size >= TUBE_CAPACITY
    val topColor: Int? get() = segments.lastOrNull()
    val isCompleted: Boolean
        get() = segments.size == TUBE_CAPACITY && segments.all { it == segments.first() }
}

@Serializable
data class PourMove(
    val fromIndex: Int,
    val toIndex: Int,
    val colorId: Int,
    val unitsMoved: Int
)

data class PourAnimationState(
    val fromIndex: Int,
    val toIndex: Int,
    val colorId: Int,
    val units: Int,
    val progress: Float,
    val isReverse: Boolean
)

enum class DifficultyTier(val label: String) {
    WARMUP("Warmup"),
    CLASSIC("Classic"),
    TRICKSTER("Trickster"),
    MASTER("Master"),
    ABYSSAL("Abyssal")
}

enum class StudioThemeId(val displayName: String, val tagline: String) {
    CLEAN_GLASS("Clean Glass", "Bright minimalist studio"),
    COZY_PASTEL("Cozy Pastel", "Warm apothecary flasks"),
    DARK_NEON("Dark Neon", "Luminous obsidian lab"),
    BOTANICAL_ZEN("Botanical Zen", "Earthy stoneware vials")
}

enum class ColorblindPattern {
    CIRCLE,
    TRIANGLE,
    DIAMOND,
    WAVES,
    STRIPES,
    DOTS,
    STAR,
    PLUS,
    RING,
    HEXAGON,
    CHEVRON,
    GRID
}

data class LevelDifficultySpec(
    val tier: DifficultyTier,
    val colorCount: Int,
    val emptyTubeCount: Int,
    val totalTubeCount: Int = colorCount + emptyTubeCount
)

data class LevelSpec(
    val levelNumber: Int,
    val tier: DifficultyTier,
    val colorCount: Int,
    val tubes: List<TubeModel>,
    val defaultTheme: StudioThemeId
)

data class DailyChallengeSpec(
    val dateKey: String,
    val tier: DifficultyTier,
    val colorCount: Int,
    val tubes: List<TubeModel>,
    val defaultTheme: StudioThemeId
)

@Serializable
data class CloudSavePayload(
    val highestUnlockedLevel: Int = 1,
    val completedLevels: Map<Int, Int> = emptyMap(),
    val completedDailyMoves: Map<String, Int> = emptyMap(),
    val updatedAtMs: Long = 0L
)

sealed interface SyncStatus {
    data object Disabled : SyncStatus
    data object NeedsAppId : SyncStatus
    data object SignedOut : SyncStatus
    data object Syncing : SyncStatus
    data class Synced(val lastSyncMs: Long) : SyncStatus
    data class Error(val message: String) : SyncStatus
}

data class DailyChallengeCardSummary(
    val dateKey: String = "",
    val displayDate: String = "",
    val tier: DifficultyTier = DifficultyTier.TRICKSTER,
    val colorCount: Int = 7,
    val isCompleted: Boolean = false,
    val inProgress: Boolean = false,
    val moveCount: Int = 0,
    val bestMoves: Int? = null,
    val currentStreak: Int = 0
)

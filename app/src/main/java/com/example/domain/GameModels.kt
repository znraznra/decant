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
data class BackupActiveSession(
    val currentLevel: Int,
    val themeOrdinal: Int,
    val tubesJson: String,
    val initialTubesJson: String = "",
    val undoStackJson: String,
    val remainingUndos: Int = MAX_UNDO_PER_STAGE,
    val extraTubeUsed: Boolean,
    val moveCount: Int,
    val updatedAtMs: Long = 0L,
    val elapsedTimeMs: Long = 0L
)

@Serializable
data class BackupDailyChallenge(
    val dateKey: String,
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
    val updatedAtMs: Long = 0L,
    val elapsedTimeMs: Long = 0L
)

@Serializable
data class ProgressTransferPayload(
    val schemaVersion: Int = 2,
    val appId: String = "decant",
    val highestUnlockedLevel: Int = 1,
    val completedLevels: Map<Int, Int> = emptyMap(),
    val completedDailyMoves: Map<String, Int> = emptyMap(),
    val bestLevelTimes: Map<Int, Long> = emptyMap(),
    val bestTierTimes: Map<Int, Long> = emptyMap(),
    val activeSession: BackupActiveSession? = null,
    val dailyChallenges: List<BackupDailyChallenge> = emptyList(),
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val colorblindEnabled: Boolean = false,
    val randomizeThemePerLevel: Boolean = true,
    val timerEnabled: Boolean = true,
    val pinnedThemeOrdinal: Int = 0,
    val exportedAtMs: Long = 0L
)

data class LevelCompletionResult(
    val isNewBestMoves: Boolean,
    val isNewBestTime: Boolean = false,
    val bestTierTimeMs: Long? = null
)

fun formatElapsedDuration(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

data class ImportSummary(
    val newLevelsAdded: Int,
    val newDailyAdded: Int,
    val highestUnlockedLevel: Int,
    val restoredActiveSession: Boolean = false
)

enum class TransferTransport(val label: String) {
    WIFI_LOCAL("Local Wi-Fi"),
    BLUETOOTH("Bluetooth")
}

data class DiscoveredPeer(
    val id: String,
    val name: String,
    val transport: TransferTransport,
    val host: String? = null,
    val port: Int = 0,
    val bluetoothAddress: String? = null
)

sealed interface TransferUiState {
    data object Idle : TransferUiState
    data class Discovering(val transport: TransferTransport) : TransferUiState
    data class WaitingToReceive(val transport: TransferTransport, val deviceName: String) : TransferUiState
    data class Transferring(val label: String) : TransferUiState
    data class Success(val message: String) : TransferUiState
    data class Error(val message: String) : TransferUiState
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

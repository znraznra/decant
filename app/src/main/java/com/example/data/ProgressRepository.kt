package com.example.data

import android.app.Activity
import com.example.data.cloud.PlayGamesSaveManager
import com.example.data.local.ActiveSessionEntity
import com.example.data.local.DailyChallengeEntity
import com.example.data.local.GameDao
import com.example.data.local.LevelProgressEntity
import com.example.data.prefs.UserPreferences
import com.example.data.prefs.UserPreferencesRepository
import com.example.domain.CloudSavePayload
import com.example.domain.DailyChallengeSpec
import com.example.domain.DifficultyTier
import com.example.domain.MAX_UNDO_PER_STAGE
import com.example.domain.PourMove
import com.example.domain.PuzzleEngine
import com.example.domain.StudioThemeId
import com.example.domain.SyncStatus
import com.example.domain.TubeModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface GameProgressStore {
    val syncStatus: StateFlow<SyncStatus>
    val allProgressFlow: Flow<List<LevelProgressEntity>>
    val allDailyChallengesFlow: Flow<List<DailyChallengeEntity>>
    val preferencesFlow: Flow<UserPreferences>

    suspend fun loadActiveSession(): ProgressRepository.ActiveSessionSnapshot?
    suspend fun saveActiveSession(
        currentLevel: Int,
        theme: StudioThemeId,
        tubes: List<TubeModel>,
        undoStack: List<PourMove>,
        remainingUndos: Int = MAX_UNDO_PER_STAGE,
        extraTubeUsed: Boolean,
        moveCount: Int,
        initialTubes: List<TubeModel> = emptyList()
    )
    suspend fun markLevelCompleted(levelNumber: Int, moves: Int): Boolean

    suspend fun loadOrCreateDailyChallenge(
        dateKey: String,
        generator: suspend (String) -> DailyChallengeSpec
    ): ProgressRepository.DailyChallengeSnapshot

    suspend fun saveDailyChallengeProgress(
        dateKey: String,
        theme: StudioThemeId,
        currentTubes: List<TubeModel>,
        undoStack: List<PourMove>,
        remainingUndos: Int,
        extraTubeUsed: Boolean,
        moveCount: Int
    )

    suspend fun markDailyChallengeCompleted(dateKey: String, moves: Int): Boolean

    suspend fun syncWithCloudIfEnabled(activity: Activity?, interactiveSignIn: Boolean = false): Boolean
    suspend fun setPlayGamesSyncEnabled(enabled: Boolean)
    suspend fun setSoundEnabled(enabled: Boolean)
    suspend fun setHapticsEnabled(enabled: Boolean)
    suspend fun setColorblindEnabled(enabled: Boolean)
    suspend fun setRandomizeThemePerLevel(enabled: Boolean)
    suspend fun setPinnedTheme(theme: StudioThemeId)
}

class ProgressRepository(
    private val dao: GameDao,
    private val preferences: UserPreferencesRepository,
    private val playGamesSave: PlayGamesSaveManager
) : GameProgressStore {
    private val json by lazy {
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }

    private val _syncStatus = MutableStateFlow<SyncStatus>(
        if (playGamesSave.isConfigured()) SyncStatus.SignedOut else SyncStatus.NeedsAppId
    )
    override val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    override val allProgressFlow: Flow<List<LevelProgressEntity>> = dao.observeAllProgress()
    override val allDailyChallengesFlow: Flow<List<DailyChallengeEntity>> = dao.observeAllDailyChallenges()
    override val preferencesFlow: Flow<UserPreferences> = preferences.preferencesFlow

    override suspend fun loadActiveSession(): ActiveSessionSnapshot? {
        val entity = dao.getActiveSession() ?: return null
        val tubes = runCatching { json.decodeFromString<List<TubeModel>>(entity.tubesJson) }.getOrNull()
            ?: return null
        val undoStack = runCatching { json.decodeFromString<List<PourMove>>(entity.undoStackJson) }
            .getOrDefault(emptyList())
        val storedInitial = if (entity.initialTubesJson.isNotBlank()) {
            runCatching { json.decodeFromString<List<TubeModel>>(entity.initialTubesJson) }.getOrNull()
        } else {
            null
        }
        val initialTubes = if (!storedInitial.isNullOrEmpty()) {
            storedInitial
        } else {
            // Reconstruct exact initial state from undoStack for legacy rows without regenerating
            var rebuilt = tubes
            for (i in undoStack.lastIndex downTo 0) {
                rebuilt = PuzzleEngine.applyReverseMove(rebuilt, undoStack[i])
            }
            if (entity.extraTubeUsed) rebuilt.filterNot { it.isExtraTube } else rebuilt
        }
        val theme = StudioThemeId.entries[entity.themeOrdinal.coerceIn(0, StudioThemeId.entries.lastIndex)]
        return ActiveSessionSnapshot(
            currentLevel = entity.currentLevel,
            theme = theme,
            tubes = tubes,
            undoStack = undoStack,
            remainingUndos = entity.remainingUndos.coerceIn(0, MAX_UNDO_PER_STAGE),
            extraTubeUsed = entity.extraTubeUsed,
            moveCount = entity.moveCount,
            initialTubes = initialTubes
        )
    }

    override suspend fun saveActiveSession(
        currentLevel: Int,
        theme: StudioThemeId,
        tubes: List<TubeModel>,
        undoStack: List<PourMove>,
        remainingUndos: Int,
        extraTubeUsed: Boolean,
        moveCount: Int,
        initialTubes: List<TubeModel>
    ) {
        val resolvedInitial = initialTubes.ifEmpty { tubes }
        dao.saveActiveSession(
            ActiveSessionEntity(
                id = 1,
                currentLevel = currentLevel,
                themeOrdinal = theme.ordinal,
                tubesJson = json.encodeToString(tubes),
                initialTubesJson = json.encodeToString(resolvedInitial),
                undoStackJson = json.encodeToString(undoStack),
                remainingUndos = remainingUndos.coerceIn(0, MAX_UNDO_PER_STAGE),
                extraTubeUsed = extraTubeUsed,
                moveCount = moveCount,
                updatedAtMs = System.currentTimeMillis()
            )
        )
    }

    override suspend fun markLevelCompleted(levelNumber: Int, moves: Int): Boolean {
        val existing = dao.getProgressForLevel(levelNumber)
        val isNewBest = existing?.bestMoves == null || moves < existing.bestMoves
        val bestMoves = if (existing?.bestMoves == null) moves else minOf(existing.bestMoves, moves)

        dao.upsertProgress(
            LevelProgressEntity(
                levelNumber = levelNumber,
                isCompleted = true,
                bestMoves = bestMoves,
                completedAtMs = System.currentTimeMillis()
            )
        )
        preferences.setHighestUnlockedLevel(levelNumber + 1)
        return isNewBest
    }

    override suspend fun loadOrCreateDailyChallenge(
        dateKey: String,
        generator: suspend (String) -> DailyChallengeSpec
    ): DailyChallengeSnapshot {
        val existing = dao.getDailyChallenge(dateKey)
        if (existing != null) {
            val initialTubes = runCatching {
                json.decodeFromString<List<TubeModel>>(existing.initialTubesJson)
            }.getOrNull()
            val currentTubes = runCatching {
                json.decodeFromString<List<TubeModel>>(existing.currentTubesJson)
            }.getOrNull()
            if (initialTubes != null && initialTubes.isNotEmpty() && currentTubes != null && currentTubes.isNotEmpty()) {
                val undoStack = runCatching {
                    json.decodeFromString<List<PourMove>>(existing.undoStackJson)
                }.getOrDefault(emptyList())
                val tier = DifficultyTier.entries[existing.tierOrdinal.coerceIn(0, DifficultyTier.entries.lastIndex)]
                val theme = StudioThemeId.entries[existing.themeOrdinal.coerceIn(0, StudioThemeId.entries.lastIndex)]
                return DailyChallengeSnapshot(
                    dateKey = existing.dateKey,
                    tier = tier,
                    colorCount = existing.colorCount,
                    theme = theme,
                    initialTubes = initialTubes,
                    currentTubes = currentTubes,
                    undoStack = undoStack,
                    remainingUndos = existing.remainingUndos.coerceIn(0, MAX_UNDO_PER_STAGE),
                    extraTubeUsed = existing.extraTubeUsed,
                    moveCount = existing.moveCount,
                    isCompleted = existing.isCompleted,
                    bestMoves = existing.bestMoves
                )
            }
        }

        val spec = generator(dateKey)
        val initialJson = json.encodeToString(spec.tubes)
        val entity = DailyChallengeEntity(
            dateKey = spec.dateKey,
            tierOrdinal = spec.tier.ordinal,
            colorCount = spec.colorCount,
            themeOrdinal = spec.defaultTheme.ordinal,
            initialTubesJson = initialJson,
            currentTubesJson = initialJson,
            undoStackJson = "[]",
            remainingUndos = MAX_UNDO_PER_STAGE,
            extraTubeUsed = false,
            moveCount = 0,
            isCompleted = false,
            bestMoves = null,
            completedAtMs = null,
            updatedAtMs = System.currentTimeMillis()
        )
        dao.upsertDailyChallenge(entity)
        return DailyChallengeSnapshot(
            dateKey = spec.dateKey,
            tier = spec.tier,
            colorCount = spec.colorCount,
            theme = spec.defaultTheme,
            initialTubes = spec.tubes,
            currentTubes = spec.tubes,
            undoStack = emptyList(),
            remainingUndos = MAX_UNDO_PER_STAGE,
            extraTubeUsed = false,
            moveCount = 0,
            isCompleted = false,
            bestMoves = null
        )
    }

    override suspend fun saveDailyChallengeProgress(
        dateKey: String,
        theme: StudioThemeId,
        currentTubes: List<TubeModel>,
        undoStack: List<PourMove>,
        remainingUndos: Int,
        extraTubeUsed: Boolean,
        moveCount: Int
    ) {
        val existing = dao.getDailyChallenge(dateKey) ?: return
        dao.upsertDailyChallenge(
            existing.copy(
                themeOrdinal = theme.ordinal,
                currentTubesJson = json.encodeToString(currentTubes),
                undoStackJson = json.encodeToString(undoStack),
                remainingUndos = remainingUndos.coerceIn(0, MAX_UNDO_PER_STAGE),
                extraTubeUsed = extraTubeUsed,
                moveCount = moveCount,
                updatedAtMs = System.currentTimeMillis()
            )
        )
    }

    override suspend fun markDailyChallengeCompleted(dateKey: String, moves: Int): Boolean {
        val existing = dao.getDailyChallenge(dateKey) ?: return false
        val isNewBest = existing.bestMoves == null || moves < existing.bestMoves
        val bestMoves = if (existing.bestMoves == null) moves else minOf(existing.bestMoves, moves)
        val nowMs = System.currentTimeMillis()
        dao.upsertDailyChallenge(
            existing.copy(
                isCompleted = true,
                bestMoves = bestMoves,
                completedAtMs = existing.completedAtMs ?: nowMs,
                updatedAtMs = nowMs
            )
        )
        return isNewBest
    }

    override suspend fun syncWithCloudIfEnabled(
        activity: Activity?,
        interactiveSignIn: Boolean
    ): Boolean {
        val prefs = preferences.preferencesFlow.first()
        if (!prefs.playGamesSyncEnabled) {
            _syncStatus.value = SyncStatus.Disabled
            return false
        }
        if (!playGamesSave.isConfigured()) {
            _syncStatus.value = SyncStatus.NeedsAppId
            return false
        }
        if (activity == null) return false

        _syncStatus.value = SyncStatus.Syncing
        val localPayload = buildLocalCloudPayload(prefs.highestUnlockedLevel)
        val result = playGamesSave.syncProgress(
            activity = activity,
            localPayload = localPayload,
            interactiveSignIn = interactiveSignIn
        )
        _syncStatus.value = result.status

        val merged = result.mergedPayload ?: return false
        applyMergedPayloadToLocal(merged)
        return merged.highestUnlockedLevel > localPayload.highestUnlockedLevel ||
            merged.completedLevels.size > localPayload.completedLevels.size ||
            merged.completedDailyMoves.size > localPayload.completedDailyMoves.size
    }

    private suspend fun buildLocalCloudPayload(highestUnlocked: Int): CloudSavePayload {
        val completed = dao.getAllProgressSnapshot()
            .filter { it.isCompleted }
            .associate { it.levelNumber to (it.bestMoves ?: 0) }
        val completedDaily = dao.getAllDailyChallengesSnapshot()
            .filter { it.isCompleted }
            .associate { it.dateKey to (it.bestMoves ?: it.moveCount) }
        val maxCompleted = completed.keys.maxOrNull() ?: 0
        return CloudSavePayload(
            highestUnlockedLevel = maxOf(highestUnlocked, maxCompleted + 1),
            completedLevels = completed,
            completedDailyMoves = completedDaily,
            updatedAtMs = System.currentTimeMillis()
        )
    }

    private suspend fun applyMergedPayloadToLocal(merged: CloudSavePayload) {
        val entities = merged.completedLevels.map { (level, moves) ->
            LevelProgressEntity(
                levelNumber = level,
                isCompleted = true,
                bestMoves = moves.takeIf { it > 0 },
                completedAtMs = merged.updatedAtMs
            )
        }
        if (entities.isNotEmpty()) {
            dao.upsertAllProgress(entities)
        }
        for ((dateKey, moves) in merged.completedDailyMoves) {
            val existing = dao.getDailyChallenge(dateKey)
            if (existing == null) {
                val spec = PuzzleEngine.generateDailyChallenge(dateKey)
                val initialJson = json.encodeToString(spec.tubes)
                dao.upsertDailyChallenge(
                    DailyChallengeEntity(
                        dateKey = dateKey,
                        tierOrdinal = spec.tier.ordinal,
                        colorCount = spec.colorCount,
                        themeOrdinal = spec.defaultTheme.ordinal,
                        initialTubesJson = initialJson,
                        currentTubesJson = initialJson,
                        undoStackJson = "[]",
                        remainingUndos = MAX_UNDO_PER_STAGE,
                        extraTubeUsed = false,
                        moveCount = moves,
                        isCompleted = true,
                        bestMoves = moves.takeIf { it > 0 },
                        completedAtMs = merged.updatedAtMs,
                        updatedAtMs = merged.updatedAtMs
                    )
                )
            } else if (!existing.isCompleted || (moves > 0 && (existing.bestMoves == null || moves < existing.bestMoves))) {
                val best = when {
                    existing.bestMoves == null -> moves.takeIf { it > 0 }
                    moves > 0 -> minOf(existing.bestMoves, moves)
                    else -> existing.bestMoves
                }
                dao.upsertDailyChallenge(
                    existing.copy(
                        isCompleted = true,
                        bestMoves = best,
                        completedAtMs = existing.completedAtMs ?: merged.updatedAtMs,
                        updatedAtMs = merged.updatedAtMs
                    )
                )
            }
        }
        preferences.setHighestUnlockedLevel(merged.highestUnlockedLevel)
    }

    override suspend fun setPlayGamesSyncEnabled(enabled: Boolean) {
        preferences.setPlayGamesSyncEnabled(enabled)
        _syncStatus.value = when {
            !enabled -> SyncStatus.Disabled
            !playGamesSave.isConfigured() -> SyncStatus.NeedsAppId
            else -> SyncStatus.SignedOut
        }
    }

    override suspend fun setSoundEnabled(enabled: Boolean) = preferences.setSoundEnabled(enabled)
    override suspend fun setHapticsEnabled(enabled: Boolean) = preferences.setHapticsEnabled(enabled)
    override suspend fun setColorblindEnabled(enabled: Boolean) = preferences.setColorblindEnabled(enabled)
    override suspend fun setRandomizeThemePerLevel(enabled: Boolean) = preferences.setRandomizeThemePerLevel(enabled)
    override suspend fun setPinnedTheme(theme: StudioThemeId) = preferences.setPinnedTheme(theme)

    data class ActiveSessionSnapshot(
        val currentLevel: Int,
        val theme: StudioThemeId,
        val tubes: List<TubeModel>,
        val undoStack: List<PourMove>,
        val remainingUndos: Int = MAX_UNDO_PER_STAGE,
        val extraTubeUsed: Boolean,
        val moveCount: Int,
        val initialTubes: List<TubeModel> = emptyList()
    )

    data class DailyChallengeSnapshot(
        val dateKey: String,
        val tier: DifficultyTier,
        val colorCount: Int,
        val theme: StudioThemeId,
        val initialTubes: List<TubeModel>,
        val currentTubes: List<TubeModel>,
        val undoStack: List<PourMove>,
        val remainingUndos: Int = MAX_UNDO_PER_STAGE,
        val extraTubeUsed: Boolean,
        val moveCount: Int,
        val isCompleted: Boolean,
        val bestMoves: Int?
    )
}

package com.example.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.local.ActiveSessionEntity
import com.example.data.local.DailyChallengeEntity
import com.example.data.local.DifficultyRecordEntity
import com.example.data.local.GameDao
import com.example.data.local.LevelProgressEntity
import com.example.data.prefs.UserPreferences
import com.example.data.prefs.UserPreferencesRepository
import com.example.data.transfer.ProgressTransferCodec
import com.example.domain.BackupActiveSession
import com.example.domain.BackupDailyChallenge
import com.example.domain.DailyChallengeSpec
import com.example.domain.DifficultyTier
import com.example.domain.ImportSummary
import com.example.domain.LevelCompletionResult
import com.example.domain.MAX_UNDO_PER_STAGE
import com.example.domain.PourMove
import com.example.domain.ProgressTransferPayload
import com.example.domain.PuzzleEngine
import com.example.domain.StudioThemeId
import com.example.domain.TubeModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface GameProgressStore {
    val allProgressFlow: Flow<List<LevelProgressEntity>>
    val allDailyChallengesFlow: Flow<List<DailyChallengeEntity>>
    val allTierRecordsFlow: Flow<List<DifficultyRecordEntity>>
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
        initialTubes: List<TubeModel> = emptyList(),
        elapsedTimeMs: Long = 0L
    )
    suspend fun markLevelCompleted(
        levelNumber: Int,
        moves: Int,
        elapsedTimeMs: Long = 0L
    ): LevelCompletionResult

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
        moveCount: Int,
        elapsedTimeMs: Long = 0L
    )

    suspend fun markDailyChallengeCompleted(
        dateKey: String,
        moves: Int,
        elapsedTimeMs: Long = 0L
    ): LevelCompletionResult

    suspend fun exportBackupPayload(): ProgressTransferPayload
    suspend fun importBackupPayload(
        incoming: ProgressTransferPayload,
        importSettings: Boolean = true
    ): ImportSummary
    suspend fun replaceWithBackupPayload(incoming: ProgressTransferPayload): ImportSummary

    suspend fun setSoundEnabled(enabled: Boolean)
    suspend fun setHapticsEnabled(enabled: Boolean)
    suspend fun setColorblindEnabled(enabled: Boolean)
    suspend fun setRandomizeThemePerLevel(enabled: Boolean)
    suspend fun setTimerEnabled(enabled: Boolean)
    suspend fun setPinnedTheme(theme: StudioThemeId)
}

class ProgressRepository(
    private val dao: GameDao,
    private val preferences: UserPreferencesRepository
) : GameProgressStore {
    private val json by lazy {
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }

    override val allProgressFlow: Flow<List<LevelProgressEntity>> = dao.observeAllProgress()
    override val allDailyChallengesFlow: Flow<List<DailyChallengeEntity>> = dao.observeAllDailyChallenges()
    override val allTierRecordsFlow: Flow<List<DifficultyRecordEntity>> = dao.observeAllTierRecords()
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
            initialTubes = initialTubes,
            elapsedTimeMs = entity.elapsedTimeMs.coerceAtLeast(0L)
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
        initialTubes: List<TubeModel>,
        elapsedTimeMs: Long
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
                updatedAtMs = System.currentTimeMillis(),
                elapsedTimeMs = elapsedTimeMs.coerceAtLeast(0L)
            )
        )
    }

    override suspend fun markLevelCompleted(
        levelNumber: Int,
        moves: Int,
        elapsedTimeMs: Long
    ): LevelCompletionResult {
        val existing = dao.getProgressForLevel(levelNumber)
        val isNewBestMoves = existing?.bestMoves == null || moves < existing.bestMoves
        val bestMoves = if (existing?.bestMoves == null) moves else minOf(existing.bestMoves, moves)

        val validTime = elapsedTimeMs.takeIf { it > 0L }
        val bestLevelTime = when {
            validTime == null -> existing?.bestTimeMs
            existing?.bestTimeMs == null -> validTime
            else -> minOf(existing.bestTimeMs, validTime)
        }
        val nowMs = System.currentTimeMillis()

        dao.upsertProgress(
            LevelProgressEntity(
                levelNumber = levelNumber,
                isCompleted = true,
                bestMoves = bestMoves,
                completedAtMs = nowMs,
                bestTimeMs = bestLevelTime
            )
        )
        preferences.setHighestUnlockedLevel(levelNumber + 1)

        val (tier, _) = PuzzleEngine.difficultyForLevel(levelNumber)
        val tierResult = recordTierTimeIfBest(tier, validTime, levelNumber, nowMs)
        return LevelCompletionResult(
            isNewBestMoves = isNewBestMoves,
            isNewBestTime = tierResult.first,
            bestTierTimeMs = tierResult.second
        )
    }

    private suspend fun recordTierTimeIfBest(
        tier: DifficultyTier,
        validTimeMs: Long?,
        levelNumber: Int,
        nowMs: Long
    ): Pair<Boolean, Long?> {
        val existingRecord = dao.getTierRecord(tier.ordinal)
        if (validTimeMs == null) {
            return false to existingRecord?.bestTimeMs
        }
        val isNewBestTierTime = existingRecord == null || validTimeMs < existingRecord.bestTimeMs
        val bestTime = if (existingRecord == null) validTimeMs else minOf(existingRecord.bestTimeMs, validTimeMs)
        if (isNewBestTierTime) {
            dao.upsertTierRecord(
                DifficultyRecordEntity(
                    tierOrdinal = tier.ordinal,
                    bestTimeMs = bestTime,
                    bestLevelNumber = levelNumber,
                    updatedAtMs = nowMs
                )
            )
        }
        return isNewBestTierTime to bestTime
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
                    bestMoves = existing.bestMoves,
                    elapsedTimeMs = existing.elapsedTimeMs.coerceAtLeast(0L)
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
            updatedAtMs = System.currentTimeMillis(),
            elapsedTimeMs = 0L
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
            bestMoves = null,
            elapsedTimeMs = 0L
        )
    }

    override suspend fun saveDailyChallengeProgress(
        dateKey: String,
        theme: StudioThemeId,
        currentTubes: List<TubeModel>,
        undoStack: List<PourMove>,
        remainingUndos: Int,
        extraTubeUsed: Boolean,
        moveCount: Int,
        elapsedTimeMs: Long
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
                updatedAtMs = System.currentTimeMillis(),
                elapsedTimeMs = elapsedTimeMs.coerceAtLeast(0L)
            )
        )
    }

    override suspend fun markDailyChallengeCompleted(
        dateKey: String,
        moves: Int,
        elapsedTimeMs: Long
    ): LevelCompletionResult {
        val existing = dao.getDailyChallenge(dateKey)
            ?: return LevelCompletionResult(isNewBestMoves = false)
        val isNewBestMoves = existing.bestMoves == null || moves < existing.bestMoves
        val bestMoves = if (existing.bestMoves == null) moves else minOf(existing.bestMoves, moves)
        val nowMs = System.currentTimeMillis()
        dao.upsertDailyChallenge(
            existing.copy(
                isCompleted = true,
                bestMoves = bestMoves,
                completedAtMs = existing.completedAtMs ?: nowMs,
                updatedAtMs = nowMs,
                elapsedTimeMs = elapsedTimeMs.coerceAtLeast(0L)
            )
        )
        val tier = DifficultyTier.entries[existing.tierOrdinal.coerceIn(0, DifficultyTier.entries.lastIndex)]
        val tierResult = recordTierTimeIfBest(tier, elapsedTimeMs.takeIf { it > 0L }, 0, nowMs)
        return LevelCompletionResult(
            isNewBestMoves = isNewBestMoves,
            isNewBestTime = tierResult.first,
            bestTierTimeMs = tierResult.second
        )
    }

    override suspend fun exportBackupPayload(): ProgressTransferPayload {
        val prefs = preferences.preferencesFlow.first()
        val progressRows = dao.getAllProgressSnapshot()
        val dailyRows = dao.getAllDailyChallengesSnapshot()
        val tierRows = dao.getAllTierRecordsSnapshot()
        val sessionRow = dao.getActiveSession()

        val completed = progressRows
            .filter { it.isCompleted }
            .associate { it.levelNumber to (it.bestMoves ?: 0) }
        val levelTimes = progressRows
            .mapNotNull { row -> row.bestTimeMs?.takeIf { it > 0L }?.let { row.levelNumber to it } }
            .toMap()
        val tierTimes = tierRows
            .filter { it.bestTimeMs > 0L }
            .associate { it.tierOrdinal to it.bestTimeMs }
        val completedDaily = dailyRows
            .filter { it.isCompleted }
            .associate { it.dateKey to (it.bestMoves ?: it.moveCount) }
        val backupSession = sessionRow?.let { s ->
            BackupActiveSession(
                currentLevel = s.currentLevel,
                themeOrdinal = s.themeOrdinal,
                tubesJson = s.tubesJson,
                initialTubesJson = s.initialTubesJson,
                undoStackJson = s.undoStackJson,
                remainingUndos = s.remainingUndos,
                extraTubeUsed = s.extraTubeUsed,
                moveCount = s.moveCount,
                updatedAtMs = s.updatedAtMs,
                elapsedTimeMs = s.elapsedTimeMs
            )
        }
        val backupDaily = dailyRows.map { d ->
            BackupDailyChallenge(
                dateKey = d.dateKey,
                tierOrdinal = d.tierOrdinal,
                colorCount = d.colorCount,
                themeOrdinal = d.themeOrdinal,
                initialTubesJson = d.initialTubesJson,
                currentTubesJson = d.currentTubesJson,
                undoStackJson = d.undoStackJson,
                remainingUndos = d.remainingUndos,
                extraTubeUsed = d.extraTubeUsed,
                moveCount = d.moveCount,
                isCompleted = d.isCompleted,
                bestMoves = d.bestMoves,
                completedAtMs = d.completedAtMs,
                updatedAtMs = d.updatedAtMs,
                elapsedTimeMs = d.elapsedTimeMs
            )
        }

        val maxCompleted = completed.keys.maxOrNull() ?: 0
        return ProgressTransferPayload(
            schemaVersion = ProgressTransferCodec.CURRENT_SCHEMA_VERSION,
            appId = ProgressTransferCodec.APP_ID,
            highestUnlockedLevel = maxOf(prefs.highestUnlockedLevel, maxCompleted + 1),
            completedLevels = completed,
            completedDailyMoves = completedDaily,
            bestLevelTimes = levelTimes,
            bestTierTimes = tierTimes,
            activeSession = backupSession,
            dailyChallenges = backupDaily,
            soundEnabled = prefs.soundEnabled,
            hapticsEnabled = prefs.hapticsEnabled,
            colorblindEnabled = prefs.colorblindEnabled,
            randomizeThemePerLevel = prefs.randomizeThemePerLevel,
            timerEnabled = prefs.timerEnabled,
            pinnedThemeOrdinal = prefs.pinnedTheme.ordinal,
            exportedAtMs = System.currentTimeMillis()
        )
    }

    override suspend fun importBackupPayload(
        incoming: ProgressTransferPayload,
        importSettings: Boolean
    ): ImportSummary {
        val local = exportBackupPayload()
        val merged = ProgressTransferCodec.mergePayloads(local, incoming)

        val newLevelsCount = (merged.completedLevels.keys - local.completedLevels.keys).size
        val newDailyCount = (merged.completedDailyMoves.keys - local.completedDailyMoves.keys).size

        val entities = merged.completedLevels.map { (level, moves) ->
            LevelProgressEntity(
                levelNumber = level,
                isCompleted = true,
                bestMoves = moves.takeIf { it > 0 },
                completedAtMs = merged.exportedAtMs,
                bestTimeMs = merged.bestLevelTimes[level]
            )
        }
        if (entities.isNotEmpty()) {
            dao.upsertAllProgress(entities)
        }

        val tierEntities = merged.bestTierTimes.map { (tierOrd, timeMs) ->
            DifficultyRecordEntity(
                tierOrdinal = tierOrd.coerceIn(0, DifficultyTier.entries.lastIndex),
                bestTimeMs = timeMs,
                bestLevelNumber = 0,
                updatedAtMs = merged.exportedAtMs
            )
        }
        if (tierEntities.isNotEmpty()) {
            dao.upsertAllTierRecords(tierEntities)
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
                        completedAtMs = merged.exportedAtMs,
                        updatedAtMs = merged.exportedAtMs
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
                        completedAtMs = existing.completedAtMs ?: merged.exportedAtMs,
                        updatedAtMs = merged.exportedAtMs
                    )
                )
            }
        }

        preferences.setHighestUnlockedLevel(merged.highestUnlockedLevel)
        if (importSettings) {
            applyPreferencesFromBackup(incoming)
        }

        return ImportSummary(
            newLevelsAdded = newLevelsCount,
            newDailyAdded = newDailyCount,
            highestUnlockedLevel = merged.highestUnlockedLevel
        )
    }

    override suspend fun replaceWithBackupPayload(incoming: ProgressTransferPayload): ImportSummary {
        val stamp = if (incoming.exportedAtMs > 0L) incoming.exportedAtMs else System.currentTimeMillis()
        val progressEntities = incoming.completedLevels.map { (level, moves) ->
            LevelProgressEntity(
                levelNumber = level,
                isCompleted = true,
                bestMoves = moves.takeIf { it > 0 },
                completedAtMs = stamp,
                bestTimeMs = incoming.bestLevelTimes[level]
            )
        }

        val tierEntities = incoming.bestTierTimes.mapNotNull { (tierOrd, timeMs) ->
            if (timeMs <= 0L) null else DifficultyRecordEntity(
                tierOrdinal = tierOrd.coerceIn(0, DifficultyTier.entries.lastIndex),
                bestTimeMs = timeMs,
                bestLevelNumber = 0,
                updatedAtMs = stamp
            )
        }

        val sessionEntity = incoming.activeSession?.let { s ->
            ActiveSessionEntity(
                id = 1,
                currentLevel = s.currentLevel,
                themeOrdinal = s.themeOrdinal.coerceIn(0, StudioThemeId.entries.lastIndex),
                tubesJson = s.tubesJson,
                initialTubesJson = s.initialTubesJson,
                undoStackJson = s.undoStackJson,
                remainingUndos = s.remainingUndos.coerceIn(0, MAX_UNDO_PER_STAGE),
                extraTubeUsed = s.extraTubeUsed,
                moveCount = s.moveCount.coerceAtLeast(0),
                updatedAtMs = if (s.updatedAtMs > 0L) s.updatedAtMs else stamp,
                elapsedTimeMs = s.elapsedTimeMs.coerceAtLeast(0L)
            )
        }

        val dailyEntitiesByDate = LinkedHashMap<String, DailyChallengeEntity>()
        for (row in incoming.dailyChallenges) {
            dailyEntitiesByDate[row.dateKey] = DailyChallengeEntity(
                dateKey = row.dateKey,
                tierOrdinal = row.tierOrdinal.coerceIn(0, DifficultyTier.entries.lastIndex),
                colorCount = row.colorCount.coerceAtLeast(1),
                themeOrdinal = row.themeOrdinal.coerceIn(0, StudioThemeId.entries.lastIndex),
                initialTubesJson = row.initialTubesJson,
                currentTubesJson = row.currentTubesJson,
                undoStackJson = row.undoStackJson,
                remainingUndos = row.remainingUndos.coerceIn(0, MAX_UNDO_PER_STAGE),
                extraTubeUsed = row.extraTubeUsed,
                moveCount = row.moveCount.coerceAtLeast(0),
                isCompleted = row.isCompleted,
                bestMoves = row.bestMoves,
                completedAtMs = row.completedAtMs,
                updatedAtMs = if (row.updatedAtMs > 0L) row.updatedAtMs else stamp,
                elapsedTimeMs = row.elapsedTimeMs.coerceAtLeast(0L)
            )
        }
        for ((dateKey, moves) in incoming.completedDailyMoves) {
            if (!dailyEntitiesByDate.containsKey(dateKey)) {
                val spec = PuzzleEngine.generateDailyChallenge(dateKey)
                val initialJson = json.encodeToString(spec.tubes)
                dailyEntitiesByDate[dateKey] = DailyChallengeEntity(
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
                    completedAtMs = stamp,
                    updatedAtMs = stamp
                )
            }
        }

        val dailyList = dailyEntitiesByDate.values.toList()
        dao.replaceDatabaseState(
            progress = progressEntities,
            activeSession = sessionEntity,
            dailyChallenges = dailyList,
            tierRecords = tierEntities
        )

        val resolvedHighest = maxOf(
            incoming.highestUnlockedLevel.coerceAtLeast(1),
            (incoming.completedLevels.keys.maxOrNull() ?: 0) + 1,
            sessionEntity?.currentLevel ?: 1
        )
        preferences.replaceHighestUnlockedLevel(resolvedHighest)
        applyPreferencesFromBackup(incoming)

        return ImportSummary(
            newLevelsAdded = progressEntities.size,
            newDailyAdded = dailyList.count { it.isCompleted },
            highestUnlockedLevel = resolvedHighest,
            restoredActiveSession = sessionEntity != null
        )
    }

    private suspend fun applyPreferencesFromBackup(payload: ProgressTransferPayload) {
        preferences.setSoundEnabled(payload.soundEnabled)
        preferences.setHapticsEnabled(payload.hapticsEnabled)
        preferences.setColorblindEnabled(payload.colorblindEnabled)
        preferences.setRandomizeThemePerLevel(payload.randomizeThemePerLevel)
        preferences.setTimerEnabled(payload.timerEnabled)
        val theme = StudioThemeId.entries[payload.pinnedThemeOrdinal.coerceIn(0, StudioThemeId.entries.lastIndex)]
        preferences.setPinnedTheme(theme)
    }

    suspend fun writeBackupToShareFile(context: Context): Uri {
        val payload = exportBackupPayload()
        val encoded = ProgressTransferCodec.encodeToJsonString(payload)
        return withContext(Dispatchers.IO) {
            val transfersDir = File(context.cacheDir, "transfers")
            if (!transfersDir.exists()) {
                transfersDir.mkdirs()
            } else {
                transfersDir.listFiles()?.forEach { it.delete() }
            }
            val outFile = File(transfersDir, "decant-backup.json")
            outFile.writeText(encoded, Charsets.UTF_8)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
        }
    }

    suspend fun parseBackupFromUri(context: Context, uri: Uri): Result<ProgressTransferPayload> {
        val rawResult = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val buffer = ByteArray(ProgressTransferCodec.MAX_BACKUP_BYTES + 1)
                    var total = 0
                    while (total < buffer.size) {
                        val read = stream.read(buffer, total, buffer.size - total)
                        if (read == -1) break
                        total += read
                    }
                    require(total in 1..ProgressTransferCodec.MAX_BACKUP_BYTES) {
                        "Backup JSON file is empty or exceeds size limit."
                    }
                    String(buffer, 0, total, Charsets.UTF_8)
                } ?: error("Could not open selected backup file.")
            }
        }
        val raw = rawResult.getOrElse { return Result.failure(it) }
        return ProgressTransferCodec.decodeFromJsonString(raw)
    }

    override suspend fun setSoundEnabled(enabled: Boolean) = preferences.setSoundEnabled(enabled)
    override suspend fun setHapticsEnabled(enabled: Boolean) = preferences.setHapticsEnabled(enabled)
    override suspend fun setColorblindEnabled(enabled: Boolean) = preferences.setColorblindEnabled(enabled)
    override suspend fun setRandomizeThemePerLevel(enabled: Boolean) = preferences.setRandomizeThemePerLevel(enabled)
    override suspend fun setTimerEnabled(enabled: Boolean) = preferences.setTimerEnabled(enabled)
    override suspend fun setPinnedTheme(theme: StudioThemeId) = preferences.setPinnedTheme(theme)

    data class ActiveSessionSnapshot(
        val currentLevel: Int,
        val theme: StudioThemeId,
        val tubes: List<TubeModel>,
        val undoStack: List<PourMove>,
        val remainingUndos: Int = MAX_UNDO_PER_STAGE,
        val extraTubeUsed: Boolean,
        val moveCount: Int,
        val initialTubes: List<TubeModel> = emptyList(),
        val elapsedTimeMs: Long = 0L
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
        val bestMoves: Int?,
        val elapsedTimeMs: Long = 0L
    )
}

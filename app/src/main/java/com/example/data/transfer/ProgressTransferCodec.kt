package com.example.data.transfer

import com.example.domain.MAX_UNDO_PER_STAGE
import com.example.domain.ProgressTransferPayload
import com.example.domain.StudioThemeId
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object ProgressTransferCodec {

    const val CURRENT_SCHEMA_VERSION = 2
    const val APP_ID = "decant"
    const val MAX_BACKUP_BYTES = 1024 * 1024

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encodeToJsonString(payload: ProgressTransferPayload): String =
        json.encodeToString(payload.copy(schemaVersion = CURRENT_SCHEMA_VERSION, appId = APP_ID))

    fun decodeFromJsonString(raw: String): Result<ProgressTransferPayload> = runCatching {
        val bytes = raw.toByteArray(Charsets.UTF_8)
        require(bytes.isNotEmpty() && bytes.size <= MAX_BACKUP_BYTES) {
            "Backup JSON file is empty or exceeds size limit."
        }
        val decoded = json.decodeFromString<ProgressTransferPayload>(raw)
        require(decoded.appId == APP_ID) {
            "This JSON file is not a Decant! backup."
        }
        require(decoded.schemaVersion in 1..CURRENT_SCHEMA_VERSION) {
            "Unsupported backup version (${decoded.schemaVersion})."
        }

        val cleanLevels = buildMap {
            for ((level, moves) in decoded.completedLevels) {
                if (level > 0 && moves >= 0) {
                    put(level, moves)
                }
            }
        }
        val cleanDailyMoves = buildMap {
            for ((dateKey, moves) in decoded.completedDailyMoves) {
                if (dateKey.length == 10 && moves >= 0) {
                    put(dateKey, moves)
                }
            }
        }
        val cleanLevelTimes = buildMap {
            for ((level, timeMs) in decoded.bestLevelTimes) {
                if (level > 0 && timeMs > 0L) put(level, timeMs)
            }
        }
        val cleanTierTimes = buildMap {
            for ((tierOrd, timeMs) in decoded.bestTierTimes) {
                if (tierOrd >= 0 && timeMs > 0L) put(tierOrd, timeMs)
            }
        }
        val cleanDailyRows = decoded.dailyChallenges.filter { row ->
            row.dateKey.length == 10 &&
                row.colorCount > 0 &&
                row.initialTubesJson.isNotBlank() &&
                row.currentTubesJson.isNotBlank()
        }.map { row ->
            row.copy(
                remainingUndos = row.remainingUndos.coerceIn(0, MAX_UNDO_PER_STAGE),
                moveCount = row.moveCount.coerceAtLeast(0),
                elapsedTimeMs = row.elapsedTimeMs.coerceAtLeast(0L)
            )
        }
        val cleanSession = decoded.activeSession
            ?.takeIf { it.currentLevel > 0 && it.tubesJson.isNotBlank() }
            ?.let { session ->
                session.copy(
                    remainingUndos = session.remainingUndos.coerceIn(0, MAX_UNDO_PER_STAGE),
                    moveCount = session.moveCount.coerceAtLeast(0),
                    elapsedTimeMs = session.elapsedTimeMs.coerceAtLeast(0L)
                )
            }

        val maxCompleted = cleanLevels.keys.maxOrNull() ?: 0
        decoded.copy(
            schemaVersion = decoded.schemaVersion,
            appId = APP_ID,
            highestUnlockedLevel = maxOf(
                decoded.highestUnlockedLevel.coerceAtLeast(1),
                maxCompleted + 1,
                cleanSession?.currentLevel ?: 1
            ),
            completedLevels = cleanLevels,
            completedDailyMoves = cleanDailyMoves,
            bestLevelTimes = cleanLevelTimes,
            bestTierTimes = cleanTierTimes,
            activeSession = cleanSession,
            dailyChallenges = cleanDailyRows,
            pinnedThemeOrdinal = decoded.pinnedThemeOrdinal.coerceIn(0, StudioThemeId.entries.lastIndex)
        )
    }

    fun mergePayloads(
        local: ProgressTransferPayload,
        incoming: ProgressTransferPayload
    ): ProgressTransferPayload {
        val mergedLevels = HashMap<Int, Int>(local.completedLevels.size + incoming.completedLevels.size)
        for ((level, moves) in local.completedLevels) {
            mergedLevels[level] = moves
        }
        for ((level, moves) in incoming.completedLevels) {
            val prev = mergedLevels[level]
            mergedLevels[level] = when {
                prev == null -> moves
                prev <= 0 -> moves
                moves <= 0 -> prev
                else -> minOf(prev, moves)
            }
        }

        val mergedDaily = HashMap<String, Int>(local.completedDailyMoves.size + incoming.completedDailyMoves.size)
        for ((date, moves) in local.completedDailyMoves) {
            mergedDaily[date] = moves
        }
        for ((date, moves) in incoming.completedDailyMoves) {
            val prev = mergedDaily[date]
            mergedDaily[date] = when {
                prev == null -> moves
                prev <= 0 -> moves
                moves <= 0 -> prev
                else -> minOf(prev, moves)
            }
        }

        val mergedLevelTimes = mergeMinTimes(local.bestLevelTimes, incoming.bestLevelTimes)
        val mergedTierTimes = mergeMinTimes(local.bestTierTimes, incoming.bestTierTimes)

        val highestLevel = maxOf(
            local.highestUnlockedLevel,
            incoming.highestUnlockedLevel,
            (mergedLevels.keys.maxOrNull() ?: 0) + 1
        )
        val useIncomingPrefs = incoming.exportedAtMs >= local.exportedAtMs
        return ProgressTransferPayload(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            appId = APP_ID,
            highestUnlockedLevel = highestLevel,
            completedLevels = mergedLevels,
            completedDailyMoves = mergedDaily,
            bestLevelTimes = mergedLevelTimes,
            bestTierTimes = mergedTierTimes,
            activeSession = local.activeSession ?: incoming.activeSession,
            dailyChallenges = (local.dailyChallenges + incoming.dailyChallenges)
                .distinctBy { it.dateKey },
            soundEnabled = if (useIncomingPrefs) incoming.soundEnabled else local.soundEnabled,
            hapticsEnabled = if (useIncomingPrefs) incoming.hapticsEnabled else local.hapticsEnabled,
            colorblindEnabled = if (useIncomingPrefs) incoming.colorblindEnabled else local.colorblindEnabled,
            randomizeThemePerLevel = if (useIncomingPrefs) incoming.randomizeThemePerLevel else local.randomizeThemePerLevel,
            timerEnabled = if (useIncomingPrefs) incoming.timerEnabled else local.timerEnabled,
            pinnedThemeOrdinal = if (useIncomingPrefs) incoming.pinnedThemeOrdinal else local.pinnedThemeOrdinal,
            exportedAtMs = maxOf(local.exportedAtMs, incoming.exportedAtMs)
        )
    }

    private fun <K> mergeMinTimes(a: Map<K, Long>, b: Map<K, Long>): Map<K, Long> {
        val out = HashMap<K, Long>(a.size + b.size)
        for ((k, v) in a) if (v > 0L) out[k] = v
        for ((k, v) in b) {
            if (v <= 0L) continue
            val prev = out[k]
            out[k] = if (prev == null) v else minOf(prev, v)
        }
        return out
    }
}

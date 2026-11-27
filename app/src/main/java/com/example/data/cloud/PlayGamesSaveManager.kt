package com.example.data.cloud

import android.app.Activity
import android.content.Context
import com.example.ChromaFlowApplication
import com.example.R
import com.example.domain.CloudSavePayload
import com.example.domain.SyncStatus
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.SnapshotsClient
import com.google.android.gms.games.snapshot.Snapshot
import com.google.android.gms.games.snapshot.SnapshotMetadataChange
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min

class PlayGamesSaveManager(private val context: Context) {

    private val json by lazy {
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }

    fun isConfigured(): Boolean {
        val appId = runCatching { context.getString(R.string.game_services_project_id).trim() }.getOrDefault("")
        return ChromaFlowApplication.isValidPlayGamesAppId(appId)
    }

    suspend fun syncProgress(
        activity: Activity,
        localPayload: CloudSavePayload,
        interactiveSignIn: Boolean
    ): SyncResult {
        if (!isConfigured()) {
            return SyncResult(status = SyncStatus.NeedsAppId, mergedPayload = null)
        }

        return try {
            val signInClient = PlayGames.getGamesSignInClient(activity)
            val authResult = signInClient.isAuthenticated.awaitResult()
            val authenticated = if (authResult.isAuthenticated) {
                true
            } else if (interactiveSignIn) {
                signInClient.signIn().awaitResult().isAuthenticated
            } else {
                false
            }

            if (!authenticated) {
                return SyncResult(status = SyncStatus.SignedOut, mergedPayload = null)
            }

            val snapshotsClient = PlayGames.getSnapshotsClient(activity)
            val merged = openMergeAndCommitSnapshot(snapshotsClient, localPayload)
            val nowMs = System.currentTimeMillis()
            SyncResult(
                status = SyncStatus.Synced(lastSyncMs = nowMs),
                mergedPayload = merged
            )
        } catch (e: Exception) {
            SyncResult(
                status = SyncStatus.Error(e.localizedMessage ?: "Play Games unavailable"),
                mergedPayload = null
            )
        }
    }

    private suspend fun openMergeAndCommitSnapshot(
        snapshotsClient: SnapshotsClient,
        localPayload: CloudSavePayload
    ): CloudSavePayload = withContext(Dispatchers.IO) {
        val dataOrConflict = snapshotsClient.open(
            SNAPSHOT_NAME,
            true,
            SnapshotsClient.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED
        ).awaitResult()

        val activeSnapshot: Snapshot
        val remotePayload: CloudSavePayload

        if (dataOrConflict.isConflict) {
            val conflict = dataOrConflict.conflict
                ?: throw IllegalStateException("Snapshot conflict metadata missing")
            val primary = decodePayload(conflict.snapshot.snapshotContents.readFully())
            val secondary = decodePayload(conflict.conflictingSnapshot.snapshotContents.readFully())
            val resolved = mergePayloads(localPayload, mergePayloads(primary, secondary))
            conflict.snapshot.snapshotContents.writeBytes(encodePayload(resolved))
            val resolvedResult = snapshotsClient.resolveConflict(conflict.conflictId, conflict.snapshot).awaitResult()
            activeSnapshot = resolvedResult.data ?: conflict.snapshot
            val metadata = SnapshotMetadataChange.Builder()
                .setDescription("Decant! progress (${resolved.completedLevels.size} levels)")
                .build()
            snapshotsClient.commitAndClose(activeSnapshot, metadata).awaitResult()
            return@withContext resolved
        } else {
            activeSnapshot = dataOrConflict.data
                ?: throw IllegalStateException("Snapshot data missing")
            remotePayload = decodePayload(activeSnapshot.snapshotContents.readFully())
        }

        val merged = mergePayloads(localPayload, remotePayload)
        if (merged != remotePayload || remotePayload.completedLevels.isEmpty()) {
            activeSnapshot.snapshotContents.writeBytes(encodePayload(merged))
            val metadata = SnapshotMetadataChange.Builder()
                .setDescription("Decant! progress (${merged.completedLevels.size} levels)")
                .build()
            snapshotsClient.commitAndClose(activeSnapshot, metadata).awaitResult()
        } else {
            snapshotsClient.discardAndClose(activeSnapshot).awaitResult()
        }
        merged
    }

    private fun decodePayload(bytes: ByteArray?): CloudSavePayload {
        if (bytes == null || bytes.isEmpty()) return CloudSavePayload()
        return runCatching {
            json.decodeFromString<CloudSavePayload>(String(bytes, Charsets.UTF_8))
        }.getOrDefault(CloudSavePayload())
    }

    private fun encodePayload(payload: CloudSavePayload): ByteArray =
        json.encodeToString(payload).toByteArray(Charsets.UTF_8)

    data class SyncResult(
        val status: SyncStatus,
        val mergedPayload: CloudSavePayload?
    )

    companion object {
        private const val SNAPSHOT_NAME = "chromaflow_progress"

        fun mergePayloads(local: CloudSavePayload, remote: CloudSavePayload): CloudSavePayload {
            val mergedLevels = HashMap<Int, Int>(local.completedLevels.size + remote.completedLevels.size)
            for ((lvl, moves) in local.completedLevels) {
                if (lvl >= 1 && moves >= 0) mergedLevels[lvl] = moves
            }
            for ((lvl, moves) in remote.completedLevels) {
                if (lvl >= 1 && moves >= 0) {
                    val prev = mergedLevels[lvl]
                    mergedLevels[lvl] = if (prev == null) moves else min(prev, moves)
                }
            }

            val mergedDaily = HashMap<String, Int>(local.completedDailyMoves.size + remote.completedDailyMoves.size)
            for ((date, moves) in local.completedDailyMoves) {
                if (date.isNotBlank() && moves >= 0) mergedDaily[date] = moves
            }
            for ((date, moves) in remote.completedDailyMoves) {
                if (date.isNotBlank() && moves >= 0) {
                    val prev = mergedDaily[date]
                    mergedDaily[date] = if (prev == null) moves else min(prev, moves)
                }
            }

            val maxCompleted = mergedLevels.keys.maxOrNull() ?: 0
            val highestUnlocked = max(
                max(local.highestUnlockedLevel, remote.highestUnlockedLevel),
                maxCompleted + 1
            ).coerceAtLeast(1)

            return CloudSavePayload(
                highestUnlockedLevel = highestUnlocked,
                completedLevels = mergedLevels.toSortedMap(),
                completedDailyMoves = mergedDaily.toSortedMap(),
                updatedAtMs = max(local.updatedAtMs, remote.updatedAtMs)
            )
        }
    }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result -> if (cont.isActive) cont.resume(result) }
    addOnFailureListener { exception -> if (cont.isActive) cont.resumeWithException(exception) }
    addOnCanceledListener { cont.cancel() }
}

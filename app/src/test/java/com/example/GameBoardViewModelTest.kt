package com.example

import com.example.audio.SoundFeedback
import com.example.audio.SoundManager
import com.example.data.GameProgressStore
import com.example.data.ProgressRepository
import com.example.data.local.DailyChallengeEntity
import com.example.data.local.DifficultyRecordEntity
import com.example.data.local.LevelProgressEntity
import com.example.data.prefs.UserPreferences
import com.example.data.transfer.ProgressTransferCodec
import com.example.domain.BackupActiveSession
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
import com.example.domain.formatElapsedDuration
import com.example.ui.viewmodel.GameBoardViewModel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameBoardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var store: FakeGameProgressStore
    private lateinit var soundRecorder: RecordingSoundFeedback
    private lateinit var viewModel: GameBoardViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        store = FakeGameProgressStore()
        soundRecorder = RecordingSoundFeedback()
        viewModel = GameBoardViewModel(
            repository = store,
            soundEngine = soundRecorder,
            pourAnimationDurationMs = 0L,
            computationDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun generateSolvableLevel_and_tubeInteractions_workEndToEnd() = runTest {
        advanceUntilIdle()

        val level5 = viewModel.generateSolvableLevel(5)
        assertEquals(5, level5.levelNumber)
        assertTrue(PuzzleEngine.isSolvable(level5.tubes))

        viewModel.loadLevel(5)
        advanceUntilIdle()

        val initial = viewModel.uiState.value
        assertFalse(initial.isLoading)
        assertEquals(5, initial.levelNumber)
        assertEquals(0, initial.moveCount)
        assertTrue(initial.tubes.isNotEmpty())

        // Pick a non-empty tube and verify selection + validTargetIndices
        val sourceIdx = initial.tubes.indexOfFirst { !it.isEmpty }
        assertTrue(sourceIdx >= 0)

        viewModel.onTubeClick(sourceIdx)
        val afterSelect = viewModel.uiState.value
        assertEquals(sourceIdx, afterSelect.selectedTubeIndex)
        assertTrue(afterSelect.validTargetIndices.isNotEmpty())

        // Pour into a valid target tube
        val targetIdx = afterSelect.validTargetIndices.first()
        viewModel.onTubeClick(targetIdx)
        advanceUntilIdle()

        val afterPour = viewModel.uiState.value
        assertNull(afterPour.selectedTubeIndex)
        assertEquals(1, afterPour.moveCount)
        assertEquals(1, afterPour.undoStack.size)

        // Undo restores original tube layout and move count
        viewModel.onUndoClick()
        advanceUntilIdle()

        val afterUndo = viewModel.uiState.value
        assertEquals(0, afterUndo.moveCount)
        assertEquals(0, afterUndo.undoStack.size)
        assertEquals(initial.tubes, afterUndo.tubes)

        // Add +1 extra tube helper works once
        val tubeCountBeforeExtra = afterUndo.tubes.size
        viewModel.onAddExtraTubeClick()
        advanceUntilIdle()

        val afterExtra = viewModel.uiState.value
        assertTrue(afterExtra.extraTubeUsed)
        assertEquals(tubeCountBeforeExtra + 1, afterExtra.tubes.size)

        // Restart resets extra tube and move state
        viewModel.restartLevel()
        advanceUntilIdle()
        val afterRestart = viewModel.uiState.value
        assertFalse(afterRestart.extraTubeUsed)
        assertEquals(tubeCountBeforeExtra, afterRestart.tubes.size)
        assertEquals(0, afterRestart.moveCount)
    }

    @Test
    fun invalidTubeTap_triggersShakeAndClearsSelection() = runTest {
        advanceUntilIdle()
        viewModel.loadLevel(4)
        advanceUntilIdle()

        val tubes = viewModel.uiState.value.tubes
        val sourceIdx = tubes.indexOfFirst { !it.isEmpty }
        val invalidTargetIdx = tubes.indices.firstOrNull { idx ->
            idx != sourceIdx && !PuzzleEngine.canPour(tubes[sourceIdx], tubes[idx])
        }
        if (invalidTargetIdx != null) {
            viewModel.onTubeClick(sourceIdx)
            val prevTick = viewModel.uiState.value.invalidShakeTick
            viewModel.onTubeClick(invalidTargetIdx)
            val afterInvalid = viewModel.uiState.value
            assertNull(afterInvalid.selectedTubeIndex)
            assertEquals(invalidTargetIdx, afterInvalid.invalidShakeIndex)
            assertEquals(prevTick + 1, afterInvalid.invalidShakeTick)
        }
    }

    @Test
    fun restartLevel_resetsTubesAndStateToInitialConfiguration() = runTest {
        advanceUntilIdle()
        viewModel.loadLevel(6)
        advanceUntilIdle()

        val initial = viewModel.uiState.value
        val initialTubes = initial.tubes

        // Make a valid move
        val sourceIdx = initialTubes.indexOfFirst { !it.isEmpty }
        val targetIdx = initialTubes.indices.first { idx ->
            idx != sourceIdx && PuzzleEngine.canPour(initialTubes[sourceIdx], initialTubes[idx])
        }
        viewModel.onTubeClick(sourceIdx)
        viewModel.onTubeClick(targetIdx)
        advanceUntilIdle()

        // Add extra tube and select a tube before restarting
        viewModel.onAddExtraTubeClick()
        val nonEmptyIdx = viewModel.uiState.value.tubes.indexOfFirst { !it.isEmpty }
        viewModel.onTubeClick(nonEmptyIdx)

        assertTrue(viewModel.uiState.value.moveCount > 0)
        assertTrue(viewModel.uiState.value.extraTubeUsed)
        assertEquals(initialTubes.size + 1, viewModel.uiState.value.tubes.size)

        // Call restartLevel and verify full reset to exact initial configuration
        viewModel.restartLevel()
        advanceUntilIdle()

        val afterRestart = viewModel.uiState.value
        assertEquals(initial.levelNumber, afterRestart.levelNumber)
        assertEquals(initialTubes, afterRestart.tubes)
        assertEquals(0, afterRestart.moveCount)
        assertTrue(afterRestart.undoStack.isEmpty())
        assertNull(afterRestart.selectedTubeIndex)
        assertTrue(afterRestart.validTargetIndices.isEmpty())
        assertNull(afterRestart.invalidShakeIndex)
        assertFalse(afterRestart.extraTubeUsed)
        assertFalse(afterRestart.isLevelComplete)
        assertTrue(afterRestart.hasValidMovesLeft)
    }

    @Test
    fun soundAndHaptics_triggerForSelectPourAndLevelComplete_andRespectPreferences() = runTest {
        advanceUntilIdle()
        // Pre-seed a 1-move-away-from-solved board in saved session
        val nearlySolvedTubes = listOf(
            TubeModel(id = 0, segments = listOf(0, 0, 0)),
            TubeModel(id = 1, segments = listOf(0)),
            TubeModel(id = 2, segments = emptyList())
        )
        store.saveActiveSession(
            currentLevel = 1,
            theme = StudioThemeId.CLEAN_GLASS,
            tubes = nearlySolvedTubes,
            undoStack = emptyList(),
            extraTubeUsed = false,
            moveCount = 2
        )

        val vm = GameBoardViewModel(
            repository = store,
            soundEngine = soundRecorder,
            pourAnimationDurationMs = 0L,
            computationDispatcher = testDispatcher
        )
        advanceUntilIdle()

        soundRecorder.clear()

        // Selecting source tube triggers select sound + haptics
        vm.onTubeClick(1)
        assertEquals(listOf(true to true), soundRecorder.selectCalls)

        // Pouring into target tube triggers pour sound + haptics, then victory sound + haptics
        vm.onTubeClick(0)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isLevelComplete)
        assertEquals(1, soundRecorder.pourCalls.size)
        assertEquals(1f, soundRecorder.pourCalls.first().first, 0.01f)
        assertEquals(true to true, soundRecorder.pourCalls.first().second to soundRecorder.pourCalls.first().third)
        assertEquals(1, soundRecorder.colorMixCalls.size)
        assertEquals(0 to 1, soundRecorder.colorMixCalls.first().first to soundRecorder.colorMixCalls.first().second)
        assertEquals(listOf(true to true), soundRecorder.victoryCalls)

        // Verify preference toggles immediately update feedback flags
        vm.setSoundEnabled(false)
        vm.setHapticsEnabled(false)
        vm.loadLevel(2)
        advanceUntilIdle()
        soundRecorder.clear()

        val sourceIdx = vm.uiState.value.tubes.indexOfFirst { !it.isEmpty }
        vm.onTubeClick(sourceIdx)
        assertEquals(listOf(false to false), soundRecorder.selectCalls)
    }

    @Test
    fun soundManager_triggersColorMixOnlyWhenPouringOntoExistingLiquid() = runTest {
        val board = listOf(
            TubeModel(id = 0, segments = listOf(2, 2)),
            TubeModel(id = 1, segments = listOf(2)),
            TubeModel(id = 2, segments = emptyList())
        )
        store.saveActiveSession(
            currentLevel = 1,
            theme = StudioThemeId.CLEAN_GLASS,
            tubes = board,
            undoStack = emptyList(),
            extraTubeUsed = false,
            moveCount = 0
        )

        val vm = GameBoardViewModel(
            repository = store,
            soundEngine = soundRecorder,
            pourAnimationDurationMs = 0L,
            computationDispatcher = testDispatcher
        )
        advanceUntilIdle()
        soundRecorder.clear()

        // Pouring into empty tube 2 triggers pour clip without color mix
        vm.onTubeClick(1)
        vm.onTubeClick(2)
        advanceUntilIdle()
        assertEquals(1, soundRecorder.pourCalls.size)
        assertTrue(soundRecorder.colorMixCalls.isEmpty())

        // Pouring from tube 2 onto existing liquid in tube 0 triggers pour + color mix
        soundRecorder.clear()
        vm.onTubeClick(2)
        vm.onTubeClick(0)
        advanceUntilIdle()
        assertEquals(1, soundRecorder.pourCalls.size)
        assertEquals(1, soundRecorder.colorMixCalls.size)
        assertEquals(2, soundRecorder.colorMixCalls.first().first)
    }

    @Test
    fun soundFeedbackSynthesis_producesBoundedAudibleBuffers() {
        val select = SoundManager.synthesizeChime(540.0, 700.0, 55, 0.25)
        val pour = SoundManager.synthesizePourClip(450.0, 195)
        val colorMix = SoundManager.synthesizeColorMixClip(colorVariant = 1, durationMs = 145)
        val levelComplete = SoundManager.synthesizeLevelCompleteClip()

        assertTrue(select.isNotEmpty())
        assertTrue(pour.isNotEmpty())
        assertTrue(colorMix.isNotEmpty())
        assertTrue(levelComplete.isNotEmpty())
        assertTrue(select.any { it.toInt() != 0 })
        assertTrue(pour.any { it.toInt() != 0 })
        assertTrue(colorMix.any { it.toInt() != 0 })
        assertTrue(levelComplete.any { it.toInt() != 0 })
    }

    @Test
    fun levelTimer_tracksElapsedTimeOnlyWhenActiveAndEnabled_andRecordsBestTierTime() = runTest {
        val board = listOf(
            TubeModel(id = 0, segments = listOf(0, 0, 0)),
            TubeModel(id = 1, segments = listOf(0)),
            TubeModel(id = 2, segments = emptyList())
        )
        store.saveActiveSession(
            currentLevel = 1,
            theme = StudioThemeId.CLEAN_GLASS,
            tubes = board,
            undoStack = emptyList(),
            extraTubeUsed = false,
            moveCount = 0
        )

        var fakeNowMs = 10_000L
        val vm = GameBoardViewModel(
            repository = store,
            soundEngine = soundRecorder,
            pourAnimationDurationMs = 0L,
            computationDispatcher = testDispatcher,
            clockMsProvider = { fakeNowMs }
        )
        advanceUntilIdle()
        assertEquals(0L, vm.uiState.value.elapsedTimeMs)

        // Activate board and advance virtual time by 3 seconds
        vm.setBoardActive(true)
        runCurrent()
        repeat(3) {
            fakeNowMs += 1_000L
            advanceTimeBy(1_000L)
            runCurrent()
        }
        assertEquals(3_000L, vm.uiState.value.elapsedTimeMs)
        assertEquals("0:03", formatElapsedDuration(vm.uiState.value.elapsedTimeMs))

        // Leaving board pauses timer cleanly
        vm.setBoardActive(false)
        runCurrent()
        fakeNowMs += 5_000L
        advanceTimeBy(5_000L)
        runCurrent()
        assertEquals(3_000L, vm.uiState.value.elapsedTimeMs)

        // Disabling timer preference prevents further accumulation even when board active
        vm.setTimerEnabled(false)
        runCurrent()
        vm.setBoardActive(true)
        runCurrent()
        fakeNowMs += 3_000L
        advanceTimeBy(3_000L)
        runCurrent()
        assertEquals(3_000L, vm.uiState.value.elapsedTimeMs)

        // Re-enabling timer resumes tracking
        vm.setTimerEnabled(true)
        runCurrent()
        repeat(2) {
            fakeNowMs += 1_000L
            advanceTimeBy(1_000L)
            runCurrent()
        }
        assertEquals(5_000L, vm.uiState.value.elapsedTimeMs)

        // Completing the level stops timer and persists Warmup tier best time
        vm.onTubeClick(1)
        vm.onTubeClick(0)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isLevelComplete)
        assertTrue(vm.uiState.value.isNewBestTime)
        assertEquals(5_000L, vm.uiState.value.bestTierTimeMs)
        assertEquals(5_000L, vm.tierBestTimes.value[DifficultyTier.WARMUP])

        // Restarting level resets elapsed timer to 0
        vm.setBoardActive(false)
        vm.restartLevel()
        advanceUntilIdle()
        assertEquals(0L, vm.uiState.value.elapsedTimeMs)
    }

    @Test
    fun undo_isCappedAtFivePerStage_andResetsOnRestartOrNextStage() = runTest {
        advanceUntilIdle()
        viewModel.loadLevel(5)
        advanceUntilIdle()

        assertEquals(MAX_UNDO_PER_STAGE, viewModel.uiState.value.remainingUndos)
        assertFalse(viewModel.uiState.value.canUndo)

        // Perform 5 move + undo cycles -> consumes all 5 undos for this stage
        repeat(MAX_UNDO_PER_STAGE) { step ->
            val currentTubes = viewModel.uiState.value.tubes
            val src = currentTubes.indexOfFirst { !it.isEmpty }
            val dst = currentTubes.indices.first { idx ->
                idx != src && PuzzleEngine.canPour(currentTubes[src], currentTubes[idx])
            }
            viewModel.onTubeClick(src)
            viewModel.onTubeClick(dst)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.canUndo)

            viewModel.onUndoClick()
            advanceUntilIdle()
            assertEquals(MAX_UNDO_PER_STAGE - step - 1, viewModel.uiState.value.remainingUndos)
        }

        assertEquals(0, viewModel.uiState.value.remainingUndos)
        assertFalse(viewModel.uiState.value.canUndo)

        // Perform a 6th move and attempt a 6th undo -> must be ignored
        val tubesBeforeMove = viewModel.uiState.value.tubes
        val src6 = tubesBeforeMove.indexOfFirst { !it.isEmpty }
        val dst6 = tubesBeforeMove.indices.first { idx ->
            idx != src6 && PuzzleEngine.canPour(tubesBeforeMove[src6], tubesBeforeMove[idx])
        }
        viewModel.onTubeClick(src6)
        viewModel.onTubeClick(dst6)
        advanceUntilIdle()

        val stateAfterMove6 = viewModel.uiState.value
        assertEquals(1, stateAfterMove6.moveCount)
        assertEquals(0, stateAfterMove6.remainingUndos)
        assertFalse(stateAfterMove6.canUndo)

        val undoCallsBefore6th = soundRecorder.undoCalls.size
        viewModel.onUndoClick()
        advanceUntilIdle()

        assertEquals(undoCallsBefore6th, soundRecorder.undoCalls.size)
        assertEquals(stateAfterMove6.tubes, viewModel.uiState.value.tubes)
        assertEquals(1, viewModel.uiState.value.moveCount)
        assertEquals(0, viewModel.uiState.value.remainingUndos)

        // Restarting level restores undo count to 5
        viewModel.restartLevel()
        advanceUntilIdle()
        assertEquals(MAX_UNDO_PER_STAGE, viewModel.uiState.value.remainingUndos)

        // Advancing to another stage also grants 5 fresh undos
        val tubesBeforeMove2 = viewModel.uiState.value.tubes
        val s = tubesBeforeMove2.indexOfFirst { !it.isEmpty }
        val d = tubesBeforeMove2.indices.first { idx ->
            idx != s && PuzzleEngine.canPour(tubesBeforeMove2[s], tubesBeforeMove2[idx])
        }
        viewModel.onTubeClick(s)
        viewModel.onTubeClick(d)
        advanceUntilIdle()
        viewModel.onUndoClick()
        advanceUntilIdle()
        assertEquals(4, viewModel.uiState.value.remainingUndos)

        viewModel.loadLevel(6)
        advanceUntilIdle()
        assertEquals(MAX_UNDO_PER_STAGE, viewModel.uiState.value.remainingUndos)
    }

    @Test
    fun dailyChallenge_persistsInStore_resumesSameDayBoard_andTracksStreak() = runTest {
        val fixedDate = "2026-11-26"
        val vm = GameBoardViewModel(
            repository = store,
            soundEngine = soundRecorder,
            pourAnimationDurationMs = 0L,
            computationDispatcher = testDispatcher,
            dateProvider = { fixedDate }
        )
        advanceUntilIdle()

        // Start Daily Challenge for fixedDate
        vm.startDailyChallenge()
        advanceUntilIdle()

        val initialDaily = vm.uiState.value
        assertTrue(initialDaily.isDailyChallenge)
        assertEquals(fixedDate, initialDaily.dailyDateKey)
        assertEquals("Nov 26", initialDaily.dailyDisplayDate)
        assertEquals(0, initialDaily.moveCount)
        assertTrue(store.dailySnapshots.containsKey(fixedDate))

        // Make a move and verify progress is saved to Daily Challenge store
        val src = initialDaily.tubes.indexOfFirst { !it.isEmpty }
        val dst = initialDaily.tubes.indices.first { idx ->
            idx != src && PuzzleEngine.canPour(initialDaily.tubes[src], initialDaily.tubes[idx])
        }
        vm.onTubeClick(src)
        vm.onTubeClick(dst)
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.moveCount)
        assertEquals(1, store.dailySnapshots[fixedDate]?.moveCount)
        assertTrue(vm.dailyChallengeSummary.value.inProgress)

        // Restarting Daily Challenge restores exact initial day board and keeps it in Daily mode
        vm.restartLevel()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isDailyChallenge)
        assertEquals(0, vm.uiState.value.moveCount)
        assertEquals(initialDaily.tubes, vm.uiState.value.tubes)

        // Returning to standard mode restores standard level without overwriting standard progress
        vm.resumeStandardMode()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isDailyChallenge)
        assertTrue(vm.uiState.value.levelNumber >= 1)
    }

    @Test
    fun importBackup_mergesHigherProgressAndUnlocksUnstartedLevel() = runTest {
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.levelNumber)

        val incoming = ProgressTransferPayload(
            highestUnlockedLevel = 6,
            completedLevels = mapOf(1 to 7, 2 to 9, 5 to 14),
            completedDailyMoves = mapOf("2026-11-26" to 20),
            exportedAtMs = 5_000L
        )
        val summary = viewModel.importPayloadDirectly(incoming)
        advanceUntilIdle()

        assertEquals(3, summary.newLevelsAdded)
        assertEquals(1, summary.newDailyAdded)
        assertEquals(6, summary.highestUnlockedLevel)
        assertEquals(6, viewModel.preferences.value.highestUnlockedLevel)
        assertEquals(6, viewModel.uiState.value.levelNumber)
    }

    @Test
    fun replaceWithBackup_replacesLocalProgressAndLoadsImportedSession() = runTest {
        advanceUntilIdle()
        viewModel.loadLevel(8)
        advanceUntilIdle()
        store.markLevelCompleted(7, 12)

        val importedTubes = listOf(
            TubeModel(id = 0, segments = listOf(0, 1, 0, 1)),
            TubeModel(id = 1, segments = listOf(1, 0, 1, 0)),
            TubeModel(id = 2, segments = emptyList()),
            TubeModel(id = 3, segments = emptyList())
        )
        val json = Json { encodeDefaults = true }
        val backup = ProgressTransferPayload(
            highestUnlockedLevel = 3,
            completedLevels = mapOf(1 to 5, 2 to 9),
            completedDailyMoves = mapOf("2026-11-27" to 16),
            activeSession = BackupActiveSession(
                currentLevel = 3,
                themeOrdinal = StudioThemeId.BOTANICAL_ZEN.ordinal,
                tubesJson = json.encodeToString(importedTubes),
                initialTubesJson = json.encodeToString(importedTubes),
                undoStackJson = "[]",
                remainingUndos = MAX_UNDO_PER_STAGE,
                extraTubeUsed = false,
                moveCount = 4
            ),
            exportedAtMs = 9_000L
        )

        val summary = viewModel.replaceWithBackupPayloadDirectly(backup)
        advanceUntilIdle()

        assertTrue(summary.restoredActiveSession)
        assertEquals(2, summary.newLevelsAdded)
        assertEquals(3, summary.highestUnlockedLevel)
        assertEquals(3, viewModel.preferences.value.highestUnlockedLevel)
        assertEquals(3, viewModel.uiState.value.levelNumber)
        assertEquals(4, viewModel.uiState.value.moveCount)
        assertEquals(importedTubes, viewModel.uiState.value.tubes)
    }

    @Test
    fun dynamicDifficulty_increasesColorCountAndTotalTubesAcrossLevels() = runTest {
        advanceUntilIdle()

        val levelsToCheck = listOf(1, 2, 5, 10, 25, 50)
        var prevColors = 0
        var prevTubes = 0
        for (lvl in levelsToCheck) {
            viewModel.loadLevel(lvl)
            advanceUntilIdle()
            val state = viewModel.uiState.value
            assertEquals(lvl, state.levelNumber)
            assertTrue("Colors should increase at level $lvl", state.colorCount > prevColors)
            assertTrue("Total tubes should increase at level $lvl", state.totalTubeCount > prevTubes)
            assertEquals(state.colorCount + 2, state.totalTubeCount)
            assertEquals(state.tubes.size, state.totalTubeCount)
            prevColors = state.colorCount
            prevTubes = state.totalTubeCount
        }
    }

    @Test
    fun restartLevel_afterSessionRestore_resetsWithoutRegeneratingNewLevel() = runTest {
        val originalInitialTubes = listOf(
            TubeModel(id = 0, segments = listOf(0, 1, 0, 1)),
            TubeModel(id = 1, segments = listOf(1, 0, 1, 0)),
            TubeModel(id = 2, segments = emptyList()),
            TubeModel(id = 3, segments = emptyList())
        )
        val afterMoveOutcome = PuzzleEngine.computePour(originalInitialTubes, 0, 2)!!
        store.saveActiveSession(
            currentLevel = 7,
            theme = StudioThemeId.DARK_NEON,
            tubes = afterMoveOutcome.updatedTubes,
            undoStack = listOf(afterMoveOutcome.move),
            remainingUndos = MAX_UNDO_PER_STAGE,
            extraTubeUsed = false,
            moveCount = 1,
            initialTubes = originalInitialTubes
        )

        val restoredVm = GameBoardViewModel(
            repository = store,
            soundEngine = soundRecorder,
            pourAnimationDurationMs = 0L,
            computationDispatcher = testDispatcher
        )
        advanceUntilIdle()

        assertEquals(7, restoredVm.uiState.value.levelNumber)
        assertEquals(afterMoveOutcome.updatedTubes, restoredVm.uiState.value.tubes)

        // Restart must restore the exact original board of level 7 without calling level generation
        restoredVm.restartLevel()
        advanceUntilIdle()

        val restarted = restoredVm.uiState.value
        assertEquals(7, restarted.levelNumber)
        assertEquals(originalInitialTubes, restarted.tubes)
        assertEquals(0, restarted.moveCount)
        assertTrue(restarted.undoStack.isEmpty())
    }

    private class RecordingSoundFeedback : SoundFeedback {
        val selectCalls = mutableListOf<Pair<Boolean, Boolean>>()
        val pourCalls = mutableListOf<Triple<Float, Boolean, Boolean>>()
        val colorMixCalls = mutableListOf<Pair<Int, Int>>()
        val undoCalls = mutableListOf<Pair<Boolean, Boolean>>()
        val errorCalls = mutableListOf<Pair<Boolean, Boolean>>()
        val victoryCalls = mutableListOf<Pair<Boolean, Boolean>>()

        override fun playSelect(soundEnabled: Boolean, hapticsEnabled: Boolean) {
            selectCalls.add(soundEnabled to hapticsEnabled)
        }

        override fun playPour(fillRatio: Float, soundEnabled: Boolean, hapticsEnabled: Boolean) {
            pourCalls.add(Triple(fillRatio, soundEnabled, hapticsEnabled))
        }

        override fun playColorMix(colorId: Int, units: Int, soundEnabled: Boolean, hapticsEnabled: Boolean) {
            colorMixCalls.add(colorId to units)
        }

        override fun playUndo(soundEnabled: Boolean, hapticsEnabled: Boolean) {
            undoCalls.add(soundEnabled to hapticsEnabled)
        }

        override fun playError(soundEnabled: Boolean, hapticsEnabled: Boolean) {
            errorCalls.add(soundEnabled to hapticsEnabled)
        }

        override fun playLevelComplete(soundEnabled: Boolean, hapticsEnabled: Boolean) {
            victoryCalls.add(soundEnabled to hapticsEnabled)
        }

        fun clear() {
            selectCalls.clear()
            pourCalls.clear()
            colorMixCalls.clear()
            undoCalls.clear()
            errorCalls.clear()
            victoryCalls.clear()
        }
    }

    private class FakeGameProgressStore : GameProgressStore {
        private val progressState = MutableStateFlow<List<LevelProgressEntity>>(emptyList())
        override val allProgressFlow: Flow<List<LevelProgressEntity>> = progressState
        private val dailyEntitiesState = MutableStateFlow<List<DailyChallengeEntity>>(emptyList())
        override val allDailyChallengesFlow: Flow<List<DailyChallengeEntity>> = dailyEntitiesState
        private val tierRecordsState = MutableStateFlow<List<DifficultyRecordEntity>>(emptyList())
        override val allTierRecordsFlow: Flow<List<DifficultyRecordEntity>> = tierRecordsState
        private val prefsState = MutableStateFlow(UserPreferences())
        override val preferencesFlow: Flow<UserPreferences> = prefsState
        private var session: ProgressRepository.ActiveSessionSnapshot? = null
        val dailySnapshots = mutableMapOf<String, ProgressRepository.DailyChallengeSnapshot>()

        override suspend fun loadActiveSession() = session

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
            val resolvedInitial = initialTubes.ifEmpty { session?.initialTubes ?: tubes }
            session = ProgressRepository.ActiveSessionSnapshot(
                currentLevel = currentLevel,
                theme = theme,
                tubes = tubes,
                undoStack = undoStack,
                remainingUndos = remainingUndos,
                extraTubeUsed = extraTubeUsed,
                moveCount = moveCount,
                initialTubes = resolvedInitial,
                elapsedTimeMs = elapsedTimeMs
            )
        }

        override suspend fun markLevelCompleted(
            levelNumber: Int,
            moves: Int,
            elapsedTimeMs: Long
        ): LevelCompletionResult {
            val existing = progressState.value.firstOrNull { it.levelNumber == levelNumber }
            val isNewBestMoves = existing?.bestMoves == null || moves < existing.bestMoves
            val bestMoves = if (existing?.bestMoves == null) moves else minOf(existing.bestMoves, moves)
            val validTime = elapsedTimeMs.takeIf { it > 0L }
            val bestLevelTime = when {
                validTime == null -> existing?.bestTimeMs
                existing?.bestTimeMs == null -> validTime
                else -> minOf(existing.bestTimeMs, validTime)
            }
            val updated = progressState.value.filterNot { it.levelNumber == levelNumber } +
                LevelProgressEntity(levelNumber, true, bestMoves, 1000L, bestLevelTime)
            progressState.value = updated

            val (tier, _) = PuzzleEngine.difficultyForLevel(levelNumber)
            val tierResult = recordTierBest(tier, validTime, levelNumber)
            return LevelCompletionResult(
                isNewBestMoves = isNewBestMoves,
                isNewBestTime = tierResult.first,
                bestTierTimeMs = tierResult.second
            )
        }

        private fun recordTierBest(
            tier: DifficultyTier,
            validTime: Long?,
            levelNumber: Int
        ): Pair<Boolean, Long?> {
            val existing = tierRecordsState.value.firstOrNull { it.tierOrdinal == tier.ordinal }
            if (validTime == null) return false to existing?.bestTimeMs
            val isNewBest = existing == null || validTime < existing.bestTimeMs
            val best = if (existing == null) validTime else minOf(existing.bestTimeMs, validTime)
            if (isNewBest) {
                tierRecordsState.value = tierRecordsState.value.filterNot { it.tierOrdinal == tier.ordinal } +
                    DifficultyRecordEntity(tier.ordinal, best, levelNumber, 1000L)
            }
            return isNewBest to best
        }

        override suspend fun loadOrCreateDailyChallenge(
            dateKey: String,
            generator: suspend (String) -> DailyChallengeSpec
        ): ProgressRepository.DailyChallengeSnapshot {
            dailySnapshots[dateKey]?.let { return it }
            val spec = generator(dateKey)
            val created = ProgressRepository.DailyChallengeSnapshot(
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
            dailySnapshots[dateKey] = created
            syncDailyEntitiesFlow()
            return created
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
            val existing = dailySnapshots[dateKey] ?: return
            dailySnapshots[dateKey] = existing.copy(
                theme = theme,
                currentTubes = currentTubes,
                undoStack = undoStack,
                remainingUndos = remainingUndos,
                extraTubeUsed = extraTubeUsed,
                moveCount = moveCount,
                elapsedTimeMs = elapsedTimeMs
            )
            syncDailyEntitiesFlow()
        }

        override suspend fun markDailyChallengeCompleted(
            dateKey: String,
            moves: Int,
            elapsedTimeMs: Long
        ): LevelCompletionResult {
            val existing = dailySnapshots[dateKey] ?: return LevelCompletionResult(false)
            val isNewBest = existing.bestMoves == null || moves < existing.bestMoves
            val best = if (existing.bestMoves == null) moves else minOf(existing.bestMoves, moves)
            dailySnapshots[dateKey] = existing.copy(
                isCompleted = true,
                bestMoves = best,
                elapsedTimeMs = elapsedTimeMs
            )
            syncDailyEntitiesFlow()
            val tierResult = recordTierBest(existing.tier, elapsedTimeMs.takeIf { it > 0L }, 0)
            return LevelCompletionResult(
                isNewBestMoves = isNewBest,
                isNewBestTime = tierResult.first,
                bestTierTimeMs = tierResult.second
            )
        }

        private fun syncDailyEntitiesFlow() {
            dailyEntitiesState.update {
                dailySnapshots.values.map { s ->
                    DailyChallengeEntity(
                        dateKey = s.dateKey,
                        tierOrdinal = s.tier.ordinal,
                        colorCount = s.colorCount,
                        themeOrdinal = s.theme.ordinal,
                        initialTubesJson = "",
                        currentTubesJson = "",
                        undoStackJson = "",
                        remainingUndos = s.remainingUndos,
                        extraTubeUsed = s.extraTubeUsed,
                        moveCount = s.moveCount,
                        isCompleted = s.isCompleted,
                        bestMoves = s.bestMoves,
                        completedAtMs = if (s.isCompleted) 1000L else null,
                        updatedAtMs = 1000L,
                        elapsedTimeMs = s.elapsedTimeMs
                    )
                }
            }
        }

        override suspend fun exportBackupPayload(): ProgressTransferPayload {
            val prefs = prefsState.value
            return ProgressTransferPayload(
                highestUnlockedLevel = prefs.highestUnlockedLevel,
                completedLevels = progressState.value.associate { it.levelNumber to (it.bestMoves ?: 0) },
                completedDailyMoves = dailySnapshots.filterValues { it.isCompleted }
                    .mapValues { (_, v) -> v.bestMoves ?: v.moveCount },
                bestLevelTimes = progressState.value.mapNotNull { row ->
                    row.bestTimeMs?.let { row.levelNumber to it }
                }.toMap(),
                bestTierTimes = tierRecordsState.value.associate { it.tierOrdinal to it.bestTimeMs },
                soundEnabled = prefs.soundEnabled,
                hapticsEnabled = prefs.hapticsEnabled,
                colorblindEnabled = prefs.colorblindEnabled,
                randomizeThemePerLevel = prefs.randomizeThemePerLevel,
                timerEnabled = prefs.timerEnabled,
                pinnedThemeOrdinal = prefs.pinnedTheme.ordinal,
                exportedAtMs = 1_000L
            )
        }

        override suspend fun importBackupPayload(
            incoming: ProgressTransferPayload,
            importSettings: Boolean
        ): ImportSummary {
            val local = exportBackupPayload()
            val merged = ProgressTransferCodec.mergePayloads(local, incoming)
            val newLevels = (merged.completedLevels.keys - local.completedLevels.keys).size
            val newDaily = (merged.completedDailyMoves.keys - local.completedDailyMoves.keys).size
            progressState.value = merged.completedLevels.map { (lvl, moves) ->
                LevelProgressEntity(lvl, true, moves.takeIf { it > 0 }, merged.exportedAtMs, merged.bestLevelTimes[lvl])
            }
            tierRecordsState.value = merged.bestTierTimes.map { (ord, ms) ->
                DifficultyRecordEntity(ord, ms, 0, merged.exportedAtMs)
            }
            for ((date, moves) in merged.completedDailyMoves) {
                val existing = dailySnapshots[date]
                if (existing == null) {
                    val spec = PuzzleEngine.generateDailyChallenge(date)
                    dailySnapshots[date] = ProgressRepository.DailyChallengeSnapshot(
                        dateKey = date,
                        tier = spec.tier,
                        colorCount = spec.colorCount,
                        theme = spec.defaultTheme,
                        initialTubes = spec.tubes,
                        currentTubes = spec.tubes,
                        undoStack = emptyList(),
                        remainingUndos = MAX_UNDO_PER_STAGE,
                        extraTubeUsed = false,
                        moveCount = moves,
                        isCompleted = true,
                        bestMoves = moves
                    )
                }
            }
            syncDailyEntitiesFlow()
            prefsState.value = prefsState.value.copy(highestUnlockedLevel = merged.highestUnlockedLevel)
            return ImportSummary(newLevels, newDaily, merged.highestUnlockedLevel)
        }

        override suspend fun replaceWithBackupPayload(incoming: ProgressTransferPayload): ImportSummary {
            val json = Json { ignoreUnknownKeys = true }
            progressState.value = incoming.completedLevels.map { (lvl, moves) ->
                LevelProgressEntity(lvl, true, moves.takeIf { it > 0 }, incoming.exportedAtMs, incoming.bestLevelTimes[lvl])
            }
            tierRecordsState.value = incoming.bestTierTimes.map { (ord, ms) ->
                DifficultyRecordEntity(ord, ms, 0, incoming.exportedAtMs)
            }
            dailySnapshots.clear()
            for ((date, moves) in incoming.completedDailyMoves) {
                val spec = PuzzleEngine.generateDailyChallenge(date)
                dailySnapshots[date] = ProgressRepository.DailyChallengeSnapshot(
                    dateKey = date,
                    tier = spec.tier,
                    colorCount = spec.colorCount,
                    theme = spec.defaultTheme,
                    initialTubes = spec.tubes,
                    currentTubes = spec.tubes,
                    undoStack = emptyList(),
                    remainingUndos = MAX_UNDO_PER_STAGE,
                    extraTubeUsed = false,
                    moveCount = moves,
                    isCompleted = true,
                    bestMoves = moves
                )
            }
            syncDailyEntitiesFlow()
            val incomingSession = incoming.activeSession
            session = if (incomingSession != null) {
                val tubes = json.decodeFromString<List<TubeModel>>(incomingSession.tubesJson)
                val initial = if (incomingSession.initialTubesJson.isNotBlank()) {
                    json.decodeFromString<List<TubeModel>>(incomingSession.initialTubesJson)
                } else {
                    tubes
                }
                val undo = json.decodeFromString<List<PourMove>>(incomingSession.undoStackJson)
                val theme = StudioThemeId.entries[incomingSession.themeOrdinal.coerceIn(0, StudioThemeId.entries.lastIndex)]
                ProgressRepository.ActiveSessionSnapshot(
                    currentLevel = incomingSession.currentLevel,
                    theme = theme,
                    tubes = tubes,
                    undoStack = undo,
                    remainingUndos = incomingSession.remainingUndos,
                    extraTubeUsed = incomingSession.extraTubeUsed,
                    moveCount = incomingSession.moveCount,
                    initialTubes = initial,
                    elapsedTimeMs = incomingSession.elapsedTimeMs
                )
            } else {
                null
            }
            prefsState.value = prefsState.value.copy(
                highestUnlockedLevel = incoming.highestUnlockedLevel,
                timerEnabled = incoming.timerEnabled
            )
            return ImportSummary(
                newLevelsAdded = incoming.completedLevels.size,
                newDailyAdded = dailySnapshots.size,
                highestUnlockedLevel = incoming.highestUnlockedLevel,
                restoredActiveSession = incomingSession != null
            )
        }

        override suspend fun setSoundEnabled(enabled: Boolean) { prefsState.value = prefsState.value.copy(soundEnabled = enabled) }
        override suspend fun setHapticsEnabled(enabled: Boolean) { prefsState.value = prefsState.value.copy(hapticsEnabled = enabled) }
        override suspend fun setColorblindEnabled(enabled: Boolean) { prefsState.value = prefsState.value.copy(colorblindEnabled = enabled) }
        override suspend fun setRandomizeThemePerLevel(enabled: Boolean) { prefsState.value = prefsState.value.copy(randomizeThemePerLevel = enabled) }
        override suspend fun setTimerEnabled(enabled: Boolean) { prefsState.value = prefsState.value.copy(timerEnabled = enabled) }
        override suspend fun setPinnedTheme(theme: StudioThemeId) { prefsState.value = prefsState.value.copy(pinnedTheme = theme) }
    }
}

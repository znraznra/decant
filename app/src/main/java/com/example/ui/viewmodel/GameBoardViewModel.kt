package com.example.ui.viewmodel

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.audio.SoundFeedback
import com.example.data.GameProgressStore
import com.example.data.local.DailyChallengeEntity
import com.example.data.local.LevelProgressEntity
import com.example.data.prefs.UserPreferences
import com.example.domain.DailyChallengeCardSummary
import com.example.domain.DifficultyTier
import com.example.domain.LevelDifficultySpec
import com.example.domain.LevelSpec
import com.example.domain.MAX_UNDO_PER_STAGE
import com.example.domain.PourAnimationState
import com.example.domain.PourMove
import com.example.domain.PuzzleEngine
import com.example.domain.StudioThemeId
import com.example.domain.SyncStatus
import com.example.domain.TUBE_CAPACITY
import com.example.domain.TubeModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GameUiState(
    val isLoading: Boolean = true,
    val levelNumber: Int = 1,
    val tier: DifficultyTier = DifficultyTier.WARMUP,
    val colorCount: Int = 3,
    val activeTheme: StudioThemeId = StudioThemeId.CLEAN_GLASS,
    val tubes: List<TubeModel> = emptyList(),
    val undoStack: List<PourMove> = emptyList(),
    val remainingUndos: Int = MAX_UNDO_PER_STAGE,
    val selectedTubeIndex: Int? = null,
    val validTargetIndices: Set<Int> = emptySet(),
    val invalidShakeIndex: Int? = null,
    val invalidShakeTick: Int = 0,
    val moveCount: Int = 0,
    val extraTubeUsed: Boolean = false,
    val isLevelComplete: Boolean = false,
    val isNewBestMoves: Boolean = false,
    val hasValidMovesLeft: Boolean = true,
    val pourAnimation: PourAnimationState? = null,
    val isDailyChallenge: Boolean = false,
    val dailyDateKey: String? = null,
    val dailyDisplayDate: String = ""
) {
    val totalTubeCount: Int get() = tubes.size
    val canUndo: Boolean get() = undoStack.isNotEmpty() && remainingUndos > 0
}

class GameBoardViewModel(
    private val repository: GameProgressStore,
    private val soundEngine: SoundFeedback,
    private val pourAnimationDurationMs: Long = 260L,
    private val computationDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val dateProvider: () -> String = { PuzzleEngine.currentLocalDateKey() }
) : ViewModel() {

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    val preferences: StateFlow<UserPreferences> = repository.preferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = UserPreferences()
    )

    val completedProgress: StateFlow<List<LevelProgressEntity>> = repository.allProgressFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val dailyChallengeSummary: StateFlow<DailyChallengeCardSummary> =
        repository.allDailyChallengesFlow
            .map { entries -> buildDailySummary(entries) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = buildDailySummary(emptyList())
            )

    val syncStatus: StateFlow<SyncStatus> = repository.syncStatus

    private var hasInitialized = false
    private var initialLevelNumber: Int = 0
    private var initialLevelTubes: List<TubeModel> = emptyList()
    private var initialDailyDateKey: String? = null
    private var initialDailyTubes: List<TubeModel> = emptyList()

    init {
        initializeSession()
    }

    private fun buildDailySummary(entries: List<DailyChallengeEntity>): DailyChallengeCardSummary {
        val todayKey = dateProvider()
        val todayEntry = entries.firstOrNull { it.dateKey == todayKey }
        val (defaultTier, defaultColorCount) = PuzzleEngine.dailyDifficultyForDate(todayKey)
        val completedKeys = entries.filter { it.isCompleted }.mapTo(HashSet()) { it.dateKey }
        val streak = PuzzleEngine.computeDailyStreak(completedKeys, todayKey)
        val tier = todayEntry?.let {
            DifficultyTier.entries[it.tierOrdinal.coerceIn(0, DifficultyTier.entries.lastIndex)]
        } ?: defaultTier
        return DailyChallengeCardSummary(
            dateKey = todayKey,
            displayDate = PuzzleEngine.formatDisplayDate(todayKey),
            tier = tier,
            colorCount = todayEntry?.colorCount ?: defaultColorCount,
            isCompleted = todayEntry?.isCompleted == true,
            inProgress = todayEntry != null && !todayEntry.isCompleted && todayEntry.moveCount > 0,
            moveCount = todayEntry?.moveCount ?: 0,
            bestMoves = todayEntry?.bestMoves,
            currentStreak = streak
        )
    }

    private fun initializeSession() {
        if (hasInitialized) return
        hasInitialized = true
        viewModelScope.launch {
            loadStandardSessionInternal()
        }
    }

    private suspend fun loadStandardSessionInternal() {
        val prefs = repository.preferencesFlow.first()
        val savedSession = repository.loadActiveSession()
        if (savedSession != null && savedSession.tubes.isNotEmpty()) {
            val (tier, colorCount) = PuzzleEngine.difficultyForLevel(savedSession.currentLevel)
            val theme = if (prefs.randomizeThemePerLevel) {
                savedSession.theme
            } else {
                prefs.pinnedTheme
            }
            initialLevelNumber = savedSession.currentLevel
            initialLevelTubes = savedSession.initialTubes.ifEmpty {
                reconstructInitialTubes(savedSession.tubes, savedSession.undoStack, savedSession.extraTubeUsed)
            }
            val solved = PuzzleEngine.isLevelSolved(savedSession.tubes)
            _uiState.value = GameUiState(
                isLoading = false,
                levelNumber = savedSession.currentLevel,
                tier = tier,
                colorCount = colorCount,
                activeTheme = theme,
                tubes = savedSession.tubes,
                undoStack = savedSession.undoStack,
                remainingUndos = savedSession.remainingUndos.coerceIn(0, MAX_UNDO_PER_STAGE),
                moveCount = savedSession.moveCount,
                extraTubeUsed = savedSession.extraTubeUsed,
                isLevelComplete = solved,
                hasValidMovesLeft = solved || PuzzleEngine.hasAnyValidMoves(savedSession.tubes),
                isDailyChallenge = false,
                dailyDateKey = null,
                dailyDisplayDate = ""
            )
        } else {
            loadLevelInternal(prefs.highestUnlockedLevel.coerceAtLeast(1), prefs)
        }
    }

    /**
     * Computes progressive difficulty for [levelNumber], visibly stepping up both
     * the number of liquid colors and total tubes along a balanced learning curve.
     */
    fun levelProgressionFor(levelNumber: Int): LevelDifficultySpec =
        PuzzleEngine.levelDifficultySpec(levelNumber.coerceAtLeast(1))

    /**
     * Generates a deterministic, solver-verified water sort level whose color count
     * and total tube count scale dynamically as levelNumber progresses.
     */
    suspend fun generateSolvableLevel(levelNumber: Int): LevelSpec = withContext(computationDispatcher) {
        val safeLevel = levelNumber.coerceAtLeast(1)
        val progression = levelProgressionFor(safeLevel)
        PuzzleEngine.generateLevel(
            levelNumber = safeLevel,
            overrideColorCount = progression.colorCount,
            overrideEmptyCount = progression.emptyTubeCount
        )
    }

    fun resumeStandardMode() {
        if (!_uiState.value.isDailyChallenge) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            loadStandardSessionInternal()
        }
    }

    fun startDailyChallenge() {
        viewModelScope.launch {
            val todayKey = dateProvider()
            _uiState.update { it.copy(isLoading = true) }
            val prefs = repository.preferencesFlow.first()
            val snapshot = repository.loadOrCreateDailyChallenge(todayKey) { key ->
                withContext(computationDispatcher) {
                    PuzzleEngine.generateDailyChallenge(key)
                }
            }
            initialDailyDateKey = snapshot.dateKey
            initialDailyTubes = snapshot.initialTubes
            val theme = if (prefs.randomizeThemePerLevel) snapshot.theme else prefs.pinnedTheme
            val boardSolved = PuzzleEngine.isLevelSolved(snapshot.currentTubes)
            _uiState.value = GameUiState(
                isLoading = false,
                levelNumber = 0,
                tier = snapshot.tier,
                colorCount = snapshot.colorCount,
                activeTheme = theme,
                tubes = snapshot.currentTubes,
                undoStack = snapshot.undoStack,
                remainingUndos = snapshot.remainingUndos.coerceIn(0, MAX_UNDO_PER_STAGE),
                selectedTubeIndex = null,
                validTargetIndices = emptySet(),
                invalidShakeIndex = null,
                moveCount = snapshot.moveCount,
                extraTubeUsed = snapshot.extraTubeUsed,
                isLevelComplete = boardSolved,
                isNewBestMoves = false,
                hasValidMovesLeft = boardSolved || PuzzleEngine.hasAnyValidMoves(snapshot.currentTubes),
                isDailyChallenge = true,
                dailyDateKey = snapshot.dateKey,
                dailyDisplayDate = PuzzleEngine.formatDisplayDate(snapshot.dateKey)
            )
        }
    }

    fun loadLevel(levelNumber: Int) {
        viewModelScope.launch {
            val prefs = repository.preferencesFlow.first()
            loadLevelInternal(levelNumber.coerceAtLeast(1), prefs)
        }
    }

    private suspend fun loadLevelInternal(levelNumber: Int, prefs: UserPreferences) {
        _uiState.update { it.copy(isLoading = true) }
        val spec = generateSolvableLevel(levelNumber)
        initialLevelNumber = spec.levelNumber
        initialLevelTubes = spec.tubes
        val theme = if (prefs.randomizeThemePerLevel) spec.defaultTheme else prefs.pinnedTheme
        _uiState.value = GameUiState(
            isLoading = false,
            levelNumber = spec.levelNumber,
            tier = spec.tier,
            colorCount = spec.colorCount,
            activeTheme = theme,
            tubes = spec.tubes,
            undoStack = emptyList(),
            remainingUndos = MAX_UNDO_PER_STAGE,
            selectedTubeIndex = null,
            validTargetIndices = emptySet(),
            invalidShakeIndex = null,
            moveCount = 0,
            extraTubeUsed = false,
            isLevelComplete = false,
            isNewBestMoves = false,
            hasValidMovesLeft = true,
            isDailyChallenge = false,
            dailyDateKey = null,
            dailyDisplayDate = ""
        )
        persistActiveBoard()
    }

    fun onTubeClick(index: Int, activity: Activity? = null) {
        val state = _uiState.value
        if (state.isLoading || state.isLevelComplete || state.pourAnimation != null) return
        if (index !in state.tubes.indices) return

        val prefs = preferences.value
        val selected = state.selectedTubeIndex

        if (selected == null) {
            if (!state.tubes[index].isEmpty) {
                soundEngine.playSelect(prefs.soundEnabled, prefs.hapticsEnabled)
                val targets = computeValidTargets(state.tubes, index)
                _uiState.update {
                    it.copy(
                        selectedTubeIndex = index,
                        validTargetIndices = targets
                    )
                }
            }
            return
        }

        if (selected == index) {
            soundEngine.playSelect(prefs.soundEnabled, prefs.hapticsEnabled)
            _uiState.update {
                it.copy(selectedTubeIndex = null, validTargetIndices = emptySet())
            }
            return
        }

        val outcome = PuzzleEngine.computePour(state.tubes, selected, index)
        if (outcome == null) {
            soundEngine.playError(prefs.soundEnabled, prefs.hapticsEnabled)
            _uiState.update {
                it.copy(
                    selectedTubeIndex = null,
                    validTargetIndices = emptySet(),
                    invalidShakeIndex = index,
                    invalidShakeTick = it.invalidShakeTick + 1
                )
            }
            return
        }

        executeAnimatedPour(
            fromIndex = selected,
            toIndex = index,
            colorId = outcome.move.colorId,
            units = outcome.move.unitsMoved,
            targetTubes = outcome.updatedTubes,
            updatedUndo = state.undoStack + outcome.move,
            newRemainingUndos = state.remainingUndos,
            newMoveCount = state.moveCount + 1,
            isReverse = false,
            activity = activity
        )
    }

    fun onUndoClick() {
        val state = _uiState.value
        if (state.isLoading || state.isLevelComplete || state.pourAnimation != null || state.remainingUndos <= 0) return
        val lastMove = state.undoStack.lastOrNull() ?: return

        val prefs = preferences.value
        soundEngine.playUndo(prefs.soundEnabled, prefs.hapticsEnabled)

        val reversedTubes = PuzzleEngine.applyReverseMove(state.tubes, lastMove)
        executeAnimatedPour(
            fromIndex = lastMove.toIndex,
            toIndex = lastMove.fromIndex,
            colorId = lastMove.colorId,
            units = lastMove.unitsMoved,
            targetTubes = reversedTubes,
            updatedUndo = state.undoStack.dropLast(1),
            newRemainingUndos = (state.remainingUndos - 1).coerceAtLeast(0),
            newMoveCount = (state.moveCount - 1).coerceAtLeast(0),
            isReverse = true
        )
    }

    private fun computeValidTargets(tubes: List<TubeModel>, sourceIndex: Int): Set<Int> {
        val source = tubes.getOrNull(sourceIndex) ?: return emptySet()
        return tubes.indices.filterTo(mutableSetOf()) { targetIdx ->
            targetIdx != sourceIndex && PuzzleEngine.canPour(source, tubes[targetIdx])
        }
    }

    private fun executeAnimatedPour(
        fromIndex: Int,
        toIndex: Int,
        colorId: Int,
        units: Int,
        targetTubes: List<TubeModel>,
        updatedUndo: List<PourMove>,
        newRemainingUndos: Int,
        newMoveCount: Int,
        isReverse: Boolean,
        activity: Activity? = null
    ) {
        val prefs = preferences.value
        if (!isReverse) {
            val fillRatio = targetTubes[toIndex].segments.size.toFloat() / TUBE_CAPACITY.toFloat()
            soundEngine.playPour(fillRatio, prefs.soundEnabled, prefs.hapticsEnabled)
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selectedTubeIndex = null,
                    validTargetIndices = emptySet(),
                    pourAnimation = PourAnimationState(
                        fromIndex = fromIndex,
                        toIndex = toIndex,
                        colorId = colorId,
                        units = units,
                        progress = 0f,
                        isReverse = isReverse
                    )
                )
            }

            if (pourAnimationDurationMs > 0L) {
                val frameStepMs = 16L
                val totalSteps = (pourAnimationDurationMs / frameStepMs).toInt().coerceAtLeast(1)
                for (step in 1..totalSteps) {
                    delay(frameStepMs)
                    val linear = step.toFloat() / totalSteps.toFloat()
                    // Smooth cubic ease-in-out
                    val eased = if (linear < 0.5f) {
                        4f * linear * linear * linear
                    } else {
                        1f - (-2f * linear + 2f).let { it * it * it } / 2f
                    }
                    _uiState.update { curr ->
                        curr.copy(pourAnimation = curr.pourAnimation?.copy(progress = eased))
                    }
                }
            }

            val solved = PuzzleEngine.isLevelSolved(targetTubes)
            val movesLeft = solved || PuzzleEngine.hasAnyValidMoves(targetTubes)

            var isNewBest = false
            val currentSnapshot = _uiState.value
            if (solved) {
                soundEngine.playVictory(prefs.soundEnabled, prefs.hapticsEnabled)
                isNewBest = if (currentSnapshot.isDailyChallenge) {
                    val key = currentSnapshot.dailyDateKey
                    if (key != null) repository.markDailyChallengeCompleted(key, newMoveCount) else false
                } else {
                    repository.markLevelCompleted(currentSnapshot.levelNumber, newMoveCount)
                }
            }

            _uiState.update {
                it.copy(
                    tubes = targetTubes,
                    undoStack = updatedUndo,
                    remainingUndos = newRemainingUndos.coerceIn(0, MAX_UNDO_PER_STAGE),
                    moveCount = newMoveCount,
                    pourAnimation = null,
                    isLevelComplete = solved,
                    isNewBestMoves = isNewBest,
                    hasValidMovesLeft = movesLeft
                )
            }

            persistActiveBoard()
            if (solved && activity != null && preferences.value.playGamesSyncEnabled) {
                repository.syncWithCloudIfEnabled(activity, interactiveSignIn = false)
            }
        }
    }

    /**
     * Resets the current puzzle board back to its initial configuration without
     * generating a new level, preserving the current level and difficulty progression.
     */
    fun restartLevel() {
        val state = _uiState.value
        if (state.isLoading || state.pourAnimation != null || state.tubes.isEmpty()) return
        val prefs = preferences.value
        soundEngine.playSelect(prefs.soundEnabled, prefs.hapticsEnabled)

        if (state.isDailyChallenge) {
            val dateKey = state.dailyDateKey ?: return
            val resetTubes = if (initialDailyDateKey == dateKey && initialDailyTubes.isNotEmpty()) {
                initialDailyTubes
            } else {
                reconstructInitialTubes(state.tubes, state.undoStack, state.extraTubeUsed).also {
                    initialDailyDateKey = dateKey
                    initialDailyTubes = it
                }
            }
            applyInitialReset(resetTubes)
            viewModelScope.launch { persistActiveBoard() }
            return
        }

        val resetTubes = if (initialLevelNumber == state.levelNumber && initialLevelTubes.isNotEmpty()) {
            initialLevelTubes
        } else {
            reconstructInitialTubes(state.tubes, state.undoStack, state.extraTubeUsed).also {
                initialLevelNumber = state.levelNumber
                initialLevelTubes = it
            }
        }
        applyInitialReset(resetTubes)
        viewModelScope.launch { persistActiveBoard() }
    }

    private fun reconstructInitialTubes(
        currentTubes: List<TubeModel>,
        undoStack: List<PourMove>,
        extraTubeUsed: Boolean
    ): List<TubeModel> {
        var rebuilt = currentTubes
        for (i in undoStack.lastIndex downTo 0) {
            rebuilt = PuzzleEngine.applyReverseMove(rebuilt, undoStack[i])
        }
        return if (extraTubeUsed) rebuilt.filterNot { it.isExtraTube } else rebuilt
    }

    private fun applyInitialReset(resetTubes: List<TubeModel>) {
        _uiState.update {
            it.copy(
                tubes = resetTubes,
                undoStack = emptyList(),
                remainingUndos = MAX_UNDO_PER_STAGE,
                selectedTubeIndex = null,
                validTargetIndices = emptySet(),
                invalidShakeIndex = null,
                moveCount = 0,
                extraTubeUsed = false,
                isLevelComplete = false,
                isNewBestMoves = false,
                hasValidMovesLeft = PuzzleEngine.hasAnyValidMoves(resetTubes)
            )
        }
    }

    fun onAddExtraTubeClick() {
        val state = _uiState.value
        if (state.extraTubeUsed || state.isLevelComplete || state.pourAnimation != null) return
        val prefs = preferences.value
        soundEngine.playSelect(prefs.soundEnabled, prefs.hapticsEnabled)

        val newId = (state.tubes.maxOfOrNull { it.id } ?: 0) + 1
        val updatedTubes = state.tubes + TubeModel(
            id = newId,
            segments = emptyList(),
            isExtraTube = true
        )
        _uiState.update {
            it.copy(
                tubes = updatedTubes,
                extraTubeUsed = true,
                selectedTubeIndex = null,
                validTargetIndices = emptySet(),
                hasValidMovesLeft = true
            )
        }
        viewModelScope.launch { persistActiveBoard() }
    }

    fun onShuffleThemeClick() {
        val state = _uiState.value
        val prefs = preferences.value
        soundEngine.playSelect(prefs.soundEnabled, prefs.hapticsEnabled)
        val themes = StudioThemeId.entries
        val nextTheme = themes[(state.activeTheme.ordinal + 1) % themes.size]
        _uiState.update { it.copy(activeTheme = nextTheme) }
        viewModelScope.launch { persistActiveBoard() }
    }

    fun onNextLevelClick() {
        val state = _uiState.value
        if (state.isDailyChallenge) return
        val nextLevel = state.levelNumber + 1
        loadLevel(nextLevel)
    }

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setSoundEnabled(enabled) }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setHapticsEnabled(enabled) }
    }

    fun setColorblindEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setColorblindEnabled(enabled) }
    }

    fun setRandomizeThemePerLevel(enabled: Boolean) {
        viewModelScope.launch {
            repository.setRandomizeThemePerLevel(enabled)
            if (!enabled) {
                val pinned = preferences.value.pinnedTheme
                _uiState.update { it.copy(activeTheme = pinned) }
                persistActiveBoard()
            }
        }
    }

    fun setPinnedTheme(theme: StudioThemeId) {
        viewModelScope.launch {
            repository.setPinnedTheme(theme)
            repository.setRandomizeThemePerLevel(false)
            _uiState.update { it.copy(activeTheme = theme) }
            persistActiveBoard()
        }
    }

    fun onAppResumeSync(activity: Activity) {
        if (!preferences.value.playGamesSyncEnabled) return
        if (syncStatus.value == SyncStatus.Syncing) return
        viewModelScope.launch {
            val updated = repository.syncWithCloudIfEnabled(activity, interactiveSignIn = false)
            if (updated) refreshUnstartedBoardAfterCloudSync()
        }
    }

    fun triggerManualCloudSync(activity: Activity) {
        viewModelScope.launch {
            val updated = repository.syncWithCloudIfEnabled(activity, interactiveSignIn = true)
            if (updated) refreshUnstartedBoardAfterCloudSync()
        }
    }

    fun setPlayGamesSyncEnabled(enabled: Boolean, activity: Activity? = null) {
        viewModelScope.launch {
            repository.setPlayGamesSyncEnabled(enabled)
            if (enabled && activity != null) {
                val updated = repository.syncWithCloudIfEnabled(activity, interactiveSignIn = true)
                if (updated) refreshUnstartedBoardAfterCloudSync()
            }
        }
    }

    private suspend fun refreshUnstartedBoardAfterCloudSync() {
        val state = _uiState.value
        if (!state.isDailyChallenge && state.moveCount == 0 && !state.isLevelComplete) {
            val latestPrefs = repository.preferencesFlow.first()
            if (latestPrefs.highestUnlockedLevel > state.levelNumber) {
                loadLevelInternal(latestPrefs.highestUnlockedLevel, latestPrefs)
            }
        }
    }

    private suspend fun persistActiveBoard() {
        val state = _uiState.value
        if (state.tubes.isEmpty()) return
        if (state.isDailyChallenge) {
            val dateKey = state.dailyDateKey ?: return
            repository.saveDailyChallengeProgress(
                dateKey = dateKey,
                theme = state.activeTheme,
                currentTubes = state.tubes,
                undoStack = state.undoStack,
                remainingUndos = state.remainingUndos,
                extraTubeUsed = state.extraTubeUsed,
                moveCount = state.moveCount
            )
        } else {
            repository.saveActiveSession(
                currentLevel = state.levelNumber,
                theme = state.activeTheme,
                tubes = state.tubes,
                undoStack = state.undoStack,
                remainingUndos = state.remainingUndos,
                extraTubeUsed = state.extraTubeUsed,
                moveCount = state.moveCount,
                initialTubes = if (initialLevelNumber == state.levelNumber) initialLevelTubes else emptyList()
            )
        }
    }

    class Factory(
        private val repository: GameProgressStore,
        private val soundEngine: SoundFeedback
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GameBoardViewModel(repository, soundEngine) as T
        }
    }
}

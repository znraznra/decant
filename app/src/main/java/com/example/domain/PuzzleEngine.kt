package com.example.domain

import java.util.Calendar
import java.util.Locale
import kotlin.math.min
import kotlin.random.Random

object PuzzleEngine {

    data class PourOutcome(
        val updatedTubes: List<TubeModel>,
        val move: PourMove
    )

    fun levelDifficultySpec(levelNumber: Int): LevelDifficultySpec {
        val safeLevel = levelNumber.coerceAtLeast(1)
        val tier = when {
            safeLevel <= 3 -> DifficultyTier.WARMUP
            safeLevel <= 15 -> DifficultyTier.CLASSIC
            safeLevel <= 39 -> DifficultyTier.TRICKSTER
            safeLevel <= 89 -> DifficultyTier.MASTER
            else -> DifficultyTier.ABYSSAL
        }
        val baseColors = when {
            safeLevel == 1 -> 3
            safeLevel <= 3 -> 4
            safeLevel <= 6 -> 5
            safeLevel <= 11 -> 6
            safeLevel <= 18 -> 7
            safeLevel <= 27 -> 8
            safeLevel <= 39 -> 9
            safeLevel <= 59 -> 10
            safeLevel <= 99 -> 11
            else -> 12
        }
        // Subtle periodic breather wave every 15 levels starting at 30
        val colorCount = if (safeLevel >= 30 && safeLevel % 15 == 0) {
            (baseColors - 1).coerceAtLeast(5)
        } else {
            baseColors
        }.coerceIn(3, MAX_COLOR_COUNT)

        val emptyTubeCount = 2
        return LevelDifficultySpec(
            tier = tier,
            colorCount = colorCount,
            emptyTubeCount = emptyTubeCount,
            totalTubeCount = colorCount + emptyTubeCount
        )
    }

    fun difficultyForLevel(levelNumber: Int): Pair<DifficultyTier, Int> {
        val spec = levelDifficultySpec(levelNumber)
        return spec.tier to spec.colorCount
    }

    fun defaultThemeForLevel(levelNumber: Int): StudioThemeId {
        val rng = Random(levelNumber.toLong() * 2654435761L + 17L)
        val themes = StudioThemeId.entries
        return themes[rng.nextInt(themes.size)]
    }

    fun generateLevel(
        levelNumber: Int,
        overrideColorCount: Int? = null,
        overrideEmptyCount: Int? = null
    ): LevelSpec {
        val safeLevel = levelNumber.coerceAtLeast(1)
        val diffSpec = levelDifficultySpec(safeLevel)
        val tier = diffSpec.tier
        val colorCount = (overrideColorCount ?: diffSpec.colorCount).coerceIn(3, MAX_COLOR_COUNT)
        val emptyCount = (overrideEmptyCount ?: diffSpec.emptyTubeCount).coerceIn(2, 3)

        var attempt = 0
        while (attempt < 64) {
            val seed = safeLevel.toLong() * 6364136223846793005L + attempt.toLong() * 1442695040888963407L + 97L
            val rng = Random(seed)
            val candidate = buildMixedBoard(colorCount, emptyCount, safeLevel, rng)
            if (!isBoardTrivial(candidate) && hasAnyValidMoves(candidate) && isSolvable(candidate)) {
                return LevelSpec(
                    levelNumber = safeLevel,
                    tier = tier,
                    colorCount = colorCount,
                    tubes = candidate,
                    defaultTheme = defaultThemeForLevel(safeLevel)
                )
            }
            attempt++
        }

        // Guaranteed deterministic fallback using validated reverse moves from solved state
        val fallbackTubes = buildGuaranteedReverseShuffle(colorCount, emptyCount, Random(safeLevel * 7919L))
        return LevelSpec(
            levelNumber = safeLevel,
            tier = tier,
            colorCount = colorCount,
            tubes = fallbackTubes,
            defaultTheme = defaultThemeForLevel(safeLevel)
        )
    }

    fun currentLocalDateKey(timeMs: Long = System.currentTimeMillis()): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timeMs }
        return String.format(
            Locale.US,
            "%04d-%02d-%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun formatDisplayDate(dateKey: String): String {
        val parts = dateKey.split('-')
        if (parts.size != 3) return dateKey
        val month = parts[1].toIntOrNull() ?: return dateKey
        val day = parts[2].toIntOrNull() ?: return dateKey
        val monthNames = arrayOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
        )
        val monthLabel = monthNames.getOrElse(month - 1) { return dateKey }
        return "$monthLabel $day"
    }

    fun previousDateKey(dateKey: String): String {
        val parts = dateKey.split('-')
        if (parts.size != 3) return dateKey
        val year = parts[0].toIntOrNull() ?: return dateKey
        val month = parts[1].toIntOrNull() ?: return dateKey
        val day = parts[2].toIntOrNull() ?: return dateKey
        val cal = Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day)
            add(Calendar.DAY_OF_MONTH, -1)
        }
        return String.format(
            Locale.US,
            "%04d-%02d-%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun computeDailyStreak(completedDateKeys: Set<String>, todayKey: String): Int {
        if (completedDateKeys.isEmpty() || todayKey.isBlank()) return 0
        var cursor = if (todayKey in completedDateKeys) todayKey else previousDateKey(todayKey)
        var streak = 0
        while (cursor in completedDateKeys) {
            streak++
            cursor = previousDateKey(cursor)
        }
        return streak
    }

    fun dailySeedForDate(dateKey: String): Long {
        var hash = -3750763034362895579L // FNV-1a 64-bit offset basis
        for (i in dateKey.indices) {
            hash = hash xor dateKey[i].code.toLong()
            hash *= 1099511628211L
        }
        return hash xor (hash ushr 33)
    }

    fun dailyDifficultyForDate(dateKey: String): Pair<DifficultyTier, Int> {
        val seed = dailySeedForDate(dateKey)
        val colorCount = 7 + ((seed ushr 8).toInt() and 0x7FFF) % 3 // 7, 8, or 9 colors
        val tier = if (colorCount <= 8) DifficultyTier.TRICKSTER else DifficultyTier.MASTER
        return tier to colorCount
    }

    fun generateDailyChallenge(dateKey: String = currentLocalDateKey()): DailyChallengeSpec {
        val dailySeed = dailySeedForDate(dateKey)
        val (tier, colorCount) = dailyDifficultyForDate(dateKey)
        val emptyCount = 2
        val themes = StudioThemeId.entries
        val defaultTheme = themes[((dailySeed ushr 16).toInt() and 0x7FFF) % themes.size]

        var attempt = 0
        while (attempt < 64) {
            val seed = dailySeed + attempt.toLong() * 1442695040888963407L + 97L
            val rng = Random(seed)
            val candidate = buildMixedBoard(colorCount, emptyCount, levelNumber = 10, rng = rng)
            if (!isBoardTrivial(candidate) && hasAnyValidMoves(candidate) && isSolvable(candidate)) {
                return DailyChallengeSpec(
                    dateKey = dateKey,
                    tier = tier,
                    colorCount = colorCount,
                    tubes = candidate,
                    defaultTheme = defaultTheme
                )
            }
            attempt++
        }

        val fallbackTubes = buildGuaranteedReverseShuffle(colorCount, emptyCount, Random(dailySeed xor 7919L))
        return DailyChallengeSpec(
            dateKey = dateKey,
            tier = tier,
            colorCount = colorCount,
            tubes = fallbackTubes,
            defaultTheme = defaultTheme
        )
    }

    private fun buildMixedBoard(
        colorCount: Int,
        emptyCount: Int,
        levelNumber: Int,
        rng: Random
    ): List<TubeModel> {
        val pool = MutableList(colorCount * TUBE_CAPACITY) { idx -> idx / TUBE_CAPACITY }
        // Fisher-Yates shuffle
        for (i in pool.lastIndex downTo 1) {
            val j = rng.nextInt(i + 1)
            val tmp = pool[i]
            pool[i] = pool[j]
            pool[j] = tmp
        }

        val tubes = MutableList(colorCount + emptyCount) { index ->
            if (index < colorCount) {
                val start = index * TUBE_CAPACITY
                TubeModel(
                    id = index,
                    segments = pool.subList(start, start + TUBE_CAPACITY).toList()
                )
            } else {
                TubeModel(id = index, segments = emptyList())
            }
        }

        // On early warmup levels, lightly encourage 2-blocks for smoother onboarding
        if (levelNumber <= 2) {
            return buildGuaranteedReverseShuffle(colorCount, emptyCount, rng, steps = 14 + levelNumber * 4)
        }
        return tubes
    }

    private fun buildGuaranteedReverseShuffle(
        colorCount: Int,
        emptyCount: Int,
        rng: Random,
        steps: Int = colorCount * 12
    ): List<TubeModel> {
        val stacks = MutableList(colorCount + emptyCount) { idx ->
            if (idx < colorCount) MutableList(TUBE_CAPACITY) { idx } else mutableListOf()
        }

        // Move 1 unit from distinct filled tubes into empty tubes to open mixing capacity
        repeat(emptyCount) { emptyOffset ->
            val srcIdx = rng.nextInt(colorCount)
            if (stacks[srcIdx].isNotEmpty()) {
                val color = stacks[srcIdx].removeAt(stacks[srcIdx].lastIndex)
                stacks[colorCount + emptyOffset].add(color)
            }
        }

        repeat(steps) {
            val candidates = mutableListOf<Pair<Int, Int>>()
            for (from in stacks.indices) {
                val src = stacks[from]
                if (src.isEmpty()) continue
                val color = src.last()
                // Reverse move from -> to is strictly invertible forward (to -> from)
                // when `from` either becomes empty or retains `color` on top.
                val invertible = src.size == 1 || src[src.lastIndex - 1] == color
                if (!invertible) continue

                for (to in stacks.indices) {
                    if (from != to && stacks[to].size < TUBE_CAPACITY) {
                        candidates.add(from to to)
                    }
                }
            }
            if (candidates.isEmpty()) return@repeat
            val (from, to) = candidates[rng.nextInt(candidates.size)]
            val color = stacks[from].removeAt(stacks[from].lastIndex)
            stacks[to].add(color)
        }

        return stacks.mapIndexed { idx, list ->
            TubeModel(id = idx, segments = list.toList())
        }
    }

    private fun isBoardTrivial(tubes: List<TubeModel>): Boolean {
        var nonEmptyCount = 0
        var firstTop = -1
        var distinctTopCount = 0
        for (tube in tubes) {
            if (tube.isEmpty) continue
            if (tube.isCompleted) return true
            nonEmptyCount++
            val top = tube.topColor ?: continue
            if (firstTop == -1) {
                firstTop = top
                distinctTopCount = 1
            } else if (top != firstTop) {
                distinctTopCount = 2
            }
        }
        return distinctTopCount < min(2, nonEmptyCount)
    }

    fun canPour(from: TubeModel, to: TubeModel): Boolean {
        if (from.id == to.id) return false
        if (from.isEmpty || to.isFull) return false
        val sourceColor = from.topColor ?: return false
        val targetColor = to.topColor
        return targetColor == null || targetColor == sourceColor
    }

    fun computePour(tubes: List<TubeModel>, fromIndex: Int, toIndex: Int): PourOutcome? {
        if (fromIndex !in tubes.indices || toIndex !in tubes.indices || fromIndex == toIndex) return null
        val from = tubes[fromIndex]
        val to = tubes[toIndex]
        if (!canPour(from, to)) return null

        val sourceColor = from.topColor ?: return null
        var contiguousCount = 0
        for (i in from.segments.lastIndex downTo 0) {
            if (from.segments[i] == sourceColor) contiguousCount++ else break
        }
        val availableSpace = TUBE_CAPACITY - to.segments.size
        val unitsToMove = min(contiguousCount, availableSpace)
        if (unitsToMove <= 0) return null

        val updatedFrom = from.copy(
            segments = from.segments.dropLast(unitsToMove)
        )
        val updatedTo = to.copy(
            segments = to.segments + List(unitsToMove) { sourceColor }
        )

        val updated = tubes.toMutableList().apply {
            this[fromIndex] = updatedFrom
            this[toIndex] = updatedTo
        }

        return PourOutcome(
            updatedTubes = updated,
            move = PourMove(
                fromIndex = fromIndex,
                toIndex = toIndex,
                colorId = sourceColor,
                unitsMoved = unitsToMove
            )
        )
    }

    fun applyReverseMove(tubes: List<TubeModel>, move: PourMove): List<TubeModel> {
        if (move.fromIndex !in tubes.indices || move.toIndex !in tubes.indices) return tubes
        val origin = tubes[move.fromIndex]
        val dest = tubes[move.toIndex]
        val count = min(move.unitsMoved, dest.segments.size)
        if (count <= 0) return tubes

        val movedBack = dest.segments.takeLast(count)
        return tubes.toMutableList().apply {
            this[move.toIndex] = dest.copy(segments = dest.segments.dropLast(count))
            this[move.fromIndex] = origin.copy(segments = origin.segments + movedBack)
        }
    }

    fun isLevelSolved(tubes: List<TubeModel>): Boolean =
        tubes.all { it.isEmpty || it.isCompleted }

    fun hasAnyValidMoves(tubes: List<TubeModel>): Boolean {
        for (i in tubes.indices) {
            val from = tubes[i]
            if (from.isEmpty || from.isCompleted) continue
            for (j in tubes.indices) {
                if (i != j && canPour(from, tubes[j])) {
                    // Avoid reporting trivial shuffling of a single-color tube into an empty tube as useful if nothing else exists
                    val isSingleColorToEmpty = tubes[j].isEmpty && from.segments.all { it == from.segments.first() }
                    if (!isSingleColorToEmpty) return true
                }
            }
        }
        return false
    }

    fun findTutorialMove(tubes: List<TubeModel>, selectedIndex: Int?): Pair<Int, Int>? {
        if (tubes.isEmpty()) return null
        if (selectedIndex != null && selectedIndex in tubes.indices) {
            val src = tubes[selectedIndex]
            var emptyTarget = -1
            for (j in tubes.indices) {
                if (j == selectedIndex || !canPour(src, tubes[j])) continue
                if (!tubes[j].isEmpty) return selectedIndex to j
                if (emptyTarget == -1) emptyTarget = j
            }
            if (emptyTarget != -1) return selectedIndex to emptyTarget
        }
        var fallbackPair: Pair<Int, Int>? = null
        for (i in tubes.indices) {
            val src = tubes[i]
            if (src.isEmpty || src.isCompleted) continue
            for (j in tubes.indices) {
                if (i == j || !canPour(src, tubes[j])) continue
                if (!tubes[j].isEmpty) return i to j
                if (fallbackPair == null && !src.segments.all { it == src.segments.first() }) {
                    fallbackPair = i to j
                }
            }
        }
        return fallbackPair
    }

    private class SearchNode(
        val state: Array<IntArray>,
        val score: Int
    )

    /**
     * Fast bounded Best-First Search solver using canonical tube hashing
     * and heuristic scoring to guarantee every generated board is solvable.
     */
    fun isSolvable(initialTubes: List<TubeModel>, maxIterations: Int = 6000): Boolean {
        val initialState = Array(initialTubes.size) { i -> initialTubes[i].segments.toIntArray() }
        if (isRawSolved(initialState)) return true

        val visited = HashSet<String>(maxIterations)
        val queue = java.util.PriorityQueue<SearchNode>(compareByDescending { it.score })
        val keyScratch = StringBuilder(64)
        val tubeScratch = Array(initialState.size) { "" }

        visited.add(canonicalKey(initialState, tubeScratch, keyScratch))
        queue.add(SearchNode(initialState, scoreState(initialState)))

        var iterations = 0
        while (queue.isNotEmpty() && iterations < maxIterations) {
            iterations++
            val node = queue.poll() ?: break
            val state = node.state
            if (isRawSolved(state)) return true

            val moves = generatePrunedMoves(state)
            for (nextState in moves) {
                if (isRawSolved(nextState)) return true
                val key = canonicalKey(nextState, tubeScratch, keyScratch)
                if (visited.add(key)) {
                    queue.add(SearchNode(nextState, scoreState(nextState)))
                }
            }
        }
        return false
    }

    private fun scoreState(state: Array<IntArray>): Int {
        var score = 0
        for (tube in state) {
            if (tube.isEmpty()) {
                score += 3
                continue
            }
            if (tube.size == TUBE_CAPACITY && isMonoTube(tube)) {
                score += 24
                continue
            }
            for (k in 1 until tube.size) {
                if (tube[k] == tube[k - 1]) score += 4
            }
        }
        return score
    }

    private fun generatePrunedMoves(state: Array<IntArray>): ArrayList<Array<IntArray>> {
        val results = ArrayList<Array<IntArray>>(12)
        var usedEmptyTarget = false

        for (i in state.indices) {
            val from = state[i]
            if (from.isEmpty()) continue
            val isMono = isMonoTube(from)
            if (isMono && from.size == TUBE_CAPACITY) continue

            val topColor = from[from.lastIndex]
            var runLen = 1
            for (k in from.lastIndex - 1 downTo 0) {
                if (from[k] == topColor) runLen++ else break
            }

            for (j in state.indices) {
                if (i == j) continue
                val to = state[j]
                if (to.size >= TUBE_CAPACITY) continue
                if (to.isEmpty()) {
                    // Never pour an already monochromatic stack into an empty tube
                    if (isMono || usedEmptyTarget) continue
                    usedEmptyTarget = true
                } else if (to[to.lastIndex] != topColor) {
                    continue
                }

                val moveCount = min(runLen, TUBE_CAPACITY - to.size)
                val next = state.copyOf()
                next[i] = from.copyOfRange(0, from.size - moveCount)
                val newTo = IntArray(to.size + moveCount)
                to.copyInto(newTo)
                for (m in to.size until newTo.size) newTo[m] = topColor
                next[j] = newTo
                results.add(next)
            }
        }
        return results
    }

    private fun isMonoTube(tube: IntArray): Boolean {
        val first = tube[0]
        for (k in 1 until tube.size) {
            if (tube[k] != first) return false
        }
        return true
    }

    private fun isRawSolved(state: Array<IntArray>): Boolean {
        for (tube in state) {
            if (tube.isNotEmpty() && (tube.size != TUBE_CAPACITY || !isMonoTube(tube))) {
                return false
            }
        }
        return true
    }

    private fun canonicalKey(
        state: Array<IntArray>,
        tubeScratch: Array<String>,
        sb: StringBuilder
    ): String {
        for (i in state.indices) {
            val tube = state[i]
            if (tube.isEmpty()) {
                tubeScratch[i] = ""
            } else {
                sb.setLength(0)
                for (k in tube.indices) {
                    if (k > 0) sb.append(',')
                    sb.append(tube[k])
                }
                tubeScratch[i] = sb.toString()
            }
        }
        tubeScratch.sort()
        sb.setLength(0)
        for (i in tubeScratch.indices) {
            if (i > 0) sb.append('|')
            sb.append(tubeScratch[i])
        }
        return sb.toString()
    }
}

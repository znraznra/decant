package com.example

import com.example.data.cloud.PlayGamesSaveManager
import com.example.domain.CloudSavePayload
import com.example.domain.DifficultyTier
import com.example.domain.PuzzleEngine
import com.example.domain.TUBE_CAPACITY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PuzzleEngineTest {

    @Test
    fun levelGeneration_isDeterministicAndSolvableAcrossTiers() {
        val sampleLevels = listOf(1, 2, 5, 10, 20, 40, 75, 120, 160)
        for (level in sampleLevels) {
            val spec1 = PuzzleEngine.generateLevel(level)
            val spec2 = PuzzleEngine.generateLevel(level)

            assertEquals("Level $level must be deterministic", spec1.tubes, spec2.tubes)
            assertTrue("Level $level must have valid moves", PuzzleEngine.hasAnyValidMoves(spec1.tubes))
            assertFalse("Level $level must not start already solved", PuzzleEngine.isLevelSolved(spec1.tubes))

            // Verify color conservation: every color appears exactly TUBE_CAPACITY times
            val counts = spec1.tubes.flatMap { it.segments }.groupingBy { it }.eachCount()
            assertEquals(spec1.colorCount, counts.size)
            counts.values.forEach { count ->
                assertEquals("Each color must have 4 units on level $level", TUBE_CAPACITY, count)
            }
        }
    }

    @Test
    fun difficultyScaling_increasesAcrossBands() {
        val (tier1, colors1) = PuzzleEngine.difficultyForLevel(1)
        val (tier10, colors10) = PuzzleEngine.difficultyForLevel(10)
        val (tier50, colors50) = PuzzleEngine.difficultyForLevel(50)
        val (tier140, colors140) = PuzzleEngine.difficultyForLevel(140)

        assertEquals(DifficultyTier.WARMUP, tier1)
        assertEquals(DifficultyTier.CLASSIC, tier10)
        assertEquals(DifficultyTier.MASTER, tier50)
        assertEquals(DifficultyTier.ABYSSAL, tier140)

        assertTrue(colors1 < colors10)
        assertTrue(colors10 < colors50)
        assertTrue(colors50 < colors140)
    }

    @Test
    fun pourAndUndo_roundTripsExactBoardState() {
        val spec = PuzzleEngine.generateLevel(3)
        val tubes = spec.tubes

        // Find any valid pour
        var movePair: Pair<Int, Int>? = null
        outer@ for (i in tubes.indices) {
            for (j in tubes.indices) {
                if (i != j && PuzzleEngine.canPour(tubes[i], tubes[j])) {
                    movePair = i to j
                    break@outer
                }
            }
        }
        assertNotNull(movePair)
        val (fromIdx, toIdx) = movePair!!

        val outcome = PuzzleEngine.computePour(tubes, fromIdx, toIdx)
        assertNotNull(outcome)
        val restored = PuzzleEngine.applyReverseMove(outcome!!.updatedTubes, outcome.move)
        assertEquals(tubes, restored)
    }

    @Test
    fun dailyChallenge_isDeterministicForSameDate_andSolvableAcrossDates() {
        val dates = listOf("2026-11-26", "2026-11-27", "2026-12-01", "2027-01-01")
        val firstRunTubes = mutableListOf<List<com.example.domain.TubeModel>>()

        for (date in dates) {
            val dailyA = PuzzleEngine.generateDailyChallenge(date)
            val dailyB = PuzzleEngine.generateDailyChallenge(date)

            assertEquals("Daily puzzle must match for all users on $date", dailyA.tubes, dailyB.tubes)
            assertEquals(dailyA.colorCount, dailyB.colorCount)
            assertEquals(dailyA.tier, dailyB.tier)
            assertEquals(dailyA.defaultTheme, dailyB.defaultTheme)
            assertTrue("Daily $date must be solvable", PuzzleEngine.isSolvable(dailyA.tubes))
            assertFalse("Daily $date must not start solved", PuzzleEngine.isLevelSolved(dailyA.tubes))

            val counts = dailyA.tubes.flatMap { it.segments }.groupingBy { it }.eachCount()
            assertEquals(dailyA.colorCount, counts.size)
            counts.values.forEach { count ->
                assertEquals(TUBE_CAPACITY, count)
            }
            firstRunTubes.add(dailyA.tubes)
        }

        // Consecutive days must generate distinct puzzles
        assertFalse(firstRunTubes[0] == firstRunTubes[1])
    }

    @Test
    fun dailyStreak_countsConsecutiveDaysCorrectly() {
        val completed = setOf("2026-11-24", "2026-11-25", "2026-11-26")
        assertEquals(3, PuzzleEngine.computeDailyStreak(completed, "2026-11-26"))
        assertEquals(3, PuzzleEngine.computeDailyStreak(completed, "2026-11-27"))
        assertEquals(0, PuzzleEngine.computeDailyStreak(completed, "2026-11-28"))

        val yearBoundary = setOf("2026-12-30", "2026-12-31", "2027-01-01")
        assertEquals(3, PuzzleEngine.computeDailyStreak(yearBoundary, "2027-01-01"))
    }

    @Test
    fun playGamesMerge_combinesLevelsAndDailyProgressSafely() {
        val local = CloudSavePayload(
            highestUnlockedLevel = 4,
            completedLevels = mapOf(1 to 8, 2 to 14),
            completedDailyMoves = mapOf("2026-11-25" to 22),
            updatedAtMs = 1_000L
        )
        val remote = CloudSavePayload(
            highestUnlockedLevel = 9,
            completedLevels = mapOf(2 to 11, 8 to 19),
            completedDailyMoves = mapOf("2026-11-25" to 18, "2026-11-26" to 24),
            updatedAtMs = 2_000L
        )

        val merged = PlayGamesSaveManager.mergePayloads(local, remote)

        assertEquals(9, merged.highestUnlockedLevel)
        assertEquals(mapOf(1 to 8, 2 to 11, 8 to 19), merged.completedLevels)
        assertEquals(mapOf("2026-11-25" to 18, "2026-11-26" to 24), merged.completedDailyMoves)
        assertEquals(2_000L, merged.updatedAtMs)
    }

    @Test
    fun tutorialMove_suggestsValidFirstPourOnLevel1() {
        val level1 = PuzzleEngine.generateLevel(1)
        val firstStep = PuzzleEngine.findTutorialMove(level1.tubes, selectedIndex = null)
        assertNotNull(firstStep)
        val (src, dst) = firstStep!!
        assertTrue(PuzzleEngine.canPour(level1.tubes[src], level1.tubes[dst]))

        val targetStep = PuzzleEngine.findTutorialMove(level1.tubes, selectedIndex = src)
        assertNotNull(targetStep)
        assertEquals(src, targetStep!!.first)
        assertTrue(PuzzleEngine.canPour(level1.tubes[targetStep.first], level1.tubes[targetStep.second]))
    }
}

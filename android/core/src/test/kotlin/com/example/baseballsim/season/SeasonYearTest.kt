package com.example.baseballsim.season

import com.example.baseballsim.core.Sm64
import com.example.baseballsim.core.seedFromText
import org.junit.Assert.*
import org.junit.Test

class SeasonYearTest {
    @Test fun firstThreeYearsUseConsecutiveSeedBlocksForZeroAndMaxSeeds() {
        for (root in listOf("0", "42", ULong.MAX_VALUE.toString())) {
            val sequential = Sm64(seedFromText(root))
            val unique = mutableSetOf<String>()
            for (year in 1L..3L) {
                val seed = SeasonYearSeeds.start(root, year)
                if (year == 1L) assertEquals(root, seed)
                for (game in PennantSchedule.allocate(seed, SeasonVersions.batterProfile())) {
                    assertEquals(sequential.nextRaw().toString(), game.seed)
                    assertTrue(unique.add(game.seed))
                }
            }
        }
        assertThrows(IllegalArgumentException::class.java) { SeasonYearSeeds.start("42", 0) }
        assertThrows(IllegalArgumentException::class.java) { SeasonYearSeeds.start("042", 1) }
        seedFromText(SeasonYearSeeds.start("42", Long.MAX_VALUE))
    }

    @Test fun secondYearPitchPlateAppearanceAndReloadAllReachTheSameFirstResult() {
        val seed = SeasonYearSeeds.start("42", 2)
        val starter = SeasonState.initialBatterProfile(seed)
        val direct = SeasonSession(starter)
        val wholeGame = direct.finishCurrent()
        val partial = SeasonSession(starter)
        repeat(6) { partial.advanceOnePitch() }
        val restored = SeasonSession(CheckpointCodec.decodeSeason(CheckpointCodec.encodeSeason(partial.state)))
        while (restored.state.results.isEmpty()) restored.advancePlateAppearance()
        assertEquals(wholeGame, restored.state)
        assertArrayEquals(CheckpointCodec.encodeSeason(wholeGame), CheckpointCodec.encodeSeason(restored.state))
    }
}

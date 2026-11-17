package com.example.baseballsim

import com.example.baseballsim.core.EventCounts
import com.example.baseballsim.data.StoreToken
import com.example.baseballsim.data.StoredSeason
import com.example.baseballsim.season.*
import org.junit.Assert.*
import org.junit.Test

class PennantNavigationTest {
    private fun ui(season: SeasonState = SeasonState.initial(), world: String = "world", generation: String = "generation", revision: Long = 0) =
        PennantUiState(loaded = true, display = pennantDisplay(StoredSeason(StoreToken(world, generation, revision), season, "operation")))

    @Test fun malformedSavedUiFallsBackToHomeAndThePublishedManagedClub() {
        val current = ui(SeasonState.initial(selectedClubId = "L1-T03"))
        for (values in listOf(emptyList(), listOf("world"),
            listOf("world", "generation", "UNKNOWN", "L2-T06", "L2-T06-P09"))) {
            assertNull(restorePennantNavigation(values))
            val fallback = (restorePennantNavigation(values) ?: PennantNavigation()).bind(current)
            assertEquals(PennantPage.HOME, fallback.page)
            assertEquals("L1-T03", fallback.browseClubId)
            assertNull(fallback.playerId)
        }
        assertEquals(PennantNavigation("world", "generation", PennantPage.PLAYER_DETAIL, "L2-T06", "L2-T06-P09"),
            restorePennantNavigation(listOf("world", "generation", "PLAYER_DETAIL", "L2-T06", "L2-T06-P09")))
    }

    @Test fun all108FixedPlayersJoinAllFivePublishedCountsAtZeroPartialAndFullSeason() {
        val session = SeasonSession()
        val states = listOf(session.state, session.finishCurrent(), session.finishSeason())
        assertEquals(0, states[0].results.size); assertTrue(states[1].results.isNotEmpty()); assertEquals(720, states[2].results.size)
        states.forEach { state ->
            val projected = PennantSchedule.clubs.flatMap { club ->
                pennantPlayers(state, club.id).also { rows ->
                    assertEquals(9, rows.size); assertEquals((1..9).toList(), rows.map { it.slot })
                    assertEquals(club.players.map { it.id }, rows.map { it.playerId })
                    assertTrue(rows.all { it.clubId == club.id })
                }
            }
            assertEquals(108, projected.size); assertEquals(108, projected.map { it.playerId }.toSet().size)
            projected.forEach { row ->
                val published = state.playerCounts.single { it.playerId == row.playerId }
                assertEquals(published, row)
                val contributions = state.results.flatMap { it.batters }.filter { it.playerId == row.playerId }
                assertEquals(EventCounts(contributions.sumOf { it.counts.completedPlateAppearances },
                    contributions.sumOf { it.counts.safeHitEvents }, contributions.sumOf { it.counts.walkEvents },
                    contributions.sumOf { it.counts.strikeoutEvents }, contributions.sumOf { it.counts.homeRunEvents }), row.counts)
            }
        }
        val active = SeasonSession(states[1]).advancePlateAppearance()
        assertNotNull(active.active)
        assertEquals(states[1].playerCounts, active.playerCounts)
        PennantSchedule.clubs.forEach { assertEquals(pennantPlayers(states[1], it.id), pennantPlayers(active, it.id)) }
    }
    @Test fun identityBindsOnlyAfterLoadingAndIgnoresOrdinaryRevisionUpdates() {
        val state = ui()
        val restored = PennantNavigation("world", "generation", PennantPage.PLAYER_DETAIL, "L2-T06", "L2-T06-P09")
        assertEquals(restored, restored.bind(PennantUiState()))
        val readFailed = restored.bind(PennantUiState(loaded = true, error = "read failed"))
        assertEquals(restored, readFailed)
        assertEquals(restored, readFailed.bind(state))
        assertEquals(restored, restored.bind(ui(revision = 99)))
        for (changed in listOf(ui(world = "same-seed-new-world"), ui(generation = "loaded-slot-generation"))) {
            val reset = restored.bind(changed)
            assertEquals(PennantPage.HOME, reset.page); assertNull(reset.playerId)
            assertEquals(changed.season!!.selectedClubId, reset.browseClubId)
            assertEquals(changed.display!!.stored.token.worldId, reset.worldId)
            assertEquals(changed.display!!.stored.token.generationId, reset.generationId)
        }
        val absent = restored.bind(PennantUiState(loaded = true))
        assertEquals(PennantPage.HOME, absent.page); assertNull(absent.playerId); assertNull(absent.worldId)
    }
    @Test fun invalidRestorationFallsBackWithoutChangingThePublishedSelection() {
        val state = ui(SeasonState.initial(selectedClubId = "L1-T03"))
        val valid = PennantNavigation("world", "generation", PennantPage.PLAYER_DETAIL, "L2-T06", "L2-T06-P09")
        listOf(valid.copy(browseClubId = "unknown"), valid.copy(playerId = "unknown"),
            valid.copy(playerId = "L1-T01-P01"), valid.copy(playerId = null)).forEach { candidate ->
            val reset = candidate.bind(state)
            assertEquals(PennantPage.HOME, reset.page); assertNull(reset.playerId); assertEquals("L1-T03", reset.browseClubId)
        }
        assertEquals("L1-T03", state.season!!.selectedClubId)
        val list = valid.back()
        assertEquals(PennantPage.PLAYERS, list.page); assertEquals(valid.browseClubId, list.browseClubId)
        assertEquals(PennantPage.HOME, list.back().page)
        assertEquals("L1-T03", valid.open(PennantPage.PLAYERS, state.season!!).browseClubId)
        assertEquals("L1-T03", valid.open(PennantPage.RECORDS, state.season!!).browseClubId)
    }
}

package com.example.baseballsim

import com.example.baseballsim.data.*
import com.example.baseballsim.season.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PennantBatterProfileTest {
    private fun stored(state: SeasonState) = StoredSeason(StoreToken("world", "generation", 0), state, "initial")

    @Test fun personTypeLabelsAreAbsentFromBothLegacyFormatsAndFollowThePersonAfterReordering() {
        for (old in listOf(SeasonState.initial(), SeasonState.initialLineup())) {
            assertFalse(old.profilesEnabled)
            assertNull(old.playerProfile("L1-T01-P01"))
        }
        val initial = SeasonState.initialBatterProfile()
        val types = listOf("standard", "contact", "slugger")
        val labels = listOf("標準", "ミート型", "長打型")
        for (number in 1..12) {
            val person = "L1-T01-P${number.toString().padStart(2, '0')}"
            assertEquals(types[(number - 1) % 3], initial.playerProfile(person))
            assertEquals(labels[(number - 1) % 3], batterProfileLabel(initial.playerProfile(person)!!))
        }
        val edited = initial.applyLineup("L1-T01", initial.lineup("L1-T01").reversed())
        assertEquals(initial.worldProfiles, edited.worldProfiles)
        assertEquals("standard", edited.playerProfile("L1-T01-P01"))
        assertEquals("9番", playerRoleLabel(edited, pennantPlayers(edited, "L1-T01").first()))
    }

    @Test fun theUiCreateCommandUsesV3WhileExplicitOldCoordinatorFactoriesRemainOld() = runTest {
        class Store : SeasonStore {
            var current: StoredSeason? = null
            var created = ""
            var count = 0
            fun adopt(kind: String, state: SeasonState): StoredSeason {
                created = kind; count++
                return StoredSeason(StoreToken("world$count", "generation$count", 0), state, "create$count").also { current = it }
            }
            override suspend fun read() = current
            override suspend fun hasSlot() = false
            override suspend fun create(masterSeed: String, selectedClubId: String, operationId: String, expected: StoreToken?, replacementConfirmed: Boolean) =
                adopt("v1", SeasonState.initial(masterSeed, selectedClubId)).copy(operationId = operationId).also { current = it }
            override suspend fun createLineup(masterSeed: String, selectedClubId: String, operationId: String, expected: StoreToken?, replacementConfirmed: Boolean) =
                adopt("v2", SeasonState.initialLineup(masterSeed, selectedClubId)).copy(operationId = operationId).also { current = it }
            override suspend fun createBatterProfile(masterSeed: String, selectedClubId: String, operationId: String, expected: StoreToken?, replacementConfirmed: Boolean) =
                adopt("v3", SeasonState.initialBatterProfile(masterSeed, selectedClubId)).copy(operationId = operationId).also { current = it }
            override suspend fun commit(expected: StoreToken, operationId: String, candidate: SeasonState): StoredSeason = error("Unexpected commit")
            override suspend fun saveSlot(expected: StoreToken, operationId: String): StoredSeason = error("Unexpected save")
            override suspend fun loadSlot(expected: StoreToken, operationId: String): StoredSeason = error("Unexpected load")
        }
        val store = Store()
        val coordinator = SeasonCoordinator(store)
        val actions = DurablePennantActions(coordinator)
        val created = actions.execute(PennantCommand.CREATE, "42", "L1-T01", false, { false }, {})
        assertEquals("v3", store.created); assertTrue(created.state.profilesEnabled)
        val v2 = coordinator.createLineup("42", "L1-T01", true)
        assertEquals("v2", store.created); assertTrue(v2.state.lineupEnabled); assertFalse(v2.state.profilesEnabled)
        val v1 = coordinator.create("42", "L1-T01", true)
        assertEquals("v1", store.created); assertFalse(v1.state.lineupEnabled); assertFalse(v1.state.profilesEnabled)
    }

    @Test fun recentDisplayReplaysTheFrozenProfileInputAfterTheNextLineupHasChanged() {
        val session = SeasonSession(SeasonState.initialBatterProfile())
        session.finishCurrent()
        val completed = session.state.results.single()
        val nextPlan = (listOf(12) + (2..9)).map { "L1-T01-P${it.toString().padStart(2, '0')}" }
        val edited = session.state.applyLineup("L1-T01", nextPlan)
        val display = pennantDisplay(stored(edited))
        assertEquals(SnapshotDto.fromCore(completed.replay().snapshot), SnapshotDto.fromCore(display.snapshot!!))
        assertEquals(completed.game.fixture, display.fixture)
        assertEquals("standard", completed.profileInput!!.profile("home", 1))
        assertEquals("slugger", edited.playerProfile("L1-T01-P12"))
        assertEquals(session.state.playerCounts, edited.playerCounts)
    }
}

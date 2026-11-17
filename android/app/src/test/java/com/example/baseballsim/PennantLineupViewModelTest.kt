package com.example.baseballsim

import com.example.baseballsim.data.*
import com.example.baseballsim.season.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PennantLineupViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private class Actions : PennantActions {
        var stored = StoredSeason(StoreToken("world", "generation", 0), SeasonState.initialLineup(), "initial")
        var calls = 0
        var reads = 0
        var failApply = false
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun read(): StoredSeason { reads++; return stored }
        override suspend fun hasSlot() = false
        override suspend fun execute(command: PennantCommand, seed: String, club: String, replacement: Boolean,
            stop: () -> Boolean, progress: suspend (StoredSeason) -> Unit): StoredSeason = error("Unexpected normal command")
        override suspend fun applyLineup(expected: StoreToken, clubId: String, players: List<String>): StoredSeason {
            calls++; gate?.await()
            if (failApply) throw StoreConflict("Injected stale apply")
            check(stored.token == expected)
            stored = stored.copy(token = expected.copy(revision = expected.revision + 1), state = stored.state.applyLineup(clubId, players), operationId = "edit$calls")
            return stored
        }
    }
    private val order = (listOf(10) + (2..9)).map { "L1-T01-P${it.toString().padStart(2, '0')}" }

    @Test fun busyAndStaleOrMalformedDraftsNeverDispatchAndInputListIsCopied() = runTest(main.dispatcher) {
        val actions = Actions(); val vm = PennantViewModel(actions, main.dispatcher)
        advanceUntilIdle()
        val old = actions.stored.token
        val mutable = order.toMutableList()
        actions.gate = CompletableDeferred()
        vm.applyLineup(old, "L1-T01", mutable)
        mutable[0] = "L1-T01-P12"
        repeat(5) { vm.applyLineup(old, "L1-T01", order) }
        runCurrent(); assertEquals(1, actions.calls)
        actions.gate!!.complete(Unit); advanceUntilIdle()
        assertEquals("L1-T01-P10", vm.state.value.season!!.lineup("L1-T01").first())
        assertEquals("打順を保存しました", vm.state.value.notice)
        vm.applyLineup(old, "L1-T01", order)
        val current = actions.stored.token
        vm.applyLineup(current, "L1-T01", List(9) { "L1-T01-P01" })
        vm.applyLineup(current, "L2-T06", order)
        advanceUntilIdle(); assertEquals(1, actions.calls)
        actions.stored = actions.stored.copy(state = actions.stored.state.beginNext())
        vm.reload(); advanceUntilIdle(); assertFalse(vm.state.value.canEditLineup)
        vm.applyLineup(current, "L1-T01", order); advanceUntilIdle(); assertEquals(1, actions.calls)
    }

    @Test fun legacyWorldIsReadOnlyAndRestoredNewPlayerMembershipIsVersionScoped() = runTest(main.dispatcher) {
        val actions = Actions(); val vm = PennantViewModel(actions, main.dispatcher); advanceUntilIdle()
        val nav = PennantNavigation("world", "generation", PennantPage.PLAYER_DETAIL, "L1-T01", "L1-T01-P12")
        assertEquals(nav, nav.bind(vm.state.value)); assertEquals(12, pennantPlayers(actions.stored.state, "L1-T01").size)
        actions.stored = actions.stored.copy(state = SeasonState.initial())
        vm.reload(); advanceUntilIdle()
        assertFalse(vm.state.value.canEditLineup); assertEquals(PennantPage.HOME, nav.bind(vm.state.value).page)
        vm.applyLineup(actions.stored.token, "L1-T01", order); advanceUntilIdle(); assertEquals(0, actions.calls)
        assertNull(restoreLineupDraft(listOf("bad")))
    }
    @Test fun failedApplyDoesNotCallTheNormalReadRecoveryUntilExplicitReload() = runTest(main.dispatcher) {
        val actions = Actions(); val vm = PennantViewModel(actions, main.dispatcher); advanceUntilIdle()
        val before = vm.state.value.display
        val reads = actions.reads
        actions.failApply = true
        vm.applyLineup(actions.stored.token, "L1-T01", order); advanceUntilIdle()
        assertNotNull(vm.state.value.error); assertSame(before, vm.state.value.display)
        assertEquals(reads, actions.reads); assertEquals(1, actions.calls)
        vm.reload(); advanceUntilIdle()
        assertEquals(reads + 1, actions.reads); assertNull(vm.state.value.error)
    }

}

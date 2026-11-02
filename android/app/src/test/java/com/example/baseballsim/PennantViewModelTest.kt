package com.example.baseballsim

import androidx.lifecycle.ViewModelStore
import com.example.baseballsim.data.*
import com.example.baseballsim.season.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PennantViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private class FakeActions(initial: StoredSeason? = null) : PennantActions {
        var stored = initial
        var slot: StoredSeason? = null
        var calls = 0
        var readError = false
        var beforeCommitError = false
        var afterCommitError = false
        var gate: CompletableDeferred<Unit>? = null
        var callback: (suspend (StoredSeason) -> Unit)? = null
        var stoppedAtEntry = false
        override suspend fun read(): StoredSeason? { check(!readError); return stored }
        override suspend fun hasSlot() = slot != null
        override suspend fun execute(command: PennantCommand, seed: String, club: String, replacement: Boolean,
            stop: () -> Boolean, progress: suspend (StoredSeason) -> Unit): StoredSeason {
            calls++; callback = progress; gate?.await(); stoppedAtEntry = stop()
            if (beforeCommitError) error("Write failed before commit")
            val old = stored
            val state = when (command) {
                PennantCommand.CREATE -> SeasonState.initial(seed, club)
                PennantCommand.SELECT -> checkNotNull(old).state.selectClub(club)
                PennantCommand.LOAD -> checkNotNull(slot).state
                PennantCommand.SAVE -> checkNotNull(old).state.also { slot = old }
                else -> {
                    val current = checkNotNull(old).state
                    val session = SeasonSession(current)
                    when (command) {
                        PennantCommand.PITCH -> session.advanceOnePitch()
                        PennantCommand.PLATE_APPEARANCE -> session.advancePlateAppearance()
                        PennantCommand.GAME -> session.finishCurrent()
                        PennantCommand.OPEN_SELECTED -> session.openNextSelected()
                        PennantCommand.DAY -> session.advanceDay(stop)
                        PennantCommand.SEASON -> if (stop() && current.active == null) current else session.advanceDay(stop)
                        else -> error("Unexpected command")
                    }
                }
            }
            stored = StoredSeason(StoreToken("world", if (command == PennantCommand.LOAD) "loaded" else "generation",
                (old?.token?.revision ?: -1) + 1), state, "operation$calls")
            if (afterCommitError) error("Lost response after commit")
            return stored!!
        }
    }
    private fun initial(active: Boolean = false): StoredSeason = StoredSeason(StoreToken("world", "generation", 0),
        SeasonState.initial().let { if (active) it.beginNext() else it }, "initial")

    @Test fun synchronousBusyClaimDropsNewProgressLoadAndSeedEdits() = runTest(main.dispatcher) {
        val fake = FakeActions(initial(active = true))
        val vm = PennantViewModel(fake, main.dispatcher)
        assertTrue(vm.state.value.busy)
        vm.execute(PennantCommand.CREATE, true); advanceUntilIdle(); assertEquals(0, fake.calls)
        val prior = vm.state.value.display
        fake.gate = CompletableDeferred()
        vm.execute(PennantCommand.PITCH)
        repeat(8) { vm.execute(PennantCommand.PITCH); vm.execute(PennantCommand.LOAD); vm.execute(PennantCommand.CREATE, true); vm.changeSeed("99") }
        runCurrent()
        assertEquals(1, fake.calls); assertSame(prior, vm.state.value.display); assertEquals("42", vm.state.value.seedInput)
        fake.gate!!.complete(Unit); advanceUntilIdle()
        assertEquals(1L, vm.state.value.season!!.active!!.snapshot.pitchesAccepted)
        assertFalse(vm.state.value.busy)
    }
    @Test fun invalidSeedOrMissingReplacementConfirmationNeverWrites() = runTest(main.dispatcher) {
        val fake = FakeActions(initial()); val vm = PennantViewModel(fake, main.dispatcher); advanceUntilIdle()
        val before = vm.state.value.display
        for (seed in listOf("", "00", "４２", "-1", "18446744073709551616", "42 ")) {
            vm.changeSeed(seed); vm.execute(PennantCommand.CREATE, true); advanceUntilIdle()
            assertNotNull(vm.state.value.seedError); assertSame(before, vm.state.value.display)
        }
        vm.changeSeed("0"); vm.execute(PennantCommand.CREATE); advanceUntilIdle(); assertEquals(0, fake.calls)
        vm.execute(PennantCommand.CREATE, true); advanceUntilIdle()
        assertEquals("0", vm.state.value.season!!.masterSeed); assertEquals(1, fake.calls)
    }
    @Test fun failedInitialReadBlocksImplicitNewDataAndCanBeRetried() = runTest(main.dispatcher) {
        val fake = FakeActions(initial()).apply { readError = true }
        val vm = PennantViewModel(fake, main.dispatcher); advanceUntilIdle()
        assertNotNull(vm.state.value.error); assertNull(vm.state.value.display)
        vm.execute(PennantCommand.CREATE, true); advanceUntilIdle(); assertEquals(0, fake.calls)
        fake.readError = false; vm.reload(); advanceUntilIdle()
        assertNull(vm.state.value.error); assertNotNull(vm.state.value.display)
    }
    @Test fun failedWriteReReadsTheDurableStateAndDoesNotReuseAnAdvancedCandidate() = runTest(main.dispatcher) {
        val fake = FakeActions(initial(active = true)); val vm = PennantViewModel(fake, main.dispatcher); advanceUntilIdle()
        fake.beforeCommitError = true; vm.execute(PennantCommand.PITCH); advanceUntilIdle()
        assertEquals(0L, vm.state.value.season!!.active!!.snapshot.pitchesAccepted); assertNotNull(vm.state.value.error)
        vm.execute(PennantCommand.PITCH); advanceUntilIdle(); assertEquals(1, fake.calls)
        fake.beforeCommitError = false; vm.reload(); advanceUntilIdle(); vm.execute(PennantCommand.PITCH); advanceUntilIdle()
        assertEquals(1L, vm.state.value.season!!.active!!.snapshot.pitchesAccepted)
        fake.afterCommitError = true; vm.execute(PennantCommand.PITCH); advanceUntilIdle()
        assertEquals(2L, vm.state.value.season!!.active!!.snapshot.pitchesAccepted); assertNotNull(vm.state.value.error)
        fake.afterCommitError = false; vm.reload(); advanceUntilIdle(); vm.execute(PennantCommand.PITCH); advanceUntilIdle()
        assertEquals(3L, vm.state.value.season!!.active!!.snapshot.pitchesAccepted)
    }
    @Test fun staleProgressAfterLoadAndAfterClearCannotOverwriteTheCurrentGeneration() = runTest(main.dispatcher) {
        val fake = FakeActions(initial()).apply { slot = initial() }
        val vm = PennantViewModel(fake, main.dispatcher); val owner = ViewModelStore(); owner.put("pennant", vm)
        advanceUntilIdle()
        vm.execute(PennantCommand.DAY); advanceUntilIdle(); val stale = checkNotNull(fake.callback)
        vm.execute(PennantCommand.LOAD); advanceUntilIdle(); val loaded = vm.state.value
        stale(initial(active = true)); assertSame(loaded, vm.state.value)
        owner.clear(); val afterClear = vm.state.value
        stale(initial(active = true)); assertSame(afterClear, vm.state.value)
    }
    @Test fun stopRequestedBeforeTheDispatcherStartsDoesNotBeginAnotherGame() = runTest(main.dispatcher) {
        val fake = FakeActions(initial()); val vm = PennantViewModel(fake, main.dispatcher); advanceUntilIdle()
        val before = vm.state.value.season
        vm.execute(PennantCommand.SEASON); vm.requestStop(); advanceUntilIdle()
        assertTrue(fake.stoppedAtEntry); assertEquals(before, vm.state.value.season); assertNull(vm.state.value.season!!.active)
        assertFalse(vm.state.value.busy)
    }
    @Test fun manualSaveAndLoadAreDisabledWhileAnActiveMatchExists() = runTest(main.dispatcher) {
        val fake = FakeActions(initial(active = true)).apply { slot = initial() }
        val vm = PennantViewModel(fake, main.dispatcher); advanceUntilIdle()
        assertFalse(vm.state.value.canSave); assertFalse(vm.state.value.canLoad)
        vm.execute(PennantCommand.SAVE); vm.execute(PennantCommand.LOAD); advanceUntilIdle(); assertEquals(0, fake.calls)
        vm.execute(PennantCommand.GAME); advanceUntilIdle()
        assertTrue(vm.state.value.canSave); assertTrue(vm.state.value.canLoad)
        vm.execute(PennantCommand.SAVE); advanceUntilIdle(); assertEquals("手動セーブしました", vm.state.value.notice)
    }
    @Test fun adoptingSavedSeedClearsItsOldValidationErrorButOrdinaryProgressDoesNot() = runTest(main.dispatcher) {
        val fake = FakeActions(initial()).apply { slot = initial() }
        val vm = PennantViewModel(fake, main.dispatcher); advanceUntilIdle()
        vm.changeSeed("invalid"); vm.execute(PennantCommand.CREATE, true); advanceUntilIdle()
        assertNotNull(vm.state.value.seedError)
        vm.execute(PennantCommand.DAY); advanceUntilIdle()
        assertEquals("invalid", vm.state.value.seedInput); assertNotNull(vm.state.value.seedError)
        vm.execute(PennantCommand.LOAD); advanceUntilIdle()
        assertEquals("42", vm.state.value.seedInput); assertNull(vm.state.value.seedError)
        vm.changeSeed("invalid"); vm.execute(PennantCommand.CREATE, true); advanceUntilIdle()
        assertNotNull(vm.state.value.seedError)
        vm.reload(); advanceUntilIdle()
        assertEquals("42", vm.state.value.seedInput); assertNull(vm.state.value.seedError)
    }
}

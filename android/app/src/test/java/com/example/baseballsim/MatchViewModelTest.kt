package com.example.baseballsim

import androidx.lifecycle.ViewModelStore
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = StandardTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

@OptIn(ExperimentalCoroutinesApi::class)
class MatchViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test
    fun busyClaimsImmediatelyAndDropsEveryFurtherOperation() = runTest(main.dispatcher) {
        val factory = FakeFactory()
        val vm = MatchViewModel(factory, main.dispatcher)
        assertTrue(vm.state.value.isBusy)
        vm.newGame()
        vm.advance(AdvanceMode.GAME)
        advanceUntilIdle()
        assertEquals(1, factory.created.size)
        assertEquals(0, factory.created.single().advances)

        vm.changeSeedInput("77")
        vm.advance(AdvanceMode.PLATE_APPEARANCE)
        assertTrue(vm.state.value.isBusy)
        repeat(8) {
            vm.advance(AdvanceMode.PITCH)
            vm.advance(AdvanceMode.GAME)
            vm.newGame()
            vm.changeSeedInput("99")
        }
        advanceUntilIdle()
        assertEquals(3, factory.created.single().advances)
        assertEquals(1, factory.created.size)
        assertEquals("77", vm.state.value.seedInput)
        assertFalse(vm.state.value.isBusy)
    }

    @Test
    fun invalidSeedChangesNeitherGameNorRandomDraws() = runTest(main.dispatcher) {
        val factory = FakeFactory()
        val vm = MatchViewModel(factory, main.dispatcher)
        advanceUntilIdle()
        vm.advance(AdvanceMode.PITCH)
        advanceUntilIdle()
        val before = vm.state.value.game
        val owned = factory.created.single()
        listOf("", "-1", "+1", " 1", "1 ", "1.0", "18446744073709551616", "４２", "٤٢", "1e2", "\n42", "00042", "00").forEach { input ->
            vm.changeSeedInput(input)
            vm.newGame()
            advanceUntilIdle()
            assertNotNull("Invalid seed accepted: $input", vm.state.value.seedError)
            assertSame(before, vm.state.value.game)
            assertEquals(1, factory.created.size)
            assertEquals(1, owned.draws)
        }
        listOf("0", "18446744073709551615", "42").forEach { input ->
            vm.changeSeedInput(input)
            vm.newGame()
            advanceUntilIdle()
            assertNull(vm.state.value.seedError)
            assertEquals(input.toULong().toString(), vm.state.value.game?.seed)
        }
    }

    @Test
    fun allModesAndMixedModesUseExactlyTheSamePitchSequence() = runTest(main.dispatcher) {
        fun play(modes: List<AdvanceMode>): GamePresentation {
            val vm = MatchViewModel(FakeFactory(terminalAt = 15), main.dispatcher)
            testScheduler.advanceUntilIdle()
            modes.forEach { vm.advance(it); testScheduler.advanceUntilIdle() }
            return requireNotNull(vm.state.value.game)
        }
        val byPitch = play(List(15) { AdvanceMode.PITCH })
        val byPa = play(List(5) { AdvanceMode.PLATE_APPEARANCE })
        val toEnd = play(listOf(AdvanceMode.GAME))
        val mixed = play(listOf(AdvanceMode.PITCH, AdvanceMode.PLATE_APPEARANCE, AdvanceMode.PITCH, AdvanceMode.GAME))
        assertEquals(byPitch, byPa)
        assertEquals(byPitch, toEnd)
        assertEquals(byPitch, mixed)
    }

    @Test
    fun newGameReplacesTheOwnedGameWithoutReusingItsSequence() = runTest(main.dispatcher) {
        val factory = FakeFactory()
        val vm = MatchViewModel(factory, main.dispatcher)
        advanceUntilIdle()
        vm.advance(AdvanceMode.PITCH)
        advanceUntilIdle()
        val oldGame = factory.created.single()
        vm.changeSeedInput("99")
        vm.newGame()
        advanceUntilIdle()
        assertEquals("99", vm.state.value.game?.seed)
        assertEquals(0L, vm.state.value.game?.pitchesAccepted)
        assertEquals(emptyList<String>(), vm.state.value.game?.recent)
        vm.advance(AdvanceMode.PITCH)
        advanceUntilIdle()
        assertEquals(1, oldGame.draws)
        assertEquals(1, factory.created.last().draws)
        assertTrue(vm.state.value.game!!.recent.all { it.startsWith("99:") })
    }

    @Test
    fun terminalGameRejectsAllProgressionAndHidesCurrentState() = runTest(main.dispatcher) {
        for (terminalStatus in listOf(GameStatus.COMPLETED, GameStatus.ABORTED)) {
            val factory = FakeFactory(terminalAt = 1, terminalStatus = terminalStatus)
            val vm = MatchViewModel(factory, main.dispatcher)
            advanceUntilIdle()
            vm.advance(AdvanceMode.GAME)
            advanceUntilIdle()
            val terminal = vm.state.value.game
            assertEquals(terminalStatus, terminal?.status)
            assertNull(terminal?.current)
            AdvanceMode.entries.forEach { vm.advance(it) }
            advanceUntilIdle()
            assertSame(terminal, vm.state.value.game)
            assertEquals(1, factory.created.single().draws)
            assertFalse(vm.state.value.canAdvance)
        }
    }

    @Test
    fun observingAndMutableEngineListsCannotAdvanceOrMutatePublishedState() = runTest(main.dispatcher) {
        val factory = FakeFactory()
        val vm = MatchViewModel(factory, main.dispatcher)
        advanceUntilIdle()
        val initial = vm.state.value.game
        repeat(100) { assertSame(initial, vm.state.value.game) }
        val owned = factory.created.single()
        assertEquals(1, owned.snapshotReads)
        assertEquals(0, owned.draws)
        owned.logs.add("not published")
        owned.bases[0] = true
        owned.lineAway[0] = "9"
        assertEquals(emptyList<String>(), initial?.recent)
        assertEquals(listOf(false, false, false), initial?.current?.bases)
        assertEquals("0", initial?.lineAway?.first())
        assertThrows(UnsupportedOperationException::class.java) {
            (initial!!.lineAway as MutableList<String?>)[0] = "6"
        }
    }

    @Test
    fun failedOperationReleasesBusyAndRequiresANewGameBeforeMoreProgress() = runTest(main.dispatcher) {
        var shouldFail = true
        val vm = MatchViewModel(GamePortFactory { seed ->
            object : GamePort {
                private val delegate = FakePort(seed)
                override fun snapshot() = delegate.snapshot()
                override fun advanceOnePitch(): GamePresentation {
                    if (shouldFail) error("test failure")
                    return delegate.advanceOnePitch()
                }
            }
        }, main.dispatcher)
        advanceUntilIdle()
        vm.advance(AdvanceMode.PITCH)
        advanceUntilIdle()
        assertFalse(vm.state.value.isBusy)
        assertNotNull(vm.state.value.operationError)
        assertFalse(vm.state.value.canAdvance)
        shouldFail = false
        vm.newGame()
        advanceUntilIdle()
        assertNull(vm.state.value.operationError)
        assertTrue(vm.state.value.canAdvance)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MatchViewModelThreadingTest {
    @Test
    fun creationReadsAndProgressionRunOutsideTheUiDispatcher() = runBlocking {
        val ui = Executors.newSingleThreadExecutor { Thread(it, "test-ui") }.asCoroutineDispatcher()
        val worker = Executors.newSingleThreadExecutor { Thread(it, "test-computation") }.asCoroutineDispatcher()
        val calls = Collections.synchronizedList(mutableListOf<String>())
        val store = ViewModelStore()
        Dispatchers.setMain(ui)
        try {
            val vm = withContext(ui) {
                MatchViewModel(GamePortFactory { seed ->
                    calls.add(Thread.currentThread().name)
                    object : GamePort {
                        private val delegate = FakePort(seed)
                        override fun snapshot(): GamePresentation {
                            calls.add(Thread.currentThread().name)
                            return delegate.snapshot()
                        }
                        override fun advanceOnePitch(): GamePresentation {
                            calls.add(Thread.currentThread().name)
                            return delegate.advanceOnePitch()
                        }
                    }
                }, worker).also { store.put("match", it) }
            }
            withTimeout(5_000) { vm.state.first { !it.isBusy } }
            withContext(ui) { vm.advance(AdvanceMode.GAME) }
            withTimeout(5_000) { vm.state.first { !it.isBusy } }
            assertEquals(11, calls.size)
            assertTrue(calls.all { it == "test-computation" })
        } finally {
            withContext(ui) { store.clear() }
            Dispatchers.resetMain()
            worker.close()
            ui.close()
        }
    }

    @Test
    fun aClearedGenerationCannotPublishALateNonCooperativeResult() = runBlocking {
        val ui = Executors.newSingleThreadExecutor { Thread(it, "test-ui") }.asCoroutineDispatcher()
        val worker = Executors.newSingleThreadExecutor { Thread(it, "test-computation") }.asCoroutineDispatcher()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val returned = CountDownLatch(1)
        val store = ViewModelStore()
        Dispatchers.setMain(ui)
        try {
            val vm = withContext(ui) {
                MatchViewModel(GamePortFactory { seed ->
                    object : GamePort {
                        private val delegate = FakePort(seed)
                        override fun snapshot() = delegate.snapshot()
                        override fun advanceOnePitch(): GamePresentation {
                            entered.countDown()
                            check(release.await(5, TimeUnit.SECONDS))
                            return delegate.advanceOnePitch().also { returned.countDown() }
                        }
                    }
                }, worker).also { store.put("match", it) }
            }
            withTimeout(5_000) { vm.state.first { !it.isBusy } }
            withContext(ui) { vm.advance(AdvanceMode.PITCH) }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val beforeClear = vm.state.value
            withContext(ui) { store.clear() }
            release.countDown()
            assertTrue(returned.await(5, TimeUnit.SECONDS))
            // Queue behind the completed worker operation, then behind any UI publication.
            withContext(worker) { Unit }
            withContext(ui) { assertEquals(beforeClear, vm.state.value) }
        } finally {
            release.countDown()
            withContext(ui) { store.clear() }
            Dispatchers.resetMain()
            worker.close()
            ui.close()
        }
    }
}

private class FakeFactory(
    private val terminalAt: Int = 9,
    private val terminalStatus: GameStatus = GameStatus.COMPLETED,
) : GamePortFactory {
    val created = mutableListOf<FakePort>()
    override fun create(seed: String) = FakePort(seed, terminalAt, terminalStatus).also { created.add(it) }
}

private class FakePort(
    private val seed: String,
    private val terminalAt: Int = 9,
    private val terminalStatus: GameStatus = GameStatus.COMPLETED,
) : GamePort {
    var advances = 0
    var draws = 0
    var snapshotReads = 0
    val logs = mutableListOf<String>()
    val bases = mutableListOf(false, false, false)
    val lineAway = MutableList<String?>(9) { if (it == 0) "0" else null }

    override fun snapshot(): GamePresentation {
        snapshotReads++
        return presentation()
    }

    override fun advanceOnePitch(): GamePresentation {
        check(advances < terminalAt)
        advances++
        draws++
        logs.add("$seed:$advances")
        return presentation()
    }

    private fun presentation() = GamePresentation(
        seed = seed,
        status = if (advances < terminalAt) GameStatus.IN_PROGRESS else terminalStatus,
        scoreAway = "0",
        scoreHome = "0",
        lineAway = lineAway,
        lineHome = List(9) { null },
        pitchesAccepted = advances.toLong(),
        completedPa = (advances / 3).toLong(),
        // Deliberately leave this populated at terminal; the app must hide it.
        current = CurrentPresentation(1, HalfSide.TOP, "ビジター", "打者1", 1, advances % 3, 0, 0, bases),
        recent = logs,
    )
}

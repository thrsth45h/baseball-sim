package com.example.baseballsim.data

import com.example.baseballsim.season.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class SeasonCoordinatorTest {
    private class FakeStore : SeasonStore {
        var saved: StoredSeason? = null
        var writes = 0
        var failBeforeWrite = 0
        var failAfterWrite = 0
        var cancelAfterWrite = 0
        var paused: CompletableDeferred<Unit>? = null
        val commits = mutableListOf<SeasonState>()
        override suspend fun read() = saved
        override suspend fun create(masterSeed: String, selectedClubId: String, operationId: String, expected: StoreToken?, replacementConfirmed: Boolean): StoredSeason {
            val state = SeasonState.initial(masterSeed, selectedClubId)
            return StoredSeason(StoreToken("world", "generation", 0), state, operationId).also { saved = it }
        }
        override suspend fun commit(expected: StoreToken, operationId: String, candidate: SeasonState): StoredSeason {
            paused?.await()
            val old = requireNotNull(saved)
            if (old.token != expected) throw StoreConflict("Stale")
            writes++
            if (writes == failBeforeWrite) error("Injected failure before commit")
            if (old.state.active == null && candidate.results.size > old.state.results.size) error("Missing start marker")
            val result = StoredSeason(old.token.copy(revision = old.token.revision + 1), candidate, operationId)
            saved = result; commits += candidate
            if (writes == cancelAfterWrite) throw CancellationException("Injected cancellation after commit")
            if (writes == failAfterWrite) error("Injected lost response")
            return result
        }
        override suspend fun saveSlot(expected: StoreToken, operationId: String) = error("Not used")
        override suspend fun loadSlot(expected: StoreToken, operationId: String) = error("Not used")
        override suspend fun hasSlot() = false
    }
    private fun coordinator(store: FakeStore): SeasonCoordinator {
        val ids = AtomicInteger()
        return SeasonCoordinator(store, operationId = { "operation-${ids.incrementAndGet()}" })
    }
    @Test fun markerCommitsBeforeCalculationAndFailureDiscardsItsCandidate() = runBlocking {
        val store = FakeStore(); val coordinator = coordinator(store)
        coordinator.create()
        store.failBeforeWrite = 2
        assertThrows(IllegalStateException::class.java) { runBlocking { coordinator.advance(SeasonAdvance.PITCH) } }
        val marker = store.saved!!
        assertEquals(0, marker.state.results.size)
        assertEquals(0L, marker.state.active!!.snapshot.pitchesAccepted)
        assertEquals(0L, marker.state.active!!.source.rawCalls)
        store.failBeforeWrite = 0
        val retried = coordinator.advance(SeasonAdvance.PITCH)
        val direct = SeasonSession(marker.state).advanceOnePitch()
        assertEquals(direct, retried.state)
        assertEquals(1L, retried.state.active!!.snapshot.pitchesAccepted)
    }
    @Test fun lostResponseIsResolvedFromDurableStateWithoutAnotherPitch() = runBlocking {
        val store = FakeStore(); val coordinator = coordinator(store)
        coordinator.create(); store.failAfterWrite = 2
        val result = coordinator.advance(SeasonAdvance.PITCH)
        assertEquals(store.saved, result)
        assertEquals(1L, result.state.active!!.snapshot.pitchesAccepted)
        assertEquals(2, store.writes)
    }
    @Test fun stoppedAtBoundaryDoesNotBeginAndInProgressStopsAfterOnlyCurrentGame() = runBlocking {
        val store = FakeStore(); val coordinator = coordinator(store)
        val initial = coordinator.create()
        assertEquals(initial, coordinator.finishSeason(stopRequested = { true }))
        assertEquals(0, store.writes)
        val one = coordinator.advance(SeasonAdvance.PITCH)
        val stopped = coordinator.finishSeason(stopRequested = { true })
        assertEquals(1, stopped.state.results.size)
        assertNull(stopped.state.active)
        assertEquals(one.state.active!!.game, stopped.state.results.single().game)
    }
    @Test fun concurrentCommandsAreRejectedRatherThanQueued() = runBlocking {
        val store = FakeStore(); val coordinator = coordinator(store)
        coordinator.create()
        val pause = CompletableDeferred<Unit>(); store.paused = pause
        val running = async(start = CoroutineStart.UNDISPATCHED) { coordinator.advance(SeasonAdvance.PITCH) }
        assertThrows(SeasonBusy::class.java) { runBlocking { coordinator.advance(SeasonAdvance.PITCH) } }
        pause.complete(Unit); running.await()
        assertEquals(1L, store.saved!!.state.active!!.snapshot.pitchesAccepted)
    }
    @Test fun cancellationAfterCommitDoesNotRollBackOrKeepAMutableCandidate() = runBlocking {
        val store = FakeStore(); val coordinator = coordinator(store)
        coordinator.create(); store.cancelAfterWrite = 2
        assertThrows(CancellationException::class.java) { runBlocking { coordinator.advance(SeasonAdvance.PITCH) } }
        val durable = coordinator.read()!!
        assertEquals(1L, durable.state.active!!.snapshot.pitchesAccepted)
        store.cancelAfterWrite = 0
        val next = coordinator.advance(SeasonAdvance.PITCH)
        assertEquals(2L, next.state.active!!.snapshot.pitchesAccepted)
    }
    @Test fun anOldHeadWithTheSameOperationIdCannotResolveAnUncommittedFailure() = runBlocking {
        val store = FakeStore()
        val coordinator = SeasonCoordinator(store, operationId = { "same" })
        val initial = coordinator.create()
        store.failBeforeWrite = 1
        assertThrows(IllegalStateException::class.java) { runBlocking { coordinator.selectClub("L2-T03") } }
        assertEquals(initial, store.saved)
    }
}

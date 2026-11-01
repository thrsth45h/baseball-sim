package com.example.baseballsim.data

import com.example.baseballsim.core.Status
import com.example.baseballsim.season.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.util.UUID

enum class SeasonAdvance { PITCH, PLATE_APPEARANCE, GAME }
class SeasonBusy : IllegalStateException("A season operation is already running")

/** Serialized durable operations. It never retains a calculation engine after success or failure. */
class SeasonCoordinator(
    private val store: SeasonStore,
    private val computation: CoroutineDispatcher = Dispatchers.Default,
    private val operationId: () -> String = { UUID.randomUUID().toString() },
) {
    private val mutex = Mutex()
    private suspend fun <T> exclusive(block: suspend () -> T): T {
        if (!mutex.tryLock()) throw SeasonBusy()
        try { return block() } finally { mutex.unlock() }
    }
    /** A read may finalize an already-durable completed checkpoint, without accepting another pitch. */
    suspend fun read(): StoredSeason? = exclusive { readLocked() }
    suspend fun hasSlot() = store.hasSlot()
    private suspend fun readLocked(): StoredSeason? {
        val current = store.read() ?: return null
        return if (current.state.active?.snapshot?.status == Status.COMPLETED) {
            val candidate = withContext(computation) { SeasonSession(current.state).advanceOnePitch() }
            commit(current, candidate)
        } else current
    }
    private suspend fun current() = readLocked() ?: throw StoreConflict("No season has been created")
    private suspend fun resolveWrite(id: String, write: suspend () -> StoredSeason): StoredSeason {
        try { return write() }
        catch (error: Exception) {
            // Cancellation can happen after SQLite committed. Re-read without mutating anything.
            val durable = try { withContext(NonCancellable) { store.read() } }
                catch (readError: Exception) { error.addSuppressed(readError); null }
            if (error is CancellationException) throw error
            if (durable?.operationId == id) return durable
            throw error
        }
    }
    private suspend fun commit(before: StoredSeason, candidate: SeasonState): StoredSeason {
        if (candidate == before.state) return before
        val id = operationId()
        return resolveWrite(id) { store.commit(before.token, id, candidate) }
    }
    suspend fun create(masterSeed: String = "42", selectedClubId: String = "L1-T01", replacementConfirmed: Boolean = false): StoredSeason = exclusive {
        val old = store.read()
        val id = operationId()
        resolveWrite(id) { store.create(masterSeed, selectedClubId, id, old?.token, replacementConfirmed) }
    }
    suspend fun selectClub(clubId: String): StoredSeason = exclusive {
        val before = current()
        val candidate = withContext(computation) { before.state.selectClub(clubId) }
        commit(before, candidate)
    }
    private suspend fun begin(before: StoredSeason): StoredSeason {
        if (before.state.active != null) return before
        check(before.state.status == SeasonStatus.IN_PROGRESS) { "The season has stopped" }
        val marker = withContext(computation) { before.state.beginNext() }
        return commit(before, marker) // No call to an engine until this durable marker succeeds.
    }
    private suspend fun advance(before: StoredSeason, mode: SeasonAdvance): StoredSeason {
        if (before.state.status == SeasonStatus.COMPLETE) return before
        check(before.state.status != SeasonStatus.BLOCKED) { "The unfinished game remains blocked" }
        val marked = begin(before)
        val candidate = withContext(computation) {
            val calculation = SeasonSession(marked.state)
            when (mode) {
                SeasonAdvance.PITCH -> calculation.advanceOnePitch()
                SeasonAdvance.PLATE_APPEARANCE -> calculation.advancePlateAppearance()
                SeasonAdvance.GAME -> calculation.finishCurrent()
            }
        }
        return commit(marked, candidate)
    }
    suspend fun advance(mode: SeasonAdvance): StoredSeason = exclusive { advance(current(), mode) }
    private suspend fun day(before: StoredSeason, stopRequested: () -> Boolean, onCommitted: suspend (StoredSeason) -> Unit): StoredSeason {
        if (before.state.status == SeasonStatus.COMPLETE) return before
        check(before.state.status != SeasonStatus.BLOCKED) { "The unfinished game remains blocked" }
        if (stopRequested() && before.state.active == null) return before
        var latest = before
        val targetDay = latest.state.dayIndex
        if (latest.state.active == null && PennantSchedule.calendar[targetDay - 1].isRestDay) {
            val candidate = withContext(computation) { latest.state.advanceRestDay() }
            latest = commit(latest, candidate); onCommitted(latest)
        } else {
            do {
                latest = advance(latest, SeasonAdvance.GAME)
                onCommitted(latest)
            } while (latest.state.status == SeasonStatus.IN_PROGRESS && latest.state.dayIndex == targetDay && !stopRequested())
        }
        return latest
    }
    suspend fun advanceDay(stopRequested: () -> Boolean = { false }, onCommitted: suspend (StoredSeason) -> Unit = {}): StoredSeason = exclusive {
        day(current(), stopRequested, onCommitted)
    }
    suspend fun finishSeason(stopRequested: () -> Boolean = { false }, onCommitted: suspend (StoredSeason) -> Unit = {}): StoredSeason = exclusive {
        var latest = current()
        while (latest.state.status == SeasonStatus.IN_PROGRESS) {
            latest = day(latest, stopRequested, onCommitted)
            if (stopRequested()) break
        }
        latest
    }
    suspend fun openNextSelected(stopRequested: () -> Boolean = { false }, onCommitted: suspend (StoredSeason) -> Unit = {}): StoredSeason = exclusive {
        var latest = current()
        while (latest.state.status == SeasonStatus.IN_PROGRESS) {
            val active = latest.state.active
            if (active == null && stopRequested()) break
            val game = active?.game ?: latest.state.games[latest.state.results.size]
            if (latest.state.selectedClubId in listOf(game.fixture.awayClubId, game.fixture.homeClubId)) {
                latest = begin(latest)
                break
            }
            latest = advance(latest, SeasonAdvance.GAME)
            onCommitted(latest)
            if (stopRequested()) break
        }
        latest
    }
    suspend fun saveSlot(): StoredSeason = exclusive {
        val before = current(); val id = operationId()
        resolveWrite(id) { store.saveSlot(before.token, id) }
    }
    suspend fun loadSlot(): StoredSeason = exclusive {
        val before = current(); val id = operationId()
        resolveWrite(id) { store.loadSlot(before.token, id) }
    }
}

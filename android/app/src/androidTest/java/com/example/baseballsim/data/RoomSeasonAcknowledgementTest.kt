package com.example.baseballsim.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class RoomSeasonAcknowledgementTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun name() = "acknowledgement-${UUID.randomUUID()}.db"
    private fun op() = UUID.randomUUID().toString()
    private fun pausedStore(db: PennantDatabase, arrived: CountDownLatch, release: CountDownLatch) =
        RoomSeasonStore(db, faults = StoreFaults {
            if (it == StoreFaultPoint.AFTER_COMMIT) {
                arrived.countDown()
                check(release.await(15, TimeUnit.SECONDS)) { "Other writer did not release acknowledgement" }
            }
        })

    @Test fun supersededBulkCannotAdoptAnotherLoadOrNewWorldAfterItsCommit(): Unit = runBlocking {
        for (replacement in listOf("load", "create")) {
            val name = name(); val db = PennantDatabase.open(context, name)
            val arrived = CountDownLatch(1); val release = CountDownLatch(1)
            var writer: Deferred<Result<StoredSeason>>? = null
            try {
                val healthy = RoomSeasonStore(db)
                val initial = healthy.create("42", "L1-T01", op())
                healthy.saveSlot(initial.token, op())
                val stop = AtomicBoolean(false)
                val coordinator = SeasonCoordinator(pausedStore(db, arrived, release), operationId = { "old-bulk" })
                writer = async(Dispatchers.Default) {
                    runCatching { coordinator.finishSeason(stopRequested = { stop.get() }, onCommitted = { stop.set(true) }) }
                }
                assertTrue(arrived.await(15, TimeUnit.SECONDS))
                val afterOldCommit = healthy.read()!!
                val replacementState = if (replacement == "load") healthy.loadSlot(afterOldCommit.token, op())
                    else healthy.create("0", "L2-T03", op(), afterOldCommit.token, replacementConfirmed = true)
                release.countDown()
                val result = writer.await()
                assertTrue("Old bulk must stop with a conflict", result.exceptionOrNull() is StoreConflict)
                assertEquals(replacementState, healthy.read())
                assertEquals(0, replacementState.state.results.size)
                assertNull(replacementState.state.active)
            } finally {
                release.countDown(); writer?.cancelAndJoin(); db.close(); context.deleteDatabase(name)
            }
        }
    }

    @Test fun duplicateAndNoopAcknowledgementsAreAlsoBoundToTheirOriginalContext(): Unit = runBlocking {
        for (kind in listOf("duplicate", "noop")) {
            val name = name(); val db = PennantDatabase.open(context, name)
            val arrived = CountDownLatch(1); val release = CountDownLatch(1)
            var writer: Deferred<Result<StoredSeason>>? = null
            try {
                val healthy = RoomSeasonStore(db)
                val initial = healthy.create("42", "L1-T01", op())
                val slotted = healthy.saveSlot(initial.token, op())
                val candidate = if (kind == "duplicate") slotted.state.advanceRestDay() else slotted.state
                val operation = op()
                if (kind == "duplicate") healthy.commit(slotted.token, operation, candidate)
                val paused = pausedStore(db, arrived, release)
                writer = async(Dispatchers.Default) { runCatching { paused.commit(slotted.token, operation, candidate) } }
                assertTrue(arrived.await(15, TimeUnit.SECONDS))
                val current = healthy.read()!!
                val loaded = healthy.loadSlot(current.token, op())
                release.countDown()
                assertTrue(writer.await().exceptionOrNull() is StoreConflict)
                assertEquals(loaded, healthy.read())
            } finally {
                release.countDown(); writer?.cancelAndJoin(); db.close(); context.deleteDatabase(name)
            }
        }
    }

    @Test fun reusedOperationIdConflictsAndUncommittedFailuresAreNeverConvertedToSuccess(): Unit = runBlocking {
        val name = name(); val db = PennantDatabase.open(context, name)
        try {
            val healthy = RoomSeasonStore(db)
            val coordinator = SeasonCoordinator(healthy, operationId = { "same" })
            val initial = coordinator.create()
            assertThrows(StoreConflict::class.java) { runBlocking { coordinator.selectClub("L2-T03") } }
            assertEquals(initial, healthy.read())
            val failing = RoomSeasonStore(db, faults = StoreFaults { if (it == StoreFaultPoint.BEFORE_TRANSACTION) error("Not committed") })
            val ambiguous = SeasonCoordinator(failing, operationId = { "same" })
            assertThrows(IllegalStateException::class.java) { runBlocking { ambiguous.selectClub("L2-T03") } }
            assertEquals(initial, healthy.read())
        } finally { db.close(); context.deleteDatabase(name) }
    }
}

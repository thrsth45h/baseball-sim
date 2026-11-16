package com.example.baseballsim.data

import android.content.Context
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.baseballsim.core.Status
import com.example.baseballsim.season.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** These checks exercise the shipped store, not the benchmark's instrumented candidate copy. */
@RunWith(AndroidJUnit4::class)
class RoomSeasonCommitProofTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun op() = UUID.randomUUID().toString()
    private suspend fun database(block: suspend (PennantDatabase) -> Unit) {
        val name = "commit-proof-${op()}.db"
        val db = PennantDatabase.open(context, name)
        try { block(db) } finally { db.close(); context.deleteDatabase(name) }
    }
    private suspend fun prefix(db: PennantDatabase, count: Int = 2): StoredSeason {
        val store = RoomSeasonStore(db)
        var saved = store.create("42", "L1-T01", op())
        repeat(count) {
            saved = store.commit(saved.token, op(), saved.state.beginNext())
            saved = store.commit(saved.token, op(), SeasonSession(saved.state).finishCurrent())
        }
        return saved
    }
    private suspend fun rejectChangedAcknowledgement(
        append: Boolean = false,
        alter: (PennantDatabase) -> Unit,
    ) = database { db ->
        var before = prefix(db)
        if (append) before = RoomSeasonStore(db).commit(before.token, op(), before.state.beginNext())
        val candidate = if (append) SeasonSession(before.state).finishCurrent() else before.state.selectClub("L2-T03")
        val faulty = RoomSeasonStore(db, faults = StoreFaults { if (it == StoreFaultPoint.AFTER_COMMIT) alter(db) })
        assertThrows(StoreConflict::class.java) { runBlocking { faulty.commit(before.token, op(), candidate) } }
        // The test never repairs, resets or rolls the database back after a failed acknowledgement.
        assertTrue(db.pennantDao().hasSavedData())
    }

    @Test fun everyHeadColumnAndPayloadHashPairMustMatchTheCommittedProof(): Unit = runBlocking {
        val updates = listOf("id=2", "worldId='changed'", "generationId='changed'", "revision=revision+1",
            "operationId='changed'", "payloadHash='changed'")
        for (update in updates) rejectChangedAcknowledgement { db ->
            db.openHelper.writableDatabase.execSQL("UPDATE working_state SET $update WHERE id=1")
        }
        for (rewriteHash in listOf(false, true)) rejectChangedAcknowledgement { db ->
            val sql = db.openHelper.writableDatabase
            val old = sql.query("SELECT payload FROM working_state WHERE id=1").use { it.moveToFirst(); it.getBlob(0) }
            val altered = old.copyOf().also { it[4] = (it[4].toInt() xor 1).toByte() }
            if (rewriteHash) sql.execSQL("UPDATE working_state SET payload=?, payloadHash=? WHERE id=1", arrayOf(altered, hash(altered)))
            else sql.execSQL("UPDATE working_state SET payload=? WHERE id=1", arrayOf(altered))
            assertArrayEquals(altered, sql.query("SELECT payload FROM working_state WHERE id=1").use { it.moveToFirst(); it.getBlob(0) })
        }
        rejectChangedAcknowledgement { db ->
            val sql = db.openHelper.writableDatabase
            val current = sql.query("SELECT payload FROM working_state WHERE id=1").use { it.moveToFirst(); it.getBlob(0) }
            // A valid canonical payload with its correct hash still is not this operation's committed value.
            val other = CheckpointCodec.encodeSeason(CheckpointCodec.decodeSeason(current).selectClub("L1-T02"))
            sql.execSQL("UPDATE working_state SET payload=?, payloadHash=? WHERE id=1", arrayOf(other, hash(other)))
            assertEquals("L1-T02", CheckpointCodec.decodeSeason(other).selectedClubId)
        }
        rejectChangedAcknowledgement { it.openHelper.writableDatabase.execSQL("DELETE FROM working_state") }
    }

    @Test fun everyReceiptColumnAndMissingReceiptMustRejectTheNormalCommit(): Unit = runBlocking {
        for (update in listOf("operationId='changed'", "fingerprint='changed'", "worldId='changed'",
            "generationId='changed'", "revision=revision+1")) rejectChangedAcknowledgement { db ->
            db.openHelper.writableDatabase.execSQL("UPDATE operation_receipts SET $update WHERE operationId=(SELECT operationId FROM working_state WHERE id=1)")
        }
        rejectChangedAcknowledgement { db ->
            db.openHelper.writableDatabase.execSQL("DELETE FROM operation_receipts WHERE operationId=(SELECT operationId FROM working_state WHERE id=1)")
        }
    }

    @Test fun ledgerPrefixTailOrderCountAndEveryColumnMustMatch(): Unit = runBlocking {
        for (tail in listOf(false, true)) {
            val pick = if (tail) "MAX" else "MIN"
            val where = "ordinal=(SELECT $pick(ordinal) FROM completed_results)"
            for (update in listOf("worldId='changed'", "generationId='changed'", "seasonId='changed'", "gameId='changed'",
                "ordinal=999", "payloadHash='changed'")) rejectChangedAcknowledgement(append = tail) { db ->
                db.openHelper.writableDatabase.execSQL("UPDATE completed_results SET $update WHERE $where")
            }
            for (rewriteHash in listOf(false, true)) rejectChangedAcknowledgement(append = tail) { db ->
                val sql = db.openHelper.writableDatabase
                val old = sql.query("SELECT payload FROM completed_results WHERE $where").use { it.moveToFirst(); it.getBlob(0) }
                val altered = old.copyOf().also { it[4] = (it[4].toInt() xor 1).toByte() }
                if (rewriteHash) sql.execSQL("UPDATE completed_results SET payload=?, payloadHash=? WHERE $where", arrayOf(altered, hash(altered)))
                else sql.execSQL("UPDATE completed_results SET payload=? WHERE $where", arrayOf(altered))
            }
            rejectChangedAcknowledgement(append = tail) { db ->
                db.openHelper.writableDatabase.execSQL("DELETE FROM completed_results WHERE $where")
            }
        }
        rejectChangedAcknowledgement { db ->
            db.openHelper.writableDatabase.execSQL("INSERT INTO completed_results SELECT worldId,generationId,seasonId,'extra',999,payloadHash,payload FROM completed_results WHERE ordinal=(SELECT MIN(ordinal) FROM completed_results)")
        }
        rejectChangedAcknowledgement { db ->
            // Reverse the two existing rows' order while preserving their count and all payload bytes.
            db.openHelper.writableDatabase.execSQL("UPDATE completed_results SET ordinal=3-ordinal")
        }
    }

    @Test fun coordinatorCannotTurnFingerprintMismatchIntoSuccessfulPublication(): Unit = runBlocking {
        for (kind in listOf("select", "advance")) database { db ->
            val healthy = RoomSeasonStore(db)
            var before = healthy.create("42", "L1-T01", op())
            if (kind == "advance") before = healthy.commit(before.token, op(), before.state.beginNext())
            var completedCallbacks = 0
            val faulty = RoomSeasonStore(db, faults = StoreFaults { if (it == StoreFaultPoint.AFTER_COMMIT) {
                db.openHelper.writableDatabase.execSQL("UPDATE operation_receipts SET fingerprint='altered' WHERE operationId=(SELECT operationId FROM working_state WHERE id=1)")
            } })
            val coordinator = SeasonCoordinator(faulty)
            val result = runCatching {
                if (kind == "select") coordinator.selectClub("L2-T03")
                else coordinator.advanceDay(stopRequested = { false }, onCommitted = { completedCallbacks++ })
            }
            assertTrue(result.exceptionOrNull() is StoreConflict)
            assertEquals(0, completedCallbacks)
            // Public read intentionally preserves its old contract; it cannot validate the request fingerprint.
            val durable = healthy.read()!!
            assertEquals(before.token.revision + 1, durable.token.revision)
            assertEquals("altered", db.pennantDao().receipt(durable.operationId)!!.fingerprint)
        }
    }

    @Test fun aSameGenerationNewRevisionCannotBeAdoptedByTheOlderOperation(): Unit = runBlocking {
        database { db ->
            val healthy = RoomSeasonStore(db)
            val before = healthy.create("42", "L1-T01", op())
            val arrived = CountDownLatch(1); val release = CountDownLatch(1)
            val paused = RoomSeasonStore(db, faults = StoreFaults { if (it == StoreFaultPoint.AFTER_COMMIT) {
                arrived.countDown(); check(release.await(20, TimeUnit.SECONDS))
            } })
            val writing = async(Dispatchers.Default) { runCatching { paused.commit(before.token, op(), before.state.selectClub("L2-T03")) } }
            try {
                assertTrue(arrived.await(20, TimeUnit.SECONDS))
                val current = healthy.read()!!
                val replacement = healthy.commit(current.token, op(), current.state.selectClub("L1-T02"))
                release.countDown()
                assertTrue(writing.await().exceptionOrNull() is StoreConflict)
                assertEquals(replacement, healthy.read())
            } finally { release.countDown(); writing.cancelAndJoin() }
        }
    }

    @Test fun frozenCallerInputsAreNotTheAcknowledgedDecodedInstance(): Unit = runBlocking {
        for (point in listOf(StoreFaultPoint.BEFORE_TRANSACTION, StoreFaultPoint.AFTER_COMMIT)) database { db ->
            val before = prefix(db)
            val callerResults = before.state.results.toMutableList()
            val candidate = SeasonState.restore("42", "L2-T03", before.state.dayIndex, callerResults, null)
            val expectedBytes = CheckpointCodec.encodeSeason(candidate)
            val store = RoomSeasonStore(db, faults = StoreFaults { if (it == point) {
                callerResults.clear()
                assertThrows(UnsupportedOperationException::class.java) { (candidate.results as MutableList<GameSummary>).clear() }
            } })
            val saved = store.commit(before.token, op(), candidate)
            assertNotSame(candidate, saved.state)
            assertArrayEquals(expectedBytes, CheckpointCodec.encodeSeason(saved.state))
            assertArrayEquals(expectedBytes, db.pennantDao().head()!!.payload)
            assertEquals(2, saved.state.results.size)
        }
    }

    @Test fun aDuplicateFoundInsideTheTransactionKeepsTheExistingStrictReadPath(): Unit = runBlocking {
        database { db ->
            val healthy = RoomSeasonStore(db)
            val initial = healthy.create("42", "L1-T01", op())
            val candidate = initial.state.selectClub("L2-T03")
            val operation = op()
            val racing = RoomSeasonStore(db, faults = StoreFaults { point ->
                if (point == StoreFaultPoint.BEFORE_TRANSACTION) runBlocking {
                    healthy.commit(initial.token, operation, candidate)
                }
                if (point == StoreFaultPoint.AFTER_COMMIT) db.openHelper.writableDatabase.execSQL(
                    "UPDATE operation_receipts SET fingerprint='old-read-contract' WHERE operationId=?", arrayOf(operation))
            })
            val result = racing.commit(initial.token, operation, candidate)
            assertEquals(healthy.read(), result)
            assertEquals(1, result.token.revision)
            assertEquals("old-read-contract", db.pennantDao().receipt(operation)!!.fingerprint)
        }
    }

    private suspend fun fixture(db: PennantDatabase, state: SeasonState) {
        val bytes = CheckpointCodec.encodeSeason(state)
        db.withTransaction {
            val dao = db.pennantDao()
            dao.insertGeneration(GenerationEntity("generation", "world"))
            dao.insertHead(WorkingEntity(worldId = "world", generationId = "generation", revision = 0,
                operationId = "fixture", payloadHash = hash(bytes), payload = bytes))
            dao.insertReceipt(ReceiptEntity("fixture", "fixture", "world", "generation", 0))
            state.results.forEach { summary ->
                val body = CheckpointCodec.encodeSummary(summary); val game = summary.game.fixture
                dao.insertResult(ResultEntity("world", "generation", game.seasonId, game.gameId, game.ordinal, hash(body), body))
            }
        }
    }

    @Test fun shippedAndFrozenBaselineStoresAgreeAcrossNormalAndExceptionalBoundaries(): Unit = runBlocking {
        database { a -> database { b ->
            val idsA = listOf("world", "generation").iterator(); val idsB = listOf("world", "generation").iterator()
            val shipped = RoomSeasonStore(a, newId = { idsA.next() })
            val baseline = BaselineRoomSeasonStore(b, newId = { idsB.next() })
            var left = shipped.create("42", "L1-T01", "create")
            var right = baseline.create("42", "L1-T01", "create")
            suspend fun compare() {
                assertEquals(left, right)
                assertArrayEquals(a.pennantDao().head()!!.payload, b.pennantDao().head()!!.payload)
                val rowsA = a.pennantDao().results(left.token.worldId, left.token.generationId)
                val rowsB = b.pennantDao().results(right.token.worldId, right.token.generationId)
                assertEquals(rowsA.size, rowsB.size)
                rowsA.zip(rowsB).forEach { (x, y) ->
                    assertEquals(x.copy(payload = y.payload), y); assertArrayEquals(x.payload, y.payload)
                }
            }
            suspend fun commit(operation: String, candidate: SeasonState) {
                val oldA = left; val oldB = right
                left = shipped.commit(left.token, operation, candidate)
                right = baseline.commit(right.token, operation, candidate)
                compare()
                if (candidate != oldA.state) {
                    assertEquals(left, shipped.commit(oldA.token, operation, candidate))
                    assertEquals(right, baseline.commit(oldB.token, operation, candidate))
                }
            }
            compare()
            commit("noop", left.state)
            commit("rest", left.state.advanceRestDay())
            commit("marker", left.state.beginNext())
            commit("pitch", SeasonSession(left.state).advanceOnePitch())
            commit("result", SeasonSession(left.state).finishCurrent())
            commit("select", left.state.selectClub("L2-T03"))
            commit("blocked-marker", left.state.beginNext())
            val old = left.state.active!!; val snap = old.snapshot
            val stopped = SnapshotDto(snap.sequence, snap.battingIndices, snap.batterCounts, 0, snap.lineScore,
                Status.ABORTED, "source_error", null, snap.terminalDelta, snap.historyOrigin)
            val diagnostic = GameCheckpoint(old.game, old.maxPitches, 1, stopped, old.source, old.initial)
            commit("blocked", left.state.checkpoint(diagnostic))
            commit("blocked-select", left.state.selectClub("L1-T03"))
            assertEquals(SeasonStatus.BLOCKED, left.state.status)
        } }
    }

    @Test fun terminal720FixtureKeepsExactlyTheSameFrameAndNormalCommitResponse(): Unit = runBlocking {
        val final = SeasonSession().finishSeason()
        database { a -> database { b ->
            fixture(a, final); fixture(b, final)
            val shipped = RoomSeasonStore(a); val baseline = BaselineRoomSeasonStore(b)
            val beforeA = shipped.read()!!; val beforeB = baseline.read()!!
            assertEquals(beforeA, beforeB)
            val candidate = final.selectClub("L2-T03")
            val afterA = shipped.commit(beforeA.token, "terminal-selection", candidate)
            val afterB = baseline.commit(beforeB.token, "terminal-selection", candidate)
            assertEquals(afterA, afterB)
            assertArrayEquals(a.pennantDao().head()!!.payload, b.pennantDao().head()!!.payload)
            assertEquals(720, afterA.state.results.size)
            assertEquals(SeasonStatus.COMPLETE, afterA.state.status)
            assertNull(afterA.state.active)
        } }
    }
}

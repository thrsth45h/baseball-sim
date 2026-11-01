package com.example.baseballsim.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.baseballsim.core.Status
import com.example.baseballsim.season.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RoomSeasonFailureTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun name() = "failure-test-${UUID.randomUUID()}.db"
    private fun op() = UUID.randomUUID().toString()
    private suspend fun begin(store: SeasonStore, seed: String = "42"): StoredSeason {
        val initial = store.create(seed, "L1-T01", op())
        return store.commit(initial.token, op(), initial.state.beginNext())
    }
    @Test fun failuresOnEveryTransactionSidePreserveEitherTheOldOrCommittedBoundary(): Unit = runBlocking {
        for (point in StoreFaultPoint.entries) {
            val name = name(); val db = PennantDatabase.open(context, name)
            try {
                val healthy = RoomSeasonStore(db); val before = begin(healthy)
                val candidate = SeasonSession(before.state).advanceOnePitch()
                val faulty = RoomSeasonStore(db, faults = StoreFaults { if (it == point) error("Injected $point") })
                val operation = op()
                assertThrows(IllegalStateException::class.java) { runBlocking { faulty.commit(before.token, operation, candidate) } }
                val after = healthy.read()!!
                if (point == StoreFaultPoint.AFTER_COMMIT) {
                    assertEquals(candidate, after.state); assertEquals(operation, after.operationId)
                    assertEquals(after, healthy.commit(before.token, operation, candidate))
                } else assertEquals(before, after)
            } finally { db.close(); context.deleteDatabase(name) }
        }
    }
    @Test fun skippingTheStartMarkerAndForgedReplayAreRejectedBeforeDatabaseChanges(): Unit = runBlocking {
        val name = name(); val db = PennantDatabase.open(context, name)
        try {
            val store = RoomSeasonStore(db); val initial = store.create("42", "L1-T01", op())
            val unmarked = SeasonSession(initial.state).finishCurrent()
            assertThrows(IllegalArgumentException::class.java) { runBlocking { store.commit(initial.token, op(), unmarked) } }
            assertEquals(initial, store.read())
            val marker = store.commit(initial.token, op(), initial.state.beginNext())
            val pitch = store.commit(marker.token, op(), SeasonSession(marker.state).advanceOnePitch())
            val checkpoint = pitch.state.active!!
            val source = checkpoint.source.copy(state = (checkpoint.source.state!!.toULong() xor 1uL).toString())
            val altered = GameCheckpoint(checkpoint.game, checkpoint.maxPitches, checkpoint.advanceCalls, checkpoint.snapshot, source, checkpoint.initial)
            val forged = SeasonState.restore(pitch.state.masterSeed, pitch.state.selectedClubId, pitch.state.dayIndex, pitch.state.results, altered)
            assertThrows(StoreCorruption::class.java) { runBlocking { store.commit(pitch.token, op(), forged) } }
            assertEquals(pitch, store.read())
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun malformedPayloadAndLedgerAreRetainedWithoutFallbackOrAutomaticReset(): Unit = runBlocking {
        val name = name(); val db = PennantDatabase.open(context, name)
        try {
            val store = RoomSeasonStore(db); val marker = begin(store)
            val completed = store.commit(marker.token, op(), SeasonSession(marker.state).finishCurrent())
            val old = db.pennantDao().head()!!
            val damaged = old.payload.copyOf().also { it[4] = (it[4].toInt() xor 1).toByte() }
            db.openHelper.writableDatabase.execSQL("UPDATE working_state SET payload=? WHERE id=1", arrayOf(damaged))
            assertThrows(StoreCorruption::class.java) { runBlocking { store.read() } }
            assertArrayEquals(damaged, db.pennantDao().head()!!.payload)
            db.openHelper.writableDatabase.execSQL("UPDATE working_state SET payloadHash=? WHERE id=1", arrayOf(hash(damaged)))
            assertThrows(StoreCorruption::class.java) { runBlocking { store.read() } }
            assertArrayEquals(damaged, db.pennantDao().head()!!.payload)
            db.openHelper.writableDatabase.execSQL("UPDATE working_state SET payload=?,payloadHash=? WHERE id=1", arrayOf(old.payload, old.payloadHash))
            val row = db.pennantDao().results(completed.token.worldId, completed.token.generationId).single()
            val badResult = row.payload.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
            db.openHelper.writableDatabase.execSQL("UPDATE completed_results SET payload=?,payloadHash=?", arrayOf(badResult, hash(badResult)))
            assertThrows(StoreCorruption::class.java) { runBlocking { store.read() } }
            assertArrayEquals(badResult, db.pennantDao().results(completed.token.worldId, completed.token.generationId).single().payload)
            assertEquals(1, db.pennantDao().results(completed.token.worldId, completed.token.generationId).size)
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun missingSchemaMigrationDoesNotDeleteTheDatabase(): Unit = runBlocking {
        val name = name()
        context.openOrCreateDatabase(name, 0, null).use {
            it.execSQL("CREATE TABLE retained_sentinel (value TEXT NOT NULL)")
            it.execSQL("INSERT INTO retained_sentinel VALUES ('keep')")
            it.execSQL("PRAGMA user_version=2")
        }
        val db = PennantDatabase.open(context, name)
        try {
            assertThrows(Exception::class.java) { runBlocking { RoomSeasonStore(db).read() } }
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { raw ->
                raw.rawQuery("PRAGMA user_version", null).use { assertTrue(it.moveToFirst()); assertEquals(2, it.getInt(0)) }
                raw.rawQuery("SELECT value FROM retained_sentinel", null).use { assertTrue(it.moveToFirst()); assertEquals("keep", it.getString(0)) }
            }
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun zeroPitchBlockedDiagnosticSurvivesAndConfirmedNewSeasonKeepsTheManualSlot(): Unit = runBlocking {
        val name = name(); var db = PennantDatabase.open(context, name)
        try {
            var store = RoomSeasonStore(db)
            val initial = store.create("42", "L1-T01", op())
            val slot = store.saveSlot(initial.token, op())
            val marker = store.commit(slot.token, op(), slot.state.beginNext())
            val old = marker.state.active!!; val snap = old.snapshot
            // Synthetic retained error boundary, not a claim that deterministic Toy naturally throws here.
            val stopped = SnapshotDto(snap.sequence, snap.battingIndices, snap.batterCounts, 0, snap.lineScore,
                Status.ABORTED, "source_error", null, snap.terminalDelta, snap.historyOrigin)
            val diagnostic = GameCheckpoint(old.game, old.maxPitches, 1, stopped, old.source, old.initial)
            val blocked = store.commit(marker.token, op(), marker.state.checkpoint(diagnostic))
            db.close(); db = PennantDatabase.open(context, name); store = RoomSeasonStore(db)
            assertEquals(blocked, store.read()); assertEquals(SeasonStatus.BLOCKED, store.read()!!.state.status)
            val coordinator = SeasonCoordinator(store)
            assertEquals(blocked, coordinator.read())
            assertThrows(IllegalStateException::class.java) { runBlocking { coordinator.advance(SeasonAdvance.PITCH) } }
            assertThrows(IllegalArgumentException::class.java) { runBlocking { coordinator.saveSlot() } }
            assertThrows(IllegalArgumentException::class.java) { runBlocking { coordinator.loadSlot() } }
            assertThrows(StoreConflict::class.java) { runBlocking { coordinator.create("0") } }
            val replacement = coordinator.create("0", replacementConfirmed = true)
            assertEquals(1, db.pennantDao().diagnosticCount()); assertTrue(store.hasSlot())
            assertNotEquals(blocked.token.worldId, replacement.token.worldId)
            assertEquals(initial.state, coordinator.loadSlot().state)
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun final720GameFrameAndRowsFitAndReadFromAFileDatabase(): Unit = runBlocking {
        val name = name(); var db = PennantDatabase.open(context, name)
        try {
            var store = RoomSeasonStore(db)
            val initial = store.create("42", "L1-T01", op())
            val final = SeasonSession(initial.state).finishSeason()
            assertEquals(720, final.results.size)
            val bytes = CheckpointCodec.encodeSeason(final); val operation = op(); val revision = initial.token.revision + 1
            val started = System.nanoTime()
            // A canonical final-state DB fixture tests maximum row size; it is not a production import API.
            db.withTransaction {
                final.results.forEach { summary ->
                    val payload = CheckpointCodec.encodeSummary(summary)
                    db.pennantDao().insertResult(ResultEntity(initial.token.worldId, initial.token.generationId,
                        summary.game.fixture.seasonId, summary.game.fixture.gameId, summary.game.fixture.ordinal, hash(payload), payload))
                }
                assertEquals(1, db.pennantDao().compareAndSetHead(initial.token.worldId, initial.token.generationId, initial.token.revision,
                    initial.token.worldId, initial.token.generationId, revision, operation, hash(bytes), bytes))
                db.pennantDao().insertReceipt(ReceiptEntity(operation, "maximum-frame-test", initial.token.worldId, initial.token.generationId, revision))
            }
            val writeNanos = System.nanoTime() - started
            val journal = db.openHelper.writableDatabase.query("PRAGMA journal_mode").use { check(it.moveToFirst()); it.getString(0) }
            val synchronous = db.openHelper.writableDatabase.query("PRAGMA synchronous").use { check(it.moveToFirst()); it.getInt(0) }
            db.close(); db = PennantDatabase.open(context, name); store = RoomSeasonStore(db)
            val readStarted = System.nanoTime(); val reloaded = store.read()!!; val readNanos = System.nanoTime()-readStarted
            assertEquals(final, reloaded.state)
            assertEquals(720, db.pennantDao().results(initial.token.worldId, initial.token.generationId).size)
            val report = JSONObject().put("schema", 1).put("room", "2.8.5").put("completed_games", 720)
                .put("frame_bytes", bytes.size).put("database_bytes", context.getDatabasePath(name).length())
                .put("journal_mode", journal).put("synchronous", synchronous)
                .put("fixture_write_ms", writeNanos / 1_000_000.0).put("close_open_read_ms", readNanos / 1_000_000.0)
                .put("scope", "canonical final-frame size/read test; not 720 transactional skip operations or a process kill")
            val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
                ?: context.getExternalFilesDir(null)!!.absolutePath
            File(output).mkdirs(); File(output, "pennant-store-evidence.json").writeText(report.toString(2))
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun manualSaveAndLoadFailuresRollBackSlotHeadAndResultRowsTogether(): Unit = runBlocking {
        val name = name(); val db = PennantDatabase.open(context, name)
        try {
            val store = RoomSeasonStore(db)
            val initial = store.create("42", "L1-T01", op())
            val failSave = RoomSeasonStore(db, faults = StoreFaults { if (it == StoreFaultPoint.AFTER_HEAD) error("Failed slot save") })
            assertThrows(IllegalStateException::class.java) { runBlocking { failSave.saveSlot(initial.token, op()) } }
            assertEquals(initial, store.read()); assertFalse(store.hasSlot())
            var latest = store.commit(initial.token, op(), initial.state.beginNext())
            latest = store.commit(latest.token, op(), SeasonSession(latest.state).finishCurrent())
            val saved = store.saveSlot(latest.token, op())
            latest = store.commit(saved.token, op(), saved.state.beginNext())
            latest = store.commit(latest.token, op(), SeasonSession(latest.state).finishCurrent())
            val failLoad = RoomSeasonStore(db, faults = StoreFaults { if (it == StoreFaultPoint.AFTER_RESULTS) error("Failed slot load") })
            assertThrows(IllegalStateException::class.java) { runBlocking { failLoad.loadSlot(latest.token, op()) } }
            assertEquals(latest, store.read())
            assertEquals(2, db.pennantDao().results(latest.token.worldId, latest.token.generationId).size)
            val loaded = store.loadSlot(latest.token, op())
            assertEquals(saved.state, loaded.state)
            assertEquals(1, db.pennantDao().results(loaded.token.worldId, loaded.token.generationId).size)
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun completedWalkoffCheckpointIsFinalizedOnceAfterCloseOpenWithoutAnotherPitch(): Unit = runBlocking {
        val name = name(); var db = PennantDatabase.open(context, name)
        try {
            var store = RoomSeasonStore(db)
            var latest = store.create("42", "L1-T01", op())
            var found = false
            while (latest.state.status == SeasonStatus.IN_PROGRESS) {
                val marker = store.commit(latest.token, op(), latest.state.beginNext())
                val calculation = marker.state.active!!.recover("42")
                calculation.finish()
                val completed = calculation.checkpoint()
                val expected = SeasonSession(marker.state).finishCurrent()
                if (completed.snapshot.reason!!.startsWith("walkoff_")) {
                    val pending = store.commit(marker.token, op(), marker.state.checkpoint(completed))
                    assertEquals(marker.state.results.size, pending.state.results.size)
                    db.close(); db = PennantDatabase.open(context, name); store = RoomSeasonStore(db)
                    val coordinator = SeasonCoordinator(store)
                    val finalized = coordinator.read()!!
                    assertEquals(expected, finalized.state)
                    assertNull(finalized.state.active)
                    assertEquals(completed.snapshot.pitchesAccepted, finalized.state.results.last().pitchesAccepted)
                    assertEquals(finalized, coordinator.read())
                    assertEquals(finalized.state.results.size, db.pennantDao().results(finalized.token.worldId, finalized.token.generationId).size)
                    found = true
                    break
                }
                latest = store.commit(marker.token, op(), expected)
            }
            assertTrue("The fixed season contains an actual toy walk-off", found)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}

package com.example.baseballsim.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.baseballsim.season.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.zip.GZIPInputStream

/** The v1 fixture is independently Python-constructed, not an old APK installation or upgrade. */
@RunWith(AndroidJUnit4::class)
class RoomLineupStoreTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val club = "L1-T01"
    private fun order(first: Int = 10) = (listOf(first) + (2..9)).map { "$club-P${it.toString().padStart(2, '0')}" }

    @Test fun independentRecordedV1DatabaseRoundtripsAndCrossFormatSlotsKeepTheirFormat(): Unit = runBlocking {
        val name = "independent-v1-${UUID.randomUUID()}.db"
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        // The neutral packaging alias retains the original gzip bytes/hash. CI also
        // verifies the exact asset entry in the assembled instrumentation APK.
        val packed = InstrumentationRegistry.getInstrumentation().context.assets.open("lineup/one-game-v1.fixture.bin").use { it.readBytes() }
        assertEquals("8596765ca1ef34c0e33b619529acf3ce52541801888691796b0c0f13a0da272f", hash(packed))
        val raw = GZIPInputStream(packed.inputStream()).use { it.readBytes() }
        assertEquals("4247d3c43dbce76e505c7676eb419df03c6602adf9ce020340e74fef6dd557c4", hash(raw))
        path.writeBytes(raw)
        val db = PennantDatabase.open(context, name)
        try {
            val store = RoomSeasonStore(db)
            val v1 = store.read()!!
            assertFalse(v1.state.lineupEnabled); assertEquals(108, v1.state.playerCounts.size)
            assertEquals(StoreToken("golden-v1-world", "golden-v1-generation", 2), v1.token)
            assertEquals(1, v1.state.results.size); assertEquals(2, v1.state.dayIndex)
            assertEquals("d208c4f99f28b5676a9a84b7dda113e186f8a84ce36e95b5c692366d6d87fc39", hash(CheckpointCodec.encodeSeason(v1.state)))
            val v2 = store.createLineup("42", club, "new-v2", v1.token, true)
            assertTrue(v2.state.lineupEnabled); assertEquals(144, v2.state.playerCounts.size)
            assertTrue(store.hasSlot()) // CREATE must preserve the historical slot.
            val loadedOld = store.loadSlot(v2.token, "load-v1")
            assertFalse(loadedOld.state.lineupEnabled); assertEquals(v1.state, loadedOld.state)
            assertNotEquals(v1.token.generationId, loadedOld.token.generationId)
            val begun = store.commit(loadedOld.token, "begin-v1", loadedOld.state.beginNext())
            val expected = begun.state.active!!.recover("42").also { it.advanceOnePitch() }.checkpoint()
            val pitched = store.commit(begun.token, "pitch-v1", SeasonSession(begun.state).advanceOnePitch())
            assertEquals(expected, pitched.state.active)
            val completed = store.commit(pitched.token, "finish-v1", SeasonSession(pitched.state).finishCurrent())
            val savedLegacy = store.saveSlot(completed.token, "save-v1")
            val modern = store.createLineup("42", club, "again-v2", savedLegacy.token, true)
            val edited = store.commit(modern.token, "edit-v2", modern.state.applyLineup(club, order()))
            val slotted = store.saveSlot(edited.token, "save-v2")
            val legacy = store.create("0", club, "new-v1", slotted.token, true)
            val loadedModern = store.loadSlot(legacy.token, "load-v2")
            assertTrue(loadedModern.state.lineupEnabled); assertEquals(edited.state, loadedModern.state)
            assertEquals(order(), loadedModern.state.lineup(club))
            assertEquals(2, db.openHelper.readableDatabase.version)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun applyChecksTheOriginalTokenAtTheRealTransactionAndPreservesEveryOtherValue(): Unit = runBlocking {
        val name = "lineup-cas-${UUID.randomUUID()}.db"; val db = PennantDatabase.open(context, name)
        try {
            val ordinary = RoomSeasonStore(db)
            val created = ordinary.createLineup("42", club, "create")
            val candidate = created.state.applyLineup(club, order())
            var injected = false
            val racing = RoomSeasonStore(db, faults = StoreFaults { point ->
                if (point == StoreFaultPoint.BEFORE_TRANSACTION && !injected) {
                    injected = true
                    runBlocking { ordinary.commit(created.token, "racing-select", created.state.selectClub("L2-T06")) }
                }
            })
            assertThrows(StoreConflict::class.java) { runBlocking { racing.commit(created.token, "stale-edit", candidate) } }
            val after = ordinary.read()!!
            assertEquals("L2-T06", after.state.selectedClubId)
            assertEquals(created.state.clubLineups, after.state.clubLineups)
            val coordinator = SeasonCoordinator(ordinary)
            assertThrows(StoreConflict::class.java) { runBlocking { coordinator.applyLineup(created.token, club, order()) } }
            val selected = coordinator.selectClub(club)
            val edited = coordinator.applyLineup(selected.token, club, order())
            assertEquals(selected.state.games, edited.state.games)
            assertEquals(selected.state.results, edited.state.results)
            assertEquals(selected.state.playerCounts, edited.state.playerCounts)
            assertEquals(selected.state.dayIndex, edited.state.dayIndex); assertNull(edited.state.active)
            assertEquals(selected.token.revision + 1, edited.token.revision)
            val beforeBytes = CheckpointCodec.encodeSeason(edited.state)
            val retry = coordinator.applyLineup(edited.token, club, order())
            assertEquals(edited, retry); assertArrayEquals(beforeBytes, CheckpointCodec.encodeSeason(retry.state))
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun validRosterMembershipCannotChangeStartOrActiveOrCompletedParticipants(): Unit = runBlocking {
        val name = "lineup-freeze-${UUID.randomUUID()}.db"; val db = PennantDatabase.open(context, name)
        try {
            val store = RoomSeasonStore(db)
            val initial = store.createLineup("42", club, "create")
            val combined = initial.state.selectClub("L2-T06").applyLineup("L2-T06", (1..9).map { "L2-T06-P${it.toString().padStart(2, '0')}" }.reversed())
            assertThrows(IllegalArgumentException::class.java) { runBlocking { store.commit(initial.token, "combined", combined) } }
            val marker = initial.state.beginNext().active!!
            val wrong = GameLineup(marker.lineup.away, order())
            fun replaced(checkpoint: GameCheckpoint) = GameCheckpoint(checkpoint.game, checkpoint.maxPitches, checkpoint.advanceCalls,
                checkpoint.snapshot, checkpoint.source, checkpoint.initial, wrong)
            fun state(checkpoint: GameCheckpoint) = SeasonState.restore("42", club, 2, emptyList(), checkpoint, initial.state.versions, initial.state.clubLineups)
            assertThrows(IllegalArgumentException::class.java) { runBlocking { store.commit(initial.token, "wrong-start", state(replaced(marker))) } }
            val begun = store.commit(initial.token, "begin", initial.state.beginNext())
            val match = marker.recover("42"); match.advanceOnePitch()
            assertThrows(IllegalArgumentException::class.java) { runBlocking { store.commit(begun.token, "wrong-advance", state(replaced(match.checkpoint()))) } }
            match.finish()
            val wrongResult = GameSummary.fromCore(match.game, match.snapshot, lineup = wrong)
            val wrongCompleted = SeasonState.restore("42", club, 2, listOf(wrongResult), null, initial.state.versions, initial.state.clubLineups)
            assertThrows(IllegalArgumentException::class.java) { runBlocking { store.commit(begun.token, "wrong-result", wrongCompleted) } }
            assertEquals(begun, store.read())
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun recomputedHashCannotHideActivePeopleDisagreeingWithSavedPlans(): Unit = runBlocking {
        val name = "lineup-cold-corrupt-${UUID.randomUUID()}.db"; val db = PennantDatabase.open(context, name)
        try {
            val store = RoomSeasonStore(db)
            val initial = store.createLineup("42", club, "create")
            val begun = store.commit(initial.token, "begin", initial.state.beginNext())
            val active = begun.state.active!!
            val alternate = GameCheckpoint(active.game, active.maxPitches, active.advanceCalls, active.snapshot,
                active.source, active.initial, GameLineup(active.lineup.away, order()))
            fun body(c: GameCheckpoint): ByteArray {
                val full = CheckpointCodec.encodeGame(c)
                val length = java.nio.ByteBuffer.wrap(full, 0, 4).int
                return full.copyOfRange(4 + length, full.size)
            }
            val legitimate = body(active); val forged = body(alternate)
            assertEquals(legitimate.size, forged.size)
            val frame = CheckpointCodec.encodeSeason(begun.state)
            val matches = (0..frame.size - legitimate.size).filter { offset -> legitimate.indices.all { frame[offset + it] == legitimate[it] } }
            assertEquals(1, matches.size)
            forged.copyInto(frame, matches.single())
            db.openHelper.writableDatabase.execSQL("UPDATE working_state SET payload=?, payloadHash=? WHERE id=1", arrayOf(frame, hash(frame)))
            assertThrows(StoreCorruption::class.java) { runBlocking { store.read() } }
            assertArrayEquals(frame, db.pennantDao().head()!!.payload)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun rejectedApplyNeverFinalizesADurableCompletedCheckpoint(): Unit = runBlocking {
        val name = "lineup-isolated-${UUID.randomUUID()}.db"; val db = PennantDatabase.open(context, name)
        try {
            val store = RoomSeasonStore(db)
            val created = store.createLineup("42", club, "create")
            val begun = store.commit(created.token, "begin", created.state.beginNext())
            val match = begun.state.active!!.recover("42"); match.finish()
            val pending = store.commit(begun.token, "completed-checkpoint", begun.state.checkpoint(match.checkpoint()))
            val frame = CheckpointCodec.encodeSeason(pending.state)
            assertTrue(pending.state.results.isEmpty()); assertNotNull(pending.state.active)
            val coordinator = SeasonCoordinator(store)
            assertThrows(StoreConflict::class.java) { runBlocking { coordinator.applyLineup(created.token, club, order()) } }
            assertEquals(pending, store.read())
            assertThrows(IllegalStateException::class.java) { runBlocking { coordinator.applyLineup(pending.token, club, order()) } }
            assertEquals(pending, store.read())
            assertArrayEquals(frame, db.pennantDao().head()!!.payload)
            assertEquals(0, db.pennantDao().results(pending.token.worldId, pending.token.generationId).size)
        } finally { db.close(); context.deleteDatabase(name) }
    }

}

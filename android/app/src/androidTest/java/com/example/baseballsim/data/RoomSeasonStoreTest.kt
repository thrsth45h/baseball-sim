package com.example.baseballsim.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.baseballsim.season.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RoomSeasonStoreTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private fun name() = "store-test-${UUID.randomUUID()}.db"
    @Test fun roomCloseOpenPreservesARealPitchAndAllReplayCounters(): Unit = runBlocking {
        val name = name()
        var db = PennantDatabase.open(context, name)
        try {
            var store = RoomSeasonStore(db)
            val initial = store.create("42", "L1-T01", "create")
            val begun = store.commit(initial.token, "begin", initial.state.beginNext())
            val calculated = SeasonSession(begun.state).advanceOnePitch()
            val saved = store.commit(begun.token, "pitch", calculated)
            assertTrue(saved.state.active!!.snapshot.pitchesAccepted > 0)
            db.close(); db = PennantDatabase.open(context, name); store = RoomSeasonStore(db)
            val restored = store.read()!!
            assertEquals(saved, restored)
            assertEquals(saved.state.active, restored.state.active!!.recover("42").checkpoint())
            assertThrows(IllegalArgumentException::class.java) { runBlocking { store.saveSlot(restored.token, "illegal-save") } }
            assertThrows(IllegalArgumentException::class.java) { runBlocking { store.loadSlot(restored.token, "illegal-load") } }
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun completedResultIsAtomicAndLostReplyCanBeRetriedWithoutDoubleCounting(): Unit = runBlocking {
        val name = name(); val db = PennantDatabase.open(context, name)
        try {
            val store = RoomSeasonStore(db)
            val start = store.create("0", "L1-T01", "create")
            val begun = store.commit(start.token, "begin", start.state.beginNext())
            val candidate = SeasonSession(begun.state).finishCurrent()
            val rollback = RoomSeasonStore(db, faults = StoreFaults { if (it == StoreFaultPoint.AFTER_RESULTS) error("Injected transaction failure") })
            assertThrows(IllegalStateException::class.java) { runBlocking { rollback.commit(begun.token, "failed", candidate) } }
            assertEquals(begun, store.read())
            val lostReply = RoomSeasonStore(db, faults = StoreFaults { if (it == StoreFaultPoint.AFTER_COMMIT) error("Injected lost response") })
            assertThrows(IllegalStateException::class.java) { runBlocking { lostReply.commit(begun.token, "complete", candidate) } }
            val durable = store.read()!!
            assertEquals(1, durable.state.results.size); assertNull(durable.state.active)
            assertEquals(durable, store.commit(begun.token, "complete", candidate))
            assertEquals(1, db.pennantDao().results(durable.token.worldId, durable.token.generationId).size)
            assertThrows(StoreConflict::class.java) { runBlocking { store.commit(begun.token, "stale", candidate) } }
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun manualSlotUsesNewGenerationAndRejectsOldCallbacks(): Unit = runBlocking {
        val name = name(); val db = PennantDatabase.open(context, name)
        try {
            val store = RoomSeasonStore(db)
            val initial = store.create("42", "L1-T01", "create")
            val slotted = store.saveSlot(initial.token, "save-slot")
            val begun = store.commit(slotted.token, "begin", slotted.state.beginNext())
            val final = store.commit(begun.token, "finish", SeasonSession(begun.state).finishCurrent())
            val loaded = store.loadSlot(final.token, "load-slot")
            assertEquals(initial.state, loaded.state)
            assertNotEquals(final.token.generationId, loaded.token.generationId)
            assertEquals(0, db.pennantDao().results(loaded.token.worldId, loaded.token.generationId).size)
            assertThrows(StoreConflict::class.java) { runBlocking { store.commit(final.token, "late", final.state.selectClub("L2-T03")) } }
            assertEquals(loaded, store.read())
        } finally { db.close(); context.deleteDatabase(name) }
    }
}

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

@RunWith(AndroidJUnit4::class)
class RoomBatterProfileStoreTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val club = "L1-T01"
    private fun order() = (listOf(11) + (2..9)).map { "$club-P${it.toString().padStart(2, '0')}" }
    private suspend fun create(store: SeasonStore, format: Int, id: String, old: StoreToken? = null): StoredSeason = when (format) {
        1 -> store.create("42", club, id, old, old != null)
        2 -> store.createLineup("42", club, id, old, old != null)
        else -> store.createBatterProfile("42", club, id, old, old != null)
    }

    @Test fun independentlyConstructedOldV2DatabaseReadsAdvancesAndResavesWithoutAdoptingProfiles(): Unit = runBlocking {
        val name = "independent-v2-${UUID.randomUUID()}.db"
        val path = context.getDatabasePath(name); path.parentFile!!.mkdirs()
        val packed = InstrumentationRegistry.getInstrumentation().context.assets.open("batter-profile/one-game-v2.fixture.bin").use { it.readBytes() }
        assertEquals("6f50ba22f38304a15dcd5925665355d2ced1915dd223fbcd5781a5869c4e377a", hash(packed))
        val raw = GZIPInputStream(packed.inputStream()).use { it.readBytes() }
        assertEquals("85e6d5316017ecccbd05ee2b8c6773e5337c78abdb42ed9ca66bcb21549481a9", hash(raw))
        path.writeBytes(raw)
        var db = PennantDatabase.open(context, name)
        try {
            var store = RoomSeasonStore(db)
            val old = store.read()!!
            assertEquals(StoreToken("golden-v2-world", "golden-v2-generation", 2), old.token)
            assertTrue(old.state.lineupEnabled); assertFalse(old.state.profilesEnabled)
            assertEquals(144, old.state.playerCounts.size); assertEquals(1, old.state.results.size); assertEquals(2, old.state.dayIndex)
            assertEquals("df5bf36fbe28cd27c8008ee8a02f682e42db04d3e80846105e2ebe4f3663ff61", hash(CheckpointCodec.encodeSeason(old.state)))
            val begun = store.commit(old.token, "begin", old.state.beginNext())
            assertNull(begun.state.active!!.profileInput)
            val expected = begun.state.active!!.recover("42").also { it.advanceOnePitch() }.checkpoint()
            val pitched = store.commit(begun.token, "pitch", SeasonSession(begun.state).advanceOnePitch())
            assertEquals(expected, pitched.state.active)
            db.close(); db = PennantDatabase.open(context, name); store = RoomSeasonStore(db)
            assertEquals(pitched, store.read())
            val finished = store.commit(pitched.token, "finish", SeasonSession(pitched.state).finishCurrent())
            val saved = store.saveSlot(finished.token, "save-old-v2")
            val new = store.createBatterProfile("42", club, "new-v3", saved.token, true)
            val loaded = store.loadSlot(new.token, "restore-old-v2")
            assertEquals(finished.state, loaded.state); assertFalse(loaded.state.profilesEnabled)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun everyOrderedPairOfFormatsLoadsTheOriginalSlotAndRecoversItsOwnNextPitch(): Unit = runBlocking {
        for (savedFormat in 1..3) for (workingFormat in 1..3) if (savedFormat != workingFormat) {
            val name = "profile-slots-$savedFormat-$workingFormat-${UUID.randomUUID()}.db"
            var db = PennantDatabase.open(context, name)
            try {
                var store = RoomSeasonStore(db)
                var initial = create(store, savedFormat, "initial")
                if (savedFormat != 1) initial = store.commit(initial.token, "edit", initial.state.applyLineup(club, order()))
                val slot = store.saveSlot(initial.token, "save")
                val working = create(store, workingFormat, "replacement", slot.token)
                assertEquals(workingFormat == 3, working.state.profilesEnabled)
                val loaded = store.loadSlot(working.token, "load")
                assertEquals(initial.state, loaded.state)
                assertNotEquals(slot.token.generationId, loaded.token.generationId)
                val begun = store.commit(loaded.token, "begin", loaded.state.beginNext())
                val expected = begun.state.active!!.recover("42").also { it.advanceOnePitch() }.checkpoint()
                val pitched = store.commit(begun.token, "pitch", SeasonSession(begun.state).advanceOnePitch())
                assertEquals(expected, pitched.state.active)
                db.close(); db = PennantDatabase.open(context, name); store = RoomSeasonStore(db)
                assertEquals(pitched, store.read())
                assertEquals(1, db.openHelper.readableDatabase.version)
            } finally { db.close(); context.deleteDatabase(name) }
        }
    }

    @Test fun staleApplyAndFormatReplacementCannotAlterAProfileWorld(): Unit = runBlocking {
        val name = "profile-cas-${UUID.randomUUID()}.db"; val db = PennantDatabase.open(context, name)
        try {
            val store = RoomSeasonStore(db)
            val initial = create(store, 3, "create")
            assertThrows(IllegalArgumentException::class.java) { runBlocking {
                store.commit(initial.token, "downgrade", SeasonState.initialLineup())
            } }
            var raced = false
            val racing = RoomSeasonStore(db, faults = StoreFaults { point ->
                if (point == StoreFaultPoint.BEFORE_TRANSACTION && !raced) {
                    raced = true
                    runBlocking { store.commit(initial.token, "select", initial.state.selectClub("L2-T06")) }
                }
            })
            assertThrows(StoreConflict::class.java) { runBlocking {
                racing.commit(initial.token, "stale-apply", initial.state.applyLineup(club, order()))
            } }
            val current = store.read()!!
            assertEquals(initial.state.worldProfiles, current.state.worldProfiles)
            assertEquals(initial.state.worldProfileTableVersion, current.state.worldProfileTableVersion)
            assertEquals(initial.state.clubLineups, current.state.clubLineups)
            val coordinator = SeasonCoordinator(store)
            assertThrows(StoreConflict::class.java) { runBlocking { coordinator.applyLineup(initial.token, club, order()) } }
            val selected = coordinator.selectClub(club)
            val edited = coordinator.applyLineup(selected.token, club, order())
            assertEquals(initial.state.worldProfiles, edited.state.worldProfiles)
            assertEquals(selected.state.playerCounts, edited.state.playerCounts)
            assertEquals(selected.state.games, edited.state.games)
            assertNull(edited.state.active)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun aValidTypeSubstitutedForAnotherPersonStillFailsColdReadEvenWithRecomputedHash(): Unit = runBlocking {
        val name = "profile-corrupt-${UUID.randomUUID()}.db"; val db = PennantDatabase.open(context, name)
        try {
            val store = RoomSeasonStore(db)
            val initial = create(store, 3, "create")
            val bytes = CheckpointCodec.encodeSeason(initial.state)
            val from = "contact".toByteArray(); val to = "slugger".toByteArray()
            val index = (0..bytes.size - from.size).first { offset -> from.indices.all { bytes[offset + it] == from[it] } }
            to.copyInto(bytes, index)
            db.openHelper.writableDatabase.execSQL("UPDATE working_state SET payload=?, payloadHash=? WHERE id=1", arrayOf(bytes, hash(bytes)))
            assertThrows(StoreCorruption::class.java) { runBlocking { RoomSeasonStore(db).read() } }
            assertArrayEquals(bytes, db.pennantDao().head()!!.payload)
            assertEquals(hash(bytes), db.pennantDao().head()!!.payloadHash)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun rejectedProfileApplyNeverFinalizesAnAlreadyDurableCompletedCheckpoint(): Unit = runBlocking {
        val name = "profile-isolated-${UUID.randomUUID()}.db"; val db = PennantDatabase.open(context, name)
        try {
            val store = RoomSeasonStore(db)
            val initial = create(store, 3, "create")
            val begun = store.commit(initial.token, "begin", initial.state.beginNext())
            val match = begun.state.active!!.recover("42"); match.finish()
            val pending = store.commit(begun.token, "completed-checkpoint", begun.state.checkpoint(match.checkpoint()))
            val bytes = CheckpointCodec.encodeSeason(pending.state)
            val coordinator = SeasonCoordinator(store)
            assertThrows(StoreConflict::class.java) { runBlocking { coordinator.applyLineup(initial.token, club, order()) } }
            assertThrows(IllegalStateException::class.java) { runBlocking { coordinator.applyLineup(pending.token, club, order()) } }
            assertEquals(pending, store.read()); assertArrayEquals(bytes, db.pennantDao().head()!!.payload)
            assertEquals(0, db.pennantDao().results(pending.token.worldId, pending.token.generationId).size)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}

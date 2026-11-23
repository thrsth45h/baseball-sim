package com.example.baseballsim.data

import android.content.Context
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.baseballsim.completedV3FrameHash
import com.example.baseballsim.installCompletedV3Fixture
import com.example.baseballsim.replaceFixture
import com.example.baseballsim.season.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomSeasonHistoryTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private suspend fun fixture(test: suspend (PennantDatabase, RoomSeasonStore) -> Unit) {
        val name = "annual-${UUID.randomUUID()}.db"
        installCompletedV3Fixture(context, name)
        val db = PennantDatabase.open(context, name)
        try { test(db, RoomSeasonStore(db)) } finally { db.close(); context.deleteDatabase(name) }
    }

    private suspend fun count(db: PennantDatabase, table: String): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { check(it.moveToFirst()); it.getInt(0) }

    @Test fun migratesV3AndRollsForwardExactlyOnceAfterRollbackOrLostAcknowledgement(): Unit = runBlocking {
        fixture { db, store ->
            val before = store.read()!!
            val head = db.pennantDao().head()!!
            val slot = db.pennantDao().slot()!!
            assertEquals(2, db.openHelper.readableDatabase.version)
            assertEquals(StoreToken("old-v3-season42-world", "old-v3-season42-generation", 1440), before.token)
            assertEquals(1L, before.timeline!!.year)
            assertEquals(completedV3FrameHash, hash(head.payload))
            assertEquals(completedV3FrameHash, hash(slot.payload))
            assertArrayEquals(head.payload, CheckpointCodec.encodeSeason(before.state))
            assertEquals(720, count(db, "completed_results"))
            val original = SeasonYear.completed(1, before.state)
            for (point in listOf(StoreFaultPoint.BEFORE_TRANSACTION, StoreFaultPoint.AFTER_RESULTS, StoreFaultPoint.AFTER_HEAD)) {
                val failing = RoomSeasonStore(db, faults = StoreFaults { if (it == point) error("Injected $point") })
                assertThrows(IllegalStateException::class.java) { runBlocking { failing.nextYear(before.token, "fail-${point.name}") } }
                assertEquals(before, store.read())
                assertEquals(720, count(db, "completed_results"))
                assertEquals(0, count(db, "annual_archives"))
                assertEquals(0, count(db, "annual_players"))
            }
            val loseAck = RoomSeasonStore(db, faults = StoreFaults { if (it == StoreFaultPoint.AFTER_COMMIT) error("Lost response") })
            val next = SeasonCoordinator(loseAck, operationId = { "year-once" }).nextYear(before.token)
            assertEquals(next, store.nextYear(before.token, "year-once"))
            assertThrows(StoreConflict::class.java) { runBlocking { store.nextYear(before.token, "stale") } }
            assertEquals(next, store.read())
            assertEquals(before.token.worldId, next.token.worldId)
            assertNotEquals(before.token.generationId, next.token.generationId)
            assertEquals(before.token.revision + 1, next.token.revision)
            assertEquals(1, count(db, "annual_archives"))
            assertEquals(12, count(db, "annual_clubs"))
            assertEquals(144, count(db, "annual_players"))
            assertEquals(0, count(db, "completed_results"))
            assertEquals(original, store.historyYear(next.token, 1))
            assertEquals(listOf(1L), store.historyYears(next.token))
            assertEquals(before.state.worldProfiles, next.state.worldProfiles)
            assertEquals(before.state.clubLineups, next.state.clubLineups)
            assertEquals(before.state.selectedClubId, next.state.selectedClubId)
            assertTrue(next.state.playerCounts.all { it.counts.completedPlateAppearances == 0L })

            // The untouched schema 1 slot still reloads the complete original 720-game ledger.
            val restored = store.loadSlot(next.token, "load-original")
            assertArrayEquals(head.payload, CheckpointCodec.encodeSeason(restored.state))
            assertEquals(1L, restored.timeline!!.year)
            val repeated = store.nextYear(restored.token, "roll-original-again")
            assertEquals(next.timeline, repeated.timeline)
            assertEquals(1, count(db, "annual_archives"))
            assertEquals(next.state, repeated.state)
            assertThrows(IllegalStateException::class.java) { runBlocking { store.nextYear(repeated.token, "incomplete") } }
        }
    }

    @Test fun branchingAfterLoadingDifferentOrdersKeepsTheirOwnPriorYearAndDetectsForgedAncestors(): Unit = runBlocking {
        fixture { db, store ->
            val completed = store.read()!!
            val club = "L1-T01"
            val prior = completed.state
            val finalOwnedIndex = prior.results.indexOfLast { club in listOf(it.game.fixture.awayClubId, it.game.fixture.homeClubId) }
            val prefix = SeasonState.restore("42", club, prior.games[finalOwnedIndex].fixture.dayIndex,
                prior.results.take(finalOwnedIndex), null, prior.versions, prior.clubLineups, prior.worldProfileTableVersion, prior.worldProfiles)
            val before = replaceFixture(db, completed, prefix, TimelineColumns())
            val slot = store.saveSlot(before, "prefix-slot")
            val coordinator = SeasonCoordinator(store)
            val branchA = coordinator.nextYear(coordinator.finishSeason().token)
            val historyA = store.historyYear(branchA.token, 1)
            val loaded = store.loadSlot(branchA.token, "load-prefix")
            assertEquals(slot.state, loaded.state)
            val bench = prior.playerIds(club).last()
            coordinator.applyLineup(loaded.token, club, listOf(bench) + loaded.state.lineup(club).drop(1))
            val branchB = coordinator.nextYear(coordinator.finishSeason().token)
            val historyB = store.historyYear(branchB.token, 1)
            assertNotEquals(historyA, historyB)
            assertNotEquals(branchA.timeline!!.previous, branchB.timeline!!.previous)
            assertEquals(2, count(db, "annual_archives"))
            assertEquals(listOf(1L), store.historyYears(branchB.token))
            assertEquals(0L, historyA.players.single { it.playerId == bench }.counts.completedPlateAppearances)
            assertTrue(historyB.players.single { it.playerId == bench }.counts.completedPlateAppearances > 0)

            val savedB = store.saveSlot(branchB.token, "branch-b-slot")
            val replacement = store.createBatterProfile("7", "L2-T01", "replace-world", savedB.token, true)
            assertNotEquals(savedB.token.worldId, replacement.token.worldId)
            assertEquals(SeasonTimeline("7", 1, null), replacement.timeline)
            assertEquals(emptyList<Long>(), store.historyYears(replacement.token))
            val restored = store.loadSlot(replacement.token, "load-branch-b")
            assertEquals(savedB.timeline, restored.timeline)
            assertEquals(historyB, store.historyYear(restored.token, 1))
            assertThrows(StoreConflict::class.java) { runBlocking { store.historyYear(branchA.token, 1) } }

            // Use two more synthetic archives to test an intermediate header, beyond the direct anchor.
            val world = restored.token.worldId
            val y2 = AnnualArchive.build(world, "42", branchB.timeline!!.previous, SeasonYear(2, historyB.standings, historyB.players))
            val y3 = AnnualArchive.build(world, "42", y2.header.id, SeasonYear(3, historyB.standings, historyB.players))
            db.withTransaction { insert(db, y2); insert(db, y3) }
            val current = replaceFixture(db, restored, SeasonState.initialBatterProfile(SeasonYearSeeds.start("42", 4), club),
                TimelineColumns.create(world, SeasonTimeline("42", 4, y3.header.id)))
            assertEquals(listOf(3L, 2L, 1L), store.historyYears(current))
            assertEquals(historyB, store.historyYear(current, 1))
            db.openHelper.writableDatabase.execSQL("UPDATE annual_archives SET parent=? WHERE id=?",
                arrayOf(branchA.timeline!!.previous, y2.header.id))
            assertEquals(current, store.read()!!.token)
            assertThrows(StoreCorruption::class.java) { runBlocking { store.historyYears(current) } }
            assertThrows(StoreCorruption::class.java) { runBlocking { store.historyYear(current, 1) } }
            assertEquals(branchA.timeline!!.previous, db.pennantDao().archive(y2.header.id)!!.parent)
            assertEquals(current, store.read()!!.token)
        }
    }

    @Test fun aHundredArchivesKeepTheCurrentRowBoundedAndRejectInvalidHistoryOrSlotMetadata(): Unit = runBlocking {
        fixture { db, store ->
            val completed = store.read()!!
            val canonical = SeasonYear.completed(1, completed.state)
            val world = completed.token.worldId
            var parent: String? = null
            db.withTransaction {
                for (year in 1L..100L) {
                    val archive = AnnualArchive.build(world, "42", parent, SeasonYear(year, canonical.standings, canonical.players))
                    insert(db, archive)
                    parent = archive.header.id
                }
            }
            val frame = SeasonState.initialBatterProfile(SeasonYearSeeds.start("42", 101))
            val current = replaceFixture(db, completed, frame, TimelineColumns.create(world, SeasonTimeline("42", 101, parent)))
            assertEquals((100L downTo 1L).toList(), store.historyYears(current))
            assertEquals(SeasonYear(37, canonical.standings, canonical.players), store.historyYear(current, 37))
            assertArrayEquals(CheckpointCodec.encodeSeason(frame), db.pennantDao().head()!!.payload)
            assertEquals(0, count(db, "completed_results"))
            val saved = store.saveSlot(current, "save-long-history")
            db.openHelper.writableDatabase.execSQL("UPDATE manual_slot SET timeline_year=102")
            assertThrows(StoreCorruption::class.java) { runBlocking { store.loadSlot(saved.token, "corrupt-slot") } }
            assertEquals(saved, store.read())
            db.openHelper.writableDatabase.execSQL("UPDATE annual_clubs SET runsFor=? WHERE archiveId=? AND clubId='L1-T01'",
                arrayOf(Long.MAX_VALUE, parent))
            assertThrows(StoreCorruption::class.java) { runBlocking { store.read() } }
            assertEquals(parent, db.pennantDao().head()!!.timeline.previous)
        }
    }

    private suspend fun insert(db: PennantDatabase, archive: AnnualArchive) {
        val dao = db.pennantDao()
        dao.insertArchive(archive.header)
        dao.insertAnnualClubs(archive.clubs)
        dao.insertAnnualPlayers(archive.players)
    }

}

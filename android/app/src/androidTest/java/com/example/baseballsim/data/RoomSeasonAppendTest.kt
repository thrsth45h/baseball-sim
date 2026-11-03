package com.example.baseballsim.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.baseballsim.season.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RoomSeasonAppendTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun operation() = UUID.randomUUID().toString()
    private fun assertSameRows(expected: List<ResultEntity>, actual: List<ResultEntity>) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (before, after) ->
            assertEquals(before.copy(payload = after.payload), after)
            assertArrayEquals(before.payload, after.payload)
        }
    }

    @Test fun zeroAndOneResultUpdatesPreserveEveryExistingRowByte(): Unit = runBlocking {
        val name = "append-test-${UUID.randomUUID()}.db"
        val db = PennantDatabase.open(context, name)
        try {
            val store = RoomSeasonStore(db)
            var saved = store.create("42", "L1-T01", operation())
            repeat(2) { completed ->
                val prefix = db.pennantDao().results(saved.token.worldId, saved.token.generationId)
                assertEquals(completed, prefix.size)
                saved = store.commit(saved.token, operation(), saved.state.beginNext())
                assertSameRows(prefix, db.pennantDao().results(saved.token.worldId, saved.token.generationId))
                val candidate = SeasonSession(saved.state).finishCurrent()
                saved = store.commit(saved.token, operation(), candidate)
                val rows = db.pennantDao().results(saved.token.worldId, saved.token.generationId)
                assertEquals(completed + 1, rows.size)
                assertSameRows(prefix, rows.take(prefix.size))
                val bytes = CheckpointCodec.encodeSummary(candidate.results.last())
                assertArrayEquals(bytes, rows.last().payload)
                assertEquals(hash(bytes), rows.last().payloadHash)
            }
            val prefix = db.pennantDao().results(saved.token.worldId, saved.token.generationId)
            saved = store.commit(saved.token, operation(), saved.state.selectClub("L2-T03"))
            assertSameRows(prefix, db.pennantDao().results(saved.token.worldId, saved.token.generationId))
            assertEquals(saved, store.read())
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun alteredExistingPrefixIsRejectedBeforeAnyNewRowIsWritten(): Unit = runBlocking {
        val name = "append-test-${UUID.randomUUID()}.db"
        val db = PennantDatabase.open(context, name)
        try {
            val store = RoomSeasonStore(db)
            val initial = store.create("42", "L1-T01", operation())
            val marked = store.commit(initial.token, operation(), initial.state.beginNext())
            val saved = store.commit(marked.token, operation(), SeasonSession(marked.state).finishCurrent())
            val rows = db.pennantDao().results(saved.token.worldId, saved.token.generationId)
            val original = saved.state.results.single()
            val people = original.batters.toMutableList()
            val first = people.first()
            people[0] = first.copy(counts = first.counts.copy(
                completedPlateAppearances = first.counts.completedPlateAppearances + 1,
                walkEvents = first.counts.walkEvents + 1))
            val altered = GameSummary(original.game, original.maxPitches, original.status, original.outcome,
                original.reason, original.score, original.lineScore, original.terminalDelta, original.pitchesAccepted, people)
            val forged = SeasonState.restore(saved.state.masterSeed, saved.state.selectedClubId, saved.state.dayIndex,
                listOf(altered), null)
            assertThrows(IllegalArgumentException::class.java) { runBlocking { store.commit(saved.token, operation(), forged) } }
            assertEquals(saved, store.read())
            assertSameRows(rows, db.pennantDao().results(saved.token.worldId, saved.token.generationId))
        } finally { db.close(); context.deleteDatabase(name) }
    }
}

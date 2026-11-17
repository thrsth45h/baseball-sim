package com.example.baseballsim

import android.content.Context
import android.database.Cursor
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.baseballsim.data.*
import com.example.baseballsim.season.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/** Creates a schema-1 fixture with the frozen pre-performance writer, not an old-APK upgrade test. */
@RunWith(AndroidJUnit4::class)
class PennantBrowseStoreTest {
    @get:Rule val compose = createComposeRule()
    private class Counted(private val delegate: PennantActions) : PennantActions {
        val reads = AtomicInteger(); val slots = AtomicInteger(); val commands = AtomicInteger()
        override suspend fun read(): StoredSeason? { reads.incrementAndGet(); return delegate.read() }
        override suspend fun hasSlot(): Boolean { slots.incrementAndGet(); return delegate.hasSlot() }
        override suspend fun execute(command: PennantCommand, seed: String, club: String, replacement: Boolean,
            stop: () -> Boolean, progress: suspend (StoredSeason) -> Unit): StoredSeason {
            commands.incrementAndGet(); return delegate.execute(command, seed, club, replacement, stop, progress)
        }
        fun log() = listOf(reads.get(), slots.get(), commands.get())
    }
    private fun click(tag: String) {
        val node = compose.onNodeWithTag(tag)
        if (tag !in listOf(PennantTags.BACK, PennantTags.STOP, PennantTags.RELOAD)) node.performScrollTo()
        node.performClick()
    }
    private fun home() {
        while (compose.onAllNodesWithTag(PennantTags.BACK).fetchSemanticsNodes().isNotEmpty()) click(PennantTags.BACK)
    }
    private fun rows(db: PennantDatabase): Map<String, List<List<String>>> = listOf("working_state", "completed_results", "manual_slot",
        "operation_receipts", "blocked_diagnostics", "generation_registry").associateWith { table ->
        db.openHelper.readableDatabase.query("SELECT * FROM $table").use { cursor ->
            buildList {
                while (cursor.moveToNext()) add((0 until cursor.columnCount).map { column -> when (cursor.getType(column)) {
                    Cursor.FIELD_TYPE_NULL -> "NULL"
                    Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(column).joinToString("") { "%02x".format(it) }
                    else -> cursor.getString(column)
                } })
            }.sortedBy { it.joinToString("|") }
        }
    }
    @Test fun oldSchema1FixtureAndPausedCheckpointStayByteIdenticalWhileBrowsingAll108Players(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "club-browser-compat-${UUID.randomUUID()}.db"
        var db = PennantDatabase.open(context, name)
        val owner = ViewModelStore()
        try {
            val old = BaselineRoomSeasonStore(db)
            val created = old.create("42", "L1-T01", "old-create", null, false)
            val begun = old.commit(created.token, "old-begin", created.state.beginNext())
            val completed = old.commit(begun.token, "old-complete", SeasonSession(begun.state).finishCurrent())
            val slotted = old.saveSlot(completed.token, "old-slot")
            val next = old.commit(slotted.token, "old-next", slotted.state.beginNext())
            val paused = old.commit(next.token, "old-paused", SeasonSession(next.state).advancePlateAppearance())
            val originalRows = rows(db)
            db.close(); db = PennantDatabase.open(context, name)
            val store = RoomSeasonStore(db)
            assertEquals(paused, store.read()); assertEquals(originalRows, rows(db))
            assertEquals(1, db.openHelper.readableDatabase.version)
            val actions = Counted(DurablePennantActions(SeasonCoordinator(store)))
            lateinit var vm: PennantViewModel
            compose.runOnUiThread { vm = PennantViewModel(actions); owner.put("browse", vm) }
            val restoration = StateRestorationTester(compose)
            restoration.setContent { BaseballTheme { PennantRoute(vm) } }
            compose.waitUntil(90_000) { vm.state.value.loaded && !vm.state.value.busy }
            assertNull(vm.state.value.error)
            val published = vm.state.value.display
            val before = rows(db); val calls = actions.log()
            val observed = JSONArray()
            fun text(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text }
            click(PennantTags.NAV_PLAYERS)
            PennantSchedule.clubs.forEach { club ->
                click(PennantTags.BROWSE_CLUB)
                compose.onNodeWithText(club.displayName, useUnmergedTree = true).performScrollTo().performClick()
                club.players.forEach { player ->
                    click(PennantTags.player(player.id))
                    compose.onNodeWithTag(PennantTags.PLAYER_DETAIL).assertExists()
                    compose.onNodeWithTag("player_id").assertTextEquals(player.id)
                    compose.onNodeWithTag("player_club").assertTextEquals(club.displayName)
                    compose.onNodeWithTag("player_slot").assertTextEquals("固定打順 ${player.slot}")
                    val expected = paused.state.playerCounts.single { it.playerId == player.id }.counts
                    val values = listOf("player_completed_pa" to expected.completedPlateAppearances, "player_safe_hits" to expected.safeHitEvents,
                        "player_walks" to expected.walkEvents, "player_strikeouts" to expected.strikeoutEvents, "player_home_runs" to expected.homeRunEvents)
                    val sample = JSONObject().put("player_id", text("player_id")).put("club", text("player_club")).put("slot", text("player_slot"))
                    values.forEach { (tag, count) ->
                        compose.onNodeWithTag(tag).assertTextEquals(count.toString())
                        sample.put(tag, text(tag))
                    }
                    observed.put(sample)
                    click(PennantTags.BACK)
                }
            }
            File(pennantTestOutputDirectory(), "player-detail-observed-108.json").writeText(JSONObject()
                .put("schema", "club-home-observed-ui-v1").put("source", "Compose semantic Text nodes, not expected-state copies")
                .put("fixture", "frozen schema-1 writer, one completed game, next paused match").put("players", observed).toString(2))
            assertEquals(108, observed.length())
            click(PennantTags.player("L2-T06-P09"))
            restoration.emulateSavedInstanceStateRestore()
            compose.onNodeWithTag(PennantTags.PLAYER_DETAIL).assertExists()
            compose.onNodeWithText("L2-T06-P09", substring = true).assertExists()
            home()
            for (tag in listOf(PennantTags.NAV_LINEUP, PennantTags.NAV_SCHEDULE, PennantTags.NAV_STANDINGS, PennantTags.NAV_RECORDS, PennantTags.NAV_MATCH, PennantTags.NAV_SETTINGS)) {
                click(tag); home()
            }
            assertSame(published, vm.state.value.display)
            assertEquals(calls, actions.log())
            assertEquals(before, rows(db)); assertEquals(originalRows, rows(db))
            val unchanged = store.read()!!
            assertEquals(paused, unchanged)
            assertArrayEquals(CheckpointCodec.encodeSeason(paused.state), CheckpointCodec.encodeSeason(unchanged.state))
            assertEquals(paused.state.active, unchanged.state.active)
            val expectedNext = paused.state.active!!.recover("42").also { it.advanceOnePitch() }.checkpoint()
            compose.runOnUiThread { vm.execute(PennantCommand.PITCH) }
            compose.waitUntil(90_000) { !vm.state.value.busy }
            assertNull(vm.state.value.error); assertEquals(expectedNext, vm.state.value.season!!.active)
            assertEquals(paused.token.revision + 1, vm.state.value.display!!.stored.token.revision)
            val expectedFinished = SeasonSession(vm.state.value.season!!).finishCurrent()
            compose.runOnUiThread { vm.execute(PennantCommand.GAME) }
            compose.waitUntil(90_000) { !vm.state.value.busy }
            assertEquals(expectedFinished, vm.state.value.season)
            compose.runOnUiThread { vm.execute(PennantCommand.SAVE) }
            compose.waitUntil(90_000) { !vm.state.value.busy }
            assertNull(vm.state.value.error)
            assertArrayEquals(CheckpointCodec.encodeSeason(expectedFinished), db.pennantDao().slot()!!.payload)
        } finally {
            compose.runOnUiThread { owner.clear() }
            db.close(); context.deleteDatabase(name)
        }
    }
}

package com.example.baseballsim

import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.baseballsim.season.*
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Separate CI device job: real UI/durable transactions, not an in-memory prefix fixture. */
@RunWith(AndroidJUnit4::class)
class PennantLineupFullSeasonTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun ready(): PennantViewModel {
        compose.waitUntil(120_000) { val s = compose.activity.pennantViewModel.state.value; s.loaded && !s.busy }
        return compose.activity.pennantViewModel.also { assertNull(it.state.value.error) }
    }
    private fun click(tag: String) {
        val node = compose.onNodeWithTag(tag)
        if (tag != PennantTags.BACK) node.performScrollTo()
        node.performClick()
    }
    private fun home() { while (compose.onAllNodesWithTag(PennantTags.BACK).fetchSemanticsNodes().isNotEmpty()) click(PennantTags.BACK) }
    private fun setOrder(numbers: List<Int>) {
        home(); click(PennantTags.NAV_LINEUP)
        // Use the actual control operations: swap an existing starter through adjacent moves;
        // reserve replacement is selected from the popup. Each change remains an unsaved draft.
        val vm = compose.activity.pennantViewModel
        val working = vm.state.value.season!!.lineup("L1-T01").toMutableList()
        numbers.map { "L1-T01-P${it.toString().padStart(2, '0')}" }.forEachIndexed { target, id ->
            var from = working.indexOf(id)
            if (from < 0) {
                click("lineup_replace_${target + 1}")
                compose.onNodeWithTag("lineup_reserve_$id").performClick()
                working[target] = id
            } else while (from > target) {
                click("lineup_up_${from + 1}")
                val moved = working.removeAt(from); working.add(from - 1, moved); from--
            }
        }
        click(PennantTags.APPLY_LINEUP); ready()
        assertEquals(numbers.map { "L1-T01-P${it.toString().padStart(2, '0')}" }, vm.state.value.season!!.lineup("L1-T01"))
    }
    @Test fun editedParticipantsComplete720RealGamesAndKeepAll144PersonRecords() {
        compose.onNodeWithTag("mode_pennant").performClick(); val vm = ready()
        // UI CREATE now makes v3. Preserve this old v2 acceptance case explicitly.
        runBlocking { createLineupTargetWorld() }
        compose.runOnUiThread { vm.reload() }; ready()
        assertTrue(vm.state.value.season!!.lineupEnabled); assertFalse(vm.state.value.season!!.profilesEnabled)
        fun ownGame() { home(); click(PennantTags.OPEN); ready(); click(PennantTags.FINISH_GAME); ready() }
        ownGame()
        val first = CheckpointCodec.encodeSummary(vm.state.value.season!!.results.first())
        setOrder(listOf(9, 2, 3, 4, 5, 6, 7, 8, 1)); ownGame()
        setOrder(listOf(9, 2, 3, 4, 5, 6, 7, 8, 10)); ownGame()
        setOrder(listOf(10, 2, 3, 4, 5, 6, 7, 8, 1))
        val output = pennantTestOutputDirectory()
        val progress = File(output, "lineup-real-season-42-progress.csv")
        progress.writeText("elapsed_ms,completed_games,busy\n")
        val started = SystemClock.elapsedRealtime(); var lastSample = 0L
        home(); click(PennantTags.SEASON)
        compose.waitUntil(1_200_000) {
            val now = SystemClock.elapsedRealtime(); val current = vm.state.value
            if (now - lastSample >= 10_000) { progress.appendText("${now - started},${current.season!!.results.size},${current.busy}\n"); lastSample = now }
            !current.busy
        }
        assertNull(vm.state.value.error)
        val state = vm.state.value.season!!
        assertEquals(720, state.results.size); assertEquals(141, state.dayIndex); assertNull(state.active)
        assertEquals(144, state.playerCounts.size); assertTrue(state.standings.all { it.totals.played == 120L })
        assertArrayEquals(first, CheckpointCodec.encodeSummary(state.results.first()))
        val frame = CheckpointCodec.encodeSeason(state)
        File(output, "lineup-real-season-42.bin").writeBytes(frame)
        fun counts(c: com.example.baseballsim.core.EventCounts) = JSONObject()
            .put("completedPlateAppearances", c.completedPlateAppearances).put("safeHitEvents", c.safeHitEvents)
            .put("walkEvents", c.walkEvents).put("strikeoutEvents", c.strikeoutEvents).put("homeRunEvents", c.homeRunEvents)
        val report = JSONObject().put("schema", "lineup-real-transactions-v1").put("master_seed", "42").put("scenario", "order_bench")
            .put("completed_games", 720).put("day_index", 141).put("elapsed_ms_bulk_remaining_707", SystemClock.elapsedRealtime() - started)
            .put("prefix_games_also_real_transactions", 13).put("frame_bytes", frame.size)
            .put("frame_sha256", MessageDigest.getInstance("SHA-256").digest(frame).joinToString("") { "%02x".format(it) })
            .put("players", JSONObject().apply { state.playerCounts.forEach { put(it.playerId, counts(it.counts)) } })
            .put("games", JSONArray().apply { state.results.forEach { game -> put(JSONObject()
                .put("game_id", game.game.fixture.gameId).put("game_seed", game.game.seed)
                .put("away_lineup", JSONArray(game.lineup.away)).put("home_lineup", JSONArray(game.lineup.home))
                .put("score", JSONObject().put("away", game.score.away).put("home", game.score.home))
                .put("participants", JSONArray().apply { game.batters.forEach { row -> put(JSONObject().put("player_id", row.playerId)
                    .put("club_id", row.clubId).put("slot", row.slot).put("side", row.side).put("counts", counts(row.counts))) } })) } })
        File(output, "lineup-real-season-42.json").writeText(report.toString(2))
        compose.pennantScreenshot("lineup-season-completed.png")
        val beforeBrowsing = vm.state.value.display
        compose.observeAllPlayerDetails(state, "lineup-player-detail-observed-144.json")
        assertSame(beforeBrowsing, vm.state.value.display)
        compose.runOnUiThread { vm.reload() }; ready(); assertEquals(state, vm.state.value.season)
    }
}

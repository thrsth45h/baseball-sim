package com.example.baseballsim

import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.baseballsim.season.*
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Separate CI device job: real UI/durable transactions, not an in-memory prefix fixture. */
@RunWith(AndroidJUnit4::class)
class PennantBatterProfileFullSeasonTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun ready(): PennantViewModel {
        compose.waitUntil(120_000) { val s = compose.activity.pennantViewModel.state.value; s.loaded && !s.busy }
        return compose.activity.pennantViewModel.also { assertNull(it.state.value.error) }
    }
    private fun click(tag: String) = compose.clickPennant(tag)
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
    @Test fun profileParticipantsComplete720RealGamesAndKeepAll144PersonTypesAndRecords() {
        compose.enterPennantMode(); val vm = ready()
        val exists = vm.state.value.season != null
        if (exists) { home(); click(PennantTags.NAV_SETTINGS) }
        compose.onNodeWithTag(PennantTags.SEED).performScrollTo().performTextReplacement("42")
        click(PennantTags.CREATE)
        if (exists) compose.onNodeWithTag(PennantTags.CONFIRM_CREATE).performClick()
        ready(); assertTrue(vm.state.value.season!!.lineupEnabled); assertTrue(vm.state.value.season!!.profilesEnabled)
        fun ownGame() { home(); compose.openNextOwnGame(); ready(); click(PennantTags.FINISH_GAME); ready() }
        ownGame()
        val first = CheckpointCodec.encodeSummary(vm.state.value.season!!.results.first())
        setOrder(listOf(9, 2, 3, 4, 5, 6, 7, 8, 1)); ownGame()
        setOrder(listOf(9, 2, 3, 4, 5, 6, 7, 8, 10)); ownGame()
        setOrder(listOf(10, 2, 3, 4, 5, 6, 7, 8, 1))
        val prefixGames = vm.state.value.season!!.results.size
        assertEquals(13, prefixGames)
        val output = pennantTestOutputDirectory()
        val progress = File(output, "batter-profile-real-season-42-progress.csv")
        progress.writeText("elapsed_ms,completed_games,busy\n")
        val started = SystemClock.elapsedRealtime()
        var lastSample = -10_000L
        fun recordProgress() {
            val current = vm.state.value
            val elapsed = SystemClock.elapsedRealtime() - started
            val sample = "$elapsed,${current.season!!.results.size},${current.busy}"
            progress.appendText("$sample\n")
            Log.i("BatterProfileSeason", "elapsed_ms,completed_games,busy=$sample")
            lastSample = elapsed
        }
        home(); compose.runOnUiThread { vm.execute(PennantCommand.SEASON) }
        try {
            // The disposable emulator's previous 20-minute allowance expired during this run.
            compose.waitUntil(1_920_000) {
                if (SystemClock.elapsedRealtime() - started - lastSample >= 10_000) recordProgress()
                !vm.state.value.busy
            }
        } finally {
            recordProgress()
        }
        val bulkElapsed = SystemClock.elapsedRealtime() - started
        assertNull(vm.state.value.error)
        val state = vm.state.value.season!!
        assertEquals(720, state.results.size); assertEquals(141, state.dayIndex); assertNull(state.active)
        assertEquals(144, state.playerCounts.size); assertTrue(state.standings.all { it.totals.played == 120L })
        assertArrayEquals(first, CheckpointCodec.encodeSummary(state.results.first()))
        val frame = CheckpointCodec.encodeSeason(state)
        File(output, "batter-profile-real-season-42.bin").writeBytes(frame)
        fun counts(c: com.example.baseballsim.core.EventCounts) = JSONObject()
            .put("completedPlateAppearances", c.completedPlateAppearances).put("safeHitEvents", c.safeHitEvents)
            .put("walkEvents", c.walkEvents).put("strikeoutEvents", c.strikeoutEvents).put("homeRunEvents", c.homeRunEvents)
        val report = JSONObject().put("schema", "batter-profile-real-transactions-v1").put("master_seed", "42").put("scenario", "order_bench")
            .put("completed_games", 720).put("day_index", 141).put("elapsed_ms_bulk_remaining_707", bulkElapsed)
            .put("prefix_games_also_real_transactions", prefixGames).put("frame_bytes", frame.size)
            .put("frame_sha256", MessageDigest.getInstance("SHA-256").digest(frame).joinToString("") { "%02x".format(it) })
            .put("players", JSONObject().apply { state.playerCounts.forEach { put(it.playerId, counts(it.counts)) } })
            .put("games", JSONArray().apply { state.results.forEach { game -> put(JSONObject()
                .put("game_id", game.game.fixture.gameId).put("game_seed", game.game.seed)
                .put("away_lineup", JSONArray(game.lineup.away)).put("home_lineup", JSONArray(game.lineup.home))
                .put("away_profiles", JSONArray(game.profileInput!!.away.map { it.profileId }))
                .put("home_profiles", JSONArray(game.profileInput!!.home.map { it.profileId }))
                .put("score", JSONObject().put("away", game.score.away).put("home", game.score.home))
                .put("participants", JSONArray().apply { game.batters.forEach { row -> put(JSONObject().put("player_id", row.playerId)
                    .put("club_id", row.clubId).put("slot", row.slot).put("side", row.side).put("counts", counts(row.counts))) } })) } })
        compose.pennantScreenshot("batter-profile-season-completed.png")
        val beforeBrowsing = vm.state.value.display
        compose.observeAllPlayerDetails(state, "batter-profile-player-detail-observed-144.json")
        assertSame(beforeBrowsing, vm.state.value.display)
        compose.runOnUiThread { vm.reload() }; ready(); assertEquals(state, vm.state.value.season)
        val beforeSave = vm.state.value.display!!.stored.token
        home(); click(PennantTags.NAV_SETTINGS); click(PennantTags.SAVE); ready()
        val saved = vm.state.value.display!!.stored.token
        assertEquals(beforeSave.copy(revision = beforeSave.revision + 1), saved)
        click(PennantTags.LOAD); compose.onNodeWithTag(PennantTags.CONFIRM_LOAD).performClick(); ready()
        val loaded = vm.state.value.display!!.stored
        assertEquals(saved.worldId, loaded.token.worldId)
        assertNotEquals(saved.generationId, loaded.token.generationId)
        assertEquals(saved.revision + 1, loaded.token.revision)
        assertEquals(state, loaded.state)
        assertArrayEquals(frame, CheckpointCodec.encodeSeason(loaded.state))
        report.put("completed_snapshot_manual_slot_roundtrip", true)
        File(output, "batter-profile-real-season-42.json").writeText(report.toString(2))
    }
}

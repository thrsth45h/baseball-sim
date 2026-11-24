package com.example.baseballsim

import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.baseballsim.data.PennantDatabase
import com.example.baseballsim.data.hash
import com.example.baseballsim.season.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File

/** Isolated device job: real Year 2 UI, game writes, year rollover and slot round-trip. */
@RunWith(AndroidJUnit4::class)
class PennantAnnualSeasonTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val setup: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            installCompletedV3Fixture(InstrumentationRegistry.getInstrumentation().targetContext, PennantDatabase.FILE_NAME)
        }
    }).around(compose)
    private val vm get() = compose.activity.pennantViewModel
    private fun ready() {
        compose.waitUntil(120_000) { vm.state.value.loaded && !vm.state.value.busy }
        assertNull(vm.state.value.error)
    }
    private fun click(tag: String) = compose.clickPennant(tag)
    private fun back() = compose.onNodeWithTag(PennantTags.BACK).performClick()
    private fun stored() = vm.state.value.display!!.stored

    @Test fun completesSecondYearAndBrowsesTwoSeasonsAfterStartingThird() {
        compose.enterPennantMode()
        ready()
        val first = stored()
        assertEquals(1L, first.timeline!!.year)
        assertEquals(720, first.state.results.size)
        assertEquals(completedV3FrameHash, hash(CheckpointCodec.encodeSeason(first.state)))
        compose.onNodeWithTag(PennantTags.VISIBLE_DAY).assertTextEquals("2026年8月12日（水）")
        click(PennantTags.NEXT_YEAR)
        compose.pennantScreenshot("annual-confirm.png")
        compose.onNodeWithText("戻る").performClick()
        assertEquals(first, stored())
        click(PennantTags.NEXT_YEAR)
        compose.onNodeWithTag(PennantTags.CONFIRM_NEXT_YEAR).performClick()
        compose.runOnUiThread {
            repeat(8) { vm.execute(PennantCommand.NEXT_YEAR, confirmationToken = first.token) }
        }
        ready()
        val second = stored()
        assertEquals(2L, second.timeline!!.year)
        assertEquals("42", second.timeline!!.rootSeed)
        assertEquals(first.state.clubLineups, second.state.clubLineups)
        assertEquals(first.state.worldProfiles, second.state.worldProfiles)
        assertEquals(first.state.selectedClubId, second.state.selectedClubId)
        assertEquals(first.token.revision + 1, second.token.revision)
        assertTrue(second.state.results.isEmpty())
        assertTrue(second.state.playerCounts.all { it.counts.completedPlateAppearances == 0L })
        compose.onNodeWithTag(PennantTags.VISIBLE_DAY).assertTextEquals("2027年3月26日（金）")

        val output = pennantTestOutputDirectory()
        val progress = File(output, "annual-year-2-progress.csv")
        progress.writeText("elapsed_ms,games,busy\n")
        val started = SystemClock.elapsedRealtime()
        var lastSample = -10_000L
        fun sample() {
            val state = vm.state.value
            lastSample = SystemClock.elapsedRealtime() - started
            progress.appendText("$lastSample,${state.season?.results?.size},${state.busy}\n")
        }
        compose.runOnUiThread { vm.execute(PennantCommand.SEASON) }
        try {
            compose.waitUntil(2_280_000) {
                if (SystemClock.elapsedRealtime() - started - lastSample >= 10_000L) sample()
                !vm.state.value.busy
            }
        } finally { sample() }
        assertNull(vm.state.value.error)
        val year2 = stored()
        assertEquals(720, year2.state.results.size)
        assertEquals(141, year2.state.dayIndex)
        assertEquals(2L, year2.timeline!!.year)
        assertEquals(second.token.worldId, year2.token.worldId)
        assertEquals(second.token.generationId, year2.token.generationId)
        assertNull(year2.state.active)
        val year2Frame = CheckpointCodec.encodeSeason(year2.state)
        assertTrue(year2Frame.size <= 1_950_000)
        File(output, "annual-year-2.bin").writeBytes(year2Frame)
        compose.pennantScreenshot("annual-year-2-completed.png")
        click(PennantTags.NEXT_YEAR)
        compose.onNodeWithTag(PennantTags.CONFIRM_NEXT_YEAR).performClick()
        ready()
        val third = stored()
        assertEquals(3L, third.timeline!!.year)
        assertTrue(third.state.results.isEmpty())
        assertEquals(second.state.clubLineups, third.state.clubLineups)
        compose.onNodeWithTag(PennantTags.VISIBLE_DAY).assertTextEquals("2028年3月26日（日）")

        click(PennantTags.NAV_STANDINGS); click(PennantTags.NAV_HISTORY)
        compose.waitUntil(120_000) { compose.onAllNodesWithTag(PennantTags.historyYear(1)).fetchSemanticsNodes().size == 1 }
        compose.pennantScreenshot("annual-year-list.png")
        click(PennantTags.historyYear(1))
        compose.waitUntil(120_000) { compose.onAllNodesWithTag(PennantTags.RECORDS).fetchSemanticsNodes().size == 1 }
        compose.onNodeWithTag(PennantTags.STANDINGS).assertExists()
        compose.onNodeWithText("L1-T01-P12").assertExists()
        compose.pennantScreenshot("annual-year-1-details.png")
        compose.onNodeWithText("年度一覧へ").performScrollTo().performClick()
        click(PennantTags.historyYear(2))
        compose.waitUntil(120_000) { compose.onAllNodesWithTag(PennantTags.RECORDS).fetchSemanticsNodes().size == 1 }
        compose.onNodeWithText("L1-T01-P10").assertExists()
        assertEquals(third, stored())
        val summaries = runBlocking { vm.historyYears(third.token).map { vm.historyYear(third.token, it) } }
        assertEquals(listOf(SeasonYear.completed(2, year2.state), SeasonYear.completed(1, first.state)), summaries)
        back()
        click(PennantTags.NAV_SETTINGS)
        click(PennantTags.SAVE)
        ready()
        val saved = stored()
        click(PennantTags.LOAD)
        compose.onNodeWithTag(PennantTags.CONFIRM_LOAD).performClick()
        ready()
        val loaded = stored()
        assertEquals(saved.timeline, loaded.timeline)
        assertEquals(saved.state, loaded.state)
        assertEquals(saved.token.worldId, loaded.token.worldId)
        assertNotEquals(saved.token.generationId, loaded.token.generationId)
        assertEquals(summaries, runBlocking { vm.historyYears(loaded.token).map { vm.historyYear(loaded.token, it) } })

        compose.openNextOwnGame()
        ready()
        val start = stored().state.active!!
        assertEquals(third.state.games.first(), start.game)
        assertEquals(0L, start.advanceCalls)
        val audit = JSONObject()
            .put("schema", "annual-season-device-v1")
            .put("root_seed", third.timeline!!.rootSeed)
            .put("year_1_frame_sha256", completedV3FrameHash)
            .put("year_2_frame_sha256", hash(year2Frame))
            .put("year_2_frame_bytes", year2Frame.size)
            .put("year_2_elapsed_ms", lastSample)
            .put("year_2_completed_games", year2.state.results.size)
            .put("year_seeds", JSONArray(listOf(first, second, third).map { entry -> entry.state.masterSeed }))
            .put("year_game_seeds", JSONArray(listOf(first, second, third).map { entry -> JSONArray(entry.state.games.map { it.seed }) }))
            .put("years", JSONArray(summaries.map(::annualJson)))
            .put("year_3_first_game_id", start.game.fixture.gameId)
            .put("year_3_first_game_seed", start.game.seed)
            .put("year_3_first_lineup_away", JSONArray(start.lineup.away))
            .put("year_3_first_lineup_home", JSONArray(start.lineup.home))
            .put("year_3_first_checkpoint", start.stateHash)
            .put("year_3_manual_slot_roundtrip", true)
            .put("history_browse_preserved_token", true)
        File(output, "annual-device-observations.json").writeText(audit.toString(2))
    }

    private fun annualJson(year: SeasonYear) = JSONObject().put("year", year.number)
        .put("standings", JSONArray(year.standings.map { row ->
            val t = row.totals
            JSONObject().put("club_id", t.clubId).put("rank", row.rank ?: JSONObject.NULL)
                .put("played", t.played).put("wins", t.wins).put("losses", t.losses).put("draws", t.draws)
                .put("runs_for", t.runsFor).put("runs_against", t.runsAgainst)
        }))
        .put("players", JSONArray(year.players.map { row ->
            val c = row.counts
            JSONObject().put("player_id", row.playerId).put("club_id", row.clubId)
                .put("plate_appearances", c.completedPlateAppearances).put("hits", c.safeHitEvents)
                .put("walks", c.walkEvents).put("strikeouts", c.strikeoutEvents).put("home_runs", c.homeRunEvents)
        }))
}

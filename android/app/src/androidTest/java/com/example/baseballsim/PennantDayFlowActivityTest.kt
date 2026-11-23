package com.example.baseballsim

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PennantDayFlowActivityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val vm get() = compose.activity.pennantViewModel

    private fun ready(): PennantUiState {
        compose.waitUntil(120_000) { vm.state.value.loaded && !vm.state.value.busy }
        return vm.state.value.also { assertNull(it.error) }
    }

    private fun fresh(club: String) {
        compose.enterPennantMode()
        ready()
        compose.runOnUiThread {
            vm.changeSeed("42")
            vm.execute(PennantCommand.CREATE, replacementConfirmed = true, club = club)
        }
        val initial = ready()
        assertEquals(1, initial.visibleDay)
        assertEquals(club, initial.season!!.selectedClubId)
        compose.onNodeWithText("休養日").assertExists()
        compose.clickPennant(PennantTags.DAY)
        val day = ready()
        assertEquals(2, day.visibleDay)
        assertTrue(day.season!!.results.isEmpty())
    }

    private fun visibleDay(): String = compose.onNodeWithTag(PennantTags.VISIBLE_DAY)
        .fetchSemanticsNode().config[SemanticsProperties.Text].single().text

    @Test fun progressShowsAllSixScoresAtTheSameDateAndNextDayDoesNotWrite() {
        fresh("L1-T01")
        val before = ready()
        val fixtureIds = before.season!!.games.take(6).map { it.fixture.gameId }
        val initialLabel = visibleDay()
        assertEquals("第1週 火曜", initialLabel)
        fixtureIds.forEach { id ->
            compose.onNode(hasText("試合前") and hasAnyAncestor(hasTestTag(PennantTags.todayGame(id))),
                useUnmergedTree = true).assertExists()
        }
        val own = fixtureIds.first()
        for (other in fixtureIds.drop(1)) {
            assertTrue(compose.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag(PennantTags.todayGame(other))),
                useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        }
        compose.pennantScreenshot("day-flow-before.png")
        compose.clickPennant(PennantTags.DAY)
        val scores = ready()
        assertEquals(2, scores.resultDay)
        assertEquals(2, scores.visibleDay)
        assertEquals(3, scores.season!!.dayIndex)
        assertEquals(6, scores.season!!.results.size)
        val scoresLabel = visibleDay()
        assertEquals(initialLabel, scoresLabel)
        compose.onNodeWithText("試合後").assertExists()
        val scoreTexts = fixtureIds.map { id ->
            val tag = hasTestTag(PennantTags.todayGame(id))
            val rows = compose.onAllNodes(hasAnyAncestor(tag), useUnmergedTree = true).fetchSemanticsNodes()
            val texts = rows.flatMap { node ->
                if (SemanticsProperties.Text in node.config) node.config[SemanticsProperties.Text] else emptyList()
            }.map { it.text }
            assertTrue("試合終了" in texts)
            texts.single { it.matches(Regex("[0-9]+ – [0-9]+")) }
        }
        assertEquals(listOf("3 – 5", "2 – 5", "4 – 3", "3 – 2", "2 – 3", "5 – 4"), scoreTexts)
        compose.pennantScreenshot("day-flow-scores.png")
        val saved = scores.display!!.stored
        compose.activityRule.scenario.recreate()
        assertEquals(saved, ready().display!!.stored)
        compose.clickPennant(PennantTags.NAV_SCHEDULE)
        compose.onNodeWithTag(PennantTags.SCHEDULE).assertExists()
        compose.clickPennant(PennantTags.NAV_HOME)
        assertEquals(initialLabel, visibleDay())
        compose.clickPennant(PennantTags.OPEN)
        compose.onNodeWithTag(PennantTags.GAME).assertExists()
        val shown = compose.onNode(hasText("3 − 5") and hasAnyAncestor(hasTestTag(PennantTags.GAME)), useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsProperties.Text].single().text
        compose.onNodeWithText("仮球団6 − 仮球団1").assertExists()
        compose.onNodeWithTag(PennantTags.PITCH).assertIsNotEnabled()
        assertEquals(saved, ready().display!!.stored)
        compose.clickPennant(PennantTags.NAV_HOME)
        compose.clickPennant(PennantTags.NEXT_DAY)
        val next = ready()
        val nextLabel = visibleDay()
        assertEquals("第1週 水曜", nextLabel)
        assertEquals(3, next.visibleDay)
        assertNull(next.resultDay)
        assertEquals(saved, next.display!!.stored)
        assertEquals(6, compose.onAllNodesWithText("試合前", useUnmergedTree = true).fetchSemanticsNodes().size)
        val evidence = JSONObject()
            .put("before", initialLabel)
            .put("scores_day", scoresLabel)
            .put("next_day", nextLabel)
            .put("scores", JSONArray(scoreTexts))
            .put("score_phase_result_count", scores.season!!.results.size)
            .put("calendar_index_after_games", scores.season!!.dayIndex)
            .put("ack_preserved_store_token", saved.token == next.display!!.stored.token)
            .put("own_fixture", own)
            .put("own_result_text", shown)
            .put("own_card_left_same_save", saved == next.display!!.stored)
        File(pennantTestOutputDirectory(), "day-flow-evidence.json").writeText(evidence.toString(2))
    }

    @Test fun ownCardStartsOnlyTheExactSameDayFixtureAndRejoinsWithoutWriting() {
        fresh("L2-T04")
        val before = ready()
        val own = before.season!!.games[5].fixture
        compose.clickPennant(PennantTags.OPEN)
        val started = ready()
        compose.onNodeWithTag(PennantTags.GAME).assertExists()
        assertEquals(5, started.season!!.results.size)
        assertEquals(2, started.visibleDay)
        assertEquals(own, started.season!!.active!!.game.fixture)
        assertEquals(0L, started.season!!.active!!.snapshot.pitchesAccepted)
        compose.clickPennant(PennantTags.NAV_HOME)
        compose.clickPennant(PennantTags.OPEN)
        compose.onNodeWithTag(PennantTags.GAME).assertExists()
        assertEquals(started.display!!.stored, ready().display!!.stored)
        compose.clickPennant(PennantTags.FINISH_GAME)
        val finished = ready()
        assertEquals(6, finished.season!!.results.size)
        assertEquals(own, finished.season!!.results.last().game.fixture)
        assertEquals(2, finished.resultDay)
        compose.onNodeWithTag(PennantTags.PITCH).assertIsNotEnabled()
        compose.onNodeWithText("5 − 4").assertExists()
    }
}

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

    private fun tapControl(tag: String, inScrollPage: Boolean = false) {
        val control = compose.onNodeWithTag(tag)
        if (inScrollPage) control.performScrollTo()
        control.assertIsDisplayed().performTouchInput { click() }
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
        tapControl(PennantTags.DAY)
        val day = ready()
        assertEquals(2, day.visibleDay)
        assertTrue(day.season!!.results.isEmpty())
    }

    private fun visibleDay(): String = compose.onNodeWithTag(PennantTags.VISIBLE_DAY)
        .fetchSemanticsNode().config[SemanticsProperties.Text].single().text

    private fun fixtureNode(id: String) =
        compose.onNodeWithTag(PennantTags.todayGame(id), useUnmergedTree = true).fetchSemanticsNode()

    private fun verifyLeagueTables(left: String, right: String) {
        val leftNode = compose.onNodeWithTag("pennant_league_standings_$left", useUnmergedTree = true).fetchSemanticsNode()
        val rightNode = compose.onNodeWithTag("pennant_league_standings_$right", useUnmergedTree = true).fetchSemanticsNode()
        assertTrue(leftNode.positionInRoot.x < rightNode.positionInRoot.x)
        assertEquals(leftNode.positionInRoot.y, rightNode.positionInRoot.y)
    }

    @Test fun progressShowsAllSixScoresAtTheSameDateAndNextDayDoesNotWrite() {
        fresh("L1-T01")
        val before = ready()
        val fixtureIds = before.season!!.games.take(6).map { it.fixture.gameId }
        val initialLabel = visibleDay()
        assertEquals("2026年3月27日（金）", initialLabel)
        val cardSizes = fixtureIds.map { fixtureNode(it).size }
        assertTrue(cardSizes.all { it.width > 0 && it.height > 0 })
        verifyLeagueTables("L1", "L2")
        for (tag in listOf(PennantTags.PROGRESS, PennantTags.NAV_MATCH, PennantTags.SEASON, PennantTags.STOP, PennantTags.YEAR)) {
            compose.onNodeWithTag(tag).assertDoesNotExist()
        }
        for (label in listOf("当日の試合", "リーグ1", "リーグ2")) compose.onNodeWithText(label).assertDoesNotExist()
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
        tapControl(PennantTags.DAY)
        val scores = ready()
        assertEquals(2, scores.resultDay)
        assertEquals(2, scores.visibleDay)
        assertEquals(3, scores.season!!.dayIndex)
        assertEquals(6, scores.season!!.results.size)
        val scoresLabel = visibleDay()
        assertEquals(initialLabel, scoresLabel)
        compose.onNodeWithTag(PennantTags.NEXT_DAY).assertIsDisplayed()
        val finalSizes = fixtureIds.map { fixtureNode(it).size }
        assertEquals(cardSizes, finalSizes)
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
        tapControl(PennantTags.OPEN, inScrollPage = true)
        compose.onNodeWithTag(PennantTags.GAME).assertExists()
        val shown = compose.onNode(hasText("3 − 5") and hasAnyAncestor(hasTestTag(PennantTags.GAME)), useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsProperties.Text].single().text
        compose.onNodeWithText("中日ドラゴンズ − 読売ジャイアンツ").assertExists()
        compose.onNodeWithTag(PennantTags.PITCH).assertIsNotEnabled()
        val afterResultTap = ready().display!!.stored
        assertEquals(saved, afterResultTap)
        compose.clickPennant(PennantTags.NAV_HOME)
        tapControl(PennantTags.NEXT_DAY)
        val next = ready()
        val nextLabel = visibleDay()
        assertEquals("2026年3月28日（土）", nextLabel)
        assertEquals(3, next.visibleDay)
        assertNull(next.resultDay)
        assertEquals(saved, next.display!!.stored)
        assertEquals(6, compose.onAllNodesWithText("試合前", useUnmergedTree = true).fetchSemanticsNodes().size)
        val evidence = JSONObject()
            .put("before", initialLabel)
            .put("scores_day", scoresLabel)
            .put("next_day", nextLabel)
            .put("scores", JSONArray(scoreTexts))
            .put("box_sizes_preserved", cardSizes == finalSizes)
            .put("score_phase_result_count", scores.season!!.results.size)
            .put("calendar_index_after_games", scores.season!!.dayIndex)
            .put("ack_preserved_store_token", saved.token == next.display!!.stored.token)
            .put("own_fixture", own)
            .put("own_result_text", shown)
            .put("own_card_left_same_save", saved == afterResultTap)
        File(pennantTestOutputDirectory(), "day-flow-evidence.json").writeText(evidence.toString(2))
    }

    @Test fun ownCardStartsOnlyTheExactSameDayFixtureAndRejoinsWithoutWriting() {
        fresh("L2-T04")
        val before = ready()
        val own = before.season!!.games[5].fixture
        verifyLeagueTables("L2", "L1")
        val ordered = before.season!!.games.take(6).map { it.fixture.gameId }
            .sortedBy { fixtureNode(it).positionInRoot.y }
        assertEquals(listOf(own.gameId) + before.season!!.games.take(5).map { it.fixture }
            .sortedBy { it.leagueId != "L2" }.map { it.gameId }, ordered)
        tapControl(PennantTags.OPEN, inScrollPage = true)
        val started = ready()
        compose.onNodeWithTag(PennantTags.GAME).assertExists()
        assertEquals(5, started.season!!.results.size)
        assertEquals(2, started.visibleDay)
        assertEquals(own, started.season!!.active!!.game.fixture)
        assertEquals(0L, started.season!!.active!!.snapshot.pitchesAccepted)
        compose.clickPennant(PennantTags.NAV_HOME)
        tapControl(PennantTags.OPEN, inScrollPage = true)
        compose.onNodeWithTag(PennantTags.GAME).assertExists()
        val rejoined = ready().display!!.stored
        assertEquals(started.display!!.stored, rejoined)
        tapControl(PennantTags.FINISH_GAME, inScrollPage = true)
        val finished = ready()
        assertEquals(6, finished.season!!.results.size)
        assertEquals(own, finished.season!!.results.last().game.fixture)
        assertEquals(2, finished.resultDay)
        compose.onNodeWithTag(PennantTags.PITCH).assertIsNotEnabled()
        compose.onNodeWithText("5 − 4").assertExists()
        val evidence = JSONObject()
            .put("own_fixture", started.season!!.active!!.game.fixture.gameId)
            .put("completed_predecessors", started.season!!.results.size)
            .put("pitches_at_start", started.season!!.active!!.snapshot.pitchesAccepted)
            .put("rejoin_preserved_store", started.display!!.stored == rejoined)
        File(pennantTestOutputDirectory(), "own-active-evidence.json").writeText(evidence.toString(2))
    }
}

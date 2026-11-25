package com.example.baseballsim

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.baseballsim.season.SeasonSession
import com.example.baseballsim.season.SeasonState
import java.io.File
import java.time.LocalDate
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private val opening = LocalDate.of(2026, 3, 27)
private val openingId = "S1-D002-L1-R01-M1-G1"

@RunWith(AndroidJUnit4::class)
class MonthlyScheduleActivityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val vm get() = compose.activity.pennantViewModel

    private fun ready(): PennantUiState {
        compose.waitUntil(120_000) { vm.state.value.loaded && !vm.state.value.busy }
        return vm.state.value.also { assertNull(it.error) }
    }

    @Test fun browseDayMonthAndResultWithoutChangingTheSavedSeason() {
        compose.enterPennantMode()
        ready()
        compose.runOnUiThread {
            vm.changeSeed("42")
            vm.execute(PennantCommand.CREATE, replacementConfirmed = true, club = "L1-T01")
        }
        assertEquals(1, ready().visibleDay)
        compose.onNodeWithTag(PennantTags.DAY).tapSchedule()
        assertEquals(2, ready().visibleDay)
        compose.onNodeWithTag(PennantTags.DAY).tapSchedule()
        val played = ready()
        assertEquals(6, played.season!!.results.size)
        assertEquals(2, played.visibleDay)
        val saved = played.display!!.stored
        compose.onNodeWithTag(PennantTags.NAV_SCHEDULE).tapSchedule()
        compose.onNodeWithTag(MonthlyScheduleTags.MONTH).assertTextContains("2026年3月")
        val grid = hasTestTag(MonthlyScheduleTags.GRID)
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange) and
            (grid or hasAnyAncestor(grid)), useUnmergedTree = true).assertCountEquals(0)
        val openingCell = compose.onNodeWithTag(MonthlyScheduleTags.day(opening))
        val openedLabel = "2026年3月27日（金）、対中日ドラゴンズ、試合終了、勝ち、自球団5点、相手3点"
        openingCell.assertContentDescriptionEquals(openedLabel).assertIsSelected()
        val future = compose.onNodeWithTag(MonthlyScheduleTags.day(opening.plusDays(1)))
        future.assertContentDescriptionEquals("2026年3月28日（土）、対中日ドラゴンズ、試合前")
        compose.onNode(hasText("中日○") and hasAnyAncestor(hasTestTag(MonthlyScheduleTags.day(opening))),
            useUnmergedTree = true).assertTextFits()
        compose.onNode(hasText("5–3") and hasAnyAncestor(hasTestTag(MonthlyScheduleTags.day(opening))),
            useUnmergedTree = true).assertTextFits()
        compose.pennantScreenshot("monthly-schedule.png")

        compose.onNodeWithTag(MonthlyScheduleTags.day(opening.minusDays(1))).tapSchedule(inScrollPage = true)
        val details = hasAnyAncestor(hasTestTag(MonthlyScheduleTags.DETAILS))
        compose.onNode(hasText("休養日") and details, useUnmergedTree = true).assertExists()
        openingCell.tapSchedule(inScrollPage = true)
        for (fixture in saved.state.games.take(6)) {
            compose.onNodeWithTag(MonthlyScheduleTags.game(fixture.fixture.gameId)).assertExists()
        }
        val fixture = compose.onNode(hasClickAction() and hasAnyAncestor(hasTestTag(MonthlyScheduleTags.game(openingId))))
        fixture.tapSchedule(inScrollPage = true)
        val detailCard = compose.onNodeWithTag(PennantTags.GAME)
        detailCard.performScrollTo().assertIsDisplayed()
        val detail = hasAnyAncestor(hasTestTag(PennantTags.GAME))
        compose.onNode(hasText("中日ドラゴンズ − 読売ジャイアンツ") and detail, useUnmergedTree = true).assertExists()
        compose.onNode(hasText("3 − 5") and detail, useUnmergedTree = true).assertExists()
        compose.onNode(hasText("計\n3\n5") and detail, useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(PennantTags.PITCH).assertDoesNotExist()
        compose.pennantScreenshot("monthly-schedule-detail.png")

        compose.onNodeWithTag(MonthlyScheduleTags.NEXT).tapSchedule(inScrollPage = true)
        compose.onNodeWithTag(MonthlyScheduleTags.MONTH).assertTextContains("2026年4月")
        val april = LocalDate.of(2026, 4, 1)
        compose.onNodeWithTag(MonthlyScheduleTags.day(april)).tapSchedule(inScrollPage = true)
        compose.onNodeWithTag(MonthlyScheduleTags.day(april)).assertIsSelected()
        compose.onNodeWithTag(PennantTags.NAV_HOME).tapSchedule()
        compose.onNodeWithTag(PennantTags.NAV_SCHEDULE).tapSchedule()
        compose.onNodeWithTag(MonthlyScheduleTags.day(april)).assertIsSelected()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag(MonthlyScheduleTags.MONTH).assertTextContains("2026年4月")
        compose.onNodeWithTag(MonthlyScheduleTags.day(april)).assertIsSelected()

        compose.onNodeWithTag(MonthlyScheduleTags.MONTH).tapSchedule(inScrollPage = true)
        compose.onNodeWithTag(MonthlyScheduleTags.month(12)).tapSchedule(inScrollPage = true)
        compose.onNodeWithTag(MonthlyScheduleTags.MONTH).assertTextContains("2026年12月")
        compose.onNodeWithTag(MonthlyScheduleTags.NEXT).assertIsNotEnabled()
        compose.onNode(hasText("シーズン日程外") and details, useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(MonthlyScheduleTags.PREVIOUS).tapSchedule(inScrollPage = true)
        compose.onNodeWithTag(MonthlyScheduleTags.MONTH).assertTextContains("2026年11月")

        val after = ready()
        assertEquals(saved, after.display!!.stored)
        assertEquals(played.visibleDay, after.visibleDay)
        val out = JSONObject()
            .put("opening_date", opening.toString())
            .put("opening_cell", openedLabel)
            .put("opening_detail", "中日 3–5 巨人")
            .put("selected_day_game_count", 6)
            .put("restoration_month", "2026-04")
            .put("browsed_outside_season", "2026-12")
            .put("revision_before", saved.token.revision)
            .put("revision_after", after.display!!.stored.token.revision)
            .put("schedule_browse_preserved_store", saved == after.display!!.stored)
        File(pennantTestOutputDirectory(), "monthly-schedule-evidence.json").writeText(out.toString(2))
    }
}

@RunWith(AndroidJUnit4::class)
class MonthlyScheduleNarrowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sevenColumnsAndOpeningScoreFitAt320dpAnd150PercentFont() {
        val season = SeasonSession(SeasonState.initialBatterProfile())
        season.advanceDay()
        season.advanceDay()
        compose.setContent {
            BaseballTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                    Box(Modifier.width(320.dp).padding(horizontal = 16.dp)) {
                        MonthlySchedule(season.state, visibleDay = 2, year = 1L)
                    }
                }
            }
        }
        val grid = hasTestTag(MonthlyScheduleTags.GRID)
        val left = compose.onNodeWithTag(MonthlyScheduleTags.day(LocalDate.of(2026, 3, 22))).fetchSemanticsNode().boundsInRoot
        val right = compose.onNodeWithTag(MonthlyScheduleTags.day(LocalDate.of(2026, 3, 28))).fetchSemanticsNode().boundsInRoot
        val area = compose.onNode(grid).fetchSemanticsNode().boundsInRoot
        assertTrue(left.left >= area.left && right.right <= area.right + 1f && right.left > left.left)
        assertEquals(left.top, right.top, 1f)
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange) and
            (grid or hasAnyAncestor(grid)), useUnmergedTree = true).assertCountEquals(0)
        val scope = hasAnyAncestor(hasTestTag(MonthlyScheduleTags.day(opening)))
        for (text in listOf("27", "中日○", "5–3")) {
            compose.onNode(hasText(text) and scope, useUnmergedTree = true).assertTextFits()
        }
        compose.onNodeWithTag(MonthlyScheduleTags.day(opening.plusDays(1))).tapSchedule()
        compose.onNodeWithTag(MonthlyScheduleTags.day(opening.plusDays(1))).assertIsSelected()
        compose.pennantScreenshot("monthly-schedule-320-font150.png")
    }
}

private fun SemanticsNodeInteraction.tapSchedule(inScrollPage: Boolean = false) {
    if (inScrollPage) performScrollTo()
    assertIsDisplayed().performTouchInput { click() }
}

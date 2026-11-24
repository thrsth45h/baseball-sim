package com.example.baseballsim

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.baseballsim.data.SeasonTimeline
import com.example.baseballsim.data.StoreToken
import com.example.baseballsim.data.StoredSeason
import com.example.baseballsim.season.PennantSchedule
import com.example.baseballsim.season.SeasonSession
import com.example.baseballsim.season.SeasonState
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PennantDashboardTest {
    @get:Rule val compose = createComposeRule()

    @Test fun narrowDashboardUsesTheCurrentDaysResultsAndFixedReadOnlyTabs() {
        val session = SeasonSession(SeasonState.initialBatterProfile())
        fun published(revision: Long) = StoredSeason(StoreToken("dashboard", "generation", revision), session.state,
            "operation-$revision", SeasonTimeline("42", 1, null))
        val state = mutableStateOf(PennantUiState(loaded = true, display = pennantDisplay(published(0))))
        val commands = mutableListOf<PennantCommand>()
        val restoration = StateRestorationTester(compose)
        restoration.setContent { BaseballTheme {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                Box(Modifier.width(320.dp)) {
                    PennantScreen(state.value, {}, { error("A tab must not select a club") },
                        { command, _, _ -> commands += command }, {}, { error("A tab must not reload") },
                        openOwnGame = { _, id -> state.value = state.value.copy(openedGameId = id); true },
                        consumeOpenedGame = { _, _ -> state.value = state.value.copy(openedGameId = null); true })
                }
            }
        } }
        compose.onNodeWithTag(PennantTags.VISIBLE_DAY).assertTextEquals("2026年3月26日（木）")
        compose.onNodeWithText("休養日").performScrollTo().assertIsDisplayed()

        session.advanceDay()
        session.finishCurrent()
        session.advanceOnePitch()
        val partialDay = published(3)
        compose.runOnUiThread { state.value = state.value.copy(display = pennantDisplay(partialDay)) }
        compose.clickPennant(PennantTags.DAY)
        assertTrue(commands == listOf(PennantCommand.DAY))
        commands.clear()

        val count = PennantSchedule.calendar[partialDay.state.dayIndex - 1].gameOrdinals
        assertTrue(count.size == 6)
        count.forEach { ordinal ->
            val fixture = partialDay.state.games[ordinal - 1].fixture
            compose.onNodeWithTag(PennantTags.todayGame(fixture.gameId), useUnmergedTree = true).assertExists()
        }
        val completed = partialDay.state.results.single()
        val completedTag = PennantTags.todayGame(completed.game.fixture.gameId)
        val activeTag = PennantTags.todayGame(partialDay.state.active!!.game.fixture.gameId)
        compose.onNodeWithTag(completedTag, useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "操作球団の試合"))
        compose.onNode(hasText("${completed.score.away} – ${completed.score.home}") and hasAnyAncestor(hasTestTag(completedTag)),
            useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("試合終了") and hasAnyAncestor(hasTestTag(completedTag)), useUnmergedTree = true).assertExists()
        compose.onNode(hasText("進行中") and hasAnyAncestor(hasTestTag(activeTag)), useUnmergedTree = true).assertExists()
        compose.pennantScreenshot("dashboard-today-320-font150.png")

        val bottom = compose.onNodeWithTag(PennantTags.BOTTOM_NAV)
        val top = compose.onNodeWithTag(PennantTags.TOP_BAR)
        val beforeScroll = bottom.fetchSemanticsNode().boundsInRoot.bottom
        val topBeforeScroll = top.fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag(PennantTags.HOME_STANDINGS).performScrollTo().assertIsDisplayed()
        assertTrue(beforeScroll == bottom.fetchSemanticsNode().boundsInRoot.bottom)
        assertTrue(topBeforeScroll == top.fetchSemanticsNode().boundsInRoot.top)
        compose.pennantScreenshot("dashboard-standings-320-font150.png")

        compose.clickPennant(PennantTags.OPEN)
        compose.onNodeWithTag(PennantTags.GAME).assertExists()
        compose.onNodeWithTag(PennantTags.NAV_HOME).assertIsSelected()
        compose.clickPennant(PennantTags.NAV_SCHEDULE)
        compose.onNodeWithTag(PennantTags.SCHEDULE).assertExists()
        compose.onNodeWithTag(PennantTags.NAV_SCHEDULE).assertIsSelected()
        compose.clickPennant(PennantTags.NAV_LINEUP)
        compose.onNodeWithTag(PennantTags.LINEUP).assertExists()
        compose.clickPennant(PennantTags.NAV_PLAYERS)
        compose.clickPennant(PennantTags.player("L1-T01-P01"))
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag(PennantTags.NAV_LINEUP).assertIsSelected()
        compose.onNodeWithTag(PennantTags.PLAYER_DETAIL).assertExists()
        compose.clickPennant(PennantTags.BACK)
        compose.onNodeWithTag(PennantTags.PLAYERS).assertExists()
        compose.clickPennant(PennantTags.NAV_STANDINGS)
        compose.onNodeWithTag(PennantTags.STANDINGS).assertExists()
        compose.clickPennant(PennantTags.NAV_RECORDS)
        compose.onNodeWithTag(PennantTags.RECORDS).assertExists()
        compose.onNodeWithTag(PennantTags.NAV_STANDINGS).assertIsSelected()
        compose.clickPennant(PennantTags.NAV_SETTINGS)
        compose.onNodeWithTag(PennantTags.SAVE).assertIsNotEnabled()
        compose.onNodeWithTag(PennantTags.NAV_SETTINGS).assertIsSelected()
        compose.clickPennant(PennantTags.NAV_HOME)
        compose.onNodeWithTag(PennantTags.NAV_HOME).assertIsSelected()
        compose.onNodeWithTag(PennantTags.HOME).assertExists()
        assertTrue(commands.isEmpty())
        assertSame(partialDay, state.value.display!!.stored)
    }
}

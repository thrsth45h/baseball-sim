package com.example.baseballsim

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.printToString
import java.io.File
import org.junit.rules.TestRule

private val pennantTabs = setOf(
    PennantTags.NAV_HOME, PennantTags.NAV_SCHEDULE, PennantTags.NAV_LINEUP,
    PennantTags.NAV_STANDINGS, PennantTags.NAV_SETTINGS,
)
private val fixedPennantControls = pennantTabs + setOf(PennantTags.BACK, PennantTags.STOP, PennantTags.RELOAD,
    PennantTags.DAY, PennantTags.NEXT_DAY, PennantTags.NEXT_YEAR)

internal fun ComposeContentTestRule.enterPennantMode() {
    if (onAllNodesWithTag("mode_pennant").fetchSemanticsNodes().isNotEmpty()) onNodeWithTag("mode_pennant").performClick()
    onNodeWithTag(PennantTags.SCREEN).assertExists()
}

internal fun <R : TestRule> AndroidComposeTestRule<R, MainActivity>.enterStandaloneMatch() {
    if (onAllNodesWithTag(MatchTags.SCREEN).fetchSemanticsNodes().isNotEmpty()) return
    val vm = activity.pennantViewModel
    waitUntil(90_000) { vm.state.value.loaded && !vm.state.value.busy }
    if (vm.state.value.season != null) clickPennant(PennantTags.NAV_SETTINGS)
    clickPennant("mode_match")
}

internal fun ComposeContentTestRule.choosePennantClub(name: String) {
    onNode(hasText(name) and hasAnyAncestor(isPopup()), useUnmergedTree = true).performScrollTo().performClick()
}

internal fun <R : TestRule> AndroidComposeTestRule<R, MainActivity>.openNextOwnGame() {
    val vm = activity.pennantViewModel
    while (true) {
        waitUntil(120_000) { !vm.state.value.busy }
        val state = vm.state.value
        val season = checkNotNull(state.season)
        check(state.canProgress) { "No further own fixture" }
        val next = season.games.drop(season.results.size).first { game ->
            season.selectedClubId in listOf(game.fixture.awayClubId, game.fixture.homeClubId)
        }.fixture
        if (next.dayIndex == state.visibleDay) {
            clickPennant(PennantTags.OPEN)
            waitUntil(120_000) { vm.state.value.season?.active?.game?.fixture?.gameId == next.gameId && !vm.state.value.busy }
            onNodeWithTag(PennantTags.GAME).assertExists()
            return
        }
        clickPennant(if (state.resultDay != null) PennantTags.NEXT_DAY else PennantTags.DAY)
        waitUntil(120_000) {
            val after = vm.state.value
            !after.busy && (after.visibleDay != state.visibleDay || after.resultDay != state.resultDay)
        }
    }
}

internal fun ComposeContentTestRule.clickPennant(tag: String) {
    val control = onNodeWithTag(tag)
    if (tag !in fixedPennantControls) control.performScrollTo()
    if (tag in pennantTabs) control.assertIsDisplayed()
    control.performClick()
    if (tag in pennantTabs) {
        try {
            control.assertIsSelected()
        } catch (failure: AssertionError) {
            runCatching {
                val directory = pennantTestOutputDirectory()
                File(directory, "tab-failed-$tag.txt").writeText(onRoot(useUnmergedTree = true).printToString())
                pennantScreenshot("tab-failed-$tag.png")
            }
            throw failure
        }
    }
}

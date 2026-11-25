package com.example.baseballsim

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.text.TextLayoutResult
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

internal fun ComposeContentTestRule.verifyFixedStandings(panelTag: String) {
    val table = hasTestTag(panelTag)
    val descendants = hasAnyAncestor(table)
    onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange) and (table or descendants),
        useUnmergedTree = true).assertCountEquals(0)
    val header = hasAnyAncestor(hasTestTag("pennant_standings_header"))
    onAllNodes(hasText("順位表") and header, useUnmergedTree = true).assertCountEquals(1)
    onAllNodes(hasText("差") and header, useUnmergedTree = true).assertCountEquals(1)
    for (former in listOf("順位", "球団名", "勝敗分", "勝率")) {
        onAllNodes(hasText(former) and descendants, useUnmergedTree = true).assertCountEquals(0)
    }
    val viewport = onNodeWithTag(panelTag).fetchSemanticsNode()
    for ((club, short) in listOf("L1-T05" to "ヤクルト", "L2-T02" to "日本ハム")) {
        val name = onNode(hasText(short) and hasAnyAncestor(hasTestTag("pennant_standing_$club")), useUnmergedTree = true)
        name.assertTextFits()
        val node = name.fetchSemanticsNode()
        val left = viewport.positionInRoot.x
        assertTrue(node.positionInRoot.x >= left && node.positionInRoot.x + node.size.width <= left + viewport.size.width + 1f)
    }
}

internal fun SemanticsNodeInteraction.assertTextFits() {
    val layouts = mutableListOf<TextLayoutResult>()
    performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(layouts)) }
    val layout = layouts.single()
    assertFalse("Text is clipped: ${layout.layoutInput.text} in ${layout.size}", layout.hasVisualOverflow)
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

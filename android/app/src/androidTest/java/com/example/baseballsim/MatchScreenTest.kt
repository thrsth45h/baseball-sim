package com.example.baseballsim

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MatchScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun narrow320DpKeepsAllProgressAndNewGameControlsUsable() {
        val modes = mutableListOf<AdvanceMode>()
        var newGames = 0
        compose.setContent {
            BaseballTheme {
                Box(Modifier.width(320.dp).fillMaxHeight()) {
                    MatchScreen(sampleState(), {}, { newGames++ }, { modes.add(it) })
                }
            }
        }
        compose.onNodeWithTag(MatchTags.ADVANCE_PITCH).assertIsDisplayed().performClick()
        compose.onNodeWithTag(MatchTags.ADVANCE_PA).assertIsDisplayed().performClick()
        compose.onNodeWithTag(MatchTags.ADVANCE_GAME).assertIsDisplayed().performClick()
        assertEquals(AdvanceMode.entries, modes)
        compose.onNodeWithTag(MatchTags.LINE_SCORE).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(MatchTags.NEW_GAME).performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, newGames)
        compose.onNodeWithTag(MatchTags.STATUS).performScrollTo().assertTextEquals("1回表")
        compose.saveScreenshot("narrow320.png")
    }

    @Test
    fun busyScreenRejectsRepeatedProgressAndNewGameTaps() {
        var calls = 0
        compose.setContent {
            BaseballTheme { MatchScreen(sampleState().copy(isBusy = true), { calls++ }, { calls++ }, { calls++ }) }
        }
        listOf(MatchTags.ADVANCE_PITCH, MatchTags.ADVANCE_PA, MatchTags.ADVANCE_GAME).forEach { tag ->
            compose.onNodeWithTag(tag).assertIsNotEnabled()
            repeat(4) { compose.onNodeWithTag(tag).performClick() }
        }
        compose.onNodeWithTag(MatchTags.NEW_GAME).performScrollTo().assertIsNotEnabled().performClick()
        compose.onNodeWithTag(MatchTags.SEED_INPUT).assertIsNotEnabled()
        assertEquals(0, calls)
    }

    @Test
    fun abortedStateHidesCurrentCountAndBasesAndDisablesProgress() {
        val state = sampleState()
        compose.setContent {
            BaseballTheme {
                MatchScreen(state.copy(game = state.game!!.copy(status = GameStatus.ABORTED, reason = "投球数の上限に到達")), {}, {}, {})
            }
        }
        compose.onNodeWithTag(MatchTags.STATUS).assertTextEquals("未完了")
        compose.onNodeWithTag(MatchTags.CURRENT).assertDoesNotExist()
        compose.onNodeWithTag(MatchTags.COUNT).assertDoesNotExist()
        compose.onNodeWithTag(MatchTags.BASES).assertDoesNotExist()
        compose.onNodeWithTag(MatchTags.ADVANCE_GAME).assertIsNotEnabled()
    }
}

private fun sampleState() = MatchUiState(
    game = GamePresentation(
        seed = "42",
        status = GameStatus.IN_PROGRESS,
        scoreAway = "0",
        scoreHome = "0",
        lineAway = List(9) { if (it == 0) "0" else null },
        lineHome = List(9) { null },
        pitchesAccepted = 0,
        completedPa = 0,
        current = CurrentPresentation(1, HalfSide.TOP, "ビジター", "仮打者1", 1, 0, 0, 0, listOf(false, false, false)),
        recent = emptyList(),
    ),
)

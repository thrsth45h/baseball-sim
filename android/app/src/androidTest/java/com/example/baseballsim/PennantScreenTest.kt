package com.example.baseballsim

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.baseballsim.core.*
import com.example.baseballsim.data.StoreToken
import com.example.baseballsim.data.StoredSeason
import com.example.baseballsim.season.*
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PennantScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun ui(state: SeasonState) = PennantUiState(loaded = true, hasSlot = true,
        display = pennantDisplay(StoredSeason(StoreToken("test", "test", 0), state, "test")))

    @Test fun narrowTablesStayWithinTheViewportAndCanScroll() {
        val state = ui(SeasonState.initial())
        compose.setContent { BaseballTheme {
            Box(Modifier.width(320.dp)) { PennantScreen(state, {}, {}, { _, _ -> }, {}, {}) }
        } }
        compose.onNodeWithText("順位", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithTag(PennantTags.STANDINGS).performScrollTo().assertIsDisplayed()
        val panel = compose.onNodeWithTag(PennantTags.STANDINGS).fetchSemanticsNode().boundsInRoot
        val viewport = compose.onNodeWithTag(PennantTags.SCREEN).fetchSemanticsNode().boundsInRoot
        assertTrue(panel.left >= viewport.left && panel.right <= viewport.right + 1)
        compose.pennantScreenshot("pennant-narrow320.png")
    }
    @Test fun blockedDiagnosticShowsTheSavedGameAndReasonWithoutManualLoadOrProgress() {
        val initial = SeasonState.initial().beginNext()
        val game = initial.active!!.game
        // A synthetic retained diagnostic; not a claim that ordinary ToyPitchSource failed.
        val snapshot = GameSnapshot(SequenceState(count = CountState()), status = Status.ABORTED, reason = "source_error")
        val source = ToyPitchSource(game.seed)
        source.nextPitch(PitchContext(SequenceState(), Score(), "away", "away_1", 1))
        val blocked = initial.checkpoint(GameCheckpoint(game, 10_000, 1, SnapshotDto.fromCore(snapshot), SourceCursor.capture(source)))
        compose.setContent { BaseballTheme { PennantScreen(ui(blocked), {}, {}, { _, _ -> }, {}, {}) } }
        compose.onNodeWithText("試合ID：${game.fixture.gameId}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("理由：投球処理でエラーが発生しました（source_error）").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(PennantTags.PITCH).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag(PennantTags.SAVE).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag(PennantTags.LOAD).performScrollTo().assertIsNotEnabled()
        compose.pennantScreenshot("pennant-blocked.png")
    }
    @Test fun failedReadDoesNotOfferToOverwriteUnreadableData() {
        compose.setContent { BaseballTheme { PennantScreen(PennantUiState(loaded = true, error = "保存データを読めません"), {}, {}, { _, _ -> }, {}, {}) } }
        compose.onNodeWithTag(PennantTags.CREATE).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag(PennantTags.RELOAD).performScrollTo().assertIsEnabled()
        compose.onNodeWithTag(PennantTags.ERROR).performScrollTo().assertTextEquals("保存データを読めません")
    }
}

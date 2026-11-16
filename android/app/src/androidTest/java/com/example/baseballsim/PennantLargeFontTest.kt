package com.example.baseballsim

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.baseballsim.data.StoreToken
import com.example.baseballsim.data.StoredSeason
import com.example.baseballsim.season.*
import com.example.baseballsim.core.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PennantLargeFontTest {
    @get:Rule val compose = createComposeRule()
    private fun click(tag: String) {
        val node = compose.onNodeWithTag(tag)
        if (tag !in listOf(PennantTags.BACK, PennantTags.STOP, PennantTags.RELOAD)) node.performScrollTo()
        node.performClick()
    }
    @Test fun narrow320AndFont150KeepsHomeListDetailSettingsErrorsAndStopReachable() {
        val initial = PennantUiState(loaded = true, hasSlot = true,
            display = pennantDisplay(StoredSeason(StoreToken("font-world", "font-generation", 0), SeasonState.initial(), "initial")))
        val state = mutableStateOf(initial)
        var commands = 0; var stops = 0
        compose.setContent { BaseballTheme {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                Box(Modifier.width(320.dp)) { PennantScreen(state.value, {}, {}, { _, _, _ -> commands++ }, { stops++ }, {}) }
            }
        } }
        compose.pennantScreenshot("pennant-home-320-font150.png")
        click(PennantTags.NAV_PLAYERS)
        compose.onNodeWithTag(PennantTags.player("L1-T01-P09")).performScrollTo().assertIsDisplayed()
        compose.pennantScreenshot("pennant-players-320-font150.png")
        click(PennantTags.player("L1-T01-P09"))
        compose.onNodeWithTag(PennantTags.PLAYER_DETAIL).assertExists()
        compose.onNodeWithText("L1-T01-P09", substring = true).performScrollTo().assertIsDisplayed()
        compose.pennantScreenshot("pennant-detail-320-font150.png")
        click(PennantTags.BACK); click(PennantTags.BACK); click(PennantTags.NAV_SETTINGS)
        compose.onNodeWithTag(PennantTags.SEED).performScrollTo().assertIsDisplayed()
        compose.pennantScreenshot("pennant-settings-320-font150.png")
        compose.runOnUiThread { state.value = initial.copy(error = "保存データを確認できませんでした。データを残したまま停止しています。") }
        compose.onNodeWithTag(PennantTags.CREATE).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag(PennantTags.SAVE).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag(PennantTags.LOAD).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag(PennantTags.RELOAD).assertIsEnabled()
        compose.pennantScreenshot("pennant-retained-error-320-font150.png")
        click(PennantTags.BACK); click(PennantTags.NAV_PLAYERS); click(PennantTags.player("L1-T01-P01"))
        compose.onNodeWithTag(PennantTags.PLAYER_DETAIL).assertExists()
        compose.onNodeWithTag(PennantTags.ERROR).assertIsDisplayed()
        compose.runOnUiThread { state.value = initial.copy(busy = true, bulk = true) }
        compose.onNodeWithTag(PennantTags.STOP).assertIsEnabled()
        compose.pennantScreenshot("pennant-stop-320-font150.png")
        click(PennantTags.STOP)
        assertEquals(1, stops); assertEquals(0, commands)
        val begun = SeasonState.initial().beginNext()
        val game = begun.active!!.game
        val snapshot = GameSnapshot(SequenceState(count = CountState()), status = Status.ABORTED, reason = "source_error")
        val source = ToyPitchSource(game.seed)
        source.nextPitch(PitchContext(SequenceState(), Score(), "away", "away_1", 1))
        val blocked = begun.checkpoint(GameCheckpoint(game, 10_000, 1, SnapshotDto.fromCore(snapshot), SourceCursor.capture(source)))
        compose.runOnUiThread { state.value = initial.copy(display = pennantDisplay(initial.display!!.stored.copy(state = blocked))) }
        click(PennantTags.BACK); click(PennantTags.BACK)
        compose.onNodeWithText("試合ID：${game.fixture.gameId}").performScrollTo().assertIsDisplayed()
        compose.pennantScreenshot("pennant-blocked-home-320-font150.png")
        click(PennantTags.NAV_MATCH)
        compose.onNodeWithText("理由：投球処理でエラーが発生しました（source_error）").performScrollTo().assertIsDisplayed()
        compose.pennantScreenshot("pennant-blocked-match-320-font150.png")
        compose.onNodeWithTag(PennantTags.PITCH).performScrollTo().assertIsNotEnabled()
    }
}

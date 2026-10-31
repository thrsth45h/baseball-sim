package com.example.baseballsim

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MatchActivityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun awaitReady(): MatchViewModel {
        compose.waitUntil(15_000) {
            val state = compose.activity.matchViewModel.state.value
            !state.isBusy && state.game != null
        }
        return compose.activity.matchViewModel
    }

    @Test
    fun threeProgressControlsReachTerminalAndHideTheCurrentCountAndBases() {
        val vm = awaitReady()
        assertEquals("42", vm.state.value.game?.seed)
        compose.onNodeWithTag(MatchTags.STATUS).assertTextEquals("1回表")
        compose.onNodeWithTag(MatchTags.COUNT).assertIsDisplayed()
        compose.saveScreenshot("initial.png")

        val beforePitch = vm.state.value.game!!.pitchesAccepted
        compose.onNodeWithTag(MatchTags.ADVANCE_PITCH).performClick()
        awaitReady()
        assertEquals(beforePitch + 1, vm.state.value.game!!.pitchesAccepted)
        val beforePa = vm.state.value.game!!.completedPa
        compose.onNodeWithTag(MatchTags.ADVANCE_PA).performClick()
        awaitReady()
        assertEquals(beforePa + 1, vm.state.value.game!!.completedPa)
        compose.onNodeWithTag(MatchTags.ADVANCE_GAME).performClick()
        awaitReady()
        assertEquals(GameStatus.COMPLETED, vm.state.value.game!!.status)
        compose.onNodeWithTag(MatchTags.STATUS).assertTextEquals("試合終了")
        compose.onNodeWithTag(MatchTags.CURRENT).assertDoesNotExist()
        compose.onNodeWithTag(MatchTags.COUNT).assertDoesNotExist()
        compose.onNodeWithTag(MatchTags.BASES).assertDoesNotExist()
        compose.onNodeWithTag(MatchTags.ADVANCE_PITCH).assertIsNotEnabled()
        compose.onNodeWithTag(MatchTags.ADVANCE_PA).assertIsNotEnabled()
        compose.onNodeWithTag(MatchTags.ADVANCE_GAME).assertIsNotEnabled()
        compose.saveScreenshot("seed42-completed.png")
    }

    @Test
    fun invalidSeedShowsAnErrorAndLeavesTheCurrentGameUntouched() {
        val vm = awaitReady()
        val before = vm.state.value.game
        compose.onNodeWithTag(MatchTags.SEED_INPUT).performScrollTo().performTextReplacement("18446744073709551616")
        compose.onNodeWithTag(MatchTags.NEW_GAME).performScrollTo().performClick()
        compose.onNodeWithTag(MatchTags.SEED_ERROR).performScrollTo().assertIsDisplayed()
        assertSame(before, vm.state.value.game)
        assertEquals(MatchViewModel.SEED_ERROR, vm.state.value.seedError)
    }

    @Test
    fun activityRecreationKeepsTheSameViewModelAndDoesNotDrawAnotherPitch() {
        val vm = awaitReady()
        compose.onNodeWithTag(MatchTags.ADVANCE_PA).performClick()
        awaitReady()
        val before = vm.state.value.game
        compose.activityRule.scenario.recreate()
        val restored = awaitReady()
        compose.waitForIdle()
        assertSame(vm, restored)
        assertSame(before, restored.state.value.game)
        compose.onNodeWithTag(MatchTags.COUNT).assertIsDisplayed()
        compose.onNodeWithTag(MatchTags.ADVANCE_PITCH).performClick()
        awaitReady()
        assertEquals(before!!.pitchesAccepted + 1, restored.state.value.game!!.pitchesAccepted)
    }
}

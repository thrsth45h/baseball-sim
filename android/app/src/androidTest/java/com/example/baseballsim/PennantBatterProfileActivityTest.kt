package com.example.baseballsim

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PennantBatterProfileActivityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun ready(): PennantViewModel {
        compose.waitUntil(120_000) { val s = compose.activity.pennantViewModel.state.value; s.loaded && !s.busy }
        return compose.activity.pennantViewModel.also { assertNull(it.state.value.error) }
    }
    private fun click(tag: String) {
        val node = compose.onNodeWithTag(tag)
        if (tag != PennantTags.BACK) node.performScrollTo()
        node.performClick()
    }
    private fun home() { while (compose.onAllNodesWithTag(PennantTags.BACK).fetchSemanticsNodes().isNotEmpty()) click(PennantTags.BACK) }

    @Test fun applyingANonstandardStarterFreezesItsTypeAndRotationAndRapidCommandsAdvanceOnlyOnce() {
        compose.onNodeWithTag("mode_pennant").performClick(); val vm = ready(); home()
        val exists = vm.state.value.season != null
        if (exists) click(PennantTags.NAV_SETTINGS)
        compose.onNodeWithTag(PennantTags.SEED).performScrollTo().performTextReplacement("42")
        click(PennantTags.CREATE)
        if (exists) compose.onNodeWithTag(PennantTags.CONFIRM_CREATE).performClick()
        ready(); assertTrue(vm.state.value.season!!.profilesEnabled)
        click(PennantTags.NAV_LINEUP)
        click("lineup_replace_1"); compose.onNodeWithTag("lineup_reserve_L1-T01-P11").performClick()
        compose.onNodeWithTag("lineup_profile_1").assertTextEquals("ミート型")
        click(PennantTags.APPLY_LINEUP); ready()
        val profiles = vm.state.value.season!!.worldProfiles
        home(); click(PennantTags.OPEN); ready()
        compose.onNodeWithTag("pennant_batter_id").assertTextEquals("打者 L1-T01-P11")
        compose.onNodeWithTag("pennant_batter_profile").assertTextEquals("試作用タイプ · ミート型")
        val frozen = vm.state.value.season!!.active!!.profileInput
        compose.pennantScreenshot("batter-profile-match-frozen.png")
        compose.activityRule.scenario.recreate()
        assertSame(vm, compose.activity.pennantViewModel)
        compose.onNodeWithTag("pennant_batter_profile").assertTextEquals("試作用タイプ · ミート型")
        val before = vm.state.value.season!!.active!!.advanceCalls
        compose.runOnUiThread { repeat(8) { vm.execute(PennantCommand.PITCH) } }
        ready()
        assertEquals(before + 1, vm.state.value.season!!.active!!.advanceCalls)
        assertEquals(frozen, vm.state.value.season!!.active!!.profileInput)
        assertEquals(profiles, vm.state.value.season!!.worldProfiles)
    }
}

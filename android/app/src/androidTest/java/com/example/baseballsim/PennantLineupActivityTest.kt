package com.example.baseballsim

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PennantLineupActivityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun ready(): PennantViewModel {
        compose.waitUntil(120_000) { val s = compose.activity.pennantViewModel.state.value; s.loaded && !s.busy }
        return compose.activity.pennantViewModel.also { assertNull(it.state.value.error) }
    }
    private fun click(tag: String) = compose.clickPennant(tag)
    private fun home() { while (compose.onAllNodesWithTag(PennantTags.BACK).fetchSemanticsNodes().isNotEmpty()) click(PennantTags.BACK) }
    private fun fresh(): PennantViewModel {
        compose.onNodeWithTag("mode_pennant").performClick(); val vm = ready(); home()
        val exists = vm.state.value.season != null
        if (exists) click(PennantTags.NAV_SETTINGS)
        compose.onNodeWithTag(PennantTags.SEED).performScrollTo().performTextReplacement("42")
        click(PennantTags.CREATE)
        if (exists) compose.onNodeWithTag(PennantTags.CONFIRM_CREATE).performClick()
        ready(); assertTrue(vm.state.value.season!!.lineupEnabled); assertEquals(144, vm.state.value.season!!.playerCounts.size)
        return vm
    }
    @Test fun editingIsUiOnlyUntilApplyAndRotationAndBackDoNotWriteTheDraft() {
        val vm = fresh(); val before = vm.state.value.display
        // No bulk/external writer is active during this snapshot window. These are persisted
        // head/token/frame/ledger/slot/receipts, not just the ViewModel's published object.
        val beforeRows = targetPennantDatabaseRows(compose.activity.applicationContext)
        click(PennantTags.NAV_LINEUP)
        click("lineup_replace_1"); compose.onNodeWithTag("lineup_reserve_L1-T01-P10").performClick()
        compose.onNodeWithTag("lineup_player_1").assertTextEquals("1番  L1-T01-P10")
        assertSame(before, vm.state.value.display)
        compose.activityRule.scenario.recreate()
        assertSame(vm, compose.activity.pennantViewModel)
        compose.onNodeWithTag("lineup_player_1").assertTextEquals("1番  L1-T01-P10")
        click(PennantTags.BACK); click(PennantTags.NAV_LINEUP)
        compose.onNodeWithTag("lineup_player_1").assertTextEquals("1番  L1-T01-P01")
        assertSame(before, vm.state.value.display)
        assertEquals(beforeRows, targetPennantDatabaseRows(compose.activity.applicationContext))
        click("lineup_replace_1"); compose.onNodeWithTag("lineup_reserve_L1-T01-P10").performClick()
        click(PennantTags.APPLY_LINEUP); ready()
        assertEquals(before!!.stored.token.revision + 1, vm.state.value.display!!.stored.token.revision)
        assertEquals("L1-T01-P10", vm.state.value.season!!.lineup("L1-T01").first())
        assertEquals(before.stored.state.results, vm.state.value.season!!.results)
        compose.pennantScreenshot("lineup-applied.png")
        click(PennantTags.BACK); click(PennantTags.NAV_LINEUP); click(PennantTags.NAV_PLAYERS); click(PennantTags.player("L1-T01-P10"))
        compose.onNodeWithTag("player_slot").assertTextEquals("1番")
        home(); click(PennantTags.OPEN); ready()
        home(); click(PennantTags.NAV_LINEUP)
        compose.onNodeWithTag(PennantTags.APPLY_LINEUP).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("lineup_replace_1").performScrollTo().assertIsNotEnabled()
    }
}

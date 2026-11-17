package com.example.baseballsim

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.baseballsim.data.StoreToken
import com.example.baseballsim.data.StoredSeason
import com.example.baseballsim.season.SeasonState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PennantLineupScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun click(tag: String) {
        val node = compose.onNodeWithTag(tag)
        if (tag != PennantTags.BACK) node.performScrollTo()
        node.performClick()
    }
    @Test fun narrowEnlargedDraftRestorationAndRevisionChangeNeverApplyAutomatically() {
        val initial = PennantUiState(loaded = true, display = pennantDisplay(StoredSeason(StoreToken("world", "gen", 0), SeasonState.initialLineup(), "initial")))
        val ui = mutableStateOf(initial)
        var normalCommands = 0; var applies = 0
        val restore = StateRestorationTester(compose)
        restore.setContent { BaseballTheme {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                Box(Modifier.width(320.dp)) { PennantScreen(ui.value, {}, {}, { _, _, _ -> normalCommands++ }, {}, {}, { _, _, _ -> applies++ }) }
            }
        } }
        click(PennantTags.NAV_LINEUP)
        compose.pennantScreenshot("lineup-320-font150.png")
        click("lineup_replace_1"); compose.onNodeWithTag("lineup_reserve_L1-T01-P10").performClick()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("lineup_player_1").assertTextEquals("1番  L1-T01-P10")
        click(PennantTags.APPLY_LINEUP); assertEquals(1, applies)
        compose.runOnUiThread { ui.value = initial.copy(display = pennantDisplay(initial.display!!.stored.copy(token = StoreToken("world", "gen", 1)))) }
        compose.onNodeWithTag("lineup_player_1").performScrollTo().assertTextEquals("1番  L1-T01-P01")
        compose.onNodeWithTag(PennantTags.APPLY_LINEUP).performScrollTo().assertIsNotEnabled()
        assertEquals(1, applies)
        compose.runOnUiThread { ui.value = ui.value.copy(busy = true, bulk = true) }
        compose.onNodeWithTag(PennantTags.STOP).assertIsEnabled()
        compose.onNodeWithTag("lineup_replace_1").performScrollTo().assertIsNotEnabled()
        compose.pennantScreenshot("lineup-busy-320-font150.png")
        assertEquals(0, normalCommands)
    }
}

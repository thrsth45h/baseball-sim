package com.example.baseballsim

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
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
class PennantBatterProfileScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun click(tag: String) = compose.clickPennant(tag)
    @Test fun narrowEnlargedProfileDraftSurvivesRotationWithoutChangingThePublishedPersonTypes() {
        val world = SeasonState.initialBatterProfile()
        val initial = PennantUiState(loaded = true, display = pennantDisplay(StoredSeason(StoreToken("world", "generation", 0), world, "initial")))
        var applies = 0
        val restore = StateRestorationTester(compose)
        restore.setContent { BaseballTheme {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                Box(Modifier.width(320.dp)) { PennantScreen(initial, {}, {}, { _, _, _ -> }, {}, {}, { _, _, _ -> applies++ }) }
            }
        } }
        click(PennantTags.NAV_LINEUP)
        compose.onNodeWithTag("lineup_profile_1").assertTextEquals("標準")
        compose.onNodeWithTag("lineup_profile_2").assertTextEquals("ミート型")
        compose.onNodeWithTag("lineup_profile_3").assertTextEquals("長打型")
        compose.pennantScreenshot("batter-profile-lineup-320-font150.png")
        click("lineup_replace_1"); compose.onNodeWithTag("lineup_reserve_L1-T01-P11").performClick()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("lineup_player_1").assertTextEquals("1番  L1-T01-P11")
        compose.onNodeWithTag("lineup_profile_1").assertTextEquals("ミート型")
        assertEquals("standard", world.playerProfile("L1-T01-P01")); assertEquals(0, applies)
        click(PennantTags.BACK); click(PennantTags.NAV_LINEUP)
        compose.onNodeWithTag("lineup_player_1").assertTextEquals("1番  L1-T01-P01")
        compose.onNodeWithTag("lineup_profile_1").assertTextEquals("標準")
        click(PennantTags.BACK); click(PennantTags.NAV_LINEUP); click(PennantTags.NAV_PLAYERS); click(PennantTags.player("L1-T01-P03"))
        compose.onNodeWithTag("player_profile").assertTextEquals("長打型")
        compose.pennantScreenshot("batter-profile-player-320-font150.png")
        assertEquals(0, applies)
    }
}

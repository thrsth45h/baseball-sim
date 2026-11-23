package com.example.baseballsim

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
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
class PennantRestorationTest {
    @get:Rule val compose = createComposeRule()
    private fun click(tag: String) = compose.clickPennant(tag)
    private fun offset(): Float = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
    @Test fun scrollAndRestoredCandidatesRespectLoadingErrorsRevisionWorldAndGenerationBoundaries() {
        val original = PennantUiState(loaded = true, hasSlot = true,
            display = pennantDisplay(StoredSeason(StoreToken("restore-world", "restore-generation", 0), SeasonState.initial(), "original")))
        val state = mutableStateOf(original)
        var commands = 0
        val restoration = StateRestorationTester(compose)
        restoration.setContent { BaseballTheme {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                Box(Modifier.width(320.dp)) { PennantScreen(state.value, {}, {}, { _, _, _ -> commands++ }, {}, {}) }
            }
        } }
        click(PennantTags.NAV_LINEUP); click(PennantTags.NAV_PLAYERS)
        compose.onNodeWithTag(PennantTags.player("L1-T01-P09")).performScrollTo()
        val listOffset = offset()
        assertTrue("The narrow enlarged list must actually scroll", listOffset > 0f)
        click(PennantTags.player("L1-T01-P09"))
        restoration.emulateSavedInstanceStateRestore()
        click(PennantTags.BACK)
        assertEquals(listOffset, offset(), 1f)
        click(PennantTags.BACK); click(PennantTags.NAV_LINEUP); click(PennantTags.NAV_PLAYERS)
        assertEquals(0f, offset(), 1f)
        click(PennantTags.player("L1-T01-P09"))
        compose.runOnUiThread { state.value = PennantUiState() }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnUiThread { state.value = PennantUiState(loaded = true, error = "読み込み失敗") }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag(PennantTags.ERROR).assertIsDisplayed()
        compose.runOnUiThread { state.value = original }
        compose.onNodeWithTag(PennantTags.PLAYER_DETAIL).assertExists()
        compose.onNodeWithTag("player_id").assertTextEquals("L1-T01-P09")
        val revision = original.display!!.stored.copy(token = original.display.stored.token.copy(revision = 2))
        compose.runOnUiThread { state.value = original.copy(display = pennantDisplay(revision)) }
        compose.onNodeWithTag("player_id").assertTextEquals("L1-T01-P09")
        val newWorld = revision.copy(token = revision.token.copy(worldId = "same-seed-new-world"), state = SeasonState.initial(selectedClubId = "L2-T06"))
        compose.runOnUiThread { state.value = original.copy(display = pennantDisplay(newWorld)) }
        compose.onNodeWithTag(PennantTags.HOME).assertExists()
        click(PennantTags.NAV_LINEUP); click(PennantTags.NAV_PLAYERS)
        compose.onNodeWithTag(PennantTags.BROWSE_CLUB).assertTextEquals("閲覧球団：仮球団12")
        assertEquals(0f, offset(), 1f)
        click(PennantTags.BACK); click(PennantTags.NAV_SETTINGS); click(PennantTags.CREATE)
        compose.onNodeWithTag(PennantTags.CONFIRM_CREATE).assertExists()
        val loaded = newWorld.copy(token = newWorld.token.copy(generationId = "slot-load-generation"))
        compose.runOnUiThread { state.value = original.copy(display = pennantDisplay(loaded)) }
        compose.onNodeWithTag(PennantTags.HOME).assertExists()
        compose.onNodeWithTag(PennantTags.CONFIRM_CREATE).assertDoesNotExist()
        click(PennantTags.NAV_SETTINGS); click(PennantTags.CREATE)
        compose.runOnUiThread { state.value = original.copy(display = pennantDisplay(loaded.copy(token = loaded.token.copy(revision = 3)))) }
        compose.onNodeWithTag(PennantTags.CONFIRM_CREATE).assertDoesNotExist()
        compose.onNodeWithTag(PennantTags.SEED).assertExists()
        assertEquals(0, commands)
    }
}

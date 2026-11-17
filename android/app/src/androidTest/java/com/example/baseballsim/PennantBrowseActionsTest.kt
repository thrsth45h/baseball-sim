package com.example.baseballsim

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.baseballsim.data.StoreToken
import com.example.baseballsim.data.StoredSeason
import com.example.baseballsim.season.*
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class PennantBrowseActionsTest {
    @get:Rule val compose = createComposeRule()
    private class Fake : PennantActions {
        val reads = AtomicInteger(); val slots = AtomicInteger(); val commands = AtomicInteger()
        val gate = CompletableDeferred<Unit>()
        @Volatile var sawStop = false
        var current = StoredSeason(StoreToken("world", "generation", 0), SeasonState.initial(), "initial")
        override suspend fun read(): StoredSeason { reads.incrementAndGet(); return current }
        override suspend fun hasSlot(): Boolean { slots.incrementAndGet(); return false }
        override suspend fun execute(command: PennantCommand, seed: String, club: String, replacement: Boolean,
            stop: () -> Boolean, progress: suspend (StoredSeason) -> Unit): StoredSeason {
            commands.incrementAndGet(); check(command == PennantCommand.SEASON)
            gate.await(); sawStop = stop()
            current = current.copy(token = current.token.copy(revision = 1), state = SeasonSession(current.state).finishCurrent(), operationId = "bulk-one-game")
            progress(current)
            return current
        }
        fun calls() = listOf(reads.get(), slots.get(), commands.get())
    }
    private fun click(tag: String) {
        val node = compose.onNodeWithTag(tag)
        if (tag !in listOf(PennantTags.BACK, PennantTags.STOP, PennantTags.RELOAD)) node.performScrollTo()
        node.performClick()
    }
    @Test fun navigationRestorationAndBusyBrowsingNeverSendAnotherActionOrStealTheSelectedPage() {
        val fake = Fake(); val owner = ViewModelStore()
        lateinit var vm: PennantViewModel
        compose.runOnUiThread { vm = PennantViewModel(fake); owner.put("fake", vm) }
        try {
            val restoration = StateRestorationTester(compose)
            restoration.setContent { BaseballTheme { PennantRoute(vm) } }
            compose.waitUntil(15_000) { vm.state.value.loaded && !vm.state.value.busy }
            val baseline = fake.calls(); val initial = vm.state.value.display
            click(PennantTags.NAV_PLAYERS); click(PennantTags.player("L1-T01-P09"))
            compose.onNodeWithTag("player_completed_pa").assertTextEquals("0")
            restoration.emulateSavedInstanceStateRestore()
            compose.onNodeWithTag(PennantTags.PLAYER_DETAIL).assertExists()
            click(PennantTags.BACK); click(PennantTags.BACK)
            assertEquals(baseline, fake.calls()); assertSame(initial, vm.state.value.display)
            click(PennantTags.SEASON)
            // Busy is published before the worker coroutine enters execute(). Observe
            // that first command before taking the no-additional-actions baseline.
            compose.waitUntil(15_000) { vm.state.value.busy && vm.state.value.bulk && fake.commands.get() == 1 }
            val busyCalls = fake.calls()
            click(PennantTags.NAV_PLAYERS); click(PennantTags.player("L1-T01-P09"))
            compose.onNodeWithTag(PennantTags.STOP).assertIsEnabled()
            restoration.emulateSavedInstanceStateRestore()
            compose.onNodeWithTag(PennantTags.PLAYER_DETAIL).assertExists()
            assertEquals(busyCalls, fake.calls())
            click(PennantTags.STOP)
            compose.onNodeWithTag(PennantTags.STOP).assertIsNotEnabled()
            compose.pennantScreenshot("pennant-busy-player-stop.png")
            fake.gate.complete(Unit)
            compose.waitUntil(15_000) { !vm.state.value.busy }
            assertTrue(fake.sawStop); assertEquals(1, fake.commands.get())
            assertEquals(1, vm.state.value.season!!.results.size)
            compose.onNodeWithTag(PennantTags.PLAYER_DETAIL).assertExists()
            compose.onNodeWithText("L1-T01-P09", substring = true).assertExists()
            val count = vm.state.value.season!!.playerCounts.single { it.playerId == "L1-T01-P09" }.counts
            assertTrue(count.completedPlateAppearances > 0)
            listOf("player_completed_pa" to count.completedPlateAppearances, "player_safe_hits" to count.safeHitEvents,
                "player_walks" to count.walkEvents, "player_strikeouts" to count.strikeoutEvents,
                "player_home_runs" to count.homeRunEvents).forEach { (tag, value) -> compose.onNodeWithTag(tag).assertTextEquals(value.toString()) }
            click(PennantTags.BACK); click(PennantTags.BACK)
            assertEquals("L1-T01", vm.state.value.season!!.selectedClubId)
        } finally { fake.gate.complete(Unit); compose.runOnUiThread { owner.clear() } }
    }
}

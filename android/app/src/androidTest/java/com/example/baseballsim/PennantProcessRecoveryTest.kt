package com.example.baseballsim

import android.os.Process
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.baseballsim.data.StoreToken
import com.example.baseballsim.season.GameCheckpoint
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Host-only: standard connected tests explicitly exclude this class. */
@RunWith(AndroidJUnit4::class)
class PennantProcessRecoveryTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun ready(): PennantViewModel {
        compose.waitUntil(120_000) { val s = compose.activity.pennantViewModel.state.value; s.loaded && !s.busy }
        return compose.activity.pennantViewModel.also { assertNull(it.state.value.error) }
    }
    private fun evidence() = File(context.filesDir, "pennant-process-expected.json")
    @Test fun prepareDurableCheckpointBeforeHostKill() {
        compose.onNodeWithTag("mode_pennant").performClick(); val vm = ready()
        compose.runOnUiThread { vm.changeSeed("42"); vm.execute(PennantCommand.CREATE, replacementConfirmed = true) }; ready()
        compose.runOnUiThread { vm.execute(PennantCommand.OPEN_SELECTED) }; ready()
        compose.runOnUiThread { vm.execute(PennantCommand.PLATE_APPEARANCE) }; ready()
        compose.runOnUiThread { vm.execute(PennantCommand.PITCH) }; ready()
        val stored = vm.state.value.display!!.stored; val active = stored.state.active!!
        assertTrue(active.snapshot.pitchesAccepted > 0)
        val proof = JSONObject().put("pid", Process.myPid()).put("world", stored.token.worldId)
            .put("generation", stored.token.generationId).put("revision", stored.token.revision)
            .put("game_id", active.game.fixture.gameId).put("checkpoint_sha256", active.stateHash)
            .put("pitches", active.snapshot.pitchesAccepted).put("advance_calls", active.advanceCalls)
        evidence().writeText(proof.toString(2))
        compose.pennantScreenshot("pennant-before-process-kill.png")
    }
    @Test fun recoverTheSamePublishedCheckpointAndAdvanceExactlyOnce() {
        val expected = JSONObject(evidence().readText())
        assertNotEquals("Host must really terminate the old app process", expected.getInt("pid"), Process.myPid())
        compose.onNodeWithTag("mode_pennant").performClick(); val vm = ready()
        val stored = vm.state.value.display!!.stored; val active = stored.state.active!!
        assertEquals(StoreToken(expected.getString("world"), expected.getString("generation"), expected.getLong("revision")), stored.token)
        assertEquals(expected.getString("game_id"), active.game.fixture.gameId)
        assertEquals(expected.getString("checkpoint_sha256"), active.stateHash)
        assertEquals(expected.getLong("pitches"), active.snapshot.pitchesAccepted)
        assertEquals(expected.getLong("advance_calls"), active.advanceCalls)
        val replay = active.recover(stored.state.masterSeed); replay.advanceOnePitch()
        val expectedNext: GameCheckpoint = replay.checkpoint()
        compose.runOnUiThread { vm.execute(PennantCommand.PITCH) }; ready()
        val next = vm.state.value.display!!.stored
        assertEquals(expectedNext, next.state.active)
        assertEquals(stored.token.revision + 1, next.token.revision)
        val proof = JSONObject().put("previous_pid", expected.getInt("pid")).put("new_pid", Process.myPid())
            .put("saved_checkpoint_sha256", active.stateHash).put("next_checkpoint_sha256", next.state.active!!.stateHash)
            .put("same_world_generation_and_checkpoint", true).put("one_additional_pitch_only", true)
        File(context.filesDir, "pennant-process-proof.json").writeText(proof.toString(2))
        compose.pennantScreenshot("pennant-after-process-kill.png")
    }
}

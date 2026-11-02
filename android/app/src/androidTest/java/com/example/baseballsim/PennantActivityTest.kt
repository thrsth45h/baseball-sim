package com.example.baseballsim

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.baseballsim.season.SeasonStatus
import com.example.baseballsim.season.CheckpointCodec
import com.example.baseballsim.data.PennantDatabase
import java.io.File
import java.security.MessageDigest
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
internal fun ComposeContentTestRule.pennantScreenshot(name: String) {
    waitForIdle()
    val directory = pennantTestOutputDirectory()
    File(directory, name).outputStream().use { stream ->
        check(onNodeWithTag(PennantTags.SCREEN).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream))
    }
}
internal fun pennantTestOutputDirectory(): File {
    val registry = InstrumentationRegistry.getInstrumentation()
    val configured = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
    val directory = if (configured.isNullOrBlank()) File(requireNotNull(registry.targetContext.getExternalFilesDir(null)), "pennant-screenshots") else File(configured)
    check(directory.isDirectory || directory.mkdirs())
    return directory
}

@RunWith(AndroidJUnit4::class)
class PennantActivityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun ready(timeout: Long = 90_000): PennantViewModel {
        compose.waitUntil(timeout) { val s = compose.activity.pennantViewModel.state.value; s.loaded && !s.busy }
        return compose.activity.pennantViewModel.also { assertNull(it.state.value.error) }
    }
    private fun click(tag: String) = compose.onNodeWithTag(tag).performScrollTo().performClick()
    private fun fresh(): PennantViewModel {
        compose.onNodeWithTag("mode_pennant").performClick()
        val vm = ready()
        compose.onNodeWithTag(PennantTags.SEED).performScrollTo().performTextReplacement("42")
        val exists = vm.state.value.season != null
        click(PennantTags.CREATE)
        if (exists) compose.onNodeWithTag(PennantTags.CONFIRM_CREATE).performClick()
        ready()
        assertEquals("42", vm.state.value.season!!.masterSeed)
        assertEquals(0, vm.state.value.season!!.results.size)
        return vm
    }
    @Test fun realActivityUsesAllThreeMatchOperationsAndRetainsRotationState() {
        val vm = fresh()
        compose.pennantScreenshot("pennant-created.png")
        click(PennantTags.OPEN); ready()
        assertEquals(0L, vm.state.value.season!!.active!!.snapshot.pitchesAccepted)
        click(PennantTags.PITCH); ready()
        assertEquals(1L, vm.state.value.season!!.active!!.snapshot.pitchesAccepted)
        val beforePa = vm.state.value.season!!.active!!.snapshot.batterCounts.sumOf { it.completedPlateAppearances }
        click(PennantTags.PA); ready()
        assertEquals(beforePa + 1, vm.state.value.season!!.active!!.snapshot.batterCounts.sumOf { it.completedPlateAppearances })
        val before = vm.state.value.display
        compose.activityRule.scenario.recreate(); ready()
        assertSame(vm, compose.activity.pennantViewModel); assertSame(before, vm.state.value.display)
        compose.pennantScreenshot("pennant-active.png")
        click(PennantTags.FINISH_GAME); ready()
        assertEquals(1, vm.state.value.season!!.results.size); assertNull(vm.state.value.season!!.active)
        compose.onNodeWithTag(PennantTags.COUNT).assertDoesNotExist()
        compose.onNodeWithTag(PennantTags.PITCH).assertIsNotEnabled()
    }
    @Test fun manualSaveLoadIsOutsideMatchesAndChangesTheDurableGeneration() {
        val vm = fresh()
        click(PennantTags.SAVE); ready()
        val savedGeneration = vm.state.value.display!!.stored.token.generationId
        click(PennantTags.OPEN); ready(); click(PennantTags.PITCH); ready()
        compose.onNodeWithTag(PennantTags.SAVE).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag(PennantTags.LOAD).performScrollTo().assertIsNotEnabled()
        click(PennantTags.FINISH_GAME); ready()
        click(PennantTags.LOAD); compose.onNodeWithTag(PennantTags.CONFIRM_LOAD).performClick(); ready()
        assertEquals(0, vm.state.value.season!!.results.size)
        assertNotEquals(savedGeneration, vm.state.value.display!!.stored.token.generationId)
        assertNull(vm.state.value.season!!.active)
    }
    @Test fun invalidSeedAndStoppedBulkRequestLeaveTheCommittedGameUntouched() {
        val vm = fresh(); val before = vm.state.value.display
        compose.onNodeWithTag(PennantTags.SEED).performScrollTo().performTextReplacement("18446744073709551616")
        click(PennantTags.CREATE); compose.onNodeWithTag(PennantTags.CONFIRM_CREATE).performClick()
        assertSame(before, vm.state.value.display); assertNotNull(vm.state.value.seedError)
        compose.runOnUiThread { vm.execute(PennantCommand.SEASON); vm.requestStop() }
        ready()
        assertEquals(0, vm.state.value.season!!.results.size); assertNull(vm.state.value.season!!.active)
    }
    @Test fun calendarStandingsAndAllClubsRecordsAreReachable() {
        val vm = fresh()
        click(PennantTags.DAY); ready(); assertEquals(2, vm.state.value.season!!.dayIndex)
        compose.onNodeWithText("順位", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithTag(PennantTags.STANDINGS).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("リーグ2").performClick()
        compose.pennantScreenshot("pennant-standings.png")
        compose.onNodeWithText("記録", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithTag(PennantTags.RECORDS).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("記録を見る球団：仮球団1").performClick()
        compose.onNodeWithText("仮球団12", useUnmergedTree = true).performScrollTo().performClick()
        assertEquals(108, vm.state.value.season!!.playerCounts.size)
        compose.pennantScreenshot("pennant-records.png")
    }
    @Test fun full720GameSkipPersistsAndPublishesTheFinalSeason() {
        val vm = fresh()
        val started = SystemClock.elapsedRealtime()
        click(PennantTags.SEASON); ready(1_200_000)
        val state = vm.state.value.season!!
        assertEquals(SeasonStatus.COMPLETE, state.status); assertEquals(720, state.results.size); assertEquals(141, state.dayIndex)
        assertNull(state.active); assertTrue(state.standings.all { it.totals.played == 120L })
        assertEquals(108, state.playerCounts.size)
        val frame = CheckpointCodec.encodeSeason(state)
        val directory = pennantTestOutputDirectory()
        File(directory, "pennant-real-season-42.bin").writeBytes(frame)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = context.getDatabasePath(PennantDatabase.FILE_NAME)
        val evidence = JSONObject().put("schema", "pennant-real-transactions-v1").put("master_seed", state.masterSeed)
            .put("completed_games", state.results.size).put("day_index", state.dayIndex)
            .put("elapsed_ms", SystemClock.elapsedRealtime() - started).put("frame_bytes", frame.size)
            .put("frame_sha256", MessageDigest.getInstance("SHA-256").digest(frame).joinToString("") { "%02x".format(it) })
            .put("database_bytes", database.length()).put("wal_bytes", File(database.path + "-wal").length())
            .put("standings", JSONArray().apply { state.standings.forEach { row ->
                val t = row.totals
                put(JSONObject().put("club_id", t.clubId).put("rank", row.rank ?: JSONObject.NULL).put("played", t.played)
                    .put("wins", t.wins).put("losses", t.losses).put("draws", t.draws)
                    .put("runs_for", t.runsFor).put("runs_against", t.runsAgainst))
            } }).put("players", JSONArray().apply { state.playerCounts.forEach { row ->
                val c = row.counts
                put(JSONObject().put("player_id", row.playerId).put("club_id", row.clubId).put("slot", row.slot)
                    .put("completed_plate_appearances", c.completedPlateAppearances).put("safe_hit_events", c.safeHitEvents)
                    .put("walk_events", c.walkEvents).put("strikeout_events", c.strikeoutEvents).put("home_run_events", c.homeRunEvents))
            } }).put("full_frame_file", "pennant-real-season-42.bin").put("physical_device", false)
        File(directory, "pennant-real-season-42.json").writeText(evidence.toString(2))
        compose.onNodeWithTag(PennantTags.PROGRESS).performScrollTo().assertTextEquals("720 / 720試合完了 · seed 42")
        compose.pennantScreenshot("pennant-season-completed.png")
        compose.runOnUiThread { vm.reload() }; ready()
        assertEquals(state, vm.state.value.season)
    }
}

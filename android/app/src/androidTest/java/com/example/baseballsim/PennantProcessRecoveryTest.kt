package com.example.baseballsim

import android.os.Process
import android.os.SystemClock
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.baseballsim.data.StoreToken
import com.example.baseballsim.data.*
import com.example.baseballsim.season.GameCheckpoint
import com.example.baseballsim.season.CheckpointCodec
import com.example.baseballsim.season.SeasonSession
import com.example.baseballsim.season.SeasonState
import androidx.room.withTransaction
import kotlinx.coroutines.runBlocking
import java.security.MessageDigest
import java.util.UUID
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Host-only: standard connected tests explicitly exclude this class. */
@RunWith(AndroidJUnit4::class)
class PennantProcessRecoveryTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val setup: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            val args = InstrumentationRegistry.getArguments()
            if (args.getString("world_format") == "annual" &&
                args.getString("class")?.endsWith("#prepareDurableCheckpointBeforeHostKill") == true) {
                runBlocking { prepareAnnualWorld() }
            }
        }
    }).around(compose)
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun ready(): PennantViewModel {
        compose.waitUntil(120_000) { val s = compose.activity.pennantViewModel.state.value; s.loaded && !s.busy }
        return compose.activity.pennantViewModel.also { assertNull(it.state.value.error) }
    }
    private fun evidence() = File(context.filesDir, "pennant-process-expected.json")
    /** Diagnostic only: canonical in-memory prefix fixtures are not a 720-transaction acceptance run. */
    @Test fun profileRealStoreCostsAtCanonicalPrefixBoundaries() = runBlocking {
        val output = File(pennantTestOutputDirectory(), "pennant-store-cost-profile.json")
        val samples = JSONArray()
        fun persist() { output.writeText(JSONObject().put("schema", "pennant-store-cost-profile-v1")
            .put("fixture_prefixes_are_not_transactional_progress", true).put("samples", samples).toString(2)) }
        suspend fun <T> timed(prefix: Int, phase: String, block: suspend () -> T): T {
            val started = SystemClock.elapsedRealtimeNanos()
            val value = block()
            val runtime = Runtime.getRuntime()
            val row = JSONObject().put("prefix", prefix).put("phase", phase)
                .put("elapsed_ms", (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000.0)
                .put("used_heap_bytes", runtime.totalMemory() - runtime.freeMemory())
            samples.put(row); persist(); android.util.Log.i("PennantStoreProfile", row.toString())
            return value
        }
        fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val memory = SeasonSession()
        for (prefix in listOf(0, 100, 300)) {
            val fixture = timed(prefix, "in_memory_fixture_setup") {
                while (memory.state.results.size < prefix) memory.finishCurrent()
                memory.state
            }
            val name = "pennant-profile-${UUID.randomUUID()}.db"
            var database = PennantDatabase.open(context, name)
            try {
                val world = UUID.randomUUID().toString(); val generation = UUID.randomUUID().toString()
                timed(prefix, "fixture_database_setup") {
                    val bytes = CheckpointCodec.encodeSeason(fixture)
                    database.withTransaction {
                        val dao = database.pennantDao()
                        dao.insertGeneration(GenerationEntity(generation, world))
                        dao.insertHead(WorkingEntity(worldId = world, generationId = generation, revision = 0,
                            operationId = "profile-setup", payloadHash = digest(bytes), payload = bytes))
                        dao.insertReceipt(ReceiptEntity("profile-setup", "profile-fixture", world, generation, 0))
                        fixture.results.forEach { result ->
                            val body = CheckpointCodec.encodeSummary(result); val f = result.game.fixture
                            dao.insertResult(ResultEntity(world, generation, f.seasonId, f.gameId, f.ordinal, digest(body), body))
                        }
                    }
                }
                database.close(); database = PennantDatabase.open(context, name)
                val store = RoomSeasonStore(database)
                var current = timed(prefix, "cold_read") { requireNotNull(store.read()) }
                repeat(3) { current = timed(prefix, "warm_read_$it") { requireNotNull(store.read()) } }
                repeat(2) { index ->
                    val marker = timed(prefix, "marker_calculation_$index") { current.state.beginNext() }
                    current = timed(prefix, "marker_commit_$index") { store.commit(current.token, UUID.randomUUID().toString(), marker) }
                    val result = timed(prefix, "game_calculation_$index") { SeasonSession(current.state).finishCurrent() }
                    current = timed(prefix, "result_commit_$index") { store.commit(current.token, UUID.randomUUID().toString(), result) }
                    assertEquals(prefix + index + 1, current.state.results.size)
                }
            } finally {
                database.close(); context.deleteDatabase(name)
            }
        }
        persist()
    }
    @Test fun prepareDurableCheckpointBeforeHostKill() {
        val format = InstrumentationRegistry.getArguments().getString("world_format") ?: "v1"
        require(format in setOf("v1", "v2", "v3", "annual"))
        compose.onNodeWithTag("mode_pennant").performClick(); val vm = ready()
        if (format == "v3") {
            compose.runOnUiThread { vm.changeSeed("42"); vm.execute(PennantCommand.CREATE, replacementConfirmed = true) }; ready()
        } else if (format != "annual") {
            runBlocking { if (format == "v2") createLineupTargetWorld() else createLegacyTargetWorld() }
            compose.runOnUiThread { vm.reload() }; ready()
        }
        if (format == "v2" || format == "v3") {
            val token = vm.state.value.display!!.stored.token
            // v3 uses a nonstandard starter; v2 retains its earlier P10 checkpoint reference.
            val people = (listOf(if (format == "v3") 11 else 10) + (2..9)).map { "L1-T01-P${it.toString().padStart(2, '0')}" }
            compose.runOnUiThread { vm.applyLineup(token, "L1-T01", people) }; ready()
        }
        assertEquals(format != "v1", vm.state.value.season!!.lineupEnabled)
        assertEquals(format in setOf("v3", "annual"), vm.state.value.season!!.profilesEnabled)
        compose.runOnUiThread { vm.execute(PennantCommand.OPEN_SELECTED) }; ready()
        compose.runOnUiThread { vm.execute(PennantCommand.PLATE_APPEARANCE) }; ready()
        compose.runOnUiThread { vm.execute(PennantCommand.PITCH) }; ready()
        val stored = vm.state.value.display!!.stored; val active = stored.state.active!!
        assertTrue(active.snapshot.pitchesAccepted > 0)
        val proof = JSONObject().put("pid", Process.myPid()).put("world", stored.token.worldId)
            .put("generation", stored.token.generationId).put("revision", stored.token.revision)
            .put("game_id", active.game.fixture.gameId).put("checkpoint_sha256", active.stateHash)
            .put("pitches", active.snapshot.pitchesAccepted).put("advance_calls", active.advanceCalls)
            .put("world_format", format).put("lineup_world", format != "v1").put("profiles_world", format in setOf("v3", "annual"))
            .put("away_lineup", JSONArray(active.lineup.away)).put("home_lineup", JSONArray(active.lineup.home))
        active.profileInput?.let {
            proof.put("away_profiles", JSONArray(it.away.map { p -> p.profileId })).put("home_profiles", JSONArray(it.home.map { p -> p.profileId }))
                .put("table_version", it.tableVersion).put("source_version", it.sourceVersion)
            if (format == "v3") assertEquals("contact", it.profile("home", 1))
        }
        if (format == "annual") {
            val timeline = stored.timeline!!
            assertEquals(2L, timeline.year)
            assertEquals(listOf(1L), runBlocking { vm.historyYears(stored.token) })
            proof.put("annual_year", timeline.year).put("root_seed", timeline.rootSeed).put("previous_archive", timeline.previous)
        }
        compose.pennantScreenshot("pennant-before-process-kill.png")
        // This is intentionally a live setup probe, not a completed JUnit assertion case.
        // The host kills this exact process after observing the ready record and pidof.
        proof.put("prepare_probe_waiting_for_host", true)
        val temporary = File(context.filesDir, "pennant-process-expected.tmp")
        temporary.writeText(proof.toString(2)); check(temporary.renameTo(evidence()))
        val deadline = SystemClock.elapsedRealtime() + 180_000
        while (SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(200)
        fail("The CI host did not terminate the live preparation process")
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
        assertEquals(expected.getBoolean("lineup_world"), stored.state.lineupEnabled)
        assertEquals(expected.getBoolean("profiles_world"), stored.state.profilesEnabled)
        assertEquals(expected.getJSONArray("away_lineup").toString(), JSONArray(active.lineup.away).toString())
        assertEquals(expected.getJSONArray("home_lineup").toString(), JSONArray(active.lineup.home).toString())
        active.profileInput?.let {
            assertEquals(expected.getJSONArray("away_profiles").toString(), JSONArray(it.away.map { p -> p.profileId }).toString())
            assertEquals(expected.getJSONArray("home_profiles").toString(), JSONArray(it.home.map { p -> p.profileId }).toString())
            assertEquals(expected.getString("table_version"), it.tableVersion); assertEquals(expected.getString("source_version"), it.sourceVersion)
        }
        val annual = expected.getString("world_format") == "annual"
        if (annual) {
            assertEquals(SeasonTimeline(expected.getString("root_seed"), expected.getLong("annual_year"),
                expected.getString("previous_archive")), stored.timeline)
            assertEquals(listOf(1L), runBlocking { vm.historyYears(stored.token) })
        }
        val replay = active.recover(stored.state.masterSeed); replay.advanceOnePitch()
        val expectedNext: GameCheckpoint = replay.checkpoint()
        compose.runOnUiThread { vm.execute(PennantCommand.PITCH) }; ready()
        val next = vm.state.value.display!!.stored
        assertEquals(expectedNext, next.state.active)
        assertEquals(stored.token.revision + 1, next.token.revision)
        val proof = JSONObject().put("previous_pid", expected.getInt("pid")).put("new_pid", Process.myPid())
            .put("saved_checkpoint_sha256", active.stateHash).put("next_checkpoint_sha256", next.state.active!!.stateHash)
            .put("same_world_generation_and_checkpoint", true).put("one_additional_pitch_only", true)
        if (annual) {
            assertEquals(stored.timeline, next.timeline)
            assertEquals(listOf(1L), runBlocking { vm.historyYears(next.token) })
            proof.put("annual_year", next.timeline!!.year).put("previous_archive", next.timeline!!.previous)
        }
        File(context.filesDir, "pennant-process-proof.json").writeText(proof.toString(2))
        compose.pennantScreenshot("pennant-after-process-kill.png")
    }

    private suspend fun prepareAnnualWorld() {
        val name = "annual-recovery-fixture-${UUID.randomUUID()}.db"
        installCompletedV3Fixture(context, name)
        val fixture = PennantDatabase.open(context, name)
        val completed = try { RoomSeasonStore(fixture).read()!!.state }
        finally { fixture.close(); context.deleteDatabase(name) }
        assertEquals(completedV3FrameHash, hash(CheckpointCodec.encodeSeason(completed)))
        val database = PennantDatabase.open(context)
        try {
            val store = RoomSeasonStore(database)
            val before = store.read()!!
            val positioned = replaceFixture(database, before, completed, TimelineColumns())
            assertEquals(2L, SeasonCoordinator(store).nextYear(positioned).timeline?.year)
        } finally { database.close() }
    }
}

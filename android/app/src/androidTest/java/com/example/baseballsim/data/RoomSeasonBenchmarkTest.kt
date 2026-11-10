package com.example.baseballsim.data

import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.baseballsim.season.CheckpointCodec
import com.example.baseballsim.season.SeasonSession
import com.example.baseballsim.season.SeasonState
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.UUID

/** Host-only ABBA diagnostic; standard connected tests explicitly exclude this class. */
@RunWith(AndroidJUnit4::class)
class RoomSeasonBenchmarkTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val rows = JSONArray()
    private val fixtureRecords = JSONArray()
    private val output: File get() {
        val path = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?: context.getExternalFilesDir(null)!!.absolutePath
        return File(path, "pennant-store-abba.json").also { it.parentFile!!.mkdirs() }
    }
    private val world = "benchmark-world"
    private val generation = "benchmark-generation"
    private val setupOperation = "benchmark-setup"
    private val operation = "benchmark-commit"
    private val fingerprint = "benchmark-fixture"
    private val token get() = StoreToken(world, generation, 0)

    private data class Fixture(val state: SeasonState, val frame: ByteArray, val ledger: List<ResultEntity>, val inputHash: String)
    private fun fixture(state: SeasonState): Fixture {
        val frame = CheckpointCodec.encodeSeason(state)
        val ledger = state.results.map { result ->
            val bytes = CheckpointCodec.encodeSummary(result)
            ResultEntity(world, generation, result.game.fixture.seasonId, result.game.fixture.gameId,
                result.game.fixture.ordinal, hash(bytes), bytes)
        }
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { stream ->
            fun text(value: String) { val raw = value.toByteArray(Charsets.UTF_8); stream.writeInt(raw.size); stream.write(raw) }
            text(world); text(generation); stream.writeLong(0); text(setupOperation); text(fingerprint); text(operation)
            text(hash(frame)); stream.writeInt(frame.size); stream.write(frame); stream.writeInt(ledger.size)
            ledger.forEach { row ->
                text(row.worldId); text(row.generationId); text(row.seasonId); text(row.gameId); stream.writeInt(row.ordinal)
                text(row.payloadHash); stream.writeInt(row.payload.size); stream.write(row.payload)
            }
        }
        return Fixture(state, frame, ledger, hash(bytes.toByteArray()))
    }
    private suspend fun prepare(db: PennantDatabase, input: Fixture) {
        db.withTransaction {
            val dao = db.pennantDao()
            dao.insertGeneration(GenerationEntity(generation, world))
            dao.insertHead(WorkingEntity(worldId = world, generationId = generation, revision = 0,
                operationId = setupOperation, payloadHash = hash(input.frame), payload = input.frame.copyOf()))
            dao.insertReceipt(ReceiptEntity(setupOperation, fingerprint, world, generation, 0))
            input.ledger.forEach { dao.insertResult(it.copy(payload = it.payload.copyOf())) }
        }
        // Setup checks are outside every measured interval. Full byte equality is checked,
        // not just the identity token or fixture hash.
        val dao = db.pennantDao()
        val head = dao.head()!!
        assertEquals(WorkingEntity(worldId = world, generationId = generation, revision = 0,
            operationId = setupOperation, payloadHash = hash(input.frame), payload = head.payload), head)
        assertArrayEquals(input.frame, head.payload)
        assertEquals(ReceiptEntity(setupOperation, fingerprint, world, generation, 0), dao.receipt(setupOperation))
        val actual = dao.results(world, generation)
        assertEquals(input.ledger.size, actual.size)
        input.ledger.zip(actual).forEach { (expected, row) ->
            assertEquals(expected.copy(payload = row.payload), row); assertArrayEquals(expected.payload, row.payload)
        }
    }
    private fun store(variant: String, db: PennantDatabase): SeasonStore = when (variant) {
        "A" -> BaselineRoomSeasonStore(db)
        "B" -> CandidateRoomSeasonStore(db)
        else -> error("Unknown benchmark variant")
    }
    private fun persist() {
        val record = JSONObject().put("schema", "pennant-store-abba-v1")
            .put("baseline_commit", "7ebb4a46a319ffde5e390a86b8f0a3a0352e4866")
            .put("seed", "42").put("device_api", Build.VERSION.SDK_INT)
            .put("device_fingerprint", Build.FINGERPRINT)
            .put("variant_A", "baseline test-only renamed/timed copy")
            .put("variant_B", "candidate test-only renamed/timed copy")
            .put("distribution_apk_uses_uninstrumented_production_store", true)
            .put("same_process_same_emulator_independent_database_per_sample", true)
            .put("cold_definition", "first store.read after Room close/open; same process/JIT/OS filesystem cache may already be warm")
            .put("phase_relation", "candidate_encode/check, before_read, transaction and after_commit are disjoint subintervals of commit total; sum excludes transition checks, row conversion, hashing and coroutine scheduling; core calculation and fixture preparation are outside commit total")
            .put("heap_definition", "used heap at sample end; not peak, RSS or physical-device memory")
            .put("fixture_setup_is_not_actual_720_transaction_progress", true)
            .put("fixtures", fixtureRecords).put("samples", rows)
        output.writeText(record.toString(2))
    }
    private suspend fun sample(prefix: Int, kind: String, variant: String, block: Int, position: Int,
        warmup: Boolean, input: Fixture, candidate: SeasonState?) {
        val name = "abba-${UUID.randomUUID()}.db"
        var db = PennantDatabase.open(context, name)
        try {
            prepare(db, input)
            db.close(); db = PennantDatabase.open(context, name)
            val owned = store(variant, db)
            if (kind != "cold_db_read") assertEquals(input.state, requireNotNull(owned.read()).state)
            val started = SystemClock.elapsedRealtimeNanos()
            val (saved, phases) = StoreBenchmarkTimers.capture {
                if (candidate == null) requireNotNull(owned.read()) else owned.commit(token, operation, candidate)
            }
            val elapsed = SystemClock.elapsedRealtimeNanos() - started
            val phaseSum = phases.values.sum()
            assertTrue("Timed disjoint phases exceed the total operation", phaseSum <= elapsed)
            assertEquals(candidate ?: input.state, saved.state)
            assertArrayEquals(CheckpointCodec.encodeSeason(candidate ?: input.state), db.pennantDao().head()!!.payload)
            assertEquals(saved, owned.read())
            val runtime = Runtime.getRuntime()
            val values = JSONObject()
            phases.forEach { (phase, nanos) -> values.put(phase, nanos) }
            val row = JSONObject().put("prefix", prefix).put("operation", kind).put("variant", variant)
                .put("block", block).put("abba_position", position).put("warmup", warmup)
                .put("input_sha256", input.inputHash).put("input_frame_sha256", hash(input.frame))
                .put("candidate_frame_sha256", candidate?.let { hash(CheckpointCodec.encodeSeason(it)) } ?: JSONObject.NULL)
                .put("input_token_world", world).put("input_token_generation", generation).put("input_revision", 0)
                .put("operation_id", operation).put("elapsed_ns", elapsed).put("phases_ns", values)
                .put("phase_sum_ns", phaseSum).put("unattributed_commit_ns", if (candidate != null) elapsed - phaseSum else JSONObject.NULL)
                .put("db_bytes", context.getDatabasePath(name).length())
                .put("wal_bytes", File(context.getDatabasePath(name).absolutePath + "-wal").length())
                .put("used_heap_bytes", runtime.totalMemory() - runtime.freeMemory())
            rows.put(row); persist(); android.util.Log.i("StoreABBA", row.toString())
        } finally { db.close(); context.deleteDatabase(name) }
    }
    private fun median(values: List<Long>): Double {
        val ordered = values.sorted(); val half = ordered.size / 2
        return if (ordered.size % 2 == 1) ordered[half].toDouble() else (ordered[half - 1].toDouble() + ordered[half]) / 2
    }
    private fun summary() {
        val comparisons = JSONArray()
        for (prefix in listOf(0, 100, 300, 600, 719)) for (kind in listOf("marker", "result")) {
            fun values(variant: String): List<Long> = (0 until rows.length()).map { rows.getJSONObject(it) }
                .filter { it.getInt("prefix") == prefix && it.getString("operation") == kind && it.getString("variant") == variant && !it.getBoolean("warmup") }
                .map { it.getLong("elapsed_ns") }
            val a = values("A"); val b = values("B")
            assertEquals(6, a.size); assertEquals(6, b.size)
            val baseline = median(a); val candidate = median(b)
            val limit = if (prefix in listOf(300, 600)) 0.8 else 1.1
            comparisons.put(JSONObject().put("prefix", prefix).put("operation", kind)
                .put("baseline_median_ns", baseline).put("candidate_median_ns", candidate)
                .put("baseline_min_ns", a.min()).put("baseline_max_ns", a.max())
                .put("candidate_min_ns", b.min()).put("candidate_max_ns", b.max())
                .put("candidate_over_baseline", candidate / baseline).put("gate_limit", limit)
                .put("passes_engineering_gate", candidate / baseline <= limit))
        }
        val all = (0 until comparisons.length()).all { comparisons.getJSONObject(it).getBoolean("passes_engineering_gate") }
        File(output.parentFile, "pennant-store-abba-summary.json").writeText(JSONObject()
            .put("schema", "pennant-store-abba-summary-v1").put("comparisons", comparisons)
            .put("all_engineering_gates_pass", all)
            .put("performance_is_not_a_correctness_test", true)
            .put("scope", "Same-emulator local operation timing; no physical-device or long-term DB guarantee. A failed speed gate is reported, not hidden by failing to upload observations.")
            .toString(2))
    }
    @Test fun pairedOperationsUseTheSameInputAndPublishEveryObservation(): Unit = runBlocking {
        assertEquals(35, Build.VERSION.SDK_INT)
        val memory = SeasonSession()
        for (prefix in listOf(0, 100, 300, 600, 719, 720)) {
            val setupStart = SystemClock.elapsedRealtimeNanos()
            while (memory.state.results.size < prefix) memory.finishCurrent()
            val plain = fixture(memory.state)
            val marker = if (prefix < 720) plain.state.beginNext() else null
            val marked = marker?.let { fixture(it) }
            val coreStart = SystemClock.elapsedRealtimeNanos()
            val result = marker?.let { SeasonSession(it).finishCurrent() }
            val coreNanos = SystemClock.elapsedRealtimeNanos() - coreStart
            fixtureRecords.put(JSONObject().put("prefix", prefix).put("plain_input_sha256", plain.inputHash)
                .put("marker_input_sha256", marked?.inputHash ?: JSONObject.NULL)
                .put("core_calculation_ns", coreNanos).put("fixture_setup_ns", coreStart - setupStart)
                .put("core_result_frame_sha256", result?.let { hash(CheckpointCodec.encodeSeason(it)) } ?: JSONObject.NULL))
            persist()
            val kinds = if (prefix == 720) listOf("cold_db_read", "warm_read") else listOf("cold_db_read", "warm_read", "marker", "result")
            for (kind in kinds) {
                val input = if (kind == "result") requireNotNull(marked) else plain
                val candidate = when (kind) { "marker" -> marker; "result" -> result; else -> null }
                // Warm-up writes use independent databases and never carry mutations into measurements.
                for (variant in listOf("A", "B")) sample(prefix, kind, variant, -1, -1, true, input, candidate)
                repeat(3) { block -> listOf("A", "B", "B", "A").forEachIndexed { position, variant ->
                    sample(prefix, kind, variant, block, position, false, input, candidate)
                } }
            }
        }
        persist(); summary()
    }
}

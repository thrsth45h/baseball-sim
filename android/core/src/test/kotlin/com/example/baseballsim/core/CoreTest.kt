package com.example.baseballsim.core

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

class CoreTest {
    private val reference get() = ReferenceAdapter.transitions()
    @Test fun frozenBytesAndExpandedJsonMatchIndependentManifest() {
        val manifest = ReferenceAdapter.json("frozen/SHA256_MANIFEST.json").asJsonObject
        for ((name, record) in manifest.entrySet()) {
            val data = if (name == "transition_cases.json") GZIPInputStream(ReferenceAdapter.bytes("frozen/transition_cases.json.gz").inputStream()).use { it.readBytes() } else ReferenceAdapter.bytes("frozen/" + name)
            assertEquals(name, record.asJsonObject["bytes"].asInt, data.size)
            assertEquals(name, record.asJsonObject["sha256"].asString, MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) })
        }
        assertEquals(49, reference.getAsJsonArray("cases").size())
    }

    @Test fun all49RecordedInputsMatch2600FrozenAdvanceSnapshotsAndResults() {
        var advances = 0
        for (row in reference.getAsJsonArray("cases")) {
            val c = row.asJsonObject; val id = c["id"].asString
            val source = ScriptedSource(c.getAsJsonArray("inputs").map { ReferenceAdapter.input(it.asJsonObject) })
            val game = GameSession(c["seed"].asString, c["max_pitches"].asLong, source, ReferenceAdapter.snapshot(c["initial_snapshot"].asJsonObject))
            for ((index, expected) in c.getAsJsonArray("expected_after_each_advance").withIndex()) {
                game.advanceOnePitch(); advances++
                assertEquals("$id advance $index", expected, ReferenceAdapter.snapshotShape(game.snapshot))
            }
            assertTrue(id, game.snapshot.done)
            assertEquals(id, ReferenceAdapter.expectedResult(c["expected_result"].asJsonObject), ReferenceAdapter.resultShape(game.result()))
            assertEquals(id, c.getAsJsonArray("inputs").size(), source.position)
        }
        assertEquals(2600, advances)
    }

    @Test fun actualNativeSourceMatchesFourFrozenGamesEveryInputSnapshotAndRngCounter() {
        var count = 0
        for (row in reference.getAsJsonArray("cases")) {
            val c = row.asJsonObject
            if (c["rng_kind"].asString != "sm64-v1") continue
            val id = c["id"].asString; val game = GameSession(c["seed"].asString); val source = game.source as ToyPitchSource
            for ((index, expected) in c.getAsJsonArray("expected_after_each_advance").withIndex()) {
                game.advanceOnePitch()
                assertEquals("$id state $index", expected, ReferenceAdapter.snapshotShape(game.snapshot))
                assertEquals("$id RNG $index", c.getAsJsonArray("expected_rng_after_each_advance")[index], ReferenceAdapter.rng(source))
            }
            val actualInputs = game.events.filter { it.pitchEvent != null }.map { ReferenceAdapter.pitchShape(it.input!!) }
            assertEquals(id, c.getAsJsonArray("inputs").toList(), actualInputs)
            assertEquals(id, ReferenceAdapter.expectedResult(c["expected_result"].asJsonObject), ReferenceAdapter.resultShape(game.result()))
            count++
        }
        assertEquals(4, count)
    }

    @Test fun raw64AndDraw100VectorsAndForcedRejectionsUseProductionAlgorithm() {
        val vectors = ReferenceAdapter.json("frozen/sm64_vectors.json").asJsonObject
        for (seed in vectors.getAsJsonArray("vectors")) {
            val row = seed.asJsonObject; val raw = Sm64(seedFromText(row["seed"].asString))
            for (value in row.getAsJsonArray("raw_outputs")) {
                val v = value.asJsonObject; assertEquals(v["raw64_u64"].asString, raw.nextRaw().toString()); assertEquals(v["state_u64"].asString, raw.state.toString())
            }
            val draw = Sm64(seedFromText(row["seed"].asString))
            for (value in row.getAsJsonArray("draw100")) {
                val v = value.asJsonObject; assertEquals(v["value"].asInt, draw.draw100()); assertEquals(v["state_u64"].asString, draw.state.toString())
                assertEquals(v["raw_output_calls"].asLong, draw.rawOutputCalls); assertEquals(v["rejected_raw_outputs"].asLong, draw.rejectedRawOutputs)
                assertEquals(v["draw100_calls"].asLong, draw.draw100Calls)
            }
        }
        for (row in vectors.getAsJsonArray("forced_rejection_examples")) {
            val r = row.asJsonObject; val inputs = r.getAsJsonArray("forced_upper32_sequence"); var position = 0
            val sampled = sample100 { inputs[position++].asLong.toULong() shl 32 }
            assertEquals(r["draw100"].asInt, sampled.value); assertEquals(r["raw_outputs_consumed"].asLong, sampled.rawOutputs); assertEquals(r["rejections"].asLong, sampled.rejections)
        }
    }

    @Test fun pitchPaFinishMixedAndInterleavedGamesHaveIdenticalNativeResultsAndLogs() {
        for (seed in listOf("0", "42", "2026", "18446744073709551615")) {
            val expected = GameSession(seed); expected.runToEnd()
            for (mode in listOf("pitch", "pa", "finish", "mixed")) {
                val game = GameSession(seed); var tick = 0
                while (!game.snapshot.done) {
                    val before = game.snapshot.completedPa; val command = if (mode == "mixed") listOf("pitch", "pa", "finish")[tick++ % 3] else mode
                    do { game.advanceOnePitch() } while (!game.snapshot.done && command != "pitch" && (command == "finish" || game.snapshot.completedPa == before))
                }
                assertEquals(ReferenceAdapter.resultShape(expected.result()), ReferenceAdapter.resultShape(game.result()))
                assertEquals(ReferenceAdapter.gson.toJsonTree(expected.events), ReferenceAdapter.gson.toJsonTree(game.events))
            }
        }
        val first = GameSession("0"); val second = GameSession("42")
        while (!first.snapshot.done || !second.snapshot.done) { if (!second.snapshot.done) second.advanceOnePitch(); if (!first.snapshot.done) first.advanceOnePitch() }
        for (game in listOf(first, second)) {
            val fresh = GameSession(game.seed); fresh.runToEnd(); assertEquals(ReferenceAdapter.resultShape(fresh.result()), ReferenceAdapter.resultShape(game.result()))
        }
    }

    @Test fun sourceValidationReasonsAndUnacceptedPitchStateAreExact() {
        val constructors = listOf<() -> PitchInput>({ PitchInput("called_ball", FairPlay("out_hold")) }, { PitchInput("called_ball", fairDraw = 1) })
        for (construct in constructors) {
            val error = assertThrows(InputFailure::class.java) { construct() }; assertEquals("invalid_pitch_input", error.reason)
            val game = GameSession(source = PitchSource { SourceInput.Pitch(construct()) }); game.advanceOnePitch()
            assertEquals("invalid_pitch_input", game.snapshot.reason); assertEquals(0L, game.snapshot.pitchesAccepted); assertEquals(0L, game.snapshot.completedPa)
            assertEquals(CountState(), game.snapshot.sequence.count)
        }
        val missing = GameSession(source = PitchSource { SourceInput.Pitch(PitchInput("fair_ball_handoff")) }); missing.advanceOnePitch()
        assertEquals("invalid_fair_result", missing.snapshot.reason)
        val invalid = GameSession(source = PitchSource { SourceInput.Pitch(PitchInput("fair_ball_handoff", FairPlay("safe_hit", 1, listOf(4, null, null)))) }); invalid.advanceOnePitch()
        assertEquals("invalid_fair_result", invalid.snapshot.reason); assertEquals(0L, invalid.snapshot.pitchesAccepted)
        assertThrows(IllegalArgumentException::class.java) { SourceInput.Abort("pitch_limit") }
        val stopped = GameSession(maxPitches = 1, source = PitchSource { SourceInput.Pitch(PitchInput("called_ball")) }); stopped.runToEnd()
        val before = ReferenceAdapter.snapshotShape(stopped.snapshot); assertThrows(IllegalStateException::class.java) { stopped.advanceOnePitch() }; assertEquals(before, ReferenceAdapter.snapshotShape(stopped.snapshot))
    }

    @Test fun strictSeedStateCollectionsAndOverflowDoNotSilentlyCoerce() {
        for (seed in listOf("", "-1", "01", " 1", "+1", "１", "1e2", "18446744073709551616")) assertThrows(IllegalArgumentException::class.java) { GameSession(seed) }
        assertThrows(IllegalArgumentException::class.java) { GameSession(maxPitches = 0) }
        assertThrows(IllegalArgumentException::class.java) { CountState(0, 0, 1) }
        assertThrows(IllegalArgumentException::class.java) { HalfState(4) }
        assertThrows(IllegalArgumentException::class.java) { GameSnapshot(lineScore = List(18) { null }) }
        val bases = mutableListOf(true, false, false); val half = HalfState(bases = bases); bases[0] = false; assertTrue(half.bases[0])
        assertThrows(UnsupportedOperationException::class.java) { (half.bases as MutableList<Boolean>)[0] = false }
        val initial = GameSnapshot.initial(); assertThrows(UnsupportedOperationException::class.java) { (initial.lineScore as MutableList<Long?>)[0] = 10 }
        assertThrows(ArithmeticException::class.java) { SequenceState(closedAwayRuns = Long.MAX_VALUE, half = HalfState(runs = 1)).score }
    }
}

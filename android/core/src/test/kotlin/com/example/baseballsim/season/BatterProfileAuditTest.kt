package com.example.baseballsim.season

import com.example.baseballsim.core.*
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/** Exports observations, never manager expected outputs. Only the scenario inputs are shared. */
class BatterProfileAuditTest {
    private data class Operation(val before: Int, val club: String, val players: List<String>)
    private data class Run(val state: SeasonState, val completed: List<GameCheckpoint>, val traces: List<Map<String, Any?>>)
    private fun input(): JsonObject = checkNotNull(javaClass.getResourceAsStream("/batter-profile/inputs.json")).use {
        JsonParser.parseString(it.readBytes().toString(Charsets.UTF_8)).asJsonObject
    }
    private fun operations(input: JsonObject, scenario: String) = input["scenarios"].asJsonObject[scenario].asJsonArray.map {
        val v = it.asJsonObject
        Operation(v["before_ordinal"].asInt, v["select_club"].asString, v["lineup"].asJsonArray.map { p -> p.asString })
    }
    private fun cursor(c: GameCheckpoint) = mapOf("advance_calls" to c.advanceCalls, "rng_state_u64" to c.source.state,
        "rng_raw_calls" to c.source.rawCalls, "rng_draw_calls" to c.source.drawCalls, "rng_rejections" to c.source.rejections,
        "source_pitch_calls" to c.source.pitchCalls, "source_fair_calls" to c.source.fairCalls)
    private fun run(master: String, operations: List<Operation>, mode: String = "game"): Run {
        val session = SeasonSession(SeasonState.initialBatterProfile(master))
        val completed = mutableListOf<GameCheckpoint>()
        val traces = mutableListOf<Map<String, Any?>>()
        while (session.state.status == SeasonStatus.IN_PROGRESS) {
            for (op in operations.filter { it.before == session.state.nextOrdinal }) {
                session.selectClub(op.club); session.applyLineup(op.club, op.players)
            }
            val ordinal = session.state.nextOrdinal
            session.beginNext()
            if (ordinal in listOf(1, 7, 13, 19)) {
                val frames = mutableListOf<Map<String, Any?>>()
                do {
                    session.advanceOnePitch()
                    val c = session.state.active ?: checkNotNull(session.lastCompletedCheckpoint)
                    frames += mapOf("snapshot" to ReferenceAdapter.snapshotShape(c.snapshot.toCore()), "cursor" to cursor(c))
                } while (session.state.active != null)
                traces += mapOf("game_id" to session.lastCompletedCheckpoint!!.game.fixture.gameId, "after_advance" to frames)
            } else when (mode) {
                "pitch" -> do { session.advanceOnePitch() } while (session.state.active != null)
                "pa" -> do { session.advancePlateAppearance() } while (session.state.active != null)
                else -> session.finishCurrent()
            }
            completed += checkNotNull(session.lastCompletedCheckpoint)
        }
        assertEquals(720, session.state.results.size)
        assertEquals(144, session.state.playerCounts.size)
        assertEquals(session.state, CheckpointCodec.decodeSeason(CheckpointCodec.encodeSeason(session.state)))
        return Run(session.state, completed, traces)
    }
    private fun dayRun(master: String, operations: List<Operation>): SeasonState {
        val session = SeasonSession(SeasonState.initialBatterProfile(master))
        val consumed = mutableSetOf<Int>()
        while (session.state.status == SeasonStatus.IN_PROGRESS) {
            operations.forEachIndexed { index, op -> if (op.before == session.state.nextOrdinal && consumed.add(index)) {
                session.selectClub(op.club); session.applyLineup(op.club, op.players)
            } }
            session.advanceDay()
        }
        assertEquals(operations.size, consumed.size)
        return session.state
    }
    private fun observation(master: String, scenario: String, actual: Run): Map<String, Any?> = mapOf(
        "schema" to "batter-profile-actual-observation-v1", "master_seed" to master, "scenario" to scenario,
        "players" to actual.state.playerCounts.associate { it.playerId to it.counts },
        "games" to actual.completed.mapIndexed { index, c ->
            val summary = actual.state.results[index]
            val profiles = requireNotNull(c.profileInput)
            mapOf("game_id" to c.game.fixture.gameId, "game_seed" to c.game.seed,
                "away_lineup" to c.lineup.away, "home_lineup" to c.lineup.home,
                "away_profiles" to profiles.away.map { it.profileId }, "home_profiles" to profiles.home.map { it.profileId },
                "score" to summary.score, "participants" to summary.batters.map { p -> mapOf("player_id" to p.playerId,
                    "club_id" to p.clubId, "slot" to p.slot, "side" to p.side, "counts" to p.counts) },
                "core_snapshot" to ReferenceAdapter.snapshotShape(c.snapshot.toCore()), "core_cursor" to cursor(c))
        })
    @Test fun nineActualProfileSeasonsExposePeopleStatesRngAndThirtySixAdvanceTraces() {
        val input = input()
        for (master in input["master_seeds"].asJsonArray.map { it.asString }) {
            for (scenario in input["scenarios"].asJsonObject.keySet()) {
                val commands = operations(input, scenario)
                val actual = run(master, commands)
                if (master == "42" && scenario == "order_bench") {
                    for (mode in listOf("pitch", "pa")) {
                        val compared = run(master, commands, mode)
                        assertEquals(actual.state, compared.state)
                        assertEquals(actual.completed, compared.completed)
                    }
                    assertEquals(actual.state, dayRun(master, commands))
                }
                System.getProperty("pennant.audit.dir")?.let { directory ->
                    val output = Path.of(directory).resolve("batter-profile"); Files.createDirectories(output)
                    Files.writeString(output.resolve("actual-$master-$scenario.json"), Ref.gson.toJson(observation(master, scenario, actual)) + "\n", StandardOpenOption.CREATE_NEW)
                    Files.writeString(output.resolve("traces-$master-$scenario.json"), Ref.gson.toJson(actual.traces) + "\n", StandardOpenOption.CREATE_NEW)
                    if (master == "42") Files.write(output.resolve("final-$master-$scenario.bin"), CheckpointCodec.encodeSeason(actual.state), StandardOpenOption.CREATE_NEW)
                }
            }
        }
    }
    @Test fun everyIntegerBoundaryAndEveryReachableCountAreObservableWithoutSeedRanking() {
        val categories = listOf("walk", "strikeout", "out_hold", "single", "double", "triple", "home_run")
        val rows = BatterProfileTables.ids.map { id ->
            val boundaries = (0..99).map { draw ->
                val fair = BatterProfileTables.fairPlay(id, draw, listOf(false, false, false))
                mapOf("draw" to draw, "pitch_event" to BatterProfileTables.pitchEvent(id, draw), "fair_kind" to fair.kind, "candidate_bases" to fair.candidateBases)
            }
            val probabilities = mutableMapOf<Pair<Int, Int>, DoubleArray>()
            val pitches = mutableMapOf<Pair<Int, Int>, Double>()
            for (balls in 3 downTo 0) for (strikes in 2 downTo 0) {
                val state = SequenceState(count = CountState(balls, strikes, (balls + strikes).toLong()))
                val result = DoubleArray(7)
                var expectedPitches = 1.0; var selfLoop = 0.0
                for (draw in 0..99) {
                    val event = BatterProfileTables.pitchEvent(id, draw)
                    val next = Rules.pitch(state, event).count!!
                    when (next.status) {
                        "walk_awarded" -> result[0] += .01
                        "strikeout_out" -> result[1] += .01
                        "fair_play_pending" -> for (fairDraw in 0..99) {
                            val fair = BatterProfileTables.fairPlay(id, fairDraw, listOf(false, false, false))
                            val index = when (fair.kind) { "out_hold" -> 2; "home_run" -> 6; else -> 2 + fair.candidateBases!! }
                            result[index] += .0001
                        }
                        else -> {
                            val key = next.balls to next.strikes
                            if (key == (balls to strikes)) selfLoop += .01
                            else {
                                val previous = probabilities.getValue(key)
                                for (index in result.indices) result[index] += .01 * previous[index]
                                expectedPitches += .01 * pitches.getValue(key)
                            }
                        }
                    }
                }
                probabilities[balls to strikes] = result.map { it / (1.0 - selfLoop) }.toDoubleArray()
                pitches[balls to strikes] = expectedPitches / (1.0 - selfLoop)
                assertEquals(1.0, probabilities.getValue(balls to strikes).sum(), 1e-12)
            }
            mapOf("profile_id" to id, "boundaries" to boundaries,
                "counts" to probabilities.map { (key, values) -> mapOf("balls" to key.first, "strikes" to key.second,
                    "terminal_probabilities" to categories.mapIndexed { index, label -> label to values[index] }.toMap(),
                    "expected_pitches" to pitches.getValue(key)) })
        }
        System.getProperty("pennant.audit.dir")?.let { directory ->
            val output = Path.of(directory).resolve("batter-profile"); Files.createDirectories(output)
            Files.writeString(output.resolve("boundary-count-observations.json"), Ref.gson.toJson(rows) + "\n", StandardOpenOption.CREATE_NEW)
        }
    }
}

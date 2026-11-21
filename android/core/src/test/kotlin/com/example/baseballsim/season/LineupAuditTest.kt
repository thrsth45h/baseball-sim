package com.example.baseballsim.season

import com.example.baseballsim.core.*
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/** Inputs were supplied by the manager before implementation. No expected output is read here. */
class LineupAuditTest {
    private data class Operation(val before: Int, val club: String, val players: List<String>)
    private data class Run(val state: SeasonState, val completed: List<GameCheckpoint>)
    private fun inputs(): JsonObject = checkNotNull(javaClass.getResourceAsStream("/lineup/inputs.json")).use {
        JsonParser.parseString(it.readBytes().toString(Charsets.UTF_8)).asJsonObject
    }
    private fun operations(input: JsonObject, scenario: String) = input["scenarios"].asJsonObject[scenario].asJsonArray.map {
        val j = it.asJsonObject
        Operation(j["before_ordinal"].asInt, j["select_club"].asString, j["lineup"].asJsonArray.map { p -> p.asString })
    }
    private fun run(master: String, operations: List<Operation>, mode: String = "game"): Run {
        val session = SeasonSession(SeasonState.initialLineup(master))
        val checkpoints = mutableListOf<GameCheckpoint>()
        while (session.state.status == SeasonStatus.IN_PROGRESS) {
            for (op in operations.filter { it.before == session.state.nextOrdinal }) {
                session.selectClub(op.club); session.applyLineup(op.club, op.players)
            }
            session.beginNext()
            when (mode) {
                "pitch" -> do { session.advanceOnePitch() } while (session.state.active != null)
                "pa" -> do { session.advancePlateAppearance() } while (session.state.active != null)
                else -> session.finishCurrent()
            }
            checkpoints += checkNotNull(session.lastCompletedCheckpoint)
        }
        assertEquals(720, session.state.results.size); assertEquals(144, session.state.playerCounts.size)
        assertEquals(session.state, CheckpointCodec.decodeSeason(CheckpointCodec.encodeSeason(session.state)))
        assertThrows(IllegalStateException::class.java) { session.state.applyLineup(session.state.selectedClubId, session.state.lineup(session.state.selectedClubId)) }
        return Run(session.state, checkpoints)
    }
    private fun dayRun(master: String, operations: List<Operation>): SeasonState {
        val session = SeasonSession(SeasonState.initialLineup(master))
        val consumed = mutableSetOf<Int>()
        while (session.state.status == SeasonStatus.IN_PROGRESS) {
            operations.forEachIndexed { i, op -> if (op.before == session.state.nextOrdinal && consumed.add(i)) {
                session.selectClub(op.club); session.applyLineup(op.club, op.players)
            } }
            session.advanceDay()
        }
        assertEquals(operations.size, consumed.size)
        return session.state
    }
    private fun observation(master: String, scenario: String, run: Run): Map<String, Any?> = mapOf(
        "schema" to "lineup-actual-observation-v1", "master_seed" to master, "scenario" to scenario,
        "players" to run.state.playerCounts.associate { it.playerId to it.counts },
        "games" to run.completed.mapIndexed { i, c ->
            val summary = run.state.results[i]
            val s = c.source
            mapOf("game_id" to c.game.fixture.gameId, "game_seed" to c.game.seed,
                "away_lineup" to c.lineup.away, "home_lineup" to c.lineup.home, "score" to summary.score,
                "participants" to summary.batters.map { p -> mapOf("player_id" to p.playerId, "club_id" to p.clubId,
                    "slot" to p.slot, "side" to p.side, "counts" to p.counts) },
                "core_snapshot" to ReferenceAdapter.snapshotShape(c.snapshot.toCore()),
                "core_cursor" to mapOf("advance_calls" to c.advanceCalls, "rng_state_u64" to s.state,
                    "rng_raw_calls" to s.rawCalls, "rng_draw_calls" to s.drawCalls, "rng_rejections" to s.rejections,
                    "source_pitch_calls" to s.pitchCalls, "source_fair_calls" to s.fairCalls))
        })

    @Test fun nineScenarioObservationsAndAllProgressModesComeFromActualV2State() {
        val input = inputs()
        val scenarios = input["scenarios"].asJsonObject.keySet().toList()
        for (master in input["master_seeds"].asJsonArray.map { it.asString }) {
            val legacy = SeasonSession(SeasonState.initial(master)).also { it.finishSeason() }.state
            for (scenario in scenarios) {
                val ops = operations(input, scenario)
                val actual = run(master, ops)
                if (master == "42" && scenario == "order_bench") {
                    for (mode in listOf("pitch", "pa")) {
                        val compared = run(master, ops, mode)
                        assertEquals(actual.state, compared.state); assertEquals(actual.completed, compared.completed)
                    }
                    assertEquals(actual.state, dayRun(master, ops))
                }
                // A deliberately player-independent source must preserve the old slot-level result.
                assertEquals(legacy.standings, actual.state.standings)
                actual.completed.forEachIndexed { i, checkpoint ->
                    val old = legacy.results[i]
                    assertEquals(old.score, checkpoint.snapshot.toCore().score)
                    assertEquals(old.lineScore, checkpoint.snapshot.lineScore)
                    assertEquals(old.batters.map { it.counts }, actual.state.results[i].batters.map { it.counts })
                }
                if (master == "42" && scenario == "default") {
                    assertEquals("0bde5d0de901771b13e75adc64aab9d21a5854c8f7aed9e34aafb72a3881bbde", Ref.hash(CheckpointCodec.encodeSeason(legacy)))
                }
                if (master == "42") {
                    val frozen = checkNotNull(javaClass.getResourceAsStream("/batter-profile/old-v2-42-$scenario.bin.gz")).use {
                        java.util.zip.GZIPInputStream(it).use { zipped -> zipped.readBytes() }
                    }
                    assertArrayEquals(frozen, CheckpointCodec.encodeSeason(actual.state))
                    assertArrayEquals(frozen, CheckpointCodec.encodeSeason(CheckpointCodec.decodeSeason(frozen)))
                }
                System.getProperty("pennant.audit.dir")?.let { directory ->
                    val output = Path.of(directory).resolve("lineup"); Files.createDirectories(output)
                    Files.writeString(output.resolve("actual-$master-$scenario.json"), Ref.gson.toJson(observation(master, scenario, actual)) + "\n", StandardOpenOption.CREATE_NEW)
                    if (master == "42") Files.write(output.resolve("final-$master-$scenario.bin"), CheckpointCodec.encodeSeason(actual.state), StandardOpenOption.CREATE_NEW)
                }
            }
        }
    }
}

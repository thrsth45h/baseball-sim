package com.example.baseballsim.season

import com.example.baseballsim.core.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class SeasonFullRunTest {
    private data class Run(val state: SeasonState,val checkpoints: List<GameCheckpoint>,val seconds: Double,val maxSampledHeap: Long)
    private fun run(master: String,mode: String): Run {
        val session=SeasonSession(SeasonState.initial(master));val checkpoints=mutableListOf<GameCheckpoint>()
        val start=System.nanoTime();var sampled=0L
        while(session.state.status==SeasonStatus.IN_PROGRESS) {
            session.beginNext();val before=session.state.results.size
            when(mode) {
                "pitch" -> do {session.advanceOnePitch()}while(session.state.active!=null && session.state.status==SeasonStatus.IN_PROGRESS)
                "pa" -> do {session.advancePlateAppearance()}while(session.state.active!=null && session.state.status==SeasonStatus.IN_PROGRESS)
                "mixed" -> {
                    session.selectClub(PennantSchedule.clubIds[(before*7)%12])
                    repeat(3){if(session.state.active!=null)session.advanceOnePitch()}
                    if(session.state.active!=null)session.advancePlateAppearance()
                    if(session.state.active!=null)session.finishCurrent()
                }
                else -> session.finishCurrent()
            }
            assertEquals(before+1,session.state.results.size)
            checkpoints+=checkNotNull(session.lastCompletedCheckpoint)
            val runtime=Runtime.getRuntime();sampled=maxOf(sampled,runtime.totalMemory()-runtime.freeMemory())
        }
        assertEquals(SeasonStatus.COMPLETE,session.state.status);assertEquals(141,session.state.dayIndex)
        assertEquals(720,session.state.results.size);assertNull(session.state.active)
        assertEquals(session.state,CheckpointCodec.decodeSeason(CheckpointCodec.encodeSeason(session.state)))
        return Run(session.state,checkpoints,(System.nanoTime()-start)/1e9,sampled)
    }
    @Test fun threeMastersComplete720GamesWithIdenticalSkipMixedSelectionAndAllModes() {
        val masters=mutableListOf<Map<String,Any?>>()
        for(master in listOf("0","42","2026")) {
            val skip=run(master,"skip");val mixed=run(master,"mixed")
            assertEquals(skip.state.results,mixed.state.results);assertEquals(skip.checkpoints,mixed.checkpoints)
            assertEquals(skip.state.standings,mixed.state.standings);assertEquals(skip.state.playerCounts,mixed.state.playerCounts)
            val selected=SeasonSession(SeasonState.initial(master))
            var loops=0
            while(selected.state.status==SeasonStatus.IN_PROGRESS) {
                selected.selectClub(PennantSchedule.clubIds[(loops++*5)%12])
                selected.openNextSelected()
                if(selected.state.status==SeasonStatus.IN_PROGRESS) {
                    selected.advancePlateAppearance()
                    if(selected.state.active!=null)selected.finishCurrent()
                }
            }
            assertEquals(skip.state.results,selected.state.results);assertEquals(skip.state.standings,selected.state.standings)
            assertEquals(skip.state.playerCounts,selected.state.playerCounts)
            val finishSeason=SeasonSession(SeasonState.initial(master));finishSeason.finishSeason { false }
            assertEquals(SeasonStatus.COMPLETE,finishSeason.state.status)
            assertEquals(141,finishSeason.state.dayIndex)
            assertEquals(skip.state.results,finishSeason.state.results)
            assertEquals(skip.state.standings,finishSeason.state.standings)
            assertEquals(skip.state.playerCounts,finishSeason.state.playerCounts)
            val modes=mutableListOf("skip","mixed","selected","finishSeason")
            if(master=="42")for(mode in listOf("pitch","pa")) {
                val whole=run(master,mode);assertEquals(skip.state.results,whole.state.results);assertEquals(skip.checkpoints,whole.checkpoints);modes+=mode
            }
            val games=skip.checkpoints.mapIndexed { i,c ->
                val source=c.source
                mapOf("game_id" to c.game.fixture.gameId,"schedule_ordinal" to c.game.fixture.ordinal,"seed_u64" to c.game.seed,
                    "max_pitches" to c.maxPitches,"advance_calls" to c.advanceCalls,"checkpoint_sha256" to c.stateHash,
                    "snapshot" to ReferenceAdapter.snapshotShape(c.snapshot.toCore()),"result" to ReferenceAdapter.resultShape(GameResult(c.snapshot.toCore())),
                    "summary" to Ref.gson.toJsonTree(skip.state.results[i]),
                    "rng" to mapOf("pitch_calls" to source.pitchCalls,"fair_calls" to source.fairCalls,"logical_draws" to source.drawCalls,
                        "draw100_calls" to source.drawCalls,"raw_output_calls" to source.rawCalls,"rejected_raw_outputs" to source.rejections,"state_u64" to source.state))
            }
            masters+=mapOf("master_seed" to master,"games" to games,"modes_checked" to modes,"standings" to Ref.gson.toJsonTree(skip.state.standings),
                "players" to Ref.gson.toJsonTree(skip.state.playerCounts),"skip_seconds" to skip.seconds,"mixed_seconds" to mixed.seconds,
                "max_sampled_jvm_heap_bytes" to maxOf(skip.maxSampledHeap,mixed.maxSampledHeap),"season_codec_bytes" to CheckpointCodec.encodeSeason(skip.state).size)
        }
        val report=mapOf("schema" to "pennant-core-audit-v1","masters" to masters,"all_comparisons_passed" to true,
            "method_count_is_not_game_or_assertion_count" to true,"memory_measurement" to "maximum sampled heap after each game; not process RSS or Android measurement")
        val bytes=(Ref.gson.toJson(report)+"\n").toByteArray(Charsets.UTF_8)
        System.getProperty("pennant.audit.dir")?.let { directory ->
            val output=Path.of(directory);Files.createDirectories(output)
            Files.write(output.resolve("pennant-core-audit.json"),bytes,StandardOpenOption.CREATE_NEW)
        }
        println("PENNANT_AUDIT games=2160 masters=3 native same-result mode comparisons passed bytes=${bytes.size}")
    }
    @Test fun reservedStopDoesNotBeginANewGameAndFinishesOnlyAnActiveGame() {
        val session=SeasonSession(SeasonState.initial().advanceRestDay());val before=session.state
        assertSame(before,session.advanceDay{true});assertSame(before,session.finishSeason{true})
        session.advanceOnePitch();assertNotNull(session.state.active)
        val activeSeed=session.state.active!!.game.seed;session.advanceDay{true}
        assertEquals(1,session.state.results.size);assertNull(session.state.active);assertEquals(2,session.state.nextOrdinal)
        assertEquals(activeSeed,session.state.results.first().game.seed)
        val boundary=session.state;assertSame(boundary,session.finishSeason{true})
        session.advanceDay();assertEquals(6,session.state.results.size);assertEquals(3,session.state.dayIndex)
    }
}

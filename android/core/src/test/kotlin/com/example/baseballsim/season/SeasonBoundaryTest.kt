package com.example.baseballsim.season

import com.example.baseballsim.core.*
import org.junit.Assert.*
import org.junit.Test

class SeasonBoundaryTest {
    @Test fun independentSparseAttributionPreservesAll108Players() {
        val ref=Ref.json("player_attribution.json")
        val contributions=ref["input_games"].asJsonArray.map { row ->
            val game=row.asJsonObject;val fixture=PennantSchedule.fixtures.single{it.gameId==Ref.s(game,"game_id")}
            SeasonRecords.project(fixture,game["local_batter_counts"].asJsonArray.map{Ref.counts(it.asJsonObject)})
        }
        val actual=SeasonRecords.players(contributions).map { p -> mapOf("player_id" to p.playerId,"club_id" to p.clubId,"slot" to p.slot,
            "completed_plate_appearances" to p.counts.completedPlateAppearances,"safe_hit_events" to p.counts.safeHitEvents,
            "walk_events" to p.counts.walkEvents,"strikeout_events" to p.counts.strikeoutEvents,"home_run_events" to p.counts.homeRunEvents) }
        assertEquals(ref["expected_all_108_players"],Ref.gson.toJsonTree(actual))
        assertEquals(72,SeasonRecords.players(contributions).count{it.counts==EventCounts()})
    }
    @Test fun reasonOutcomeAndCalendarCursorRejectContradictions() {
        val input=Ref.json("small_ledger.json")["input_results"].asJsonArray
        var state=SeasonState.initial()
        val first=Ref.summary(input[0].asJsonObject,state.games)
        assertThrows(IllegalArgumentException::class.java){GameSummary(first.game,first.maxPitches,first.status,first.outcome,"tie_after_nine",first.score,first.lineScore,first.terminalDelta,first.pitchesAccepted,first.batters)}
        assertEquals(1,CheckpointCodec.decodeSeason(CheckpointCodec.encodeSeason(state)).dayIndex)
        assertEquals(2,state.advanceRestDay().dayIndex)
        input.take(6).forEach{state=state.beginNext().accept(Ref.summary(it.asJsonObject,state.games))}
        assertEquals(3,state.dayIndex)
        assertThrows(IllegalArgumentException::class.java){SeasonState.restore(state.masterSeed,state.selectedClubId,2,state.results,null)}
        val valid=SeasonState.restore(state.masterSeed,state.selectedClubId,3,state.results,null)
        assertEquals(state,valid)
        assertThrows(IllegalArgumentException::class.java){SeasonState.restore(state.masterSeed,state.selectedClubId,4,state.results,null)}
        val partial=SeasonState.initial().beginNext().accept(first)
        assertThrows(IllegalArgumentException::class.java){SeasonState.restore(partial.masterSeed,partial.selectedClubId,1,partial.results,null)}
        assertThrows(IllegalArgumentException::class.java){partial.beginNext().accept(Ref.summary(input[2].asJsonObject,partial.games))}
    }
    @Test fun checkpointEveryValueAndSourceCounterIsCompared() {
        val start=SeasonState.initial("0").beginNext();val m=start.active!!.recover("0");repeat(23){m.advanceOnePitch()};val c=m.checkpoint()
        val counters=listOf(c.source.copy(state=(seedFromText(c.source.state!!)+1u).toString()),
            c.source.copy(rawCalls=c.source.rawCalls!!+1,rejections=c.source.rejections!!+1),
            c.source.copy(rawCalls=c.source.rawCalls!!+1,drawCalls=c.source.drawCalls!!+1,pitchCalls=c.source.pitchCalls+1),
            c.source.copy(rawCalls=c.source.rawCalls!!+1,drawCalls=c.source.drawCalls!!+1,fairCalls=c.source.fairCalls!!+1))
        counters.forEach{s->assertThrows(IllegalArgumentException::class.java){GameCheckpoint(c.game,c.maxPitches,c.advanceCalls,c.snapshot,s).recover("0")}}
        val changedOrder=c.snapshot.battingIndices.toMutableList();changedOrder[0]=(changedOrder[0]+1)%9
        val s=c.snapshot
        val different=SnapshotDto(s.sequence,changedOrder,s.batterCounts,s.pitchesAccepted,s.lineScore,s.status,s.reason,s.outcome,s.terminalDelta,s.historyOrigin)
        assertThrows(IllegalArgumentException::class.java){GameCheckpoint(c.game,c.maxPitches,c.advanceCalls,different,c.source).recover("0")}
        val foreignInitial=SnapshotDto.fromCore(GameSnapshot.initial().copy(historyOrigin="provided_snapshot"))
        assertThrows(IllegalArgumentException::class.java){GameCheckpoint(c.game,c.maxPitches,c.advanceCalls,c.snapshot,c.source,foreignInitial).recover("0")}
        val foreignGame=c.game.copy(fixture=c.game.fixture.copy(gameId="foreign"))
        assertThrows(IllegalArgumentException::class.java){GameCheckpoint(foreignGame,c.maxPitches,c.advanceCalls,c.snapshot,c.source).recover("0")}
        assertThrows(IllegalArgumentException::class.java){c.game.copy(versions=SeasonVersions(toy="future"))}
    }
    @Test fun zeroAcceptedAbortRetainsTheCallBoundaryAndBlocksProgression() {
        val start=SeasonState.initial().beginNext();val game=start.active!!.game
        val diagnostic=SeasonMatch(game,GameSession(game.seed,source=ScriptedSource(listOf(SourceInput.Abort("source_error")))))
        diagnostic.advanceOnePitch();val stopped=diagnostic.checkpoint()
        assertEquals(0L,stopped.snapshot.pitchesAccepted);assertEquals(1L,stopped.advanceCalls);assertNotNull(stopped.snapshot.sequence.count)
        assertEquals(stopped,CheckpointCodec.decodeGame(CheckpointCodec.encodeGame(stopped)))
        assertThrows(IllegalArgumentException::class.java){stopped.recover("42")}
        assertThrows(IllegalArgumentException::class.java){start.checkpoint(stopped)}
        // Synthetic retained production error: the source consumed a draw but no pitch was accepted.
        // This is a diagnostic state, not a claim that an ordinary ToyPitchSource run failed.
        val toy=ToyPitchSource(game.seed);toy.nextPitch(PitchContext(SequenceState(),Score(),"away","away_1",1))
        val retained=GameCheckpoint(game,10_000,1,stopped.snapshot,SourceCursor.capture(toy))
        val blocked=start.checkpoint(retained)
        assertEquals(SeasonStatus.BLOCKED,blocked.status);assertEquals(0,blocked.results.size)
        assertEquals(blocked,CheckpointCodec.decodeSeason(CheckpointCodec.encodeSeason(blocked)))
        val restored=SeasonSession(blocked)
        assertThrows(IllegalStateException::class.java){restored.advanceOnePitch()}
        assertThrows(IllegalStateException::class.java){restored.advanceDay()}
        assertThrows(IllegalArgumentException::class.java){retained.recover("42")}
        assertEquals(blocked,restored.state)
        val capped=GameSession(game.seed,maxPitches=1);capped.advanceOnePitch()
        assertEquals(Status.ABORTED,capped.snapshot.status)
        assertThrows(IllegalArgumentException::class.java){GameSummary.fromCore(game,capped.snapshot)}
    }
    @Test fun completedActiveReplayRegistersExactlyOnceWithoutAnotherPitch() {
        val start=SeasonState.initial().beginNext();val match=start.active!!.recover("42");match.finish()
        val checkpoint=match.checkpoint();val pending=start.checkpoint(checkpoint)
        assertEquals(0,pending.results.size)
        val restore=SeasonSession(CheckpointCodec.decodeSeason(CheckpointCodec.encodeSeason(pending)))
        val settled=restore.advanceOnePitch()
        assertEquals(1,settled.results.size);assertNull(settled.active)
        assertEquals(checkpoint,restore.lastCompletedCheckpoint)
        assertSame(settled,settled.accept(settled.results.first()))
    }
    @Test fun rationalRankingAndAdditionsDoNotRoundOrSilentlyOverflow() {
        assertTrue(WinRatio.of(Long.MAX_VALUE-1,Long.MAX_VALUE)>WinRatio.of(Long.MAX_VALUE-2,Long.MAX_VALUE-1))
        val totals=PennantSchedule.clubIds.map{ClubTotals(it)}.toMutableList()
        totals[0]=ClubTotals(totals[0].clubId,115,58,57)
        totals[1]=ClubTotals(totals[1].clubId,119,60,59)
        totals[2]=ClubTotals(totals[2].clubId,115,58,57)
        val ranked=SeasonRecords.rank(totals).take(6)
        assertEquals(listOf(1,1,3,null,null,null),ranked.map{it.rank})
        assertEquals(listOf("L1-T01","L1-T03","L1-T02"),ranked.take(3).map{it.totals.clubId})
        assertThrows(ArithmeticException::class.java){SeasonRecords.plusCounts(EventCounts(Long.MAX_VALUE),EventCounts(1))}
        assertThrows(ArithmeticException::class.java){ClubTotals("L1-T01",Long.MAX_VALUE,Long.MAX_VALUE,1)}
        assertThrows(IllegalArgumentException::class.java){WinRatio.of(1,0)}
        assertThrows(IllegalArgumentException::class.java){WinRatio.of(1,2).copy(denominator=0)}
    }
    @Test fun collectionInputsAndPublishedValuesRemainImmutable() {
        val state=SeasonState.initial().beginNext();val snapshot=state.active!!.snapshot
        assertThrows(UnsupportedOperationException::class.java){(state.games as MutableList).clear()}
        assertThrows(UnsupportedOperationException::class.java){(state.playerCounts as MutableList).clear()}
        assertThrows(UnsupportedOperationException::class.java){(PennantSchedule.clubs[0].players as MutableList).clear()}
        assertThrows(UnsupportedOperationException::class.java){(snapshot.lineScore as MutableList).clear()}
        assertThrows(UnsupportedOperationException::class.java){(snapshot.battingIndices as MutableList).clear()}
        val counts=MutableList(18){EventCounts()};val projected=SeasonRecords.project(state.games[0].fixture,counts)
        counts[0]=EventCounts(9);assertEquals(EventCounts(),projected[0].counts)
        assertThrows(UnsupportedOperationException::class.java){(projected as MutableList).clear()}
    }
    @Test fun codecRejectsUnknownVersionTruncationTrailingBytesAndInconsistentPlan() {
        val state=SeasonState.initial().beginNext();val bytes=CheckpointCodec.encodeSeason(state)
        assertThrows(IllegalArgumentException::class.java){CheckpointCodec.decodeSeason(bytes+byteArrayOf(0))}
        assertThrows(Exception::class.java){CheckpointCodec.decodeSeason(bytes.copyOf(bytes.size-1))}
        val changed=bytes.clone();changed[4]='x'.code.toByte()
        assertThrows(IllegalArgumentException::class.java){CheckpointCodec.decodeSeason(changed)}
        val marker="season-seeds-v1".toByteArray();val index=bytes.indices.first{i->i+marker.size<=bytes.size&&bytes.copyOfRange(i,i+marker.size).contentEquals(marker)}
        val version=bytes.clone();version[index]='x'.code.toByte()
        assertThrows(IllegalArgumentException::class.java){CheckpointCodec.decodeSeason(version)}
        assertThrows(IllegalArgumentException::class.java){SeasonState.restore("0",state.selectedClubId,state.dayIndex,state.results,state.active)}
    }
    @Test fun walkoffSummaryUsesFinalProjectedScoreOnlyOnce() {
        val game=PennantSchedule.allocate("42")[0]
        val sequence=SequenceState(9,Side.BOTTOM,0,0,count=null,half=HalfState(bases=listOf(true,true,true)))
        val initial=GameSnapshot(sequence,lineScore=List(18){0L},historyOrigin="provided_snapshot")
        val session=GameSession(game.seed,source=ScriptedSource(List(4){SourceInput.Pitch(PitchInput("called_ball"))}),initial=initial)
        session.runToEnd();val summary=GameSummary.fromCore(game,session.snapshot)
        assertEquals("walkoff_walk",summary.reason);assertEquals(Score(0,1),summary.score);assertEquals(Score(0,1),summary.terminalDelta)
        val state=SeasonState.initial().beginNext().accept(summary)
        assertEquals(1L,state.standings.single{it.totals.clubId==game.fixture.homeClubId}.totals.runsFor)
        assertEquals(1L,state.playerCounts.single{it.playerId==game.fixture.homeClubId+"-P01"}.counts.walkEvents)
        val hrInitial=GameSnapshot(sequence.copy(half=HalfState(bases=listOf(true,false,false))),lineScore=List(18){0L},historyOrigin="provided_snapshot")
        val hr=GameSession(game.seed,source=ScriptedSource(listOf(SourceInput.Pitch(PitchInput("fair_ball_handoff",FairPlay("home_run"))))),initial=hrInitial)
        hr.runToEnd();val twoRunHr=GameSummary.fromCore(game,hr.snapshot)
        assertEquals(Score(0,2),twoRunHr.score)
        fun invalid(reason: String,away: Long,home: Long,delta: Long,finalHalf: Long=home): GameSummary {
            val lines=MutableList<Long?>(18){0L};lines[0]=away;lines[1]=home-finalHalf;lines[17]=finalHalf
            return GameSummary(game,10_000,Status.COMPLETED,"home_win",reason,Score(away,home),lines,Score(0,delta),summary.pitchesAccepted,summary.batters)
        }
        val bad=listOf<()->GameSummary>(
            {invalid("walkoff_walk",0,2,2)}, {invalid("walkoff_safe_hit",0,2,2)},
            {invalid("walkoff_safe_hit",3,4,4)}, {invalid("walkoff_home_run",0,5,5)},
            {invalid("walkoff_home_run",0,3,2)}, {invalid("walkoff_home_run",1,2,2,1)})
        val unchanged=SeasonState.initial().beginNext();val before=CheckpointCodec.encodeSeason(unchanged)
        bad.forEach { make ->
            assertThrows(IllegalArgumentException::class.java){CheckpointCodec.encodeSummary(make())}
            assertThrows(IllegalArgumentException::class.java){unchanged.accept(make())}
        }
        assertArrayEquals(before,CheckpointCodec.encodeSeason(unchanged))
        // Forge only the reason in an otherwise valid encoded two-run HR; numeric fields stay intact.
        val encoded=CheckpointCodec.encodeSummary(twoRunHr)
        val old="walkoff_home_run".toByteArray(Charsets.UTF_8)
        val offset=encoded.indices.single { i -> i+old.size<=encoded.size && encoded.copyOfRange(i,i+old.size).contentEquals(old) }
        for(reason in listOf("walkoff_walk","walkoff_safe_hit")) {
            val replacement=reason.toByteArray(Charsets.UTF_8)
            val length=java.nio.ByteBuffer.allocate(4).putInt(replacement.size).array()
            val forged=encoded.copyOfRange(0,offset-4)+length+replacement+encoded.copyOfRange(offset+old.size,encoded.size)
            assertThrows(IllegalArgumentException::class.java){CheckpointCodec.decodeSummary(forged)}
        }
    }
}

package com.example.baseballsim.season

import com.example.baseballsim.core.*
import org.junit.Assert.*
import org.junit.Test

class LineupStateTest {
    private val club = "L1-T01"
    private fun people(vararg n: Int) = n.map { "$club-P${it.toString().padStart(2, '0')}" }
    private fun beforeOwnGame(session: SeasonSession, occurrence: Int) {
        val ordinal = PennantSchedule.fixtures.filter { club in listOf(it.awayClubId, it.homeClubId) }[occurrence - 1].ordinal
        while (session.state.nextOrdinal < ordinal) session.finishCurrent()
        assertNull(session.state.active)
    }
    @Test fun personTotalsSurviveOrderChangesAndBenchExchangeWithoutMovingPastCounts() {
        val session = SeasonSession(SeasonState.initialLineup())
        assertEquals(144, session.state.playerCounts.size)
        assertEquals(108, SeasonState.initial().playerCounts.size)
        assertTrue(session.state.playerCounts.all { it.slot == null })
        session.finishCurrent()
        val first = session.state.results.single()
        val firstBytes = CheckpointCodec.encodeSummary(first)
        assertEquals(first, CheckpointCodec.decodeSummary(firstBytes))
        val p01 = session.state.playerCounts.single { it.playerId == "$club-P01" }.counts
        assertTrue(p01.completedPlateAppearances > 0)
        beforeOwnGame(session, 2)
        session.applyLineup(club, people(9, 2, 3, 4, 5, 6, 7, 8, 1))
        session.finishCurrent()
        val second = session.state.results.last().batters.single { it.playerId == "$club-P01" }
        assertEquals(9, second.slot)
        assertEquals(SeasonRecords.plusCounts(p01, second.counts), session.state.playerCounts.single { it.playerId == "$club-P01" }.counts)
        beforeOwnGame(session, 3)
        val p01BeforeBench = session.state.playerCounts.single { it.playerId == "$club-P01" }.counts
        session.applyLineup(club, people(9, 2, 3, 4, 5, 6, 7, 8, 10))
        session.finishCurrent()
        assertEquals(p01BeforeBench, session.state.playerCounts.single { it.playerId == "$club-P01" }.counts)
        assertTrue(session.state.playerCounts.single { it.playerId == "$club-P10" }.counts.completedPlateAppearances > 0)
        assertEquals(EventCounts(), session.state.playerCounts.single { it.playerId == "$club-P12" }.counts)
        assertArrayEquals(firstBytes, CheckpointCodec.encodeSummary(session.state.results.first()))
        assertEquals(session.state, CheckpointCodec.decodeSeason(CheckpointCodec.encodeSeason(session.state)))
    }
    @Test fun immutableStartInputsRejectValidButDifferentPeopleDuringAdvanceAndAccept() {
        val initial = SeasonState.initialLineup().applyLineup(club, people(10, 2, 3, 4, 5, 6, 7, 8, 9)).beginNext()
        val marker = initial.active!!
        assertEquals("$club-P10", marker.lineup.home.first())
        assertThrows(IllegalStateException::class.java) { initial.applyLineup(club, people(1, 2, 3, 4, 5, 6, 7, 8, 9)) }
        val match = marker.recover(initial.masterSeed)
        match.advanceOnePitch()
        val checkpoint = match.checkpoint()
        val different = GameLineup(checkpoint.lineup.away, people(1, 2, 3, 4, 5, 6, 7, 8, 9))
        val forged = GameCheckpoint(checkpoint.game, checkpoint.maxPitches, checkpoint.advanceCalls, checkpoint.snapshot,
            checkpoint.source, checkpoint.initial, different)
        assertThrows(IllegalArgumentException::class.java) { initial.checkpoint(forged) }
        val progressed = initial.checkpoint(checkpoint)
        match.finish()
        val good = GameSummary.fromCore(match.game, match.snapshot, lineup = match.lineup)
        val wrong = GameSummary.fromCore(match.game, match.snapshot, lineup = different)
        assertThrows(IllegalArgumentException::class.java) { progressed.accept(wrong) }
        assertEquals(good, progressed.accept(good).results.single())
        assertEquals(checkpoint, CheckpointCodec.decodeGame(CheckpointCodec.encodeGame(checkpoint)).recover("42").checkpoint())
    }
    @Test fun editingOnlySelectedFuturePlanPreservesSeedsResultsAndDeepImmutability() {
        val initial = SeasonState.initialLineup()
        val mutable = people(10, 2, 3, 4, 5, 6, 7, 8, 9).toMutableList()
        val edited = initial.applyLineup(club, mutable)
        mutable[0] = "$club-P12"
        assertEquals("$club-P10", edited.lineup(club).first())
        assertEquals(initial.games, edited.games); assertEquals(initial.dayIndex, edited.dayIndex)
        assertSame(initial.results, edited.results); assertSame(initial.playerCounts, edited.playerCounts)
        assertSame(edited, edited.applyLineup(club, edited.lineup(club)))
        val selectedOther = edited.selectClub("L2-T06")
        assertEquals(edited.lineup(club), selectedOther.lineup(club))
        assertEquals(edited.lineup(club), selectedOther.beginNext().active!!.lineup.home)
        assertThrows(IllegalArgumentException::class.java) { selectedOther.applyLineup(club, people(1, 2, 3, 4, 5, 6, 7, 8, 9)) }
        assertThrows(UnsupportedOperationException::class.java) { (edited.lineup(club) as MutableList<String>)[0] = "$club-P12" }
        assertThrows(UnsupportedOperationException::class.java) { (edited.clubLineups as MutableMap<String, List<String>>).clear() }
        assertThrows(IllegalStateException::class.java) { SeasonState.initial().applyLineup(club, people(1, 2, 3, 4, 5, 6, 7, 8, 9)) }
        for (invalid in listOf(people(1, 2, 3, 4, 5, 6, 7, 8), people(1, 2, 3, 4, 5, 6, 7, 8, 8),
            people(1, 2, 3, 4, 5, 6, 7, 8, 13), people(1, 2, 3, 4, 5, 6, 7, 8, 9).dropLast(1) + "L2-T01-P09")) {
            assertThrows(IllegalArgumentException::class.java) { initial.applyLineup(club, invalid) }
        }
    }
    @Test fun formatsStaySeparateAndRejectUnknownMixedOrNoncanonicalPayloads() {
        val legacy = SeasonState.initial().beginNext()
        val current = SeasonState.initialLineup().applyLineup(club, people(12, 2, 3, 4, 5, 6, 7, 8, 9)).beginNext()
        val oldBytes = CheckpointCodec.encodeSeason(legacy)
        assertEquals(legacy, CheckpointCodec.decodeSeason(oldBytes))
        assertArrayEquals(oldBytes, CheckpointCodec.encodeSeason(CheckpointCodec.decodeSeason(oldBytes)))
        val newBytes = CheckpointCodec.encodeSeason(current)
        assertEquals(current, CheckpointCodec.decodeSeason(newBytes))
        assertEquals(108, CheckpointCodec.decodeSeason(oldBytes).playerCounts.size)
        assertEquals(144, CheckpointCodec.decodeSeason(newBytes).playerCounts.size)
        assertThrows(IllegalArgumentException::class.java) { SeasonVersions(roster = "season-roster-v2").requireSupported() }
        fun replace(bytes: ByteArray, from: String, to: String): ByteArray {
            assertEquals(from.length, to.length)
            val text = bytes.toString(Charsets.ISO_8859_1); assertTrue(text.contains(from))
            return text.replaceFirst(from, to).toByteArray(Charsets.ISO_8859_1)
        }
        for (bytes in listOf(replace(newBytes, "pennant-season-checkpoint-v2", "pennant-season-checkpoint-v3"),
            replace(newBytes, "pennant-season-checkpoint-v2", "pennant-season-checkpoint-v1"),
            replace(newBytes, "$club-P12", "$club-P99"), replace(newBytes, "$club-P02", "$club-P12"),
            replace(oldBytes, "pennant-season-checkpoint-v1", "pennant-season-checkpoint-v2"), newBytes + byteArrayOf(0))) {
            assertThrows(Exception::class.java) { CheckpointCodec.decodeSeason(bytes) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            SeasonState.restore("42", club, 2, emptyList(), current.active)
        }
    }
    @Test fun restoredActiveAndBlockedPeopleMustMatchTheSavedPlansButCompletedHistoryNeedNot() {
        val state = SeasonState.initialLineup().beginNext()
        val c = state.active!!
        val different = GameLineup(c.lineup.away, people(10, 2, 3, 4, 5, 6, 7, 8, 9))
        val altered = GameCheckpoint(c.game, c.maxPitches, c.advanceCalls, c.snapshot, c.source, c.initial, different)
        fun restore(checkpoint: GameCheckpoint, plans: Map<String, List<String>>) = SeasonState.restore(
            "42", club, state.dayIndex, emptyList(), checkpoint, state.versions, plans)
        assertThrows(IllegalArgumentException::class.java) { restore(altered, state.clubLineups) }
        val matchingPlans = state.clubLineups + (club to different.home)
        val matching = restore(altered, matchingPlans)
        assertEquals(matching, CheckpointCodec.decodeSeason(CheckpointCodec.encodeSeason(matching)))
        val text = CheckpointCodec.encodeSeason(matching).toString(Charsets.ISO_8859_1)
        // Only the future plan's first P10 is replaced; the later active start input still names P10.
        val tampered = text.replaceFirst("$club-P10", "$club-P01").toByteArray(Charsets.ISO_8859_1)
        assertThrows(IllegalArgumentException::class.java) { CheckpointCodec.decodeSeason(tampered) }
        val aborted = SnapshotDto.fromCore(c.snapshot.toCore().copy(status = Status.ABORTED, reason = "source_error"))
        val blocked = GameCheckpoint(c.game, c.maxPitches, c.advanceCalls, aborted, c.source, c.initial, different)
        assertThrows(IllegalArgumentException::class.java) { restore(blocked, state.clubLineups) }
        assertEquals(SeasonStatus.BLOCKED, restore(blocked, matchingPlans).status)
        val completed = SeasonSession(matching).also { it.finishCurrent() }.state
        val future = completed.applyLineup(club, people(1, 2, 3, 4, 5, 6, 7, 8, 9))
        assertEquals(different, future.results.single().lineup)
        assertEquals(future, CheckpointCodec.decodeSeason(CheckpointCodec.encodeSeason(future)))
    }
}

package com.example.baseballsim.season

import com.example.baseballsim.core.*
import org.junit.Assert.*
import org.junit.Test

class BatterProfileStateTest {
    private val club = "L1-T01"
    private fun newState() = SeasonState.initialBatterProfile("42", club)
    @Test fun closedVersionsCanonicalProfilesAndDeepCopiesRejectForgedWorlds() {
        val state = newState()
        assertTrue(state.lineupEnabled && state.profilesEnabled)
        assertEquals(144, state.worldProfiles.size)
        assertEquals(BatterProfiles.TABLE_VERSION, state.worldProfileTableVersion)
        assertFalse(SeasonState.initial().profilesEnabled)
        assertFalse(SeasonState.initialLineup().profilesEnabled)
        assertEquals(108, SeasonState.initial().playerCounts.size)
        assertEquals(144, SeasonState.initialLineup().playerCounts.size)
        for (c in PennantSchedule.clubIds) {
            assertEquals(mapOf("standard" to 4, "contact" to 4, "slugger" to 4), state.playerIds(c).groupingBy { state.playerProfile(it) }.eachCount())
        }
        val supplied = state.worldProfiles.toMutableMap()
        val restored = SeasonState.restore("42", club, 1, emptyList(), null, state.versions, state.clubLineups,
            state.worldProfileTableVersion, supplied)
        supplied["$club-P01"] = "slugger"
        assertEquals("standard", restored.playerProfile("$club-P01"))
        assertThrows(UnsupportedOperationException::class.java) { (restored.worldProfiles as MutableMap<String, String>).clear() }
        assertThrows(IllegalArgumentException::class.java) { SeasonState.restore("42", club, 1, emptyList(), null, state.versions, state.clubLineups, state.worldProfileTableVersion, supplied) }
        assertThrows(IllegalArgumentException::class.java) { SeasonState.restore("42", club, 1, emptyList(), null, state.versions, state.clubLineups, "unknown", state.worldProfiles) }
        for (bad in listOf(state.versions.copy(toy = "toy-source-v1"), state.versions.copy(checkpoint = "season-checkpoint-v2"), state.versions.copy(roster = "season-roster-v3"))) {
            assertThrows(IllegalArgumentException::class.java) { bad.requireSupported() }
        }
        assertThrows(IllegalArgumentException::class.java) { state.playerProfile("$club-P13") }
    }
    @Test fun fixedTablesCoverEveryDrawAndAllSlotsUseTheirFrozenProfile() {
        val events = listOf("called_ball", "called_strike_caught", "swinging_strike_caught", "ordinary_foul_uncaught", "fair_ball_handoff")
        val pitch = listOf(listOf(35,15,15,15,20), listOf(35,15,10,15,25), listOf(35,15,20,15,15))
        val fair = listOf(listOf(65,22,7,1,5), listOf(65,26,5,1,3), listOf(65,15,7,1,12))
        BatterProfileTables.ids.forEachIndexed { i, id ->
            val expectedPitch = pitch[i].flatMapIndexed { index, count -> List(count) { events[index] } }
            val expectedFair = fair[i].flatMapIndexed { index, count -> List(count) { index } }
            for (draw in 0..99) {
                assertEquals(expectedPitch[draw], BatterProfileTables.pitchEvent(id, draw))
                val actual = BatterProfileTables.fairPlay(id, draw, listOf(true, true, true))
                val branch = expectedFair[draw]
                assertEquals(if (branch == 0) "out_hold" else if (branch == 4) "home_run" else "safe_hit", actual.kind)
                if (branch in 1..3) {
                    assertEquals(branch, actual.candidateBases)
                    assertEquals((1..3).map { minOf(it + branch, 4) }, actual.runnerDestinations)
                }
            }
        }
        val game = newState().games.first()
        val lineup = GameLineup.initial(game)
        val input = GameProfileInput.canonical(game, lineup)!!
        val mutableEntries = input.away.toMutableList()
        val copied = GameProfileInput(mutableEntries, input.home)
        mutableEntries.clear()
        assertEquals(9, copied.away.size)
        assertThrows(UnsupportedOperationException::class.java) { (copied.away as MutableList<BatterProfileEntry>).clear() }
        val mutableTypes = List(9) { "standard" }.toMutableList()
        val copiedSource = ProfileToyPitchSource("42", mutableTypes, mutableTypes)
        mutableTypes.clear()
        assertEquals(9, copiedSource.awayProfiles.size)
        assertThrows(UnsupportedOperationException::class.java) { (copiedSource.homeProfiles as MutableList<String>).clear() }

        for (side in listOf("away", "home")) for (slot in 1..9) {
            val rng = Sm64(seedFromText("42"))
            val draw = rng.draw100()
            val source = input.newSource("42")
            val context = PitchContext(SequenceState(), Score(), side, "${side}_$slot", slot)
            val actual = (source.nextPitch(context) as SourceInput.Pitch).value
            assertEquals(BatterProfileTables.pitchEvent(input.profile(side, slot), draw), actual.event)
            if (actual.event == "fair_ball_handoff") rng.draw100()
            assertEquals(rng.state, source.rng.state)
            assertEquals(rng.rawOutputCalls, source.rng.rawOutputCalls)
        }
        assertThrows(IllegalArgumentException::class.java) { BatterProfileTables.pitchEvent("unknown", 0) }
        assertThrows(IllegalArgumentException::class.java) { BatterProfileTables.pitchEvent("standard", 100) }
        val forced = arrayOf(4_294_967_295uL shl 32, 4_294_967_200uL shl 32, 99uL shl 32).iterator()
        val rejected = sample100 { forced.next() }
        assertEquals(DrawSample(99, 3, 2), rejected)
    }
    @Test fun allStandardDiagnosticMatchesLegacyWhileDifferentTypesChangeEvents() {
        for (seed in listOf("0", "42", "2026")) {
            val old = ToyPitchSource(seed)
            val trial = ProfileToyPitchSource(seed, List(9) { "standard" }, List(9) { "standard" })
            val oldEngine = GameSession(seed, source = old)
            val newEngine = GameSession(seed, source = trial)
            while (!oldEngine.snapshot.done) {
                oldEngine.advanceOnePitch(); newEngine.advanceOnePitch()
                assertEquals(SnapshotDto.fromCore(oldEngine.snapshot), SnapshotDto.fromCore(newEngine.snapshot))
                val a = SourceCursor.capture(old); val b = SourceCursor.capture(trial)
                assertEquals(a.copy(kind = BatterProfiles.CURSOR_KIND), b)
            }
            assertTrue(newEngine.snapshot.done)
        }
        assertEquals("fair_ball_handoff", BatterProfileTables.pitchEvent("contact", 77))
        assertEquals("ordinary_foul_uncaught", BatterProfileTables.pitchEvent("slugger", 77))
        assertEquals("home_run", BatterProfileTables.fairPlay("slugger", 90, listOf(false,false,false)).kind)
        assertEquals("safe_hit", BatterProfileTables.fairPlay("contact", 90, listOf(false,false,false)).kind)
        val game = newState().games.first()
        val diagnostic = ProfileToyPitchSource(game.seed, List(9) { "standard" }, List(9) { "standard" })
        assertThrows(IllegalArgumentException::class.java) { SeasonMatch(game, GameSession(game.seed, source = diagnostic)) }
    }
    @Test fun sameTypeExchangeChangesOnlyPeopleAndFrozenReplaySurvivesLaterPlans() {
        val base = newState()
        val swapped = base.lineup(club).toMutableList().also { val first = it[0]; it[0] = it[3]; it[3] = first }
        val a = SeasonSession(base).also { it.finishCurrent() }
        val b = SeasonSession(base.applyLineup(club, swapped)).also { it.finishCurrent() }
        val ac = a.lastCompletedCheckpoint!!; val bc = b.lastCompletedCheckpoint!!
        assertEquals(ac.snapshot, bc.snapshot); assertEquals(ac.source, bc.source)
        assertEquals(ac.advanceCalls, bc.advanceCalls)
        assertEquals(a.state.results.single().batters.map { it.counts }, b.state.results.single().batters.map { it.counts })
        assertNotEquals(a.state.results.single().batters.map { it.playerId }, b.state.results.single().batters.map { it.playerId })
        val first = b.state.results.single()
        val firstBytes = CheckpointCodec.encodeSummary(first)
        val afterEdit = b.state.applyLineup(club, base.lineup(club))
        assertArrayEquals(firstBytes, CheckpointCodec.encodeSummary(afterEdit.results.single()))
        assertEquals(first, CheckpointCodec.decodeSummary(firstBytes))
        assertEquals(first, GameSummary.fromCore(first.game, first.replay().snapshot, first.maxPitches, first.lineup, first.profileInput))
        assertEquals(base.worldProfiles, afterEdit.worldProfiles)
    }
    @Test fun v3CodecAndReplayRejectRehashedProfileAndCursorCorruption() {
        val session = SeasonSession(newState())
        repeat(7) { session.advanceOnePitch() }
        val active = session.state.active!!
        val frame = CheckpointCodec.encodeSeason(session.state)
        assertEquals(session.state, CheckpointCodec.decodeSeason(frame))
        assertArrayEquals(frame, CheckpointCodec.encodeSeason(CheckpointCodec.decodeSeason(frame)))
        val checkpoint = CheckpointCodec.decodeGame(CheckpointCodec.encodeGame(active))
        assertEquals(active, checkpoint.recover("42").checkpoint())
        val entries = active.profileInput!!.home.toMutableList()
        entries[0] = entries[0].copy(profileId = "slugger")
        val forged = GameProfileInput(active.profileInput.away, entries)
        assertThrows(IllegalArgumentException::class.java) {
            GameCheckpoint(active.game, active.maxPitches, active.advanceCalls, active.snapshot, active.source, active.initial, active.lineup, forged)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GameCheckpoint(active.game, active.maxPitches, active.advanceCalls, active.snapshot, active.source.copy(kind = "toy"), active.initial, active.lineup, active.profileInput)
        }
        val raw = frame.copyOf()
        val from = "toy-batter-profile-v1".toByteArray(); val to = "toy-batter-profile-v2".toByteArray()
        val at = raw.asList().windowed(from.size).indexOfFirst { it == from.asList() }
        assertTrue(at >= 0); to.copyInto(raw, at)
        assertThrows(IllegalArgumentException::class.java) { CheckpointCodec.decodeSeason(raw) }
        assertThrows(IllegalArgumentException::class.java) { CheckpointCodec.decodeSeason(frame + 0) }
        assertThrows(IllegalArgumentException::class.java) {
            SeasonState.restore("42", club, session.state.dayIndex, emptyList(), active, SeasonVersions.lineup())
        }
    }
}

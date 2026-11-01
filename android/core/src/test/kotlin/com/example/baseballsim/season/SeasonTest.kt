package com.example.baseballsim.season

import com.example.baseballsim.core.*
import com.google.gson.*
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

internal object Ref {
    val gson = GsonBuilder().serializeNulls().create()
    fun bytes(name: String): ByteArray = checkNotNull(javaClass.getResourceAsStream("/pennant-reference/$name")).use { it.readBytes() }
    fun json(name: String) = JsonParser.parseString(bytes("frozen/$name").toString(Charsets.UTF_8)).asJsonObject
    fun s(j: JsonObject, key: String) = j[key].asString
    fun n(j: JsonObject, key: String) = j[key].asLong
    fun counts(j: JsonObject) = EventCounts(n(j,"completed_plate_appearances"),n(j,"safe_hit_events"),n(j,"walk_events"),n(j,"strikeout_events"),n(j,"home_run_events"))
    fun fixture(f: Fixture): Map<String, Any> = linkedMapOf("season_id" to f.seasonId,"schedule_ordinal" to f.ordinal,"game_id" to f.gameId,
        "day_index" to f.dayIndex,"week" to f.week,"weekday" to f.weekday,"league_id" to f.leagueId,"series_round" to f.seriesRound,
        "cycle" to f.cycle,"round_in_cycle" to f.roundInCycle,"base_round" to f.baseRound,"matchup_index" to f.matchupIndex,
        "series_game" to f.seriesGame,"away_club_id" to f.awayClubId,"home_club_id" to f.homeClubId)
    fun summary(j: JsonObject, games: List<ScheduledGame>): GameSummary {
        val game = games[j["schedule_ordinal"].asInt-1]
        assertEquals(game.fixture.gameId,s(j,"game_id"));assertEquals(game.seed,s(j,"seed_u64"))
        val lines=j["line_score"].asJsonObject
        val flat=(0..8).flatMap { i -> listOf(lines["away"].asJsonArray[i],lines["home"].asJsonArray[i]) }.map { if(it.isJsonNull) null else it.asLong }
        val people=j["batter_counts"].asJsonArray.map { p -> val v=p.asJsonObject
            PlayerGameCounts(s(v,"player_id"),if(s(v,"side")=="away")game.fixture.awayClubId else game.fixture.homeClubId,v["slot"].asInt,s(v,"side"),counts(v)) }
        val score=j["score"].asJsonObject;val delta=j["terminal_score_delta"].asJsonObject
        return GameSummary(game,10_000,Status.valueOf(s(j,"status").uppercase()),s(j,"outcome"),s(j,"reason"),
            Score(n(score,"away"),n(score,"home")),flat,Score(n(delta,"away"),n(delta,"home")),n(j,"pitches_accepted"),people)
    }
    fun hash(b: ByteArray)=MessageDigest.getInstance("SHA-256").digest(b).joinToString(""){ "%02x".format(it) }
}

class SeasonTest {
    @Test fun frozenReceiptBytesRemainUnchanged() {
        assertEquals("ba54cc0173928f5fbaa752473f09a1db9b31a9c36e29af075636979a518745a8",Ref.hash(Ref.bytes("FREEZE_MANIFEST.json")))
        val seal=JsonParser.parseString(Ref.bytes("FREEZE_MANIFEST.json").toString(Charsets.UTF_8)).asJsonObject
        for((name,meta) in seal["files"].asJsonObject.entrySet()) {
            val bytes=Ref.bytes(name);assertEquals(name,meta.asJsonObject["bytes"].asInt,bytes.size)
            assertEquals(name,meta.asJsonObject["sha256"].asString,Ref.hash(bytes))
        }
    }
    @Test fun fullIndependentScheduleRosterCalendarAnd2160Seeds() {
        val expected=Ref.json("schedule.json")["games"].asJsonArray
        assertEquals(720,PennantSchedule.fixtures.size)
        PennantSchedule.fixtures.forEachIndexed { i,f -> assertEquals("schedule ${i+1}",expected[i],Ref.gson.toJsonTree(Ref.fixture(f))) }
        val days=Ref.json("calendar.json")["days"].asJsonArray
        PennantSchedule.calendar.forEachIndexed { i,d -> assertEquals(days[i],Ref.gson.toJsonTree(mapOf("day_index" to d.dayIndex,"week" to d.week,
            "weekday" to d.weekday,"is_rest_day" to d.isRestDay,"game_ordinals" to d.gameOrdinals))) }
        val clubs=PennantSchedule.clubs.map { c -> mapOf("club_id" to c.id,"league_id" to c.leagueId,"display_name" to c.displayName,
            "players" to c.players.map{mapOf("player_id" to it.id,"slot" to it.slot)}) }
        assertEquals(Ref.json("roster.json")["clubs"],Ref.gson.toJsonTree(clubs))
        for(master in listOf("0","42","2026")) {
            val e=Ref.json("seeds_master_$master.json")["assignments"].asJsonArray
            PennantSchedule.allocate(master).forEachIndexed { i,g -> assertEquals(e[i],Ref.gson.toJsonTree(mapOf("game_id" to g.fixture.gameId,"schedule_ordinal" to g.fixture.ordinal,"seed_u64" to g.seed))) }
        }
    }
    @Test fun handLedgerStandingsAndDuplicateConflictAreFullStateOperations() {
        val fixture=Ref.json("small_ledger.json");var state=SeasonState.initial("42")
        val games=fixture["input_results"].asJsonArray.map { Ref.summary(it.asJsonObject,state.games) }
        games.forEachIndexed { i,result ->
            state=state.beginNext().accept(result)
            if(i==5||i==11) {
                val expected=fixture["checkpoints"].asJsonArray[if(i==5)0 else 1].asJsonObject["expected_standings"]
                val actual=state.standings.map { s -> val t=s.totals;mapOf("club_id" to t.clubId,"league_id" to PennantSchedule.club(t.clubId).leagueId,
                    "played" to t.played,"wins" to t.wins,"losses" to t.losses,"draws" to t.draws,"runs_for" to t.runsFor,"runs_against" to t.runsAgainst,
                    "rank" to s.rank,"win_ratio" to t.ratio?.let{mapOf("numerator" to it.numerator,"denominator" to it.denominator)}) }
                assertEquals(expected,Ref.gson.toJsonTree(actual))
            }
        }
        state=state.beginNext();val before=CheckpointCodec.encodeSeason(state)
        assertSame(state,state.accept(games.first()));assertArrayEquals(before,CheckpointCodec.encodeSeason(state))
        val changed=Ref.summary(fixture["receipt_cases"].asJsonArray[1].asJsonObject["input"].asJsonObject,state.games)
        assertThrows(IllegalArgumentException::class.java){state.accept(changed)}
        assertArrayEquals(before,CheckpointCodec.encodeSeason(state));assertEquals(13,state.nextOrdinal);assertNotNull(state.active)
    }
    @Test fun liveCheckpointRoundtripReplayAndFixedProductionDefinition() {
        val state=SeasonState.initial("42").beginNext();val owned=state.active!!.recover("42")
        repeat(17){owned.advanceOnePitch()}
        val checkpoint=owned.checkpoint();val encoded=CheckpointCodec.encodeGame(checkpoint)
        assertEquals(checkpoint,CheckpointCodec.decodeGame(encoded));assertEquals(checkpoint,checkpoint.recover("42").checkpoint())
        val saved=state.checkpoint(checkpoint);assertEquals(saved,CheckpointCodec.decodeSeason(CheckpointCodec.encodeSeason(saved)))
        assertThrows(IllegalArgumentException::class.java){checkpoint.recover("0")}
        val altered=GameCheckpoint(checkpoint.game,20_000,checkpoint.advanceCalls,checkpoint.snapshot,checkpoint.source,checkpoint.initial)
        assertThrows(IllegalArgumentException::class.java){altered.recover("42")}
        assertThrows(IllegalArgumentException::class.java){state.checkpoint(altered)}
    }
}

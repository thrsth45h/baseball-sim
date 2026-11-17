package com.example.baseballsim.season

import com.example.baseballsim.core.*
import java.math.BigInteger

data class PlayerGameCounts(val playerId: String, val clubId: String, val slot: Int, val side: String, val counts: EventCounts)
/** Legacy totals retain their historical fixed slot; v2 person totals deliberately have no slot. */
data class PlayerSeasonCounts(val playerId: String, val clubId: String, val slot: Int?, val counts: EventCounts)

/** Immutable completed summary; accepting one is not a proof of its full legal pitch history. */
class GameSummary(val game: ScheduledGame, val maxPitches: Long, val status: Status, val outcome: String, val reason: String,
    val score: Score, lineScore: List<Long?>, val terminalDelta: Score, val pitchesAccepted: Long,
    batters: List<PlayerGameCounts>, val lineup: GameLineup = GameLineup.initial(game)) : SeasonValue() {
    val lineScore = freeze(lineScore)
    val batters = freeze(batters)
    override val values get() = listOf(game, maxPitches, status, outcome, reason, score, lineScore, terminalDelta, pitchesAccepted, batters, lineup)
    init {
        lineup.validate(game)
        require(status == Status.COMPLETED && reason in endingReasons)
        require(maxPitches > 0 && pitchesAccepted in 0..maxPitches)
        require(outcome == if (score.away > score.home) "away_win" else if (score.home > score.away) "home_win" else "draw")
        require(outcome == when (reason) { "away_lead_after_bottom9" -> "away_win"; "tie_after_nine" -> "draw"; else -> "home_win" })
        require(lineScore.size == 18 && lineScore.all { it == null || it >= 0 })
        require(lineScore.filterIndexed { i, _ -> i % 2 == 0 }.all { it != null })
        require(lineScore.filterIndexed { i, _ -> i % 2 == 1 }.take(8).all { it != null })
        require((lineScore[17] == null) == (reason == "home_lead_after_top9"))
        require(lineScore.filterIndexed { i, _ -> i % 2 == 0 }.fold(0L) { a, b -> add(a, b ?: 0) } == score.away)
        require(lineScore.filterIndexed { i, _ -> i % 2 == 1 }.fold(0L) { a, b -> add(a, b ?: 0) } == score.home)
        require(terminalDelta.away == 0L && terminalDelta.home <= score.home)
        require(if (reason.startsWith("walkoff_")) terminalDelta.home > 0 && outcome == "home_win" else terminalDelta == Score())
        if (reason.startsWith("walkoff_")) {
            val delta = terminalDelta.home
            val margin = score.home - score.away
            require(delta <= checkNotNull(lineScore[17])) { "Terminal runs exceed the final half-inning runs" }
            require(when (reason) {
                "walkoff_walk" -> delta == 1L && margin == 1L
                "walkoff_safe_hit" -> delta in 1L..3L && margin == 1L
                "walkoff_home_run" -> delta in 1L..4L && margin <= delta
                else -> false
            }) { "Inconsistent local walkoff summary" }
        }
        require(batters.size == 18)
        batters.forEachIndexed { index, row ->
            val side = if (index < 9) "away" else "home"
            val club = PennantSchedule.club(if (index < 9) game.fixture.awayClubId else game.fixture.homeClubId)
            val person = (if (index < 9) lineup.away else lineup.home)[index % 9]
            require(row.side == side && row.slot == index % 9 + 1 && row.clubId == club.id && row.playerId == person)
        }
    }
    companion object {
        fun fromCore(game: ScheduledGame, snapshot: GameSnapshot, maxPitches: Long = 10_000,
            lineup: GameLineup = GameLineup.initial(game)): GameSummary {
            require(snapshot.status == Status.COMPLETED) { "Only COMPLETED games enter the ledger" }
            return GameSummary(game, maxPitches, snapshot.status, snapshot.outcome!!, snapshot.reason!!, snapshot.score,
                snapshot.lineScore, snapshot.terminalScoreDelta, snapshot.pitchesAccepted,
                SeasonRecords.project(game.fixture, snapshot.batterCounts, lineup), lineup)
        }
    }
}

data class WinRatio private constructor(val numerator: Long, val denominator: Long) : Comparable<WinRatio> {
    init { require(denominator > 0 && numerator in 0..denominator && BigInteger.valueOf(numerator).gcd(BigInteger.valueOf(denominator)) == BigInteger.ONE) }
    override fun compareTo(other: WinRatio): Int = BigInteger.valueOf(numerator).multiply(BigInteger.valueOf(other.denominator))
        .compareTo(BigInteger.valueOf(other.numerator).multiply(BigInteger.valueOf(denominator)))
    companion object {
        fun of(wins: Long, decisiveGames: Long): WinRatio {
            require(wins >= 0 && decisiveGames > 0 && wins <= decisiveGames)
            var a = wins; var b = decisiveGames
            while (b != 0L) { val r = a % b; a = b; b = r }
            return WinRatio(wins / a, decisiveGames / a)
        }
    }
}

data class ClubTotals(val clubId: String, val played: Long = 0, val wins: Long = 0, val losses: Long = 0, val draws: Long = 0,
    val runsFor: Long = 0, val runsAgainst: Long = 0) {
    init {
        PennantSchedule.club(clubId)
        require(listOf(played, wins, losses, draws, runsFor, runsAgainst).all { it >= 0 })
        require(played == add(add(wins, losses), draws))
    }
    val ratio: WinRatio? get() = if (add(wins, losses) == 0L) null else WinRatio.of(wins, add(wins, losses))
}
data class Standing(val totals: ClubTotals, val rank: Int?)

object SeasonRecords {
    fun plusCounts(a: EventCounts, b: EventCounts) = EventCounts(add(a.completedPlateAppearances, b.completedPlateAppearances),
        add(a.safeHitEvents, b.safeHitEvents), add(a.walkEvents, b.walkEvents), add(a.strikeoutEvents, b.strikeoutEvents), add(a.homeRunEvents, b.homeRunEvents))
    fun project(fixture: Fixture, counts: List<EventCounts>, lineup: GameLineup = GameLineup(
        PennantSchedule.club(fixture.awayClubId).players.map { it.id }, PennantSchedule.club(fixture.homeClubId).players.map { it.id })): List<PlayerGameCounts> {
        require(counts.size == 18)
        LineupRoster.validate(fixture.awayClubId, lineup.away, SeasonVersions.lineup())
        LineupRoster.validate(fixture.homeClubId, lineup.home, SeasonVersions.lineup())
        return freeze(counts.mapIndexed { i, count ->
            val club = PennantSchedule.club(if (i < 9) fixture.awayClubId else fixture.homeClubId)
            PlayerGameCounts((if (i < 9) lineup.away else lineup.home)[i % 9], club.id, i % 9 + 1, if (i < 9) "away" else "home", count)
        })
    }
    fun players(contributions: List<List<PlayerGameCounts>>, versions: SeasonVersions = SeasonVersions()): List<PlayerSeasonCounts> {
        versions.requireSupported()
        val records = PennantSchedule.clubs.flatMap { club -> LineupRoster.playerIds(club.id, versions).mapIndexed { i, id ->
            PlayerSeasonCounts(id, club.id, if (versions.lineupEnabled) null else i + 1, EventCounts())
        } }.associateBy { it.playerId }.toMutableMap()
        contributions.forEach { game -> game.forEach { row ->
            val before = requireNotNull(records[row.playerId]); require(before.clubId == row.clubId && (versions.lineupEnabled || before.slot == row.slot))
            require(row.slot in 1..9 && row.side in setOf("away", "home"))
            records[row.playerId] = before.copy(counts = plusCounts(before.counts, row.counts))
        } }
        return freeze(records.values)
    }
    fun standings(results: List<GameSummary>): List<Standing> {
        val totals = PennantSchedule.clubIds.associateWith { ClubTotals(it) }.toMutableMap()
        for (game in results) {
            for ((id, own, other) in listOf(Triple(game.game.fixture.awayClubId, game.score.away, game.score.home), Triple(game.game.fixture.homeClubId, game.score.home, game.score.away))) {
                val t = totals.getValue(id)
                totals[id] = t.copy(played = add(t.played, 1), wins = add(t.wins, if (own > other) 1 else 0),
                    losses = add(t.losses, if (own < other) 1 else 0), draws = add(t.draws, if (own == other) 1 else 0),
                    runsFor = add(t.runsFor, own), runsAgainst = add(t.runsAgainst, other))
            }
        }
        return rank(totals.values.toList())
    }
    fun rank(totals: List<ClubTotals>): List<Standing> {
        require(totals.map { it.clubId }.toSet() == PennantSchedule.clubIds.toSet() && totals.size == 12)
        return freeze(listOf("L1", "L2").flatMap { league ->
            val group = totals.filter { PennantSchedule.club(it.clubId).leagueId == league }
            val sorted = group.sortedWith { a, b ->
                val x = a.ratio; val y = b.ratio
                when { x == null && y != null -> 1; x != null && y == null -> -1; x != null && y != null && x != y -> -x.compareTo(y); else -> a.clubId.compareTo(b.clubId) }
            }
            sorted.map { row -> Standing(row, row.ratio?.let { ratio -> 1 + group.count { it.ratio?.let { other -> other > ratio } == true } }) }
        })
    }
}

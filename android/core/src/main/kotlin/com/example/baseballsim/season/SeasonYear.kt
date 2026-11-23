package com.example.baseballsim.season

import com.example.baseballsim.core.EventCounts
import com.example.baseballsim.core.seedFromText

/** One completed year of standings and person counts, without the current-year game ledger. */
class SeasonYear(val number: Long, standings: List<Standing>, players: List<PlayerSeasonCounts>) : SeasonValue() {
    val standings = freeze(standings)
    val players = freeze(players)
    override val values get() = listOf(number, standings, players)

    init {
        require(number > 0)
        require(standings == SeasonRecords.rank(standings.map { it.totals }))
        // The current schedule allows 120 games per club, at most 10,000 pitches per game,
        // and at most four runs on one pitch. These bounds also prevent wrapped sums on read.
        require(standings.all { it.totals.played == 120L && it.totals.runsFor <= 4_800_000L && it.totals.runsAgainst <= 4_800_000L })
        for (league in listOf("L1", "L2")) {
            val totals = standings.map { it.totals }.filter { PennantSchedule.club(it.clubId).leagueId == league }
            require(totals.sumOf { it.wins } == totals.sumOf { it.losses })
            require(totals.sumOf { it.runsFor } == totals.sumOf { it.runsAgainst })
            require(totals.sumOf { it.draws } % 2 == 0L)
        }
        val roster = SeasonRecords.players(emptyList(), SeasonVersions.batterProfile())
        require(players.all { it.counts.completedPlateAppearances <= 1_200_000L })
        require(players.map { it.copy(counts = EventCounts()) } == roster)
    }

    companion object {
        fun completed(number: Long, season: SeasonState): SeasonYear {
            check(season.profilesEnabled && season.status == SeasonStatus.COMPLETE && season.active == null)
            return SeasonYear(number, season.standings, season.playerCounts)
        }
    }
}

object SeasonYearSeeds {
    const val VERSION = "season-year-seeds-v1"

    fun start(rootSeed: String, year: Long): String {
        require(year > 0)
        val priorDraws = (year - 1).toULong() * PennantSchedule.fixtures.size.toULong()
        return (seedFromText(rootSeed) + 0x9E3779B97F4A7C15uL * priorDraws).toString()
    }

    fun next(season: SeasonState, rootSeed: String, year: Long): SeasonState {
        check(season.profilesEnabled && season.status == SeasonStatus.COMPLETE && season.active == null)
        require(season.masterSeed == start(rootSeed, year))
        val seed = start(rootSeed, Math.addExact(year, 1))
        return SeasonState.restore(seed, season.selectedClubId, 1, emptyList(), null,
            season.versions, season.clubLineups, season.worldProfileTableVersion, season.worldProfiles)
    }
}

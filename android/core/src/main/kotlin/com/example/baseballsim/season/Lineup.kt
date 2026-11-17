package com.example.baseballsim.season

/** Versioned fixed trial data. The number in a person ID is neither batting order nor jersey number. */
object LineupRoster {
    private val legacy = freezeMap(PennantSchedule.clubs.associate { club -> club.id to freeze(club.players.map { it.id }) })
    private val expanded = freezeMap(PennantSchedule.clubIds.associateWith { club ->
        freeze((1..12).map { "$club-P${it.toString().padStart(2, '0')}" })
    })
    fun playerIds(clubId: String, versions: SeasonVersions): List<String> {
        versions.requireSupported()
        return requireNotNull((if (versions.lineupEnabled) expanded else legacy)[clubId]) { "Unknown club" }
    }
    fun validate(clubId: String, players: List<String>, versions: SeasonVersions) {
        val canonical = playerIds(clubId, versions)
        require(players.size == 9 && players.toSet().size == 9 && players.all { it in canonical }) { "Invalid batting order" }
        if (!versions.lineupEnabled) require(players == canonical) { "Legacy batting order is fixed" }
    }
    fun defaultPlans(versions: SeasonVersions): Map<String, List<String>> = freezeMap(PennantSchedule.clubIds.associateWith {
        freeze(playerIds(it, versions).take(9))
    })
    fun checkedPlans(plans: Map<String, List<String>>, versions: SeasonVersions): Map<String, List<String>> {
        require(plans.keys == PennantSchedule.clubIds.toSet()) { "A plan is required for every fixed club" }
        return freezeMap(PennantSchedule.clubIds.associateWith { club ->
            val order = requireNotNull(plans[club]); validate(club, order, versions); freeze(order)
        })
    }
}

/** The actual participants frozen before a game's first advance; never inferred from later club plans. */
class GameLineup(away: List<String>, home: List<String>) : SeasonValue() {
    val away = freeze(away)
    val home = freeze(home)
    override val values get() = listOf(away, home)
    fun validate(game: ScheduledGame) {
        LineupRoster.validate(game.fixture.awayClubId, away, game.versions)
        LineupRoster.validate(game.fixture.homeClubId, home, game.versions)
    }
    companion object {
        fun initial(game: ScheduledGame) = GameLineup(
            LineupRoster.playerIds(game.fixture.awayClubId, game.versions).take(9),
            LineupRoster.playerIds(game.fixture.homeClubId, game.versions).take(9))
        fun fromPlans(game: ScheduledGame, plans: Map<String, List<String>>) = GameLineup(
            requireNotNull(plans[game.fixture.awayClubId]), requireNotNull(plans[game.fixture.homeClubId])).also { it.validate(game) }
    }
}

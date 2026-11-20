package com.example.baseballsim.season

import com.example.baseballsim.core.*

/** Closed canonical trial data; no editable abilities or future-version inference. */
object BatterProfiles {
    const val TABLE_VERSION = "toy-batter-profile-v1"
    const val SOURCE_VERSION = "toy-batter-source-v1"
    const val CURSOR_KIND = "batter-profile-toy-v1"
    private val canonical: Map<String, String> = freezeMap(PennantSchedule.clubIds.flatMap { club ->
        (1..12).map { n -> "$club-P${n.toString().padStart(2, '0')}" to BatterProfileTables.ids[(n - 1) % 3] }
    }.toMap())
    fun profileFor(playerId: String): String = requireNotNull(canonical[playerId]) { "Unknown trial person" }
    fun defaultWorld(versions: SeasonVersions): Map<String, String> = if (versions.profilesEnabled) canonical else emptyMap()
    fun checkedWorld(versions: SeasonVersions, tableVersion: String?, profiles: Map<String, String>): Map<String, String> {
        versions.requireSupported()
        require(tableVersion == if (versions.profilesEnabled) TABLE_VERSION else null) { "Wrong world profile table" }
        require(profiles == defaultWorld(versions)) { "World profiles disagree with the fixed trial roster" }
        return if (versions.profilesEnabled) canonical else emptyMap()
    }
}

data class BatterProfileEntry(val playerId: String, val profileId: String) {
    init { require(profileId in BatterProfileTables.ids) }
}

/** Actual participants and source definition fixed before the first advance. */
class GameProfileInput(away: List<BatterProfileEntry>, home: List<BatterProfileEntry>,
    val tableVersion: String = BatterProfiles.TABLE_VERSION, val sourceVersion: String = BatterProfiles.SOURCE_VERSION) : SeasonValue() {
    val away = freeze(away)
    val home = freeze(home)
    override val values get() = listOf(away, home, tableVersion, sourceVersion)
    init {
        require(this.away.size == 9 && this.home.size == 9)
        require(tableVersion == BatterProfiles.TABLE_VERSION && sourceVersion == BatterProfiles.SOURCE_VERSION)
    }
    fun validate(game: ScheduledGame, lineup: GameLineup) {
        require(game.versions.profilesEnabled)
        lineup.validate(game)
        require(away.map { it.playerId } == lineup.away && home.map { it.playerId } == lineup.home) { "Profile people disagree with start lineup" }
        require((away + home).all { it.profileId == BatterProfiles.profileFor(it.playerId) }) { "Start profile disagrees with canonical person" }
    }
    fun profile(side: String, slot: Int): String {
        require(side in setOf("away", "home") && slot in 1..9)
        return (if (side == "away") away else home)[slot - 1].profileId
    }
    fun newSource(seed: String) = ProfileToyPitchSource(seed, away.map { it.profileId }, home.map { it.profileId })
    companion object {
        fun fromWorld(game: ScheduledGame, lineup: GameLineup, profiles: Map<String, String>): GameProfileInput? {
            if (!game.versions.profilesEnabled) { require(profiles.isEmpty()); return null }
            return GameProfileInput(lineup.away.map { BatterProfileEntry(it, requireNotNull(profiles[it])) },
                lineup.home.map { BatterProfileEntry(it, requireNotNull(profiles[it])) }).also { it.validate(game, lineup) }
        }
        fun canonical(game: ScheduledGame, lineup: GameLineup): GameProfileInput? = fromWorld(game, lineup, BatterProfiles.defaultWorld(game.versions))
    }
}

object SeasonSources {
    fun create(game: ScheduledGame, lineup: GameLineup, input: GameProfileInput?): PitchSource {
        lineup.validate(game)
        return if (game.versions.profilesEnabled) {
            requireNotNull(input).also { it.validate(game, lineup) }.newSource(game.seed)
        } else { require(input == null); ToyPitchSource(game.seed) }
    }
    fun validate(game: ScheduledGame, lineup: GameLineup, input: GameProfileInput?) {
        if (game.versions.profilesEnabled) requireNotNull(input).validate(game, lineup) else require(input == null)
    }
}

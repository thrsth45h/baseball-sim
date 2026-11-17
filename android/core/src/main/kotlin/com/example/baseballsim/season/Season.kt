package com.example.baseballsim.season

import com.example.baseballsim.core.Status
import com.example.baseballsim.core.seedFromText

enum class SeasonStatus { IN_PROGRESS, COMPLETE, BLOCKED }

/** No database, UI, clocks, global RNG, or retained logs of completed games. */
class SeasonState private constructor(val masterSeed: String, val selectedClubId: String, val dayIndex: Int,
    val results: List<GameSummary>, val active: GameCheckpoint?, val games: List<ScheduledGame>,
    val standings: List<Standing>, val playerCounts: List<PlayerSeasonCounts>, val versions: SeasonVersions,
    plans: Map<String, List<String>>, validateLedger: Boolean) : SeasonValue() {
    // Public restore validates/copies; private transitions only share already deeply immutable plans.
    val clubLineups = if (validateLedger) LineupRoster.checkedPlans(plans, versions) else plans
    val lineupEnabled get() = versions.lineupEnabled
    fun playerIds(clubId: String) = LineupRoster.playerIds(clubId, versions)
    fun lineup(clubId: String): List<String> = requireNotNull(clubLineups[clubId]) { "Unknown club" }
    val nextOrdinal get() = results.size + 1
    val status get() = when { active?.snapshot?.status == Status.ABORTED -> SeasonStatus.BLOCKED; results.size == 720 -> SeasonStatus.COMPLETE; else -> SeasonStatus.IN_PROGRESS }
    override val values get() = listOf(masterSeed, selectedClubId, dayIndex, results, active, standings, playerCounts, versions, clubLineups)
    init {
        versions.requireSupported()
        seedFromText(masterSeed); PennantSchedule.club(selectedClubId)
        require(dayIndex in 1..141 && results.size <= 720)
        if (validateLedger) results.forEachIndexed { i, result -> require(result.game == games[i] && result.maxPitches == 10_000L) { "Ledger is not the canonical GameId sequence" } }
        val minimumDay = if (results.isEmpty()) 1 else {
            val lastDay = results.last().game.fixture.dayIndex
            if (results.size == 720 || games[results.size].fixture.dayIndex > lastDay) lastDay + 1 else lastDay
        }
        require(dayIndex >= minimumDay) { "A completed calendar day cannot be reopened" }
        if (results.size == 720) require(dayIndex == 141 && active == null)
        else {
            require(dayIndex <= games[results.size].fixture.dayIndex)
            if (active != null) {
                active.requireProduction(games[results.size]); require(dayIndex == active.game.fixture.dayIndex)
                // No plan can be edited during any active/blocked game, so its start participants
                // must agree with the still-current plans. Old completed results may legitimately differ.
                require(active.lineup == GameLineup.fromPlans(active.game, clubLineups)) { "Active participants disagree with the saved club plans" }
            }
        }
    }
    fun selectClub(clubId: String) = changed(selected = clubId)
    fun applyLineup(clubId: String, playerIds: List<String>): SeasonState {
        check(lineupEnabled && status == SeasonStatus.IN_PROGRESS && active == null) { "Lineup editing is unavailable" }
        require(clubId == selectedClubId) { "Only the selected club may be edited" }
        LineupRoster.validate(clubId, playerIds, versions)
        if (lineup(clubId) == playerIds) return this
        return changed(nextPlans = LineupRoster.checkedPlans(clubLineups + (clubId to playerIds), versions))
    }
    fun beginNext(): SeasonState {
        check(status == SeasonStatus.IN_PROGRESS && active == null)
        val game = games[results.size]
        return changed(day = game.fixture.dayIndex, nextActive = SeasonMatch(game, lineup = GameLineup.fromPlans(game, clubLineups)).checkpoint())
    }
    fun checkpoint(match: GameCheckpoint): SeasonState {
        check(status == SeasonStatus.IN_PROGRESS && active != null)
        require(match.game == active.game && match.maxPitches == active.maxPitches && match.initial == active.initial && match.lineup == active.lineup)
        require(match.advanceCalls >= active.advanceCalls)
        if (match.advanceCalls == active.advanceCalls) require(match == active) { "The same advance boundary must have the same state" }
        return changed(nextActive = match)
    }
    fun accept(result: GameSummary): SeasonState {
        val prior = results.find { it.game.fixture.gameId == result.game.fixture.gameId }
        if (prior != null) { require(prior == result) { "Conflicting completed result" }; return this }
        check(status == SeasonStatus.IN_PROGRESS && active != null)
        require(result.game == active.game && result.maxPitches == active.maxPitches && result.lineup == active.lineup)
        val updated = results + result
        val moreToday = updated.size < 720 && games[updated.size].fixture.dayIndex == dayIndex
        return changed(day = if (moreToday) dayIndex else dayIndex + 1, nextResults = freeze(updated), nextActive = null)
    }
    fun advanceRestDay(): SeasonState {
        check(status == SeasonStatus.IN_PROGRESS && active == null)
        require(PennantSchedule.calendar[dayIndex - 1].isRestDay)
        return changed(day = dayIndex + 1)
    }
    private fun changed(selected: String = selectedClubId, day: Int = dayIndex, nextResults: List<GameSummary> = results,
        nextActive: GameCheckpoint? = active, nextPlans: Map<String, List<String>> = clubLineups) = SeasonState(masterSeed, selected, day, nextResults, nextActive, games,
            if (nextResults === results) standings else SeasonRecords.standings(nextResults),
            if (nextResults === results) playerCounts else SeasonRecords.players(nextResults.map { it.batters }, versions), versions, nextPlans, false)
    companion object {
        fun initial(masterSeed: String = "42", selectedClubId: String = "L1-T01") = restore(masterSeed, selectedClubId, 1, emptyList(), null)
        fun initialLineup(masterSeed: String = "42", selectedClubId: String = "L1-T01") =
            restore(masterSeed, selectedClubId, 1, emptyList(), null, SeasonVersions.lineup())
        fun restore(masterSeed: String, selectedClubId: String, dayIndex: Int, results: List<GameSummary>, active: GameCheckpoint?,
            versions: SeasonVersions = SeasonVersions(), lineups: Map<String, List<String>> = LineupRoster.defaultPlans(versions)) =
            SeasonState(masterSeed, selectedClubId, dayIndex, freeze(results), active, PennantSchedule.allocate(masterSeed, versions),
                SeasonRecords.standings(results), SeasonRecords.players(results.map { it.batters }, versions), versions, lineups, true)
    }
}

/** Calculation-only owner. The later store must commit before adopting/publishing a candidate. */
class SeasonSession(initial: SeasonState = SeasonState.initial()) {
    var state: SeasonState = initial; private set
    var lastCompletedCheckpoint: GameCheckpoint? = null; private set
    private var match: SeasonMatch? = null
    init { if (state.active != null && state.status != SeasonStatus.BLOCKED) match = state.active!!.recover(state.masterSeed) }
    fun selectClub(clubId: String) { state = state.selectClub(clubId) }
    fun applyLineup(clubId: String, playerIds: List<String>) { state = state.applyLineup(clubId, playerIds) }
    fun beginNext() {
        check(state.status == SeasonStatus.IN_PROGRESS)
        if (state.active != null) return
        state = state.beginNext(); match = state.active!!.recover(state.masterSeed)
    }
    fun advanceOnePitch(): SeasonState {
        beginNext()
        val owned = checkNotNull(match)
        if (owned.snapshot.status == Status.COMPLETED) return consumeCandidate()
        owned.advanceOnePitch()
        return consumeCandidate()
    }
    fun advancePlateAppearance(): SeasonState {
        beginNext(); if (!match!!.snapshot.done) match!!.advancePlateAppearance(); return consumeCandidate()
    }
    fun finishCurrent(): SeasonState {
        beginNext(); match!!.finish(); return consumeCandidate()
    }
    private fun consumeCandidate(): SeasonState {
        val owned = checkNotNull(match); val checkpoint = owned.checkpoint()
        state = state.checkpoint(checkpoint)
        if (checkpoint.snapshot.status == Status.COMPLETED) {
            state = state.accept(GameSummary.fromCore(owned.game, owned.snapshot, owned.engine.maxPitches, owned.lineup)); lastCompletedCheckpoint = checkpoint; match = null
        }
        return state
    }
    fun advanceDay(stopRequested: () -> Boolean = { false }): SeasonState {
        check(state.status == SeasonStatus.IN_PROGRESS)
        if (stopRequested() && state.active == null) return state
        val day = state.dayIndex
        if (state.active == null && PennantSchedule.calendar[day - 1].isRestDay) state = state.advanceRestDay()
        else do { finishCurrent() } while (state.status == SeasonStatus.IN_PROGRESS && state.dayIndex == day && !stopRequested())
        return state
    }
    fun finishSeason(stopRequested: () -> Boolean = { false }): SeasonState {
        while (state.status == SeasonStatus.IN_PROGRESS) { advanceDay(stopRequested); if (stopRequested()) break }
        return state
    }
    fun openNextSelected(): SeasonState {
        check(state.status == SeasonStatus.IN_PROGRESS)
        while (true) {
            val current = state.active
            if (current != null) {
                if (state.selectedClubId in listOf(current.game.fixture.awayClubId, current.game.fixture.homeClubId)) return state
                finishCurrent()
                if (state.status != SeasonStatus.IN_PROGRESS) return state
                continue
            }
            val next = state.games[state.results.size]
            if (state.selectedClubId in listOf(next.fixture.awayClubId, next.fixture.homeClubId)) { beginNext(); return state }
            finishCurrent()
            if (state.status != SeasonStatus.IN_PROGRESS) return state
        }
    }
    val currentCheckpoint get() = state.active
}

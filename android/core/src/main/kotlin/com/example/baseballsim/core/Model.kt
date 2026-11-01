package com.example.baseballsim.core

import java.util.Collections

internal fun <T> frozen(values: List<T>): List<T> = Collections.unmodifiableList(ArrayList(values))
internal fun plus(a: Long, b: Long): Long = Math.addExact(a, b)
enum class Side { TOP, BOTTOM; val team get() = if (this == TOP) "away" else "home" }
enum class Status { IN_PROGRESS, COMPLETED, ABORTED }
enum class Stage { BETWEEN_BATTERS, PITCHING, AWAITING_RESOLUTION, HALF_COMPLETE }
data class Score(val away: Long = 0, val home: Long = 0) {
    init { require(away >= 0 && home >= 0) }
    operator fun get(team: String) = if (team == "away") away else home
}

val pitchEvents = setOf("called_ball", "called_strike_caught", "swinging_strike_caught", "foul_tip_caught",
    "ordinary_foul_uncaught", "bunt_foul_uncaught", "foul_fly_caught", "fair_ball_handoff")
val sourceAbortReasons = setOf("source_exhausted", "source_error", "invalid_pitch_input", "invalid_fair_result")
val endingReasons = setOf("home_lead_after_top9", "away_lead_after_bottom9", "tie_after_nine", "walkoff_walk", "walkoff_safe_hit", "walkoff_home_run")

data class CountState(val balls: Int = 0, val strikes: Int = 0, val pitchesSeen: Long = 0, val status: String = "active") {
    init {
        require(pitchesSeen >= 0)
        val valid = when (status) {
            "active" -> balls in 0..3 && strikes in 0..2 && if (strikes < 2) pitchesSeen == (balls + strikes).toLong() else pitchesSeen >= balls + 2L
            "walk_awarded" -> balls == 4 && strikes in 0..2 && if (strikes < 2) pitchesSeen == (4 + strikes).toLong() else pitchesSeen >= 6
            "strikeout_out" -> balls in 0..3 && strikes == 3 && pitchesSeen >= balls + 3L
            "fair_play_pending", "caught_foul_out" -> balls in 0..3 && strikes in 0..2 && if (strikes < 2) pitchesSeen == balls + strikes + 1L else pitchesSeen >= balls + 3L
            else -> false
        }
        require(valid) { "Unreachable count" }
    }
}

class HalfState(val outs: Int = 0, bases: List<Boolean> = listOf(false, false, false), val runs: Long = 0) {
    val bases: List<Boolean> = frozen(bases)
    init { require(outs in 0..3 && bases.size == 3 && runs >= 0) }
}

data class SequenceState(val inning: Int = 1, val side: Side = Side.TOP, val closedAwayRuns: Long = 0,
    val closedHomeRuns: Long = 0, val count: CountState? = null, val half: HalfState = HalfState()) {
    init { require(inning >= 1 && closedAwayRuns >= 0 && closedHomeRuns >= 0); require(half.outs != 3 || count == null) }
    val score: Score get() = if (side == Side.TOP) Score(plus(closedAwayRuns, half.runs), closedHomeRuns) else Score(closedAwayRuns, plus(closedHomeRuns, half.runs))
    val stage: Stage get() = when { half.outs == 3 -> Stage.HALF_COMPLETE; count == null -> Stage.BETWEEN_BATTERS; count.status == "active" -> Stage.PITCHING; else -> Stage.AWAITING_RESOLUTION }
}

data class EventCounts(val completedPlateAppearances: Long = 0, val safeHitEvents: Long = 0, val walkEvents: Long = 0,
    val strikeoutEvents: Long = 0, val homeRunEvents: Long = 0) {
    init {
        require(listOf(completedPlateAppearances, safeHitEvents, walkEvents, strikeoutEvents, homeRunEvents).all { it >= 0 })
        require(homeRunEvents <= safeHitEvents && plus(plus(safeHitEvents, walkEvents), strikeoutEvents) <= completedPlateAppearances)
    }
    fun completed(category: String): EventCounts {
        require(category in setOf("out_hold", "walk", "strikeout", "safe_hit", "home_run"))
        return EventCounts(plus(completedPlateAppearances, 1), plus(safeHitEvents, if (category in setOf("safe_hit", "home_run")) 1 else 0),
            plus(walkEvents, if (category == "walk") 1 else 0), plus(strikeoutEvents, if (category == "strikeout") 1 else 0), plus(homeRunEvents, if (category == "home_run") 1 else 0))
    }
}

class GameSnapshot(val sequence: SequenceState = SequenceState(), battingIndices: List<Int> = listOf(0, 0),
    batterCounts: List<EventCounts> = List(18) { EventCounts() }, val pitchesAccepted: Long = 0,
    lineScore: List<Long?> = listOf(0L) + List(17) { null }, val status: Status = Status.IN_PROGRESS,
    val reason: String? = null, val outcome: String? = null, val terminalScoreDelta: Score = Score(), val historyOrigin: String = "initial_zero") {
    val battingIndices = frozen(battingIndices)
    val batterCounts = frozen(batterCounts)
    val lineScore = frozen(lineScore)
    val halfIndex get() = (sequence.inning - 1) * 2 + if (sequence.side == Side.BOTTOM) 1 else 0
    val score get() = Score(plus(sequence.score.away, terminalScoreDelta.away), plus(sequence.score.home, terminalScoreDelta.home))
    val done get() = status != Status.IN_PROGRESS
    val completedPa get() = batterCounts.fold(0L) { total, c -> plus(total, c.completedPlateAppearances) }
    init {
        require(sequence.inning in 1..9 && battingIndices.size == 2 && battingIndices.all { it in 0..8 })
        require(batterCounts.size == 18 && pitchesAccepted >= 0 && lineScore.size == 18 && lineScore.all { it == null || it >= 0 })
        require(historyOrigin in setOf("initial_zero", "provided_snapshot"))
        when (status) {
            Status.IN_PROGRESS -> require(reason == null && outcome == null && terminalScoreDelta == Score())
            Status.ABORTED -> require(reason in sourceAbortReasons + "pitch_limit" && outcome == null && terminalScoreDelta == Score())
            Status.COMPLETED -> {
                require(reason in endingReasons)
                require(outcome == if (score.away > score.home) "away_win" else if (score.home > score.away) "home_win" else "draw")
                if (reason!!.startsWith("walkoff_")) {
                    require(sequence.inning == 9 && sequence.side == Side.BOTTOM && sequence.stage == Stage.AWAITING_RESOLUTION)
                    require(terminalScoreDelta.away == 0L && terminalScoreDelta.home > 0 && outcome == "home_win")
                    require(sequence.count!!.status == if (reason == "walkoff_walk") "walk_awarded" else "fair_play_pending")
                } else {
                    require(terminalScoreDelta == Score() && sequence.inning == 9 && sequence.stage == Stage.HALF_COMPLETE)
                    require(when (reason) { "home_lead_after_top9" -> sequence.side == Side.TOP && outcome == "home_win"
                        "away_lead_after_bottom9" -> sequence.side == Side.BOTTOM && outcome == "away_win"
                        else -> sequence.side == Side.BOTTOM && outcome == "draw" })
                }
            }
        }
        require(lineScore.take(halfIndex + 1).all { it != null } && lineScore.drop(halfIndex + 1).all { it == null })
        require(lineScore[halfIndex] == plus(sequence.half.runs, plus(terminalScoreDelta.away, terminalScoreDelta.home)))
        require(lineScore.filterIndexed { i, _ -> i % 2 == 0 }.fold(0L) { total, r -> plus(total, r ?: 0) } == score.away)
        require(lineScore.filterIndexed { i, _ -> i % 2 == 1 }.fold(0L) { total, r -> plus(total, r ?: 0) } == score.home)
    }
    fun copy(sequence: SequenceState = this.sequence, battingIndices: List<Int> = this.battingIndices, batterCounts: List<EventCounts> = this.batterCounts,
        pitchesAccepted: Long = this.pitchesAccepted, lineScore: List<Long?> = this.lineScore, status: Status = this.status, reason: String? = this.reason,
        outcome: String? = this.outcome, terminalScoreDelta: Score = this.terminalScoreDelta, historyOrigin: String = this.historyOrigin) =
        GameSnapshot(sequence, battingIndices, batterCounts, pitchesAccepted, lineScore, status, reason, outcome, terminalScoreDelta, historyOrigin)
    companion object { fun initial() = GameSnapshot() }
}

class InputFailure(val reason: String) : IllegalArgumentException(reason) {
    init { require(reason in sourceAbortReasons) }
}
class FairPlay(val kind: String, val candidateBases: Int? = null, runnerDestinations: List<Int?>? = null) {
    val runnerDestinations: List<Int?>? = runnerDestinations?.let { frozen(it) }
    init {
        if (kind !in setOf("out_hold", "safe_hit", "home_run")) throw InputFailure("invalid_fair_result")
        if (kind == "safe_hit") {
            if (candidateBases == null || candidateBases !in 1..3 || runnerDestinations == null || runnerDestinations.size != 3 || runnerDestinations.any { it != null && it !in 1..4 }) throw InputFailure("invalid_fair_result")
        } else if (candidateBases != null || runnerDestinations != null) throw InputFailure("invalid_fair_result")
    }
}
data class PitchInput(val event: String, val fair: FairPlay? = null, val pitchDraw: Int? = null, val fairDraw: Int? = null) {
    init {
        if (event !in pitchEvents || pitchDraw != null && pitchDraw !in 0..99 || fairDraw != null && fairDraw !in 0..99) throw InputFailure("invalid_pitch_input")
        if (event == "fair_ball_handoff" && fair == null) throw InputFailure("invalid_fair_result")
        if (event != "fair_ball_handoff" && (fair != null || fairDraw != null)) throw InputFailure("invalid_pitch_input")
    }
}
sealed interface SourceInput {
    data class Pitch(val value: PitchInput) : SourceInput
    data class Abort(val reason: String) : SourceInput { init { require(reason in sourceAbortReasons) } }
}
data class PitchContext(val sequence: SequenceState, val score: Score, val team: String, val batterId: String, val slot: Int)
fun interface PitchSource { fun nextPitch(context: PitchContext): SourceInput }
class ScriptedSource(inputs: List<SourceInput>) : PitchSource {
    private val inputs = frozen(inputs)
    var position = 0; private set
    var calls = 0L; private set
    override fun nextPitch(context: PitchContext): SourceInput {
        calls = plus(calls, 1)
        return if (position < inputs.size) inputs[position++] else SourceInput.Abort("source_exhausted")
    }
}

data class GameEvent(val kind: String, val pitchesAccepted: Long, val team: String? = null, val batterId: String? = null,
    val pitchEvent: String? = null, val category: String? = null, val runsAdded: Long = 0, val reason: String? = null,
    val actionKind: String? = null, val before: SequenceState? = null, val after: SequenceState? = null,
    val input: PitchInput? = null, val scoreDelta: Score = Score(), val scoredRunnerSlots: List<Int> = emptyList())
class GameResult(val snapshot: GameSnapshot) { init { require(snapshot.done) } }

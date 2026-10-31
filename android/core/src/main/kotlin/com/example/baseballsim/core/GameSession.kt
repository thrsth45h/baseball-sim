package com.example.baseballsim.core

/** One game owns its source. All UI modes call only advanceOnePitch. */
class GameSession(seed: String = "42", val maxPitches: Long = 10_000,
    source: PitchSource? = null, initial: GameSnapshot = GameSnapshot.initial()) {
    val seed = seedFromText(seed).toString()
    val source: PitchSource = source ?: ToyPitchSource(this.seed)
    val initialSnapshot = initial
    var snapshot = initial; private set
    private val log = mutableListOf<GameEvent>()
    val events: List<GameEvent> get() = frozen(log)
    init {
        require(maxPitches > 0)
        if (!initial.done) {
            require(initial.sequence.stage != Stage.AWAITING_RESOLUTION)
            require(initial.pitchesAccepted <= maxPitches)
            require(!(initial.sequence.inning == 9 && initial.sequence.side == Side.BOTTOM && initial.score.home > initial.score.away))
        }
    }
    private fun context(): PitchContext {
        val seq = snapshot.sequence; val teamIndex = if (seq.side == Side.TOP) 0 else 1
        val slot = snapshot.battingIndices[teamIndex]
        return PitchContext(seq, snapshot.score, seq.side.team, seq.side.team + "_" + (slot + 1), slot + 1)
    }
    private fun action(kind: String, after: SequenceState, input: PitchInput? = null) {
        val before = snapshot.sequence; val who = context(); val lines = snapshot.lineScore.toMutableList()
        lines[(after.inning - 1) * 2 + if (after.side == Side.BOTTOM) 1 else 0] = after.half.runs
        snapshot = snapshot.copy(sequence = after, lineScore = lines,
            pitchesAccepted = plus(snapshot.pitchesAccepted, if (kind == "pitch") 1 else 0))
        log += GameEvent("action", snapshot.pitchesAccepted, who.team, if (kind == "advance_half") null else who.batterId,
            pitchEvent = input?.event, actionKind = kind, before = before, after = after, input = input,
            scoreDelta = Score(after.score.away - before.score.away, after.score.home - before.score.home))
    }
    private fun completion(context: PitchContext, category: String, runs: Long, terminal: Boolean) {
        val team = if (context.team == "away") 0 else 1; val index = team * 9 + context.slot - 1
        val counts = snapshot.batterCounts.toMutableList(); counts[index] = counts[index].completed(category)
        val order = snapshot.battingIndices.toMutableList(); order[team] = context.slot % 9
        snapshot = snapshot.copy(batterCounts = counts, battingIndices = order)
        log += GameEvent("plate_appearance_completed", snapshot.pitchesAccepted, context.team, context.batterId,
            category = category, runsAdded = runs, reason = if (terminal) snapshot.reason else null)
    }
    private fun endEvent() { log += GameEvent("game_end", snapshot.pitchesAccepted, reason = snapshot.reason) }
    private fun normalEnd(): Boolean {
        val seq = snapshot.sequence
        if (seq.inning != 9 || seq.stage != Stage.HALF_COMPLETE) return false
        val score = snapshot.score
        val result = if (seq.side == Side.TOP) {
            if (score.home <= score.away) return false
            "home_lead_after_top9" to "home_win"
        } else {
            check(score.home <= score.away)
            if (score.away > score.home) "away_lead_after_bottom9" to "away_win" else "tie_after_nine" to "draw"
        }
        snapshot = snapshot.copy(status = Status.COMPLETED, reason = result.first, outcome = result.second)
        endEvent(); return true
    }
    private fun abort(reason: String) {
        snapshot = snapshot.copy(status = Status.ABORTED, reason = reason)
        log += GameEvent("abort", snapshot.pitchesAccepted, reason = reason)
    }
    private data class Resolution(val category: String, val runs: Long, val scoringSlots: List<Int>)
    private fun resolution(input: PitchInput): Resolution {
        val count = snapshot.sequence.count!!; val bases = snapshot.sequence.half.bases
        return when (count.status) {
            "walk_awarded" -> if (bases.all { it }) Resolution("walk", 1, listOf(3)) else Resolution("walk", 0, emptyList())
            "strikeout_out" -> Resolution("strikeout", 0, emptyList())
            "caught_foul_out" -> Resolution("out_hold", 0, emptyList())
            "fair_play_pending" -> when (input.fair!!.kind) {
                "out_hold" -> Resolution("out_hold", 0, emptyList())
                "home_run" -> Resolution("home_run", bases.count { it } + 1L, (3 downTo 1).filter { bases[it - 1] } + 0)
                else -> { val scored = (3 downTo 1).filter { input.fair.runnerDestinations!![it - 1] == 4 }; Resolution("safe_hit", scored.size.toLong(), scored) }
            }
            else -> error("Resolution is not pending")
        }
    }
    private fun walkoff(input: PitchInput, context: PitchContext, resolved: Resolution): Boolean {
        val seq = snapshot.sequence; val needed = plus(snapshot.score.away - snapshot.score.home, 1)
        if (seq.inning != 9 || seq.side != Side.BOTTOM || needed < 1 || resolved.runs < needed) return false
        val amount = if (resolved.category == "home_run") resolved.runs else needed
        val reason = when (resolved.category) { "walk" -> "walkoff_walk"; "home_run" -> "walkoff_home_run"; "safe_hit" -> "walkoff_safe_hit"; else -> error("Non-scoring terminal play") }
        val lines = snapshot.lineScore.toMutableList(); lines[snapshot.halfIndex] = plus(lines[snapshot.halfIndex]!!, amount)
        // Keep seq exactly at the unresolved pitch boundary. No Rules.resolve/advance.
        snapshot = snapshot.copy(status = Status.COMPLETED, reason = reason, outcome = "home_win", lineScore = lines, terminalScoreDelta = Score(0, amount))
        val slots = if (resolved.category == "home_run") resolved.scoringSlots else resolved.scoringSlots.take(needed.toInt())
        log += GameEvent("terminal_play", snapshot.pitchesAccepted, context.team, context.batterId, category = resolved.category,
            runsAdded = amount, reason = reason, before = seq, after = null, input = input, scoreDelta = Score(0, amount), scoredRunnerSlots = frozen(slots))
        completion(context, resolved.category, amount, true); endEvent(); return true
    }
    fun advanceOnePitch(): GameSnapshot {
        check(!snapshot.done) { "Game has stopped" }
        if (normalEnd()) return snapshot
        if (snapshot.pitchesAccepted >= maxPitches) { abort("pitch_limit"); return snapshot }
        if (snapshot.sequence.stage == Stage.HALF_COMPLETE) action("advance_half", Rules.advance(snapshot.sequence))
        if (snapshot.sequence.stage == Stage.BETWEEN_BATTERS) action("begin_batter", Rules.begin(snapshot.sequence))
        val who = context()
        val input: PitchInput
        try {
            when (val offered = source.nextPitch(who)) {
                is SourceInput.Abort -> { abort(offered.reason); return snapshot }
                is SourceInput.Pitch -> { input = offered.value; input.fair?.let { Rules.validateFair(it, snapshot.sequence.half) } }
            }
        } catch (error: InputFailure) { abort(error.reason); return snapshot }
        catch (_: Exception) { abort("source_error"); return snapshot }
        action("pitch", Rules.pitch(snapshot.sequence, input.event), input)
        if (snapshot.sequence.stage == Stage.AWAITING_RESOLUTION) {
            val resolved = resolution(input)
            if (walkoff(input, who, resolved)) return snapshot
            val oldScore = snapshot.score[who.team]
            action(if (resolved.category == "safe_hit") "resolve_safe_hit" else "resolve", Rules.resolve(snapshot.sequence, resolved.category, input.fair))
            completion(who, resolved.category, snapshot.score[who.team] - oldScore, false)
        }
        if (normalEnd()) return snapshot
        if (snapshot.pitchesAccepted >= maxPitches) { abort("pitch_limit"); return snapshot }
        if (snapshot.sequence.stage == Stage.HALF_COMPLETE) action("advance_half", Rules.advance(snapshot.sequence))
        return snapshot
    }
    fun runToEnd(): GameResult { while (!snapshot.done) advanceOnePitch(); return result() }
    fun result() = GameResult(snapshot)
}

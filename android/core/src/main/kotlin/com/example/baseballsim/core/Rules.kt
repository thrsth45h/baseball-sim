package com.example.baseballsim.core

/** Deterministic partial rules, with the same adjudicated-event assumptions as Python. */
object Rules {
    fun pitch(before: SequenceState, event: String): SequenceState {
        require(before.stage == Stage.PITCHING)
        if (event !in pitchEvents) throw InputFailure("invalid_pitch_input")
        val old = before.count!!; var b = old.balls; var s = old.strikes; var status = "active"
        when (event) {
            "called_ball" -> { b++; if (b == 4) status = "walk_awarded" }
            "called_strike_caught", "swinging_strike_caught", "foul_tip_caught", "bunt_foul_uncaught" -> { s++; if (s == 3) status = "strikeout_out" }
            "ordinary_foul_uncaught" -> s = minOf(s + 1, 2)
            "foul_fly_caught" -> status = "caught_foul_out"
            "fair_ball_handoff" -> status = "fair_play_pending"
        }
        return before.copy(count = CountState(b, s, plus(old.pitchesSeen, 1), status))
    }

    fun begin(before: SequenceState): SequenceState {
        require(before.stage == Stage.BETWEEN_BATTERS)
        return before.copy(count = CountState())
    }

    fun advance(before: SequenceState): SequenceState {
        require(before.stage == Stage.HALF_COMPLETE)
        return if (before.side == Side.TOP) SequenceState(before.inning, Side.BOTTOM, plus(before.closedAwayRuns, before.half.runs), before.closedHomeRuns)
            else SequenceState(Math.addExact(before.inning, 1), Side.TOP, before.closedAwayRuns, plus(before.closedHomeRuns, before.half.runs))
    }

    fun validateFair(fair: FairPlay, half: HalfState) {
        if (fair.kind != "safe_hit") return
        val dest = fair.runnerDestinations!!
        for (i in 0..2) {
            if (half.bases[i] != (dest[i] != null) || dest[i] != null && dest[i]!! < i + 1) throw InputFailure("invalid_fair_result")
        }
        val ordered = listOf(fair.candidateBases!!) + dest.filterNotNull()
        for (i in ordered.indices) for (j in i + 1 until ordered.size) {
            if (ordered[i] >= ordered[j] && !(ordered[i] == 4 && ordered[j] == 4)) throw InputFailure("invalid_fair_result")
        }
    }

    fun resolve(before: SequenceState, category: String, fair: FairPlay?): SequenceState {
        require(before.stage == Stage.AWAITING_RESOLUTION)
        val count = before.count!!; val half = before.half
        val bases = half.bases.toMutableList(); var outs = half.outs; var added = 0L
        when (category) {
            "walk" -> {
                require(count.status == "walk_awarded")
                if (bases[0]) {
                    if (bases[1]) { if (bases[2]) added = 1; bases[2] = true }
                    bases[1] = true
                }
                bases[0] = true
            }
            "strikeout", "out_hold" -> {
                require(count.status in setOf("strikeout_out", "caught_foul_out", "fair_play_pending"))
                require(category != "strikeout" || count.status == "strikeout_out"); outs++
            }
            "home_run" -> { require(count.status == "fair_play_pending"); added = bases.count { it } + 1L; bases.fill(false) }
            "safe_hit" -> {
                require(count.status == "fair_play_pending" && fair != null && fair.kind == "safe_hit")
                validateFair(fair, half); bases.fill(false); bases[fair.candidateBases!! - 1] = true
                for (dest in fair.runnerDestinations!!) if (dest != null) { if (dest == 4) added++ else bases[dest - 1] = true }
            }
            else -> throw IllegalArgumentException("Unknown resolution")
        }
        return before.copy(count = null, half = HalfState(outs, bases, plus(half.runs, added)))
    }
}

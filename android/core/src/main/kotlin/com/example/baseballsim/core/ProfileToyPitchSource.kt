package com.example.baseballsim.core

import java.util.Collections

/** Uncalibrated D062 trial tables, not public abilities or a cognitive model. */
object BatterProfileTables {
    val ids: List<String> = Collections.unmodifiableList(listOf("standard", "contact", "slugger"))
    private val pitches = listOf(listOf(35, 15, 15, 15, 20), listOf(35, 15, 10, 15, 25), listOf(35, 15, 20, 15, 15))
    private val fair = listOf(listOf(65, 22, 7, 1, 5), listOf(65, 26, 5, 1, 3), listOf(65, 15, 7, 1, 12))
    private val events = listOf("called_ball", "called_strike_caught", "swinging_strike_caught", "ordinary_foul_uncaught", "fair_ball_handoff")
    fun pitchWeights(profile: String): List<Int> = Collections.unmodifiableList(pitches[index(profile)])
    fun fairWeights(profile: String): List<Int> = Collections.unmodifiableList(fair[index(profile)])
    private fun index(profile: String) = ids.indexOf(profile).also { require(it >= 0) { "Unknown trial profile" } }
    private fun choose(draw: Int, weights: List<Int>): Int {
        require(draw in 0..99)
        var upper = 0
        weights.forEachIndexed { index, weight -> upper += weight; if (draw < upper) return index }
        error("Incomplete trial weights")
    }
    fun pitchEvent(profile: String, draw: Int): String = events[choose(draw, pitches[index(profile)])]
    fun fairPlay(profile: String, draw: Int, bases: List<Boolean>): FairPlay {
        require(bases.size == 3)
        return when (val branch = choose(draw, fair[index(profile)])) {
            0 -> FairPlay("out_hold")
            4 -> FairPlay("home_run")
            else -> FairPlay("safe_hit", branch, bases.mapIndexed { index, present -> if (present) minOf(index + 1 + branch, 4) else null })
        }
    }
}

/** A game owns one RNG and copies the profiles from its frozen participant input. */
class ProfileToyPitchSource(seed: String, awayProfiles: List<String>, homeProfiles: List<String>) : PitchSource {
    val awayProfiles: List<String> = Collections.unmodifiableList(ArrayList(awayProfiles))
    val homeProfiles: List<String> = Collections.unmodifiableList(ArrayList(homeProfiles))
    val rng = Sm64(seedFromText(seed))
    var pitchCalls = 0L; private set
    var fairCalls = 0L; private set
    init {
        require(this.awayProfiles.size == 9 && this.homeProfiles.size == 9)
        require((this.awayProfiles + this.homeProfiles).all { it in BatterProfileTables.ids })
    }
    override fun nextPitch(context: PitchContext): SourceInput {
        require(context.slot in 1..9 && context.team in setOf("away", "home"))
        val profile = (if (context.team == "away") awayProfiles else homeProfiles)[context.slot - 1]
        pitchCalls = Math.addExact(pitchCalls, 1)
        val draw = rng.draw100()
        val event = BatterProfileTables.pitchEvent(profile, draw)
        var fair: FairPlay? = null; var fairDraw: Int? = null
        if (event == "fair_ball_handoff") {
            fairCalls = Math.addExact(fairCalls, 1)
            fairDraw = rng.draw100()
            fair = BatterProfileTables.fairPlay(profile, fairDraw, context.sequence.half.bases)
        }
        return SourceInput.Pitch(PitchInput(event, fair, draw, fairDraw))
    }
}

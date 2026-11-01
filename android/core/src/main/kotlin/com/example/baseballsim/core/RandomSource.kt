package com.example.baseballsim.core

fun seedFromText(text: String): ULong {
    require(Regex("0|[1-9][0-9]{0,19}").matches(text)) { "Seed must be canonical ASCII decimal" }
    return requireNotNull(text.toULongOrNull()) { "Seed exceeds unsigned 64 bits" }
}

data class DrawSample(val value: Int, val rawOutputs: Long, val rejections: Long)
/** The same production rejection loop is injectable for boundary tests. */
fun sample100(nextRaw: () -> ULong): DrawSample {
    var used = 0L
    while (true) {
        val upper = (nextRaw() shr 32).toLong(); used = plus(used, 1)
        if (upper < 4_294_967_200L) return DrawSample((upper % 100).toInt(), used, used - 1)
    }
}

/** sm64-v1 for this technical prototype; not a permanent product RNG promise. */
class Sm64(seed: ULong) {
    var state = seed; private set
    var rawOutputCalls = 0L; private set
    var draw100Calls = 0L; private set
    var rejectedRawOutputs = 0L; private set
    fun nextRaw(): ULong {
        state += 0x9E3779B97F4A7C15uL
        var z = state
        z = (z xor (z shr 30)) * 0xBF58476D1CE4E5B9uL
        z = (z xor (z shr 27)) * 0x94D049BB133111EBuL
        rawOutputCalls = plus(rawOutputCalls, 1)
        return z xor (z shr 31)
    }
    fun draw100(): Int {
        draw100Calls = plus(draw100Calls, 1)
        val sampled = sample100(::nextRaw)
        rejectedRawOutputs = plus(rejectedRawOutputs, sampled.rejections)
        return sampled.value
    }
}

class ToyPitchSource(seed: String) : PitchSource {
    val rng = Sm64(seedFromText(seed))
    var pitchCalls = 0L; private set
    var fairCalls = 0L; private set
    override fun nextPitch(context: PitchContext): SourceInput {
        pitchCalls = plus(pitchCalls, 1)
        val draw = rng.draw100()
        val event = when (draw) {
            in 0..34 -> "called_ball"
            in 35..49 -> "called_strike_caught"
            in 50..64 -> "swinging_strike_caught"
            in 65..79 -> "ordinary_foul_uncaught"
            else -> "fair_ball_handoff"
        }
        var fair: FairPlay? = null; var fairDraw: Int? = null
        if (event == "fair_ball_handoff") {
            fairCalls = plus(fairCalls, 1); fairDraw = rng.draw100()
            fair = when (fairDraw) {
                in 0..64 -> FairPlay("out_hold")
                in 65..86 -> safe(context, 1)
                in 87..93 -> safe(context, 2)
                94 -> safe(context, 3)
                else -> FairPlay("home_run")
            }
        }
        return SourceInput.Pitch(PitchInput(event, fair, draw, fairDraw))
    }
    private fun safe(context: PitchContext, value: Int) = FairPlay("safe_hit", value,
        context.sequence.half.bases.mapIndexed { index, present -> if (present) minOf(index + 1 + value, 4) else null })
}

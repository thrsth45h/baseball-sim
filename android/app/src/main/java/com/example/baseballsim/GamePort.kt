package com.example.baseballsim

import java.util.Collections

/** The app's display boundary. Reading a snapshot never advances the game or its RNG. */
interface GamePort {
    fun snapshot(): GamePresentation
    fun advanceOnePitch(): GamePresentation
}

fun interface GamePortFactory {
    fun create(seed: String): GamePort
}

enum class GameStatus { IN_PROGRESS, COMPLETED, ABORTED }
enum class HalfSide { TOP, BOTTOM }

data class CurrentPresentation(
    val inning: Int,
    val side: HalfSide,
    val team: String,
    val batterName: String,
    val batterSlot: Int,
    val balls: Int,
    val strikes: Int,
    val outs: Int,
    /** First, second, third base. */
    val bases: List<Boolean>,
)

data class GamePresentation(
    val seed: String,
    val status: GameStatus,
    val scoreAway: String,
    val scoreHome: String,
    val lineAway: List<String?>,
    val lineHome: List<String?>,
    val pitchesAccepted: Long,
    val completedPa: Long,
    val current: CurrentPresentation?,
    /** Chronological order, oldest first. */
    val recent: List<String>,
    val outcome: String? = null,
    val reason: String? = null,
    val walkoff: Boolean = false,
)

/** Detach display lists from any mutable engine storage before publishing them. */
internal fun GamePresentation.detached(): GamePresentation = copy(
    lineAway = immutableCopy(lineAway),
    lineHome = immutableCopy(lineHome),
    current = if (status == GameStatus.IN_PROGRESS) current?.let { it.copy(bases = immutableCopy(it.bases)) } else null,
    recent = immutableCopy(recent),
)

private fun <T> immutableCopy(values: List<T>): List<T> = Collections.unmodifiableList(ArrayList(values))

package com.example.baseballsim.season

import com.example.baseballsim.core.*
import java.security.MessageDigest

data class HalfDto(val outs: Int, val first: Boolean, val second: Boolean, val third: Boolean, val runs: Long) {
    fun toCore() = HalfState(outs, listOf(first, second, third), runs)
}
data class SequenceDto(val inning: Int, val side: Side, val closedAway: Long, val closedHome: Long, val count: CountState?, val half: HalfDto) {
    fun toCore() = SequenceState(inning, side, closedAway, closedHome, count, half.toCore())
    companion object {
        fun fromCore(s: SequenceState) = SequenceDto(s.inning, s.side, s.closedAwayRuns, s.closedHomeRuns, s.count,
            HalfDto(s.half.outs, s.half.bases[0], s.half.bases[1], s.half.bases[2], s.half.runs))
    }
}

/** Full value DTO, independent of reference identity in the existing core classes. */
class SnapshotDto(val sequence: SequenceDto, battingIndices: List<Int>, batterCounts: List<EventCounts>,
    val pitchesAccepted: Long, lineScore: List<Long?>, val status: Status, val reason: String?, val outcome: String?,
    val terminalDelta: Score, val historyOrigin: String) : SeasonValue() {
    val battingIndices = freeze(battingIndices)
    val batterCounts = freeze(batterCounts)
    val lineScore = freeze(lineScore)
    override val values get() = listOf(sequence, battingIndices, batterCounts, pitchesAccepted, lineScore, status, reason, outcome, terminalDelta, historyOrigin)
    init { toCore() }
    fun toCore() = GameSnapshot(sequence.toCore(), battingIndices, batterCounts, pitchesAccepted, lineScore, status, reason, outcome, terminalDelta, historyOrigin)
    companion object {
        fun fromCore(s: GameSnapshot) = SnapshotDto(SequenceDto.fromCore(s.sequence), s.battingIndices, s.batterCounts,
            s.pitchesAccepted, s.lineScore, s.status, s.reason, s.outcome, s.terminalScoreDelta, s.historyOrigin)
    }
}

data class SourceCursor(val kind: String, val pitchCalls: Long, val fairCalls: Long?, val state: String?,
    val rawCalls: Long?, val drawCalls: Long?, val rejections: Long?, val scriptedPosition: Int? = null) {
    init {
        require(pitchCalls >= 0)
        when (kind) {
            "toy" -> {
                require(fairCalls != null && fairCalls in 0..pitchCalls && state != null && rawCalls != null && drawCalls != null && rejections != null)
                seedFromText(state); require(rawCalls >= 0 && drawCalls >= 0 && rejections >= 0)
                require(drawCalls == add(pitchCalls, fairCalls) && rawCalls == add(drawCalls, rejections) && scriptedPosition == null)
            }
            "scripted-diagnostic" -> require(fairCalls == null && state == null && rawCalls == null && drawCalls == null && rejections == null && scriptedPosition != null && scriptedPosition >= 0)
            else -> error("Unknown source cursor kind")
        }
    }
    companion object {
        fun capture(source: PitchSource) = when (source) {
            is ToyPitchSource -> SourceCursor("toy", source.pitchCalls, source.fairCalls, source.rng.state.toString(), source.rng.rawOutputCalls, source.rng.draw100Calls, source.rng.rejectedRawOutputs)
            is ScriptedSource -> SourceCursor("scripted-diagnostic", source.calls, null, null, null, null, null, source.position)
            else -> error("Source counters are unavailable")
        }
    }
}

class GameCheckpoint(val game: ScheduledGame, val maxPitches: Long, val advanceCalls: Long, val snapshot: SnapshotDto,
    val source: SourceCursor, val initial: SnapshotDto = SnapshotDto.fromCore(GameSnapshot.initial())) : SeasonValue() {
    override val values get() = listOf(game, maxPitches, advanceCalls, snapshot, source, initial)
    init {
        require(maxPitches > 0 && advanceCalls >= 0 && snapshot.pitchesAccepted <= maxPitches)
        require(snapshot.pitchesAccepted >= initial.pitchesAccepted)
        require(snapshot.pitchesAccepted - initial.pitchesAccepted <= advanceCalls)
        require(source.kind == "toy" || snapshot.status == Status.ABORTED) { "Only fixed toy games can resume" }
    }
    val stateHash: String get() = sha256(CheckpointCodec.encodeGame(this))
    /** ABORTED is a retained diagnostic, never an engine that can progress again. */
    fun recover(masterSeed: String): SeasonMatch {
        game.versions.requireSupported()
        require(game.fixture.ordinal in 1..720)
        requireProduction(PennantSchedule.allocate(masterSeed)[game.fixture.ordinal - 1])
        require(snapshot.status != Status.ABORTED) { "Incomplete game is blocked; diagnostic must be retained" }
        require(source.kind == "toy")
        val restored = SeasonMatch(game, GameSession(game.seed, maxPitches, initial = initial.toCore()))
        repeatLong(advanceCalls) { restored.advanceOnePitch() }
        require(restored.checkpoint() == this) { "Saved snapshot or RNG/source cursor does not match replay" }
        return restored
    }
    internal fun requireProduction(expected: ScheduledGame) {
        require(game == expected && maxPitches == 10_000L && initial == SnapshotDto.fromCore(GameSnapshot.initial())) { "Not the fixed production starting definition" }
        require(source.kind == "toy") { "Scripted source cursors are diagnostic only" }
    }
}

internal fun repeatLong(count: Long, block: () -> Unit) { var i = 0L; while (i < count) { block(); i++ } }
internal fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/** Mutable calculation candidate; a store must discard it if commit fails. */
class SeasonMatch internal constructor(val game: ScheduledGame, internal val engine: GameSession = GameSession(game.seed)) {
    var advanceCalls: Long = 0; private set
    val snapshot get() = engine.snapshot
    val events get() = engine.events
    fun advanceOnePitch(): GameSnapshot {
        check(!snapshot.done)
        val next = add(advanceCalls, 1)
        val result = engine.advanceOnePitch()
        advanceCalls = next
        return result
    }
    fun advancePlateAppearance(): GameSnapshot {
        val pa = snapshot.completedPa
        do { advanceOnePitch() } while (!snapshot.done && snapshot.completedPa == pa)
        return snapshot
    }
    fun finish(): GameSnapshot { while (!snapshot.done) advanceOnePitch(); return snapshot }
    fun checkpoint() = GameCheckpoint(game, engine.maxPitches, advanceCalls, SnapshotDto.fromCore(snapshot), SourceCursor.capture(engine.source), SnapshotDto.fromCore(engine.initialSnapshot))
}

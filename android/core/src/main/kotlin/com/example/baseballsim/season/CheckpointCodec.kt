package com.example.baseballsim.season

import com.example.baseballsim.core.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** Explicit big-endian, bounded, versioned value codec. No Java object serialization or core annotations.
 * It is an internal prototype format, not a promise of permanent product save compatibility.
 */
object CheckpointCodec {
    private const val MAX_BYTES = 32 * 1024 * 1024
    fun encodeGame(value: GameCheckpoint): ByteArray = write { text("pennant-game-checkpoint-v1"); gameCheckpoint(value) }
    fun decodeGame(bytes: ByteArray): GameCheckpoint = read(bytes) { require(text() == "pennant-game-checkpoint-v1"); gameCheckpoint() }
    fun encodeSeason(value: SeasonState): ByteArray = write {
        text("pennant-season-checkpoint-v1"); text(value.masterSeed); text(value.selectedClubId); int(value.dayIndex)
        int(value.games.size); value.games.forEach { scheduled(it) }
        int(value.results.size); value.results.forEach { summary(it) }
        bool(value.active != null); value.active?.let { gameCheckpoint(it) }
        // Persist derived displays as well, then verify them against the result ledger on decoding.
        int(value.standings.size); value.standings.forEach { standing(it) }
        int(value.playerCounts.size); value.playerCounts.forEach { player(it) }
    }
    fun decodeSeason(bytes: ByteArray): SeasonState = read(bytes) {
        require(text() == "pennant-season-checkpoint-v1")
        val master = text(); val selected = text(); val day = int()
        val games = List(size(720, 720)) { scheduled() }
        val results = List(size(0, 720)) { summary() }
        val active = if (bool()) gameCheckpoint() else null
        val standings = List(size(12, 12)) { standing() }
        val players = List(size(108, 108)) { player() }
        val state = SeasonState.restore(master, selected, day, results, active)
        require(games == state.games && standings == state.standings && players == state.playerCounts) { "Saved plan or aggregates disagree with canonical plan/ledger" }
        state
    }
    private fun write(block: Writer.() -> Unit): ByteArray {
        val bytes = ByteArrayOutputStream(); Writer(DataOutputStream(bytes)).block()
        return bytes.toByteArray().also { require(it.size <= MAX_BYTES) }
    }
    private fun <T> read(bytes: ByteArray, block: Reader.() -> T): T {
        require(bytes.size <= MAX_BYTES)
        val input = DataInputStream(ByteArrayInputStream(bytes))
        return Reader(input).block().also { require(input.available() == 0) { "Trailing checkpoint bytes" } }
    }
    private class Writer(val output: DataOutputStream) {
        fun text(v: String) { val b = v.toByteArray(Charsets.UTF_8); require(b.size <= 4096); int(b.size); output.write(b) }
        fun int(v: Int) = output.writeInt(v)
        fun long(v: Long) = output.writeLong(v)
        fun bool(v: Boolean) = output.writeByte(if (v) 1 else 0)
        fun nullableText(v: String?) { bool(v != null); v?.let { text(it) } }
        fun nullableLong(v: Long?) { bool(v != null); v?.let { long(it) } }
        fun versions(v: SeasonVersions) { text(v.schedule); text(v.seedDerivation); text(v.rng); text(v.coreSource); text(v.toy); text(v.roster); text(v.checkpoint) }
        fun scheduled(g: ScheduledGame) {
            val f = g.fixture
            text(f.seasonId); int(f.ordinal); text(f.gameId); int(f.dayIndex); int(f.week); text(f.weekday); text(f.leagueId)
            int(f.seriesRound); int(f.cycle); int(f.roundInCycle); int(f.baseRound); int(f.matchupIndex); int(f.seriesGame)
            text(f.awayClubId); text(f.homeClubId); text(g.seed); versions(g.versions)
        }
        fun counts(c: EventCounts) { long(c.completedPlateAppearances); long(c.safeHitEvents); long(c.walkEvents); long(c.strikeoutEvents); long(c.homeRunEvents) }
        fun score(s: Score) { long(s.away); long(s.home) }
        fun snapshot(s: SnapshotDto) {
            val q = s.sequence; text(q.side.name); int(q.inning); long(q.closedAway); long(q.closedHome)
            bool(q.count != null); q.count?.let { int(it.balls); int(it.strikes); long(it.pitchesSeen); text(it.status) }
            int(q.half.outs); bool(q.half.first); bool(q.half.second); bool(q.half.third); long(q.half.runs)
            int(s.battingIndices.size); s.battingIndices.forEach { int(it) }
            int(s.batterCounts.size); s.batterCounts.forEach { counts(it) }
            long(s.pitchesAccepted); int(s.lineScore.size); s.lineScore.forEach { nullableLong(it) }
            text(s.status.name); nullableText(s.reason); nullableText(s.outcome); score(s.terminalDelta); text(s.historyOrigin)
        }
        fun source(s: SourceCursor) {
            text(s.kind); long(s.pitchCalls); nullableLong(s.fairCalls); nullableText(s.state); nullableLong(s.rawCalls)
            nullableLong(s.drawCalls); nullableLong(s.rejections); bool(s.scriptedPosition != null); s.scriptedPosition?.let { int(it) }
        }
        fun gameCheckpoint(v: GameCheckpoint) { scheduled(v.game); long(v.maxPitches); long(v.advanceCalls); snapshot(v.snapshot); source(v.source); snapshot(v.initial) }
        fun summary(s: GameSummary) {
            scheduled(s.game); long(s.maxPitches); text(s.status.name); text(s.outcome); text(s.reason); score(s.score)
            int(s.lineScore.size); s.lineScore.forEach { nullableLong(it) }; score(s.terminalDelta); long(s.pitchesAccepted)
            int(s.batters.size); s.batters.forEach { text(it.playerId); text(it.clubId); int(it.slot); text(it.side); counts(it.counts) }
        }
        fun standing(s: Standing) {
            val t = s.totals; text(t.clubId); long(t.played); long(t.wins); long(t.losses); long(t.draws); long(t.runsFor); long(t.runsAgainst)
            bool(s.rank != null); s.rank?.let { int(it) }
            bool(t.ratio != null); t.ratio?.let { long(it.numerator); long(it.denominator) }
        }
        fun player(p: PlayerSeasonCounts) { text(p.playerId); text(p.clubId); int(p.slot); counts(p.counts) }
    }
    private class Reader(val input: DataInputStream) {
        fun int() = input.readInt()
        fun long() = input.readLong()
        fun bool(): Boolean { val n = input.readUnsignedByte(); require(n == 0 || n == 1); return n == 1 }
        fun size(min: Int, max: Int): Int = int().also { require(it in min..max) }
        fun text(): String {
            val bytes = ByteArray(size(0, 4096)); input.readFully(bytes)
            val text = bytes.toString(Charsets.UTF_8)
            require(text.toByteArray(Charsets.UTF_8).contentEquals(bytes)) { "Invalid UTF-8" }
            return text
        }
        fun nullableText() = if (bool()) text() else null
        fun nullableLong() = if (bool()) long() else null
        fun versions() = SeasonVersions(text(), text(), text(), text(), text(), text(), text()).also { it.requireSupported() }
        fun scheduled(): ScheduledGame {
            val f = Fixture(text(), int(), text(), int(), int(), text(), text(), int(), int(), int(), int(), int(), int(), text(), text())
            return ScheduledGame(f, text(), versions())
        }
        fun counts() = EventCounts(long(), long(), long(), long(), long())
        fun score() = Score(long(), long())
        fun snapshot(): SnapshotDto {
            val side = Side.valueOf(text()); val inning = int(); val away = long(); val home = long()
            val count = if (bool()) CountState(int(), int(), long(), text()) else null
            val half = HalfDto(int(), bool(), bool(), bool(), long())
            val order = List(size(2, 2)) { int() }; val people = List(size(18, 18)) { counts() }; val pitches = long()
            val lines = List(size(18, 18)) { nullableLong() }; val status = Status.valueOf(text())
            return SnapshotDto(SequenceDto(inning, side, away, home, count, half), order, people, pitches, lines, status, nullableText(), nullableText(), score(), text())
        }
        fun source() = SourceCursor(text(), long(), nullableLong(), nullableText(), nullableLong(), nullableLong(), nullableLong(), if (bool()) int() else null)
        fun gameCheckpoint() = GameCheckpoint(scheduled(), long(), long(), snapshot(), source(), snapshot())
        fun summary(): GameSummary {
            val game = scheduled(); val limit = long(); val status = Status.valueOf(text()); val outcome = text(); val reason = text(); val score = score()
            val lines = List(size(18, 18)) { nullableLong() }; val delta = score(); val pitches = long()
            val people = List(size(18, 18)) { PlayerGameCounts(text(), text(), int(), text(), counts()) }
            return GameSummary(game, limit, status, outcome, reason, score, lines, delta, pitches, people)
        }
        fun standing(): Standing {
            val t = ClubTotals(text(), long(), long(), long(), long(), long(), long())
            val rank = if (bool()) int() else null
            val ratio = if (bool()) WinRatio.of(long(), long()) else null
            require(ratio == t.ratio)
            return Standing(t, rank)
        }
        fun player() = PlayerSeasonCounts(text(), text(), int(), counts())
    }
}

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
    private fun format(versions: SeasonVersions) = when (versions) { SeasonVersions() -> 1; SeasonVersions.lineup() -> 2; SeasonVersions.batterProfile() -> 3; else -> error("Unsupported versions") }
    private fun header(kind: String, version: Int) = "pennant-$kind-v$version"
    fun encodeGame(value: GameCheckpoint): ByteArray = write { text(header("game-checkpoint", format(value.game.versions))); gameCheckpoint(value) }
    fun decodeGame(bytes: ByteArray): GameCheckpoint = read(bytes) {
        val version = readHeader("game-checkpoint"); gameCheckpoint().also { require(format(it.game.versions) == version) }
    }
    fun encodeSummary(value: GameSummary): ByteArray = write { text(header("game-summary", format(value.game.versions))); summary(value) }
    fun decodeSummary(bytes: ByteArray): GameSummary = read(bytes) {
        val version = readHeader("game-summary"); summary().also { require(format(it.game.versions) == version) }
    }
    fun encodeSeason(value: SeasonState): ByteArray = write {
        text(header("season-checkpoint", format(value.versions))); text(value.masterSeed); text(value.selectedClubId); int(value.dayIndex)
        if (value.lineupEnabled) {
            versions(value.versions)
            int(value.clubLineups.size)
            value.clubLineups.forEach { (club, people) -> text(club); int(people.size); people.forEach { text(it) } }
        }
        if (value.profilesEnabled) {
            text(requireNotNull(value.worldProfileTableVersion)); int(value.worldProfiles.size)
            value.worldProfiles.forEach { (person, profile) -> text(person); text(profile) }
        }
        int(value.games.size); value.games.forEach { scheduled(it) }
        int(value.results.size); value.results.forEach { summary(it) }
        bool(value.active != null); value.active?.let { gameCheckpoint(it) }
        // Persist derived displays as well, then verify them against the result ledger on decoding.
        int(value.standings.size); value.standings.forEach { standing(it) }
        int(value.playerCounts.size); value.playerCounts.forEach { player(it, value.lineupEnabled) }
    }
    fun decodeSeason(bytes: ByteArray): SeasonState = read(bytes) {
        val version = readHeader("season-checkpoint"); val v2 = version >= 2
        val master = text(); val selected = text(); val day = int()
        val versions = if (v2) versions().also { require(format(it) == version) } else SeasonVersions()
        val plans = if (v2) {
            List(size(12, 12)) { index ->
                val club = text(); require(club == PennantSchedule.clubIds[index]) { "Noncanonical club plan order" }
                club to List(size(9, 9)) { text() }
            }.toMap()
        } else LineupRoster.defaultPlans(versions)
        val tableVersion = if (version == 3) text() else null
        val worldProfiles = if (version == 3) {
            val ids = PennantSchedule.clubIds.flatMap { club -> LineupRoster.playerIds(club, versions) }
            List(size(144, 144)) { index ->
                val person = text(); require(person == ids[index]) { "Noncanonical profile person order" }; person to text()
            }.toMap()
        } else emptyMap()
        val games = List(size(720, 720)) { scheduled() }
        val results = List(size(0, 720)) { summary() }
        val active = if (bool()) gameCheckpoint() else null
        val standings = List(size(12, 12)) { standing() }
        val count = if (v2) 144 else 108
        val players = List(size(count, count)) { player(v2) }
        val state = SeasonState.restore(master, selected, day, results, active, versions, plans, tableVersion, worldProfiles)
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
        fun lineup(v: GameLineup) { int(v.away.size); v.away.forEach { text(it) }; int(v.home.size); v.home.forEach { text(it) } }
        fun profileInput(v: GameProfileInput) {
            text(v.tableVersion); text(v.sourceVersion)
            // The same frame already carries the immutable away/home lineup IDs in this order.
            for (side in listOf(v.away, v.home)) side.forEach { entry ->
                output.writeByte(when (entry.profileId) {
                    "standard" -> 0
                    "contact" -> 1
                    "slugger" -> 2
                    else -> error("Unsupported batter profile ID")
                })
            }
        }
        fun gameCheckpoint(v: GameCheckpoint) {
            scheduled(v.game); long(v.maxPitches); long(v.advanceCalls); snapshot(v.snapshot); source(v.source); snapshot(v.initial)
            if (v.game.versions.lineupEnabled) lineup(v.lineup)
            if (v.game.versions.profilesEnabled) profileInput(requireNotNull(v.profileInput))
        }
        fun summary(s: GameSummary) {
            scheduled(s.game); long(s.maxPitches); text(s.status.name); text(s.outcome); text(s.reason); score(s.score)
            int(s.lineScore.size); s.lineScore.forEach { nullableLong(it) }; score(s.terminalDelta); long(s.pitchesAccepted)
            int(s.batters.size); s.batters.forEach { text(it.playerId); text(it.clubId); int(it.slot); text(it.side); counts(it.counts) }
            if (s.game.versions.lineupEnabled) lineup(s.lineup)
            if (s.game.versions.profilesEnabled) profileInput(requireNotNull(s.profileInput))
        }
        fun standing(s: Standing) {
            val t = s.totals; text(t.clubId); long(t.played); long(t.wins); long(t.losses); long(t.draws); long(t.runsFor); long(t.runsAgainst)
            bool(s.rank != null); s.rank?.let { int(it) }
            bool(t.ratio != null); t.ratio?.let { long(it.numerator); long(it.denominator) }
        }
        fun player(p: PlayerSeasonCounts, v2: Boolean) {
            text(p.playerId); text(p.clubId)
            if (v2) require(p.slot == null) else int(requireNotNull(p.slot))
            counts(p.counts)
        }
    }
    private class Reader(val input: DataInputStream) {
        fun readHeader(kind: String): Int {
            val value = text()
            val version = (1..3).singleOrNull { value == header(kind, it) }
            require(version != null) { "Unsupported checkpoint header" }
            return version
        }
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
        fun lineup() = GameLineup(List(size(9, 9)) { text() }, List(size(9, 9)) { text() })
        fun profileInput(lineup: GameLineup): GameProfileInput {
            val table = text(); val source = text()
            fun entries(people: List<String>) = people.map { playerId ->
                val profileId = when (input.readUnsignedByte()) {
                    0 -> "standard"
                    1 -> "contact"
                    2 -> "slugger"
                    else -> throw IllegalArgumentException("Unsupported batter profile code")
                }
                BatterProfileEntry(playerId, profileId)
            }
            return GameProfileInput(entries(lineup.away), entries(lineup.home), table, source)
        }
        fun gameCheckpoint(): GameCheckpoint {
            val game = scheduled(); val limit = long(); val advances = long(); val current = snapshot(); val cursor = source(); val initial = snapshot()
            val people = if (game.versions.lineupEnabled) lineup() else GameLineup.initial(game)
            val profiles = if (game.versions.profilesEnabled) profileInput(people) else null
            return GameCheckpoint(game, limit, advances, current, cursor, initial, people, profiles)
        }
        fun summary(): GameSummary {
            val game = scheduled(); val limit = long(); val status = Status.valueOf(text()); val outcome = text(); val reason = text(); val scoreValue = score()
            val lines = List(size(18, 18)) { nullableLong() }; val delta = score(); val pitches = long()
            val people = List(size(18, 18)) { PlayerGameCounts(text(), text(), int(), text(), counts()) }
            val order = if (game.versions.lineupEnabled) lineup() else GameLineup.initial(game)
            val profiles = if (game.versions.profilesEnabled) profileInput(order) else null
            return GameSummary(game, limit, status, outcome, reason, scoreValue, lines, delta, pitches, people, order, profiles)
        }
        fun standing(): Standing {
            val t = ClubTotals(text(), long(), long(), long(), long(), long(), long())
            val rank = if (bool()) int() else null
            val ratio = if (bool()) WinRatio.of(long(), long()) else null
            require(ratio == t.ratio)
            return Standing(t, rank)
        }
        fun player(v2: Boolean) = PlayerSeasonCounts(text(), text(), if (v2) null else int(), counts())
    }
}

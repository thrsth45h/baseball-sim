package com.example.baseballsim.season

import com.example.baseballsim.core.Sm64
import com.example.baseballsim.core.seedFromText
import java.util.Collections

internal fun <T> freeze(values: Collection<T>): List<T> = Collections.unmodifiableList(ArrayList(values))
internal fun <K, V> freezeMap(values: Map<K, V>): Map<K, V> = Collections.unmodifiableMap(LinkedHashMap(values))
internal fun add(a: Long, b: Long): Long = Math.addExact(a, b)

/** Collection-bearing values compare contents and never expose a mutable backing collection. */
abstract class SeasonValue {
    protected abstract val values: List<Any?>
    final override fun equals(other: Any?) = other != null && javaClass == other.javaClass && values == (other as SeasonValue).values
    final override fun hashCode() = values.hashCode()
}

data class Player(val id: String, val clubId: String, val slot: Int)
class Club(val id: String, val leagueId: String, val displayName: String, players: List<Player>) : SeasonValue() {
    val players = freeze(players)
    override val values get() = listOf(id, leagueId, displayName, players)
}

data class SeasonVersions(
    val schedule: String = "season-schedule-v1",
    val seedDerivation: String = "season-seeds-v1",
    val rng: String = "sm64-v1",
    val coreSource: String = "470467a27d6390b6415e5b16b44c0c1a0aa1704f",
    val toy: String = "toy-source-v1",
    val roster: String = "season-roster-v1",
    val checkpoint: String = "season-checkpoint-v1",
) {
    fun requireSupported() { require(this == SeasonVersions()) { "Unsupported saved versions" } }
}

data class Fixture(
    val seasonId: String, val ordinal: Int, val gameId: String, val dayIndex: Int, val week: Int,
    val weekday: String, val leagueId: String, val seriesRound: Int, val cycle: Int,
    val roundInCycle: Int, val baseRound: Int, val matchupIndex: Int, val seriesGame: Int,
    val awayClubId: String, val homeClubId: String,
)
data class ScheduledGame(val fixture: Fixture, val seed: String, val versions: SeasonVersions = SeasonVersions()) {
    init { seedFromText(seed); versions.requireSupported() }
}
class CalendarDay(val dayIndex: Int, val week: Int, val weekday: String, val isRestDay: Boolean, gameOrdinals: List<Int>) : SeasonValue() {
    val gameOrdinals = freeze(gameOrdinals)
    override val values get() = listOf(dayIndex, week, weekday, isRestDay, gameOrdinals)
}

/** The accepted S1 experiment; these constants do not define a permanent product calendar. */
object PennantSchedule {
    private val weekdays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val clubs: List<Club> = freeze((1..2).flatMap { league -> (1..6).map { number ->
        val id = "L$league-T${number.toString().padStart(2, '0')}"
        Club(id, "L$league", "仮球団${(league - 1) * 6 + number}", (1..9).map { slot -> Player("$id-P${slot.toString().padStart(2, '0')}", id, slot) })
    } })
    val clubIds: List<String> = freeze(clubs.map { it.id })
    fun club(id: String): Club = requireNotNull(clubs.find { it.id == id }) { "Unknown club" }
    val fixtures: List<Fixture> = run {
        var ring = (1..6).toList()
        val rounds = (0..4).map { round ->
            val pairs = (0..2).map { i ->
                val left = ring[i]; val right = ring[5 - i]
                if ((round + i) % 2 == 0) right to left else left to right
            }
            ring = listOf(ring.first(), ring.last()) + ring.subList(1, 5)
            pairs
        }
        val rows = (1..40).flatMap { series ->
            val week = (series - 1) / 2 + 1
            val start = 7 * (week - 1) + if (series % 2 == 1) 2 else 5
            val within = (series - 1) % 10 + 1
            (1..3).flatMap { game -> (1..2).flatMap { league -> (0..2).map { match ->
                val pair = rounds[(within - 1) % 5][match]
                val away = if (within <= 5) pair.first else pair.second
                val home = if (within <= 5) pair.second else pair.first
                val day = start + game - 1
                Fixture("S1", 0, "S1-D${day.toString().padStart(3, '0')}-L$league-R${series.toString().padStart(2, '0')}-M${match + 1}-G$game",
                    day, week, weekdays[(day - 1) % 7], "L$league", series, (series - 1) / 10 + 1,
                    within, (within - 1) % 5 + 1, match + 1, game,
                    "L$league-T${away.toString().padStart(2, '0')}", "L$league-T${home.toString().padStart(2, '0')}")
            } } }
        }.sortedWith(compareBy<Fixture> { it.dayIndex }.thenBy { it.leagueId }.thenBy { it.matchupIndex })
        freeze(rows.mapIndexed { i, row -> row.copy(ordinal = i + 1) })
    }
    val calendar = freeze((1..140).map { day -> CalendarDay(day, (day - 1) / 7 + 1, weekdays[(day - 1) % 7],
        (day - 1) % 7 == 0, fixtures.filter { it.dayIndex == day }.map { it.ordinal }) })
    fun allocate(masterSeed: String): List<ScheduledGame> {
        val rng = Sm64(seedFromText(masterSeed))
        return freeze(fixtures.map { ScheduledGame(it, rng.nextRaw().toString()) })
    }
}

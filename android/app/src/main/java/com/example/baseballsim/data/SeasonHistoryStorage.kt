package com.example.baseballsim.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.example.baseballsim.core.EventCounts
import com.example.baseballsim.core.seedFromText
import com.example.baseballsim.season.ClubTotals
import com.example.baseballsim.season.PlayerSeasonCounts
import com.example.baseballsim.season.SeasonRecords
import com.example.baseballsim.season.SeasonState
import com.example.baseballsim.season.SeasonYear
import com.example.baseballsim.season.SeasonYearSeeds

data class TimelineColumns(
    @ColumnInfo(defaultValue = "0") val version: Int = 0,
    val rootSeed: String? = null,
    val year: Long? = null,
    val previous: String? = null,
    val seal: String? = null,
) {
    companion object {
        fun create(world: String, timeline: SeasonTimeline): TimelineColumns {
            val version = 1
            val seal = digest("timeline", version, world, timeline.rootSeed, timeline.year, timeline.previous)
            return TimelineColumns(version, timeline.rootSeed, timeline.year, timeline.previous, seal)
        }
    }
}

@Entity(tableName = "annual_archives")
data class AnnualArchiveEntity(
    @PrimaryKey val id: String, val worldId: String, val rootSeed: String, val year: Long, val parent: String?, val summaryHash: String,
)

@Entity(
    tableName = "annual_clubs", primaryKeys = ["archiveId", "clubId"],
    foreignKeys = [ForeignKey(entity = AnnualArchiveEntity::class, parentColumns = ["id"], childColumns = ["archiveId"])],
)
data class AnnualClubEntity(val archiveId: String, val clubId: String, val played: Long, val wins: Long,
    val losses: Long, val draws: Long, val runsFor: Long, val runsAgainst: Long, val rank: Int?) {
    fun totals() = ClubTotals(clubId, played, wins, losses, draws, runsFor, runsAgainst)
}

@Entity(
    tableName = "annual_players", primaryKeys = ["archiveId", "playerId"],
    foreignKeys = [ForeignKey(entity = AnnualArchiveEntity::class, parentColumns = ["id"], childColumns = ["archiveId"])],
)
data class AnnualPlayerEntity(val archiveId: String, val playerId: String, val clubId: String,
    val plateAppearances: Long, val hits: Long, val walks: Long, val strikeouts: Long, val homeRuns: Long) {
    fun counts() = PlayerSeasonCounts(playerId, clubId, null, EventCounts(plateAppearances, hits, walks, strikeouts, homeRuns))
}

internal data class AnnualArchive(
    val header: AnnualArchiveEntity,
    val clubs: List<AnnualClubEntity>,
    val players: List<AnnualPlayerEntity>,
) {
    fun checkedSummary(): SeasonYear {
        val standings = SeasonRecords.rank(clubs.map { it.totals() })
        val summary = SeasonYear(header.year, standings, players.map { it.counts() })
        require(build(header.worldId, header.rootSeed, header.parent, summary) == this) { "Invalid historical rows or archive hash" }
        return summary
    }

    companion object {
        fun build(world: String, root: String, parent: String?, summary: SeasonYear): AnnualArchive {
            val rankRows = summary.standings.sortedBy { it.totals.clubId }
            val playerRows = summary.players.sortedBy { it.playerId }
            val fields = buildList<Any?> {
                addAll(listOf("annual-summary", 1))
                rankRows.forEach { row ->
                    val t = row.totals
                    addAll(listOf(t.clubId, t.played, t.wins, t.losses, t.draws, t.runsFor, t.runsAgainst, row.rank))
                }
                playerRows.forEach { row ->
                    val c = row.counts
                    addAll(listOf(row.playerId, row.clubId, c.completedPlateAppearances, c.safeHitEvents,
                        c.walkEvents, c.strikeoutEvents, c.homeRunEvents))
                }
            }
            val summaryHash = digest(*fields.toTypedArray())
            val id = archiveId(world, root, summary.number, parent, summaryHash)
            return AnnualArchive(AnnualArchiveEntity(id, world, root, summary.number, parent, summaryHash), rankRows.map { row ->
                val t = row.totals
                AnnualClubEntity(id, t.clubId, t.played, t.wins, t.losses, t.draws, t.runsFor, t.runsAgainst, row.rank)
            }, playerRows.map { row ->
                val c = row.counts
                AnnualPlayerEntity(id, row.playerId, row.clubId, c.completedPlateAppearances, c.safeHitEvents,
                    c.walkEvents, c.strikeoutEvents, c.homeRunEvents)
            })
        }
    }
}

private fun digest(vararg fields: Any?): String = hash(fields.joinToString("\u0000") { it?.toString().orEmpty() }.toByteArray(Charsets.UTF_8))
private fun archiveId(world: String, root: String, year: Long, parent: String?, summaryHash: String) =
    digest("annual-archive", 1, world, root, year, parent, summaryHash)

internal fun checkedTimeline(world: String, columns: TimelineColumns, current: SeasonState, parent: AnnualArchive?): SeasonTimeline? =
    checkedHistory {
        if (columns.version == 0) {
            require(columns == TimelineColumns() && parent == null)
            return@checkedHistory if (current.profilesEnabled) SeasonTimeline(current.masterSeed, 1, null) else null
        }
        require(current.profilesEnabled)
        val timeline = SeasonTimeline(requireNotNull(columns.rootSeed), requireNotNull(columns.year), requireNotNull(columns.previous))
        require(timeline.year >= 2 && TimelineColumns.create(world, timeline) == columns)
        require(current.masterSeed == SeasonYearSeeds.start(timeline.rootSeed, timeline.year))
        val archive = requireNotNull(parent)
        checkArchiveHeader(archive.header, timeline.previous, world, timeline.rootSeed, timeline.year - 1)
        archive.checkedSummary()
        timeline
    }

internal fun checkArchiveHeader(archive: AnnualArchiveEntity, id: String?, world: String, rootSeed: String, year: Long) {
    require(archive.id == id && validArchiveId(archive.id) && archive.worldId == world &&
        archive.rootSeed == rootSeed && archive.year == year && year > 0)
    seedFromText(rootSeed)
    require(if (year == 1L) archive.parent == null else archive.parent?.let(::validArchiveId) == true)
    require(validArchiveId(archive.summaryHash) && archive.id == archiveId(world, rootSeed, year, archive.parent, archive.summaryHash))
}

private fun validArchiveId(id: String) = id.length == 64 && id.all { it in '0'..'9' || it in 'a'..'f' }

internal inline fun <T> checkedHistory(block: () -> T): T = try {
    block()
} catch (error: IllegalArgumentException) {
    throw StoreCorruption("Saved annual history is inconsistent; data has been retained", error)
} catch (error: ArithmeticException) {
    throw StoreCorruption("Saved annual history has an invalid number; data has been retained", error)
}

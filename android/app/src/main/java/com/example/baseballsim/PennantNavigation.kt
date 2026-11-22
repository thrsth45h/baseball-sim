package com.example.baseballsim

import com.example.baseballsim.season.PennantSchedule
import com.example.baseballsim.season.PlayerSeasonCounts
import com.example.baseballsim.season.SeasonState

enum class PennantPage(val title: String) {
    HOME("球団ホーム"), LINEUP("打順・出場選手"), PLAYERS("選手一覧"), PLAYER_DETAIL("選手詳細"), SCHEDULE("日程"),
    STANDINGS("順位"), RECORDS("記録"), MATCH("試合"), SETTINGS("設定・セーブ"), HISTORY("年度別成績")
}

/** Malformed OS UI restoration falls back to an unbound home, then binds to the published save. */
internal fun restorePennantNavigation(values: List<String>): PennantNavigation? {
    if (values.size != 5) return null
    val page = PennantPage.entries.find { it.name == values[2] } ?: return null
    return PennantNavigation(values[0].ifEmpty { null }, values[1].ifEmpty { null }, page, values[3], values[4].ifEmpty { null })
}

/** Saveable UI values only. A revision changes the displayed numbers, not the browsing identity. */
data class PennantNavigation(
    val worldId: String? = null, val generationId: String? = null,
    val page: PennantPage = PennantPage.HOME, val browseClubId: String = "L1-T01", val playerId: String? = null,
) {
    fun bind(state: PennantUiState): PennantNavigation {
        // OS restoration may arrive before the existing startup read has completed.
        if (!state.loaded) return this
        if (state.display == null && state.error != null) return this
        val stored = state.display?.stored ?: return PennantNavigation()
        val club = stored.state.selectedClubId
        if (worldId != stored.token.worldId || generationId != stored.token.generationId) {
            return PennantNavigation(stored.token.worldId, stored.token.generationId, browseClubId = club)
        }
        val validClub = PennantSchedule.clubs.find { it.id == browseClubId }
        val validPlayer = playerId == null || (validClub != null && playerId in stored.state.playerIds(browseClubId))
        if (validClub == null || !validPlayer || (page == PennantPage.PLAYER_DETAIL && playerId == null)) {
            return PennantNavigation(worldId, generationId, browseClubId = club)
        }
        return this
    }

    fun open(target: PennantPage, season: SeasonState) = copy(page = target, playerId = null,
        browseClubId = if (target in listOf(PennantPage.PLAYERS, PennantPage.RECORDS, PennantPage.HISTORY)) season.selectedClubId else browseClubId)
    fun browse(clubId: String) = copy(browseClubId = clubId, playerId = null)
    fun player(id: String) = copy(page = PennantPage.PLAYER_DETAIL, playerId = id)
    fun back() = copy(page = if (page == PennantPage.PLAYER_DETAIL) PennantPage.PLAYERS else PennantPage.HOME, playerId = null)
}

/** Join only the fixed roster to the already published, completed-game counts. No replay or IO. */
fun pennantPlayers(state: SeasonState, clubId: String): List<PlayerSeasonCounts> {
    val published = state.playerCounts.associateBy { it.playerId }
    return state.playerIds(clubId).map { id ->
        checkNotNull(published[id]).also { check(it.clubId == clubId) }
    }
}

internal fun playerRoleLabel(state: SeasonState, row: PlayerSeasonCounts): String {
    if (!state.lineupEnabled) return "固定打順 ${row.slot}"
    val position = state.lineup(row.clubId).indexOf(row.playerId)
    return if (position < 0) "控え" else "${position + 1}番"
}

/** These labels describe this prototype's fixed tables, not calibrated 1–20 skills. */
internal fun batterProfileLabel(profileId: String): String = when (profileId) {
    "standard" -> "標準"
    "contact" -> "ミート型"
    "slugger" -> "長打型"
    else -> error("Unknown saved batter profile")
}

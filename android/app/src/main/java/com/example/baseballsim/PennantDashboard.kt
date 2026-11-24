package com.example.baseballsim

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.baseballsim.core.Status
import com.example.baseballsim.season.CalendarDay
import com.example.baseballsim.season.PennantSchedule
import com.example.baseballsim.season.SeasonState
import com.example.baseballsim.season.SeasonStatus
import com.example.baseballsim.season.Standing

private enum class BottomTab(val label: String, val page: PennantPage, val icon: Int, val tag: String) {
    HOME("ホーム", PennantPage.HOME, R.drawable.ic_pennant_home, PennantTags.NAV_HOME),
    SCHEDULE("日程", PennantPage.SCHEDULE, R.drawable.ic_pennant_schedule, PennantTags.NAV_SCHEDULE),
    LINEUP("編成", PennantPage.LINEUP, R.drawable.ic_pennant_lineup, PennantTags.NAV_LINEUP),
    RECORDS("成績", PennantPage.STANDINGS, R.drawable.ic_pennant_records, PennantTags.NAV_STANDINGS),
    SETTINGS("設定", PennantPage.SETTINGS, R.drawable.ic_pennant_settings, PennantTags.NAV_SETTINGS)
}

@Composable internal fun PennantBottomNavigation(page: PennantPage, open: (PennantPage) -> Unit) {
    val selected = when (page) {
        PennantPage.HOME, PennantPage.MATCH -> BottomTab.HOME
        PennantPage.SCHEDULE -> BottomTab.SCHEDULE
        PennantPage.LINEUP, PennantPage.PLAYERS, PennantPage.PLAYER_DETAIL -> BottomTab.LINEUP
        PennantPage.STANDINGS, PennantPage.RECORDS, PennantPage.HISTORY -> BottomTab.RECORDS
        PennantPage.SETTINGS -> BottomTab.SETTINGS
    }
    NavigationBar(Modifier.zIndex(1f).testTag(PennantTags.BOTTOM_NAV), containerColor = MaterialTheme.colorScheme.surface) {
        BottomTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { open(tab.page) },
                modifier = Modifier.testTag(tab.tag),
                icon = { Icon(painterResource(tab.icon), contentDescription = null, modifier = Modifier.size(22.dp)) },
                label = { Text(tab.label, maxLines = 1) },
            )
        }
    }
}

@Composable internal fun PennantTopBar(state: PennantUiState, season: SeasonState, visibleDay: Int,
    advance: () -> Unit, nextYear: () -> Unit) {
    val standing = season.standings.first { it.totals.clubId == season.selectedClubId }
    val calendarDay = visibleDay.coerceAtMost(PennantSchedule.calendar.size)
    val buttonWidth = Modifier.widthIn(min = (88 * LocalDensity.current.fontScale).dp)
    Surface(Modifier.fillMaxWidth().testTag(PennantTags.TOP_BAR), color = MaterialTheme.colorScheme.primaryContainer) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(pennantClubLabel(season.selectedClubId).short, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Text("${standing.rank?.let { "${it}位" } ?: "順位 —"} · ${recordText(standing)}",
                    style = MaterialTheme.typography.labelMedium)
            }
            Text(pennantDate(state.timeline?.year ?: 1L, calendarDay), Modifier.weight(1.1f).testTag(PennantTags.VISIBLE_DAY),
                style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End)
            when {
                state.resultDay != null -> ProgressButton(if (season.status == SeasonStatus.COMPLETE) "終了画面へ" else "翌日へ",
                    PennantTags.NEXT_DAY, state.canUse, buttonWidth, advance)
                season.status == SeasonStatus.IN_PROGRESS -> ProgressButton("進行", PennantTags.DAY, state.canProgress,
                    buttonWidth.then(if (state.dailyProgress) Modifier.semantics { stateDescription = "進行中" } else Modifier), advance)
                season.status == SeasonStatus.COMPLETE && state.timeline != null ->
                    ProgressButton("翌年へ", PennantTags.NEXT_YEAR, state.canAdvanceYear, buttonWidth, nextYear)
            }
        }
    }
}

@Composable internal fun PennantDashboard(state: PennantUiState, season: SeasonState, openOwnGame: (String) -> Unit) {
    val calendar = PennantSchedule.calendar.getOrNull(checkNotNull(state.visibleDay) - 1)
    Column(Modifier.fillMaxWidth().testTag(PennantTags.HOME), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (season.status == SeasonStatus.BLOCKED) BlockedDiagnostic(state)
        if (calendar != null) TodayGames(state, season, calendar, openOwnGame)
        PennantStandings(season.selectedClubId, season.standings, PennantTags.HOME_STANDINGS)
    }
}

@Composable private fun TodayGames(state: PennantUiState, season: SeasonState, day: CalendarDay,
    openOwnGame: (String) -> Unit) {
    val club = season.selectedClubId
    val league = PennantSchedule.club(club).leagueId
    val fixtures = day.gameOrdinals.map { season.games[it - 1].fixture }
    val active = season.active
    Column(Modifier.fillMaxWidth().testTag(PennantTags.TODAY), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (day.isRestDay) {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Text("休養日", Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }
        for (group in listOf(league, otherLeague(league))) {
            val games = fixtures.filter { it.leagueId == group }.sortedBy { club != it.awayClubId && club != it.homeClubId }
            if (games.isEmpty()) continue
            OutlinedCard(Modifier.fillMaxWidth()) {
                games.forEachIndexed { index, fixture ->
                    if (index > 0) HorizontalDivider()
                    val result = season.results.getOrNull(fixture.ordinal - 1)
                    val checkpoint = active?.takeIf { it.game.fixture.gameId == fixture.gameId }
                    val score = result?.score ?: state.display?.takeIf {
                        checkpoint != null && it.fixture?.gameId == fixture.gameId
                    }?.snapshot?.score
                    val status = when {
                        result != null -> "試合終了"
                        checkpoint?.snapshot?.status == Status.ABORTED -> "停止中"
                        checkpoint?.snapshot?.status == Status.COMPLETED -> "確定待ち"
                        checkpoint != null -> "進行中"
                        else -> "試合前"
                    }
                    val ownAway = club == fixture.awayClubId
                    val ownHome = club == fixture.homeClubId
                    val isOwn = ownAway || ownHome
                    Surface(modifier = Modifier.testTag(PennantTags.todayGame(fixture.gameId))
                        .then(if (isOwn) Modifier.semantics { stateDescription = "操作球団の試合" } else Modifier),
                        color = if (isOwn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
                        Row(Modifier.fillMaxWidth().then(if (isOwn) Modifier.testTag(PennantTags.OPEN)
                            .clickable(enabled = state.canUse && (result != null || checkpoint != null || state.canProgress),
                                onClickLabel = if (result != null) "試合結果を見る" else "試合を開く") { openOwnGame(fixture.gameId) }
                            else Modifier).padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(pennantClubLabel(fixture.awayClubId).short, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (ownAway) FontWeight.SemiBold else null, maxLines = 1)
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(score?.let { "${it.away} – ${it.home}" }.orEmpty(), fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center, minLines = 1, maxLines = 1)
                                Text(status, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 1)
                            }
                            Text(pennantClubLabel(fixture.homeClubId).short, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (ownHome) FontWeight.SemiBold else null, textAlign = TextAlign.End, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

private fun otherLeague(league: String) = if (league == "L1") "L2" else "L1"

@Composable internal fun PennantStandings(club: String, standings: List<Standing>, tag: String) {
    val own = PennantSchedule.club(club).leagueId
    BoxWithConstraints(Modifier.fillMaxWidth().testTag(tag)) {
        val minWidth = (180 * LocalDensity.current.fontScale).dp * 2 + 8.dp
        val tableWidth = maxOf(maxWidth, minWidth)
        Box(Modifier.horizontalScroll(rememberScrollState())) {
            Row(Modifier.width(tableWidth), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LeagueStandings(own, club, standings, Modifier.weight(1f))
                LeagueStandings(otherLeague(own), club, standings, Modifier.weight(1f))
            }
        }
    }
}

@Composable private fun LeagueStandings(league: String, club: String, standings: List<Standing>, modifier: Modifier) {
    val rows = standings.filter { PennantSchedule.club(it.totals.clubId).leagueId == league }
    val leader = rows.firstOrNull { it.rank == 1 }
    OutlinedCard(modifier.testTag("pennant_league_standings_$league")) {
        StandingCells(listOf("順位", "球団名", "勝敗分", "勝率", "差"))
        rows.forEach { row ->
            HorizontalDivider()
            val selected = row.totals.clubId == club
            Surface(Modifier.testTag("pennant_standing_${row.totals.clubId}").then(
                if (selected) Modifier.semantics { stateDescription = "操作球団" } else Modifier),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
                StandingCells(listOf(row.rank?.toString() ?: "—", standingClubName(row.totals.clubId),
                    with(row.totals) { "$wins/$losses/$draws" }, winPercentage(row), gamesBehind(row, leader)))
            }
        }
    }
}

private fun standingClubName(id: String) = when (id) {
    "L2-T01" -> "ソフトB"
    "L2-T03" -> "オリ"
    else -> pennantClubLabel(id).short
}

@Composable private fun StandingCells(values: List<String>) {
    val weights = listOf(0.63f, 1.24f, 1f, 0.75f, 0.65f)
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        values.forEachIndexed { index, value ->
            Text(value, Modifier.weight(weights[index]), style = MaterialTheme.typography.labelSmall,
                textAlign = if (index == 1) TextAlign.Start else TextAlign.End, maxLines = 1)
        }
    }
}

private fun recordText(row: Standing): String = with(row.totals) { "${wins}勝 ${losses}敗 ${draws}分" }

package com.example.baseballsim

import androidx.compose.foundation.clickable
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

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun PennantTopBar(state: PennantUiState, season: SeasonState, visibleDay: Int,
    advance: () -> Unit, nextYear: () -> Unit) {
    val team = PennantSchedule.club(season.selectedClubId)
    val standing = season.standings.first { it.totals.clubId == team.id }
    val calendar = PennantSchedule.calendar.getOrNull(visibleDay - 1)
    Surface(Modifier.fillMaxWidth().testTag(PennantTags.TOP_BAR), color = MaterialTheme.colorScheme.primaryContainer) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(team.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("${standing.rank?.let { "${it}位" } ?: "順位 —"} · ${recordText(standing)}", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.timeline?.let { Text("${it.year}年目", Modifier.testTag(PennantTags.YEAR), style = MaterialTheme.typography.labelMedium) }
                    val day = if (calendar == null) "シーズン終了" else "第${calendar.week}週 ${weekday(calendar.weekday)}"
                    Text(day, Modifier.testTag(PennantTags.VISIBLE_DAY), style = MaterialTheme.typography.labelMedium)
                    if (state.resultDay != null) Text("試合後", style = MaterialTheme.typography.labelMedium)
                }
            }
            when {
                state.resultDay != null -> ProgressButton(if (season.status == SeasonStatus.COMPLETE) "終了画面へ" else "翌日へ",
                    PennantTags.NEXT_DAY, state.canUse, Modifier, advance)
                season.status == SeasonStatus.IN_PROGRESS -> ProgressButton("進行", PennantTags.DAY, state.canProgress, Modifier, advance)
                season.status == SeasonStatus.COMPLETE && state.timeline != null ->
                    ProgressButton("翌年へ", PennantTags.NEXT_YEAR, state.canAdvanceYear, Modifier, nextYear)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun PennantDashboard(
    state: PennantUiState,
    season: SeasonState,
    finishSeason: () -> Unit,
    openMatch: () -> Unit,
    openOwnGame: (String) -> Unit,
) {
    val team = PennantSchedule.club(season.selectedClubId)
    val calendar = PennantSchedule.calendar.getOrNull(checkNotNull(state.visibleDay) - 1)
    Column(Modifier.fillMaxWidth().testTag(PennantTags.HOME), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("${season.results.size} / ${season.games.size}試合完了 · seed ${season.masterSeed}",
            Modifier.testTag(PennantTags.PROGRESS), style = MaterialTheme.typography.bodySmall)
        Column {
            if (season.status == SeasonStatus.BLOCKED) BlockedDiagnostic(state)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (state.display?.fixture != null) {
                    TextButton(openMatch, Modifier.testTag(PennantTags.NAV_MATCH)) {
                        Text(if (season.active != null) "試合を見る" else "直近の試合を見る")
                    }
                }
                if (season.status == SeasonStatus.IN_PROGRESS) {
                    TextButton(finishSeason, Modifier.testTag(PennantTags.SEASON), enabled = state.canProgress) {
                        Text("シーズン終了まで")
                    }
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (calendar != null && maxWidth >= (650 * LocalDensity.current.fontScale).dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    TodayGames(state, season, calendar, openOwnGame, Modifier.weight(3f))
                    HomeStandings(team.leagueId, team.id, season.standings, Modifier.weight(2f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (calendar != null) TodayGames(state, season, calendar, openOwnGame)
                    HomeStandings(team.leagueId, team.id, season.standings)
                }
            }
        }
    }
}

@Composable private fun TodayGames(state: PennantUiState, season: SeasonState, day: CalendarDay,
    openOwnGame: (String) -> Unit, modifier: Modifier = Modifier) {
    // A day's scheduled ordinals and the published results identify the same fixtures.
    val fixtures = day.gameOrdinals.map { season.games[it - 1].fixture }
    val active = season.active
    Column(modifier.fillMaxWidth().testTag(PennantTags.TODAY), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("当日の試合", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (day.isRestDay) {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Text("休養日", Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            }
        }
        fixtures.groupBy { it.leagueId }.forEach { (league, games) ->
            Text("リーグ${league.last()}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        checkpoint?.snapshot?.status == Status.COMPLETED -> "結果確定待ち"
                        checkpoint != null -> "進行中"
                        else -> "試合前"
                    }
                    val ownAway = season.selectedClubId == fixture.awayClubId
                    val ownHome = season.selectedClubId == fixture.homeClubId
                    val isOwn = ownAway || ownHome
                    Surface(modifier = Modifier.testTag(PennantTags.todayGame(fixture.gameId))
                        .then(if (isOwn) Modifier.semantics { stateDescription = "操作球団の試合" } else Modifier),
                        color = if (isOwn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
                        Row(Modifier.fillMaxWidth().then(if (isOwn) Modifier.testTag(PennantTags.OPEN)
                            .clickable(enabled = state.canUse && (result != null || checkpoint != null || state.canProgress),
                                onClickLabel = if (result != null) "試合結果を見る" else "試合を開く") { openOwnGame(fixture.gameId) }
                            else Modifier)
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(PennantSchedule.club(fixture.awayClubId).displayName, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (ownAway) FontWeight.SemiBold else null)
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                score?.let { Text("${it.away} – ${it.home}", fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center) }
                                Text(status, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                            }
                            Text(PennantSchedule.club(fixture.homeClubId).displayName, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (ownHome) FontWeight.SemiBold else null, textAlign = TextAlign.End)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun HomeStandings(league: String, club: String, standings: List<Standing>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().testTag(PennantTags.HOME_STANDINGS), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("リーグ${league.last()}の順位", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        OutlinedCard(Modifier.fillMaxWidth()) {
            standings.filter { PennantSchedule.club(it.totals.clubId).leagueId == league }.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider()
                val selected = row.totals.clubId == club
                Surface(color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
                    Row(Modifier.fillMaxWidth().then(if (selected) Modifier.semantics { stateDescription = "操作球団" } else Modifier)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(row.rank?.toString() ?: "—", Modifier.width(20.dp), style = MaterialTheme.typography.labelLarge)
                        Text(PennantSchedule.club(row.totals.clubId).displayName, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else null)
                        Text(recordText(row), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private fun recordText(row: Standing): String = with(row.totals) { "${wins}勝 ${losses}敗 ${draws}分" }

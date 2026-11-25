package com.example.baseballsim

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.baseballsim.core.Score
import com.example.baseballsim.core.Status
import com.example.baseballsim.season.CalendarDay
import com.example.baseballsim.season.Fixture
import com.example.baseballsim.season.GameSummary
import com.example.baseballsim.season.PennantSchedule
import com.example.baseballsim.season.SeasonState
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

internal object MonthlyScheduleTags {
    const val GRID = "schedule_grid"
    const val MONTH = "schedule_month"
    const val PREVIOUS = "schedule_previous_month"
    const val NEXT = "schedule_next_month"
    const val DETAILS = "schedule_day_details"
    fun month(number: Int) = "schedule_month_$number"
    fun day(date: LocalDate) = "schedule_day_$date"
    fun game(id: String) = "schedule_game_$id"
}

internal fun scheduleWeeks(month: YearMonth): List<List<LocalDate?>> {
    val firstColumn = month.atDay(1).dayOfWeek.value % 7
    val count = month.lengthOfMonth()
    val slots = ((firstColumn + count + 6) / 7) * 7
    return List(slots) { index ->
        val day = index - firstColumn + 1
        if (day in 1..count) month.atDay(day) else null
    }.chunked(7)
}

internal fun scheduleDay(first: LocalDate, date: LocalDate): CalendarDay? {
    val offset = ChronoUnit.DAYS.between(first, date)
    val calendar = PennantSchedule.calendar
    return if (offset in 0L until calendar.size.toLong()) calendar[offset.toInt()] else null
}

internal fun scheduleMonthDate(selected: LocalDate, month: YearMonth): LocalDate =
    month.atDay(minOf(selected.dayOfMonth, month.lengthOfMonth()))

internal data class ScheduleScore(val own: Long, val opponent: Long) {
    val symbol get() = when {
        own > opponent -> "○"
        own < opponent -> "●"
        else -> "△"
    }
    val outcome get() = when {
        own > opponent -> "勝ち"
        own < opponent -> "負け"
        else -> "引き分け"
    }
}

internal fun scheduleScore(score: Score, isHome: Boolean) =
    if (isHome) ScheduleScore(score.home, score.away) else ScheduleScore(score.away, score.home)

private data class ScheduledCard(val fixture: Fixture, val result: GameSummary?, val active: Status?) {
    val status get() = when {
        result != null -> "試合終了"
        active == Status.ABORTED -> "停止中"
        active != null -> "進行中"
        else -> "試合前"
    }
}

private fun scheduledCards(season: SeasonState, day: CalendarDay?): List<ScheduledCard> =
    day?.gameOrdinals.orEmpty().map { ordinal ->
        val fixture = season.games[ordinal - 1].fixture
        ScheduledCard(fixture, season.results.getOrNull(ordinal - 1),
            season.active?.takeIf { it.game.fixture.gameId == fixture.gameId }?.snapshot?.status)
    }

private data class ClubCalendarGame(val opponent: String, val status: String, val score: ScheduleScore?)

private fun clubCalendarGame(cards: List<ScheduledCard>, club: String): ClubCalendarGame? {
    val card = cards.firstOrNull { club == it.fixture.homeClubId || club == it.fixture.awayClubId } ?: return null
    val home = club == card.fixture.homeClubId
    return ClubCalendarGame(if (home) card.fixture.awayClubId else card.fixture.homeClubId, card.status,
        card.result?.let { scheduleScore(it.score, home) })
}

private val calendarClubNames = PennantSchedule.clubIds.zip(
    listOf("巨人", "阪神", "De", "広島", "ヤク", "中日", "ソフ", "日ハ", "オリ", "楽天", "ロッ", "西武")
).toMap()
private val compactCalendarClubNames = PennantSchedule.clubIds.zip(
    listOf("巨", "神", "De", "広", "ヤ", "中", "ソ", "日", "オ", "楽", "ロ", "西")
).toMap()
private val scheduleWeekdays = listOf("日", "月", "火", "水", "木", "金", "土")

@Composable
internal fun MonthlySchedule(season: SeasonState, visibleDay: Int, year: Long) {
    val first = pennantLocalDate(year, 1)
    val today = pennantLocalDate(year, visibleDay.coerceIn(1, PennantSchedule.calendar.size))
    // This key also separates restored state saved against a different displayed day.
    key(today) {
        var selectedEpoch by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
        var openedGameId by rememberSaveable(selectedEpoch) { mutableStateOf<String?>(null) }
        val selected = LocalDate.ofEpochDay(selectedEpoch)
        val month = YearMonth.from(selected)
        val selectedDay = scheduleDay(first, selected)
        Column(Modifier.fillMaxWidth().testTag(PennantTags.SCHEDULE), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ScheduleMonthControl(month) { destination ->
                selectedEpoch = scheduleMonthDate(selected, destination).toEpochDay()
            }
            ScheduleGrid(first, today, selected, month, season) { selectedEpoch = it.toEpochDay() }
            ScheduleDayDetails(selected, selectedDay, scheduledCards(season, selectedDay), season.selectedClubId, openedGameId) {
                openedGameId = if (it == openedGameId) null else it
            }
        }
    }
}

@Composable
private fun ScheduleMonthControl(month: YearMonth, changeMonth: (YearMonth) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        TextButton({ changeMonth(month.minusMonths(1)) }, Modifier.testTag(MonthlyScheduleTags.PREVIOUS),
            enabled = month.monthValue > 1) { Text("前月") }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            TextButton({ expanded = true }, Modifier.testTag(MonthlyScheduleTags.MONTH), contentPadding = PaddingValues(0.dp)) {
                Text("${month.year}年${month.monthValue}月", textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                Text("▾", Modifier.padding(start = 4.dp).clearAndSetSemantics {}, style = MaterialTheme.typography.labelSmall)
            }
            DropdownMenu(expanded, { expanded = false }) {
                for (number in 1..12) {
                    DropdownMenuItem(text = { Text("${number}月") }, onClick = {
                        expanded = false
                        changeMonth(YearMonth.of(month.year, number))
                    }, modifier = Modifier.testTag(MonthlyScheduleTags.month(number)))
                }
            }
        }
        TextButton({ changeMonth(month.plusMonths(1)) }, Modifier.testTag(MonthlyScheduleTags.NEXT),
            enabled = month.monthValue < 12) { Text("翌月") }
    }
}

@Composable
private fun ScheduleGrid(first: LocalDate, today: LocalDate, selected: LocalDate, month: YearMonth,
    season: SeasonState, select: (LocalDate) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().testTag(MonthlyScheduleTags.GRID)) {
        val compact = maxWidth / LocalDensity.current.fontScale < 274.dp
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.outlineVariant),
                verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(vertical = 7.dp)) {
                    scheduleWeekdays.forEach { label ->
                        Text(label, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
                    }
                }
                scheduleWeeks(month).forEach { week ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                        week.forEach { date ->
                            val cell = Modifier.weight(1f).fillMaxHeight()
                            if (date == null) {
                                Spacer(cell.background(MaterialTheme.colorScheme.surfaceVariant))
                            } else {
                                val day = scheduleDay(first, date)
                                val game = clubCalendarGame(scheduledCards(season, day), season.selectedClubId)
                                ScheduleDayCell(date, day, game, date == today, date == selected, compact, cell) { select(date) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleDayCell(date: LocalDate, day: CalendarDay?, game: ClubCalendarGame?, today: Boolean, selected: Boolean,
    compact: Boolean, modifier: Modifier, select: () -> Unit) {
    val score = game?.score
    val palette = MaterialTheme.colorScheme
    val background = when {
        selected -> palette.primaryContainer
        today -> palette.secondaryContainer
        else -> palette.surface
    }
    val description = buildString {
        append(pennantDate(date))
        when {
            day == null -> append("、シーズン日程外")
            day.isRestDay -> append("、休養日")
            game != null -> {
                append("、対${pennantClubLabel(game.opponent).full}、${game.status}")
                if (score != null) append("、${score.outcome}、自球団${score.own}点、相手${score.opponent}点")
            }
        }
    }
    Column(modifier.background(background).testTag(MonthlyScheduleTags.day(date))
        .selectable(selected, onClick = select, role = Role.Button)
        .semantics { contentDescription = description; if (today) stateDescription = "当日" }
        .heightIn(min = 62.dp).padding(horizontal = 1.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(date.dayOfMonth.toString(), Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
            color = if (today) palette.primary else palette.onSurface,
            fontWeight = if (today || selected) FontWeight.Bold else FontWeight.Normal)
        if (game != null) {
            val names = if (compact) compactCalendarClubNames else calendarClubNames
            val namesAndResult = names.getValue(game.opponent) + (score?.symbol ?: "")
            Text(namesAndResult, Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        }
        if (score != null) {
            val long = score.own >= 10 || score.opponent >= 10
            val join = if (long && compact) "–\n" else "–"
            Text("${score.own}$join${score.opponent}", Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ScheduleDayDetails(date: LocalDate, day: CalendarDay?, cards: List<ScheduledCard>,
    club: String, openedGameId: String?, toggle: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().testTag(MonthlyScheduleTags.DETAILS), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(pennantDate(date), style = MaterialTheme.typography.titleMedium)
        if (day == null || day.isRestDay) {
            Text(if (day == null) "シーズン日程外" else "休養日")
        }
        cards.forEach { card ->
            val id = card.fixture.gameId
            val own = club == card.fixture.awayClubId || club == card.fixture.homeClubId
            val opened = card.result != null && openedGameId == id
            val action = if (card.result == null) Modifier else Modifier.clickable(
                onClickLabel = if (opened) "試合結果を閉じる" else "試合結果を見る",
                role = Role.Button, onClick = { toggle(id) }
            )
            OutlinedCard(Modifier.fillMaxWidth().testTag(MonthlyScheduleTags.game(id)),
                colors = CardDefaults.outlinedCardColors(containerColor = if (own) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().then(action).padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val visitor = pennantClubLabel(card.fixture.awayClubId).short
                    val home = pennantClubLabel(card.fixture.homeClubId).short
                    Text("$visitor − $home", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    val result = card.result
                    Text(result?.let { "${it.score.away} – ${it.score.home}" } ?: card.status,
                        style = MaterialTheme.typography.labelMedium)
                    if (result != null) Text(if (opened) "▲" else "▼", Modifier.clearAndSetSemantics {},
                        style = MaterialTheme.typography.labelSmall)
                }
                if (opened) {
                    HorizontalDivider()
                    CompletedGameCard(checkNotNull(card.result))
                }
            }
        }
    }
}

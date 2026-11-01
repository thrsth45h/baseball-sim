package com.example.baseballsim

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.baseballsim.core.Side
import com.example.baseballsim.core.Status
import com.example.baseballsim.season.*
import java.util.Locale

object PennantTags {
    const val SCREEN = "pennant_screen"
    const val SEED = "pennant_seed"
    const val CREATE = "pennant_create"
    const val CONFIRM_CREATE = "pennant_confirm_create"
    const val PROGRESS = "pennant_progress"
    const val GAME = "pennant_game"
    const val COUNT = "pennant_count"
    const val PITCH = "pennant_pitch"
    const val PA = "pennant_pa"
    const val FINISH_GAME = "pennant_finish_game"
    const val OPEN = "pennant_open"
    const val DAY = "pennant_day"
    const val SEASON = "pennant_season"
    const val STOP = "pennant_stop"
    const val SAVE = "pennant_save"
    const val LOAD = "pennant_load"
    const val CONFIRM_LOAD = "pennant_confirm_load"
    const val ERROR = "pennant_error"
    const val RELOAD = "pennant_reload"
    const val SCHEDULE = "pennant_schedule"
    const val STANDINGS = "pennant_standings"
    const val RECORDS = "pennant_records"
}

@Composable fun PennantRoute(viewModel: PennantViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PennantScreen(state, viewModel::changeSeed, viewModel::selectClub,
        { command, confirmed -> viewModel.execute(command, confirmed) }, viewModel::requestStop, viewModel::reload)
}

@Composable fun PennantScreen(state: PennantUiState, changeSeed: (String) -> Unit, selectClub: (String) -> Unit,
    execute: (PennantCommand, Boolean) -> Unit, stop: () -> Unit, reload: () -> Unit) {
    var confirmCreate by rememberSaveable { mutableStateOf(false) }
    var confirmLoad by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable { mutableIntStateOf(0) }
    val season = state.season
    Surface(Modifier.fillMaxSize().testTag(PennantTags.SCREEN), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("ペナント", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (!state.loaded || state.busy) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text(if (!state.loaded) "保存データを確認中" else if (state.stopRequested) "現在の試合が終わったら停止します" else "進行中")
                }
            }
            state.error?.let {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(it, Modifier.testTag(PennantTags.ERROR))
                        OutlinedButton(reload, enabled = !state.busy, modifier = Modifier.testTag(PennantTags.RELOAD)) { Text("保存を読み直す") }
                    }
                }
            }
            state.notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            if (season == null) Text("仮の12球団で1シーズンを進めます。各球団120試合、選手と確率は試作用の同一設定です。")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(state.seedInput, changeSeed, enabled = state.canUse,
                    label = { Text("開始値（seed）") }, singleLine = true, isError = state.seedError != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f).testTag(PennantTags.SEED))
                Button(onClick = { if (season == null) execute(PennantCommand.CREATE, false) else confirmCreate = true },
                    enabled = state.canUse, modifier = Modifier.padding(top = 8.dp).testTag(PennantTags.CREATE)) { Text("新シーズン") }
            }
            state.seedError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            ClubSelector("注目球団", state.selectedClubInput, state.canUse, selectClub)
            if (season != null) {
                Text("${season.results.size} / 720試合完了 · seed ${season.masterSeed}", Modifier.testTag(PennantTags.PROGRESS), fontWeight = FontWeight.SemiBold)
                if (season.status == SeasonStatus.BLOCKED) {
                    Text("試合を完了できなかったため停止しています。記録を保持し、自動では進めません。", color = MaterialTheme.colorScheme.error)
                } else if (season.status == SeasonStatus.COMPLETE) Text("シーズン終了", style = MaterialTheme.typography.titleLarge)
                state.display?.let { GameCard(it) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProgressButton("1球", PennantTags.PITCH, state.canAdvanceMatch, Modifier.weight(1f)) { execute(PennantCommand.PITCH, false) }
                    ProgressButton("1打席", PennantTags.PA, state.canAdvanceMatch, Modifier.weight(1f)) { execute(PennantCommand.PLATE_APPEARANCE, false) }
                    ProgressButton("最後まで", PennantTags.FINISH_GAME, state.canAdvanceMatch, Modifier.weight(1f)) { execute(PennantCommand.GAME, false) }
                }
                OutlinedButton({ execute(PennantCommand.OPEN_SELECTED, false) }, enabled = state.canProgress,
                    modifier = Modifier.fillMaxWidth().testTag(PennantTags.OPEN)) { Text("次の自球団試合を開く") }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProgressButton("1日進める", PennantTags.DAY, state.canProgress, Modifier.weight(1f)) { execute(PennantCommand.DAY, false) }
                    ProgressButton("シーズン終了まで", PennantTags.SEASON, state.canProgress, Modifier.weight(1f)) { execute(PennantCommand.SEASON, false) }
                }
                if (state.busy && state.bulk) OutlinedButton(stop, enabled = !state.stopRequested,
                    modifier = Modifier.fillMaxWidth().testTag(PennantTags.STOP)) { Text(if (state.stopRequested) "停止予約済み" else "停止予約") }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ execute(PennantCommand.SAVE, false) }, enabled = state.canSave,
                        modifier = Modifier.weight(1f).testTag(PennantTags.SAVE)) { Text("セーブ") }
                    OutlinedButton({ confirmLoad = true }, enabled = state.canLoad,
                        modifier = Modifier.weight(1f).testTag(PennantTags.LOAD)) { Text("ロード") }
                }
                Text(if (season.active != null) "試合中は自動復旧用に保存します。手動セーブ・ロードは試合外で利用できます。"
                    else if (state.hasSlot) "手動セーブあり" else "手動セーブなし", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("日程", "順位", "記録").forEachIndexed { index, label ->
                        if (page == index) Button({ page = index }, Modifier.weight(1f)) { Text(label) }
                        else TextButton({ page = index }, Modifier.weight(1f)) { Text(label) }
                    }
                }
                when (page) {
                    0 -> SchedulePanel(season)
                    1 -> StandingsPanel(season)
                    else -> RecordsPanel(season)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    if (confirmCreate) AlertDialog(onDismissRequest = { confirmCreate = false },
        title = { Text("新しいシーズンを開始") }, text = { Text("現在の進行データを置き換えます。手動セーブは残ります。") },
        confirmButton = { TextButton({ confirmCreate = false; execute(PennantCommand.CREATE, true) }, Modifier.testTag(PennantTags.CONFIRM_CREATE)) { Text("開始する") } },
        dismissButton = { TextButton({ confirmCreate = false }) { Text("戻る") } })
    if (confirmLoad) AlertDialog(onDismissRequest = { confirmLoad = false },
        title = { Text("手動セーブをロード") }, text = { Text("保存した時点に戻ります。現在の進行を置き換えます。") },
        confirmButton = { TextButton({ confirmLoad = false; execute(PennantCommand.LOAD, false) }, Modifier.testTag(PennantTags.CONFIRM_LOAD)) { Text("読み込む") } },
        dismissButton = { TextButton({ confirmLoad = false }) { Text("戻る") } })
}

@Composable private fun ProgressButton(label: String, tag: String, enabled: Boolean, modifier: Modifier, action: () -> Unit) {
    Button(action, enabled = enabled, modifier = modifier.testTag(tag), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)) { Text(label) }
}

@Composable private fun ClubSelector(label: String, selected: String, enabled: Boolean, select: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({ expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("$label：${PennantSchedule.club(selected).displayName}") }
        DropdownMenu(expanded, { expanded = false }) {
            PennantSchedule.clubs.forEach { club -> DropdownMenuItem(text = { Text(club.displayName) },
                onClick = { expanded = false; select(club.id) }) }
        }
    }
}

@Composable private fun GameCard(display: PennantDisplay) {
    val fixture = display.fixture ?: return
    val snapshot = display.snapshot ?: return
    Card(Modifier.fillMaxWidth().testTag(PennantTags.GAME)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(PennantSchedule.club(fixture.awayClubId).displayName)
                Text("${snapshot.score.away} − ${snapshot.score.home}", style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace)
                Text(PennantSchedule.club(fixture.homeClubId).displayName)
            }
            if (snapshot.status == Status.IN_PROGRESS) {
                val sequence = snapshot.sequence
                val slot = snapshot.battingIndices[if (sequence.side == Side.TOP) 0 else 1] + 1
                Text("${sequence.inning}回${if (sequence.side == Side.TOP) "表" else "裏"} · 打順 $slot")
                Text("B ${sequence.count?.balls ?: 0}   S ${sequence.count?.strikes ?: 0}   O ${sequence.half.outs}", Modifier.testTag(PennantTags.COUNT), fontFamily = FontFamily.Monospace)
                Text(sequence.half.bases.mapIndexed { index, on -> "${index + 1}塁 ${if (on) "有" else "無"}" }.joinToString(" · "))
            } else Text(if (snapshot.status == Status.COMPLETED) "試合終了" else "未完了・停止")
            Text("${snapshot.pitchesAccepted}球 · ${snapshot.completedPa}打席終了", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("回\n表\n裏")
                for (inning in 0..8) Text("${inning + 1}\n${snapshot.lineScore[inning * 2] ?: "—"}\n${snapshot.lineScore[inning * 2 + 1] ?: "—"}", fontFamily = FontFamily.Monospace)
                Text("計\n${snapshot.score.away}\n${snapshot.score.home}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
            display.recent.takeLast(5).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable private fun SchedulePanel(state: SeasonState) {
    var day by remember(state.masterSeed, state.dayIndex) { mutableIntStateOf(minOf(state.dayIndex, 140)) }
    val calendar = PennantSchedule.calendar[day - 1]
    Column(Modifier.testTag(PennantTags.SCHEDULE), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton({ day-- }, enabled = day > 1) { Text("前日") }
            Text("第${calendar.week}週 ${weekday(calendar.weekday)}", modifier = Modifier.padding(top = 12.dp))
            TextButton({ day++ }, enabled = day < 140) { Text("翌日") }
        }
        if (calendar.isRestDay) Text("休養日")
        val results = state.results.associateBy { it.game.fixture.gameId }
        calendar.gameOrdinals.forEach { ordinal ->
            val fixture = state.games[ordinal - 1].fixture
            val result = results[fixture.gameId]
            val active = state.active?.game?.fixture?.gameId == fixture.gameId
            val score = result?.let { "${it.score.away} − ${it.score.home}" } ?: if (active) "進行中" else "未開始"
            Text("${PennantSchedule.club(fixture.awayClubId).displayName}  $score  ${PennantSchedule.club(fixture.homeClubId).displayName}")
        }
    }
}

@Composable private fun StandingsPanel(state: SeasonState) {
    var league by rememberSaveable { mutableStateOf("L1") }
    Column(Modifier.testTag(PennantTags.STANDINGS), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row { listOf("L1", "L2").forEach { id -> TextButton({ league = id }) { Text("リーグ${id.last()}") } } }
        Surface(Modifier.fillMaxWidth()) {
            Column(Modifier.horizontalScroll(rememberScrollState())) {
                TableRow(listOf("位", "球団", "試", "勝", "敗", "分", "得点", "失点", "勝率"))
                state.standings.filter { PennantSchedule.club(it.totals.clubId).leagueId == league }.forEach { row ->
                    val t = row.totals
                    val ratio = t.ratio?.let { String.format(Locale.ROOT, "%.3f", it.numerator.toDouble() / it.denominator) } ?: "—"
                    TableRow(listOf(row.rank?.toString() ?: "—", PennantSchedule.club(t.clubId).displayName, t.played.toString(), t.wins.toString(),
                        t.losses.toString(), t.draws.toString(), t.runsFor.toString(), t.runsAgainst.toString(), ratio))
                }
            }
        }
    }
}

@Composable private fun RecordsPanel(state: SeasonState) {
    var club by remember(state.selectedClubId) { mutableStateOf(state.selectedClubId) }
    Column(Modifier.testTag(PennantTags.RECORDS), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ClubSelector("記録を見る球団", club, true) { club = it }
        Text("試作用イベント数。セーフ安打には本塁打を含みます。", style = MaterialTheme.typography.bodySmall)
        Surface(Modifier.fillMaxWidth()) {
            Column(Modifier.horizontalScroll(rememberScrollState())) {
                TableRow(listOf("打順", "完了打席", "セーフ安打", "四球", "三振", "本塁打"), records = true)
                state.playerCounts.filter { it.clubId == club }.forEach { row ->
                    val c = row.counts
                    TableRow(listOf(row.slot.toString(), c.completedPlateAppearances.toString(), c.safeHitEvents.toString(),
                        c.walkEvents.toString(), c.strikeoutEvents.toString(), c.homeRunEvents.toString()), records = true)
                }
            }
        }
    }
}

@Composable private fun TableRow(values: List<String>, records: Boolean = false) {
    Row(Modifier.padding(vertical = 6.dp)) {
        values.forEachIndexed { i, value -> Text(value, Modifier.width(if (records) 82.dp else when (i) { 0 -> 32.dp; 1 -> 86.dp; 8 -> 64.dp; else -> 48.dp }), fontFamily = FontFamily.Monospace) }
    }
}
private fun weekday(value: String) = mapOf("Mon" to "月曜", "Tue" to "火曜", "Wed" to "水曜", "Thu" to "木曜", "Fri" to "金曜", "Sat" to "土曜", "Sun" to "日曜").getValue(value)

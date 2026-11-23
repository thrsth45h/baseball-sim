package com.example.baseballsim

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import com.example.baseballsim.data.StoreToken
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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
import kotlinx.coroutines.CancellationException

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
    const val NEXT_DAY = "pennant_next_day"
    const val TOP_BAR = "pennant_top_bar"
    const val VISIBLE_DAY = "pennant_visible_day"
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
    const val HOME = "pennant_home"
    const val PLAYERS = "pennant_players"
    const val PLAYER_DETAIL = "pennant_player_detail"
    const val BACK = "pennant_back"
    const val BROWSE_CLUB = "pennant_browse_club"
    const val BOTTOM_NAV = "pennant_bottom_nav"
    const val NAV_HOME = "pennant_nav_home"
    const val NAV_LINEUP = "pennant_nav_lineup"
    const val LINEUP = "pennant_lineup"
    const val APPLY_LINEUP = "pennant_apply_lineup"
    const val RESET_LINEUP = "pennant_reset_lineup"
    const val NAV_PLAYERS = "pennant_nav_players"
    const val NAV_SCHEDULE = "pennant_nav_schedule"
    const val NAV_STANDINGS = "pennant_nav_standings"
    const val NAV_RECORDS = "pennant_nav_records"
    const val NAV_MATCH = "pennant_nav_match"
    const val NAV_SETTINGS = "pennant_nav_settings"
    const val YEAR = "pennant_year"
    const val NEXT_YEAR = "pennant_next_year"
    const val CONFIRM_NEXT_YEAR = "pennant_confirm_next_year"
    const val NAV_HISTORY = "pennant_nav_history"
    const val TODAY = "pennant_dashboard_today"
    const val HOME_STANDINGS = "pennant_dashboard_standings"
    fun historyYear(year: Long) = "pennant_history_$year"
    fun player(id: String) = "pennant_player_$id"
    fun todayGame(id: String) = "pennant_today_$id"
}

private val NavigationSaver = listSaver<PennantNavigation, String>(
    save = { listOf(it.worldId.orEmpty(), it.generationId.orEmpty(), it.page.name, it.browseClubId, it.playerId.orEmpty(), it.matchId.orEmpty()) },
    restore = ::restorePennantNavigation)
private data class PennantConfirmation(val command: PennantCommand, val token: StoreToken)
private val ConfirmationSaver = listSaver<PennantConfirmation?, String>(
    save = { pending -> pending?.let { listOf(it.command.name, it.token.worldId, it.token.generationId, it.token.revision.toString()) } ?: emptyList() },
    restore = { v -> runCatching {
        if (v.size != 4) null else PennantCommand.valueOf(v[0]).takeIf {
            it in listOf(PennantCommand.CREATE, PennantCommand.LOAD, PennantCommand.NEXT_YEAR)
        }
            ?.let { PennantConfirmation(it, StoreToken(v[1], v[2], v[3].toLong())) }
    }.getOrNull() })

@Composable fun PennantRoute(viewModel: PennantViewModel, standaloneMatch: (() -> Unit)? = null) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PennantScreen(state, viewModel::changeSeed, viewModel::selectClub,
        { command, confirmed, token -> viewModel.execute(command, confirmed, confirmationToken = token) },
        viewModel::requestStop, viewModel::reload, viewModel::applyLineup, viewModel::historyYears, viewModel::historyYear,
        viewModel::advanceVisibleDay, viewModel::openOwnGame, viewModel::consumeOpenedGame, standaloneMatch)
}

@Composable fun PennantScreen(state: PennantUiState, changeSeed: (String) -> Unit, selectClub: (String) -> Unit,
    execute: (PennantCommand, Boolean, StoreToken?) -> Unit, stop: () -> Unit, reload: () -> Unit,
    applyLineup: (StoreToken, String, List<String>) -> Unit = { _, _, _ -> },
    historyYears: suspend (StoreToken) -> List<Long> = { emptyList() },
    historyYear: suspend (StoreToken, Long) -> SeasonYear = { _, _ -> error("Annual history is unavailable") },
    advanceDay: (StoreToken, Int) -> Boolean = { expected, _ -> execute(PennantCommand.DAY, false, expected); true },
    openOwnGame: (StoreToken, String) -> Boolean = { _, _ -> false },
    consumeOpenedGame: (StoreToken, String) -> Boolean = { _, _ -> false },
    standaloneMatch: (() -> Unit)? = null) {
    var savedNavigation by rememberSaveable(stateSaver = NavigationSaver) { mutableStateOf(PennantNavigation()) }
    var confirmation by rememberSaveable(stateSaver = ConfirmationSaver) { mutableStateOf<PennantConfirmation?>(null) }
    val restoredNavigation = savedNavigation
    val navigation = restoredNavigation.bind(state)
    val season = state.season
    val token = state.display?.stored?.token
    val timeline = state.timeline
    val pages = rememberSaveableStateHolder()
    val latestPublished by rememberUpdatedState(state)
    SideEffect {
        // Only persist a rebinding if the user has not already chosen another page.
        if (navigation != restoredNavigation && savedNavigation == restoredNavigation) savedNavigation = navigation
    }
    // Do not discard OS restoration candidates until the startup identity is known.
    LaunchedEffect(state.loaded, state.error, token) {
        if (state.loaded && (state.display != null || state.error == null) && confirmation?.token != token) confirmation = null
    }
    val back = {
        val current = savedNavigation.bind(latestPublished)
        if (current.page == PennantPage.LINEUP) pages.removeState("${current.worldId}/${current.generationId}/${current.page}")
        savedNavigation = current.back()
    }
    BackHandler(enabled = season != null && confirmation == null && navigation.page != PennantPage.HOME) { back() }
    fun command(value: PennantCommand) { execute(value, false, null) }
    fun open(page: PennantPage, matchId: String? = null) {
        // A tab keeps the same destination when the displayed save generation changes.
        val published = latestPublished
        val currentSeason = published.season ?: return
        val current = savedNavigation.bind(published)
        if (current.page == page && current.matchId == matchId) return
        if (page in listOf(PennantPage.PLAYERS, PennantPage.RECORDS, PennantPage.LINEUP, PennantPage.HISTORY)) {
            pages.removeState("${current.worldId}/${current.generationId}/$page")
        }
        savedNavigation = current.open(page, currentSeason, matchId)
    }
    LaunchedEffect(token, state.openedGameId) {
        val target = state.openedGameId ?: return@LaunchedEffect
        if (token != null && consumeOpenedGame(token, target) && savedNavigation.bind(latestPublished).page == PennantPage.HOME) {
            open(PennantPage.MATCH, target)
        }
    }
    Surface(Modifier.fillMaxSize().testTag(PennantTags.SCREEN), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            if (season != null && token != null) {
                val visibleDay = checkNotNull(state.visibleDay)
                PennantTopBar(state, season, visibleDay,
                    advance = { if (advanceDay(token, visibleDay)) open(PennantPage.HOME) },
                    nextYear = {
                        val latest = latestPublished
                        if (latest.display?.stored?.token == token && latest.resultDay == null && latest.canAdvanceYear) {
                            open(PennantPage.HOME)
                            confirmation = PennantConfirmation(PennantCommand.NEXT_YEAR, token)
                        }
                    })
            }
            // Common state and stop stay reachable independently of the page's scroll position.
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (season != null && navigation.page != PennantPage.HOME) TextButton(back, Modifier.testTag(PennantTags.BACK)) { Text("戻る") }
                    if (season == null || navigation.page != PennantPage.HOME) {
                        Text((if (season == null) PennantPage.HOME else navigation.page).title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f).padding(top = 8.dp))
                    }
                }
                if (!state.loaded || state.busy) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text(if (!state.loaded) "保存データを確認中" else if (state.stopRequested) "現在の試合が終わったら停止します" else "進行中")
                }
                if (state.busy && state.bulk) OutlinedButton(stop, enabled = !state.stopRequested,
                    modifier = Modifier.fillMaxWidth().testTag(PennantTags.STOP)) { Text(if (state.stopRequested) "停止予約済み" else "停止予約") }
                state.error?.let {
                    Text(it, Modifier.testTag(PennantTags.ERROR), color = MaterialTheme.colorScheme.error)
                    OutlinedButton(reload, enabled = !state.busy, modifier = Modifier.testTag(PennantTags.RELOAD)) { Text("保存を読み直す") }
                }
                state.notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            }
            // Keep the scroll destination's surfaces and touch targets inside its viewport.
            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                pages.SaveableStateProvider(if (season == null) "startup" else "${navigation.worldId}/${navigation.generationId}/${navigation.page}") {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (season == null) {
                            Text("仮の12球団で1シーズンを進めます。各球団120試合、打者の3タイプは試作用の仮設定です。")
                            SeasonSettings(state, changeSeed, selectClub, { command(PennantCommand.CREATE) }, {}, {})
                            standaloneMatch?.let { NavigationButton("1試合", "mode_match", it) }
                        } else when (navigation.page) {
                            PennantPage.HOME -> PennantDashboard(state, season,
                                finishSeason = { command(PennantCommand.SEASON) }, openMatch = { open(PennantPage.MATCH) },
                                openOwnGame = { gameId -> token?.let { openOwnGame(it, gameId) } })
                            PennantPage.LINEUP -> {
                                NavigationButton("選手一覧", PennantTags.NAV_PLAYERS) { open(PennantPage.PLAYERS) }
                                token?.let { LineupPanel(state, it, applyLineup) }
                            }
                            PennantPage.PLAYERS -> PlayersPanel(season, navigation.browseClubId,
                                { savedNavigation = navigation.browse(it) }, { savedNavigation = navigation.player(it) })
                            PennantPage.PLAYER_DETAIL -> PlayerPanel(pennantPlayers(season, navigation.browseClubId).first { it.playerId == navigation.playerId }, season)
                            PennantPage.SCHEDULE -> SchedulePanel(season, checkNotNull(state.visibleDay))
                            PennantPage.STANDINGS -> {
                                NavigationButton("個人成績", PennantTags.NAV_RECORDS) { open(PennantPage.RECORDS) }
                                if (timeline != null && timeline.year > 1) {
                                    NavigationButton("年度別成績", PennantTags.NAV_HISTORY) { open(PennantPage.HISTORY) }
                                }
                                StandingsPanel(season.standings)
                            }
                            PennantPage.RECORDS -> RecordsPanel(pennantPlayers(season, navigation.browseClubId), season.lineupEnabled,
                                navigation.browseClubId) { savedNavigation = navigation.browse(it) }
                            PennantPage.HISTORY -> if (token != null && timeline != null && timeline.year > 1) {
                                HistoryPanel(token, state.canUse, navigation.browseClubId, { savedNavigation = navigation.browse(it) }, historyYears, historyYear)
                            } else Unit
                            PennantPage.MATCH -> {
                                val selectedId = navigation.matchId
                                val result = if (selectedId == null) null else season.results.firstOrNull { it.game.fixture.gameId == selectedId }
                                val match = state.display?.takeIf { selectedId == null || it.fixture?.gameId == selectedId }
                                if (match != null) GameCard(match) else if (result != null) CompletedGameCard(result)
                                val canAdvance = state.canAdvanceMatch && result == null && match != null
                                ProgressButton("1球", PennantTags.PITCH, canAdvance, Modifier.fillMaxWidth()) { command(PennantCommand.PITCH) }
                                ProgressButton("1打席", PennantTags.PA, canAdvance, Modifier.fillMaxWidth()) { command(PennantCommand.PLATE_APPEARANCE) }
                                ProgressButton("最後まで", PennantTags.FINISH_GAME, canAdvance, Modifier.fillMaxWidth()) { command(PennantCommand.GAME) }
                            }
                            PennantPage.SETTINGS -> {
                                SeasonSettings(state, changeSeed, selectClub,
                                    { token?.let { confirmation = PennantConfirmation(PennantCommand.CREATE, it) } },
                                    { command(PennantCommand.SAVE) }, { token?.let { confirmation = PennantConfirmation(PennantCommand.LOAD, it) } })
                                standaloneMatch?.let { NavigationButton("1試合", "mode_match", it) }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
            if (season != null) PennantBottomNavigation(navigation.page) { open(it) }
        }
    }
    confirmation?.takeIf { season != null && state.loaded && it.token == token &&
        (it.command != PennantCommand.NEXT_YEAR || timeline != null) }?.let { pending ->
        val (title, message, button, tag) = when (pending.command) {
            PennantCommand.CREATE -> ConfirmationText("新しいシーズンを開始", "現在の進行データを置き換えます。手動セーブは残ります。", "開始する", PennantTags.CONFIRM_CREATE)
            PennantCommand.NEXT_YEAR -> ConfirmationText("翌年へ進む", "${checkNotNull(timeline).year}年目の成績を残し、同じ選手・打順で翌年を開始します。", "進む", PennantTags.CONFIRM_NEXT_YEAR)
            else -> ConfirmationText("手動セーブをロード", "保存した時点に戻ります。現在の進行を置き換えます。", "読み込む", PennantTags.CONFIRM_LOAD)
        }
        AlertDialog(onDismissRequest = { confirmation = null }, title = { Text(title) }, text = { Text(message) },
            confirmButton = { TextButton({ confirmation = null; execute(pending.command, pending.command == PennantCommand.CREATE, pending.token) },
                Modifier.testTag(tag)) { Text(button) } },
            dismissButton = { TextButton({ confirmation = null }) { Text("戻る") } })
    }
}

private data class ConfirmationText(val title: String, val message: String, val button: String, val tag: String)

@Composable private fun HistoryPanel(token: StoreToken, canRead: Boolean, club: String, browse: (String) -> Unit,
    listYears: suspend (StoreToken) -> List<Long>, loadYear: suspend (StoreToken, Long) -> SeasonYear) {
    var requested by rememberSaveable { mutableStateOf<Long?>(null) }
    // Ordinary play can publish hundreds of revisions while skipping a season. A history
    // anchor only changes with the generation, so keep any loaded year and wait for idle.
    var years by remember(token.worldId, token.generationId) { mutableStateOf<List<Long>?>(null) }
    var loaded by remember(token.worldId, token.generationId, requested) { mutableStateOf<SeasonYear?>(null) }
    var error by remember(token, requested, canRead) { mutableStateOf(false) }
    LaunchedEffect(token, requested, canRead) {
        if (!canRead) return@LaunchedEffect
        try {
            if (years == null) years = listYears(token)
            if (loaded == null) requested?.let { loaded = loadYear(token, it) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = true }
    }
    when {
        error -> Text("年度別成績を読み込めませんでした。", color = MaterialTheme.colorScheme.error)
        requested != null -> {
            TextButton({ requested = null }) { Text("年度一覧へ") }
            val year = loaded
            if (year == null) Text("成績を読み込み中") else {
                Text("${year.number}年目", style = MaterialTheme.typography.titleLarge)
                StandingsPanel(year.standings)
                RecordsPanel(year.players.filter { it.clubId == club }, true, club, browse)
            }
        }
        else -> years?.forEach { year -> NavigationButton("${year}年目", PennantTags.historyYear(year)) { requested = year } }
            ?: Text("年度を読み込み中")
    }
}

@Composable private fun NavigationButton(label: String, tag: String, action: () -> Unit) {
    OutlinedButton(action, modifier = Modifier.fillMaxWidth().testTag(tag)) { Text(label) }
}
@Composable private fun SeasonSettings(state: PennantUiState, changeSeed: (String) -> Unit, selectClub: (String) -> Unit,
    create: () -> Unit, save: () -> Unit, load: () -> Unit) {
    OutlinedTextField(state.seedInput, changeSeed, enabled = state.canUse, label = { Text("開始値（seed）") }, singleLine = true,
        isError = state.seedError != null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().testTag(PennantTags.SEED))
    state.seedError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    ClubSelector("操作球団", state.selectedClubInput, state.canUse, selectClub)
    Button(create, enabled = state.canUse, modifier = Modifier.fillMaxWidth().testTag(PennantTags.CREATE)) { Text("新シーズン") }
    val season = state.season
    if (season != null) {
        OutlinedButton(save, enabled = state.canSave, modifier = Modifier.fillMaxWidth().testTag(PennantTags.SAVE)) { Text("セーブ") }
        OutlinedButton(load, enabled = state.canLoad, modifier = Modifier.fillMaxWidth().testTag(PennantTags.LOAD)) { Text("ロード") }
        Text(if (season.active != null) "試合中は自動復旧用に保存します。手動セーブ・ロードは試合外で利用できます。"
            else if (state.hasSlot) "手動セーブあり" else "手動セーブなし", style = MaterialTheme.typography.bodySmall)
    }
}
@Composable private fun PlayersPanel(state: SeasonState, club: String, browse: (String) -> Unit, select: (String) -> Unit) {
    Column(Modifier.testTag(PennantTags.PLAYERS), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ClubSelector("閲覧球団", club, true, browse, PennantTags.BROWSE_CLUB)
        Text("確定試合の記録", style = MaterialTheme.typography.bodySmall)
        if (state.profilesEnabled) Text("試作用タイプ", style = MaterialTheme.typography.bodySmall)
        pennantPlayers(state, club).forEach { row ->
            Card(Modifier.fillMaxWidth().testTag(PennantTags.player(row.playerId)).clickable(onClickLabel = "選手詳細を開く") { select(row.playerId) }) {
                Column(Modifier.padding(12.dp)) {
                    Text(row.playerId, fontWeight = FontWeight.SemiBold)
                    state.playerProfile(row.playerId)?.let { Text(batterProfileLabel(it)) }
                    Text("${playerRoleLabel(state, row)} · 完了打席 ${row.counts.completedPlateAppearances}")
                }
            }
        }
    }
}
@Composable private fun PlayerPanel(row: PlayerSeasonCounts, state: SeasonState) {
    Column(Modifier.testTag(PennantTags.PLAYER_DETAIL), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(row.playerId, Modifier.testTag("player_id"), style = MaterialTheme.typography.titleLarge)
        Text(PennantSchedule.club(row.clubId).displayName, Modifier.testTag("player_club"))
        Text(playerRoleLabel(state, row), Modifier.testTag("player_slot"))
        state.playerProfile(row.playerId)?.let {
            Text("試作用タイプ", style = MaterialTheme.typography.bodySmall)
            Text(batterProfileLabel(it), Modifier.testTag("player_profile"))
        }
        Text("確定試合の記録", style = MaterialTheme.typography.titleMedium)
        val c = row.counts
        listOf(Triple("完了打席", c.completedPlateAppearances, "player_completed_pa"), Triple("セーフ安打", c.safeHitEvents, "player_safe_hits"),
            Triple("四球", c.walkEvents, "player_walks"), Triple("三振", c.strikeoutEvents, "player_strikeouts"),
            Triple("本塁打", c.homeRunEvents, "player_home_runs")).forEach { (label, value, tag) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label); Text(value.toString(), Modifier.testTag(tag), fontFamily = FontFamily.Monospace)
            }
        }
        Text("セーフ安打には本塁打を含みます。", style = MaterialTheme.typography.bodySmall)
    }
}
@Composable internal fun BlockedDiagnostic(state: PennantUiState) {
    Text("試合を完了できなかったため停止しています。記録を保持し、自動では進めません。", color = MaterialTheme.colorScheme.error)
    state.display?.let { display ->
        Text("試合ID：${display.fixture?.gameId}", style = MaterialTheme.typography.bodySmall)
        Text("理由：${pennantFailureReason(display.snapshot?.reason)}", style = MaterialTheme.typography.bodySmall)
    }
}
@Composable internal fun ProgressButton(label: String, tag: String, enabled: Boolean, modifier: Modifier, action: () -> Unit) {
    Button(action, enabled = enabled, modifier = modifier.testTag(tag), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)) { Text(label) }
}

@Composable private fun ClubSelector(label: String, selected: String, enabled: Boolean, select: (String) -> Unit, tag: String = "pennant_operating_club") {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({ expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag(tag)) { Text("$label：${PennantSchedule.club(selected).displayName}") }
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
            Text("${PennantSchedule.club(fixture.awayClubId).displayName} − ${PennantSchedule.club(fixture.homeClubId).displayName}")
            Text("${snapshot.score.away} − ${snapshot.score.home}", style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace)
            if (snapshot.status == Status.IN_PROGRESS) {
                val sequence = snapshot.sequence
                val slot = snapshot.battingIndices[if (sequence.side == Side.TOP) 0 else 1] + 1
                Text("${sequence.inning}回${if (sequence.side == Side.TOP) "表" else "裏"} · 打順 $slot")
                if (display.stored.state.lineupEnabled) {
                    val lineup = display.stored.state.active?.lineup ?: display.stored.state.results.lastOrNull()?.lineup
                    lineup?.let { Text("打者 ${if (sequence.side == Side.TOP) it.away[slot - 1] else it.home[slot - 1]}", Modifier.testTag("pennant_batter_id")) }
                    // Match labels use the frozen start input, never the club's current plan.
                    val profiles = display.stored.state.active?.profileInput ?: display.stored.state.results.lastOrNull()?.profileInput
                    profiles?.let { Text("試作用タイプ · ${batterProfileLabel(it.profile(if (sequence.side == Side.TOP) "away" else "home", slot))}",
                        Modifier.testTag("pennant_batter_profile")) }
                }
                Text("B ${sequence.count?.balls ?: 0}   S ${sequence.count?.strikes ?: 0}   O ${sequence.half.outs}", Modifier.testTag(PennantTags.COUNT), fontFamily = FontFamily.Monospace)
                Text(sequence.half.bases.mapIndexed { index, on -> "${index + 1}塁 ${if (on) "有" else "無"}" }.joinToString(" · "))
            } else if (snapshot.status == Status.COMPLETED) Text("試合終了")
            else {
                Text("未完了・停止", color = MaterialTheme.colorScheme.error)
                Text("試合ID：${fixture.gameId}", style = MaterialTheme.typography.bodySmall)
                Text("理由：${pennantFailureReason(snapshot.reason)}", style = MaterialTheme.typography.bodySmall)
            }
            Text("${snapshot.pitchesAccepted}球 · ${snapshot.completedPa}打席終了", style = MaterialTheme.typography.bodySmall)
            GameLineScore(snapshot.lineScore, snapshot.score.away, snapshot.score.home)
            display.recent.takeLast(5).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable private fun CompletedGameCard(result: GameSummary) {
    val fixture = result.game.fixture
    Card(Modifier.fillMaxWidth().testTag(PennantTags.GAME)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${PennantSchedule.club(fixture.awayClubId).displayName} − ${PennantSchedule.club(fixture.homeClubId).displayName}")
            Text("${result.score.away} − ${result.score.home}", style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace)
            Text("試合終了")
            GameLineScore(result.lineScore, result.score.away, result.score.home)
        }
    }
}

@Composable private fun GameLineScore(lineScore: List<Long?>, away: Long, home: Long) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("回\n表\n裏")
        for (inning in 0..8) Text("${inning + 1}\n${lineScore[inning * 2] ?: "—"}\n${lineScore[inning * 2 + 1] ?: "—"}", fontFamily = FontFamily.Monospace)
        Text("計\n$away\n$home", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

@Composable private fun SchedulePanel(state: SeasonState, visibleDay: Int) {
    var day by rememberSaveable(visibleDay) { mutableIntStateOf(minOf(visibleDay, PennantSchedule.calendar.size)) }
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

@Composable private fun StandingsPanel(standings: List<Standing>) {
    var league by rememberSaveable { mutableStateOf("L1") }
    Column(Modifier.testTag(PennantTags.STANDINGS), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row { listOf("L1", "L2").forEach { id -> TextButton({ league = id }) { Text("リーグ${id.last()}") } } }
        Surface(Modifier.fillMaxWidth()) {
            Column(Modifier.horizontalScroll(rememberScrollState())) {
                TableRow(listOf("位", "球団", "試", "勝", "敗", "分", "得点", "失点", "勝率"))
                standings.filter { PennantSchedule.club(it.totals.clubId).leagueId == league }.forEach { row ->
                    val t = row.totals
                    val ratio = t.ratio?.let { String.format(Locale.ROOT, "%.3f", it.numerator.toDouble() / it.denominator) } ?: "—"
                    TableRow(listOf(row.rank?.toString() ?: "—", PennantSchedule.club(t.clubId).displayName, t.played.toString(), t.wins.toString(),
                        t.losses.toString(), t.draws.toString(), t.runsFor.toString(), t.runsAgainst.toString(), ratio))
                }
            }
        }
    }
}

@Composable private fun RecordsPanel(players: List<PlayerSeasonCounts>, lineupEnabled: Boolean, club: String, browse: (String) -> Unit) {
    Column(Modifier.testTag(PennantTags.RECORDS), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ClubSelector("記録を見る球団", club, true, browse, PennantTags.BROWSE_CLUB)
        Text("確定試合の記録。セーフ安打には本塁打を含みます。", style = MaterialTheme.typography.bodySmall)
        Surface(Modifier.fillMaxWidth()) {
            Column(Modifier.horizontalScroll(rememberScrollState())) {
                TableRow(listOf(if (lineupEnabled) "選手" else "打順", "完了打席", "セーフ安打", "四球", "三振", "本塁打"), records = true, firstWide = lineupEnabled)
                players.forEach { row ->
                    val c = row.counts
                    TableRow(listOf(if (lineupEnabled) row.playerId else row.slot.toString(), c.completedPlateAppearances.toString(), c.safeHitEvents.toString(),
                        c.walkEvents.toString(), c.strikeoutEvents.toString(), c.homeRunEvents.toString()), records = true, firstWide = lineupEnabled)
                }
            }
        }
    }
}

@Composable private fun TableRow(values: List<String>, records: Boolean = false, firstWide: Boolean = false) {
    Row(Modifier.padding(vertical = 6.dp)) {
        values.forEachIndexed { i, value -> Text(value, Modifier.width(if (records) (if (i == 0 && firstWide) 156.dp else 82.dp) else when (i) { 0 -> 32.dp; 1 -> 86.dp; 8 -> 64.dp; else -> 48.dp }), fontFamily = FontFamily.Monospace) }
    }
}
internal fun weekday(value: String) = mapOf("Mon" to "月曜", "Tue" to "火曜", "Wed" to "水曜", "Thu" to "木曜", "Fri" to "金曜", "Sat" to "土曜", "Sun" to "日曜").getValue(value)
private fun pennantFailureReason(reason: String?) = when (reason) {
    "pitch_limit" -> "投球数の上限に到達しました（pitch_limit）"
    "source_exhausted" -> "投球入力が不足しています（source_exhausted）"
    "source_error" -> "投球処理でエラーが発生しました（source_error）"
    "invalid_pitch_input" -> "投球入力が不正です（invalid_pitch_input）"
    "invalid_fair_result" -> "打球結果が不正です（invalid_fair_result）"
    else -> "保存された理由を確認してください（${reason ?: "理由なし"}）"
}

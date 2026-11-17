package com.example.baseballsim

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.baseballsim.data.StoreToken

/** UI-only draft; the full published token is carried through to the actual store CAS. */
internal data class LineupDraft(val token: StoreToken, val clubId: String, val players: List<String>)
internal fun restoreLineupDraft(values: List<String>): LineupDraft? = runCatching {
    if (values.size != 13) null else LineupDraft(StoreToken(values[0], values[1], values[2].toLong()), values[3], values.drop(4))
}.getOrNull()
private val DraftSaver = listSaver<LineupDraft?, String>(
    save = { it?.let { d -> listOf(d.token.worldId, d.token.generationId, d.token.revision.toString(), d.clubId) + d.players } ?: emptyList() },
    restore = ::restoreLineupDraft)

@Composable internal fun LineupPanel(ui: PennantUiState, token: StoreToken, apply: (StoreToken, String, List<String>) -> Unit) {
    val season = checkNotNull(ui.season)
    val club = season.selectedClubId
    val saved = season.lineup(club)
    val fresh = LineupDraft(token, club, saved)
    var restored by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf<LineupDraft?>(null) }
    val draft = restored?.takeIf { d -> season.lineupEnabled && d.token == token && d.clubId == club && d.players.size == 9 &&
        d.players.toSet().size == 9 && d.players.all { it in season.playerIds(club) } } ?: fresh
    LaunchedEffect(draft) { restored = draft }
    val editable = ui.canEditLineup
    val bench = season.playerIds(club).filter { it !in draft.players }
    Column(Modifier.fillMaxWidth().testTag(PennantTags.LINEUP), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(com.example.baseballsim.season.PennantSchedule.club(club).displayName, style = MaterialTheme.typography.titleLarge)
        if (!season.lineupEnabled) Text("旧形式の固定オーダーです。新シーズンから編成を変更できます。")
        else if (!editable) Text(if (season.active != null) "試合中は変更できません" else "現在は変更できません")
        draft.players.forEachIndexed { index, id ->
            Card(Modifier.fillMaxWidth().testTag("lineup_row_${index + 1}")) {
                Column(Modifier.padding(12.dp)) {
                    Text("${index + 1}番  $id", fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("lineup_player_${index + 1}"))
                    if (season.lineupEnabled) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        fun swap(other: Int) {
                            val next = draft.players.toMutableList()
                            next[index] = next[other].also { next[other] = next[index] }
                            restored = draft.copy(players = next.toList())
                        }
                        TextButton({ swap(index - 1) }, enabled = editable && index > 0, modifier = Modifier.testTag("lineup_up_${index + 1}")) { Text("上へ") }
                        TextButton({ swap(index + 1) }, enabled = editable && index < 8, modifier = Modifier.testTag("lineup_down_${index + 1}")) { Text("下へ") }
                        var expanded by remember { mutableStateOf(false) }
                        LaunchedEffect(token, editable) { expanded = false }
                        Box {
                            TextButton({ expanded = true }, enabled = editable, modifier = Modifier.testTag("lineup_replace_${index + 1}")) { Text("控えと交換") }
                            DropdownMenu(expanded && editable, { expanded = false }) {
                                bench.forEach { reserve -> DropdownMenuItem(text = { Text(reserve) }, onClick = {
                                    expanded = false
                                    restored = draft.copy(players = draft.players.mapIndexed { i, old -> if (i == index) reserve else old })
                                }, modifier = Modifier.testTag("lineup_reserve_$reserve")) }
                            }
                        }
                    }
                }
            }
        }
        if (season.lineupEnabled) {
            Text("控え", style = MaterialTheme.typography.titleMedium)
            bench.forEach { Text(it, Modifier.testTag("lineup_bench_$it")) }
            Button({ apply(draft.token, draft.clubId, draft.players.toList()) }, enabled = editable && draft.players != saved,
                modifier = Modifier.fillMaxWidth().testTag(PennantTags.APPLY_LINEUP)) { Text("適用") }
            OutlinedButton({ restored = fresh }, enabled = draft.players != saved,
                modifier = Modifier.fillMaxWidth().testTag(PennantTags.RESET_LINEUP)) { Text("変更を戻す") }
        }
    }
}

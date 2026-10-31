package com.example.baseballsim

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

object MatchTags {
    const val SCREEN = "match_screen"
    const val SCORE = "score"
    const val STATUS = "status"
    const val CURRENT = "current"
    const val COUNT = "count"
    const val BASES = "bases"
    const val LINE_SCORE = "line_score"
    const val RECENT = "recent"
    const val OUTCOME = "outcome"
    const val SEED_INPUT = "seed_input"
    const val SEED_ERROR = "seed_error"
    const val NEW_GAME = "new_game"
    const val ADVANCE_PITCH = "advance_pitch"
    const val ADVANCE_PA = "advance_pa"
    const val ADVANCE_GAME = "advance_game"
    const val BUSY = "busy"
}

@Composable
fun BaseballTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF1E5945),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFDCECE3),
            onPrimaryContainer = Color(0xFF123B2C),
            background = Color(0xFFF5F5EF),
            onBackground = Color(0xFF1D2B24),
            surface = Color(0xFFFEFFFA),
            surfaceVariant = Color(0xFFE8EBE2),
            onSurfaceVariant = Color(0xFF536057),
        ),
        content = content,
    )
}

@Composable
fun MatchRoute(viewModel: MatchViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MatchScreen(state, viewModel::changeSeedInput, viewModel::newGame, viewModel::advance)
}

@Composable
fun MatchScreen(
    state: MatchUiState,
    onSeedChange: (String) -> Unit,
    onNewGame: () -> Unit,
    onAdvance: (AdvanceMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.testTag(MatchTags.SCREEN),
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
    ) { insets ->
        Column(
            Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("1試合", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("技術検証版", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val game = state.game
            if (game != null) ScoreCard(game)

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AdvanceButton("1球", MatchTags.ADVANCE_PITCH, state.canAdvance, Modifier.weight(1f)) {
                        onAdvance(AdvanceMode.PITCH)
                    }
                    AdvanceButton("1打席", MatchTags.ADVANCE_PA, state.canAdvance, Modifier.weight(1f)) {
                        onAdvance(AdvanceMode.PLATE_APPEARANCE)
                    }
                    AdvanceButton("最後まで", MatchTags.ADVANCE_GAME, state.canAdvance, Modifier.weight(1f)) {
                        onAdvance(AdvanceMode.GAME)
                    }
                }
                if (state.isBusy) {
                    Row(
                        Modifier.testTag(MatchTags.BUSY),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("計算中", style = MaterialTheme.typography.bodySmall)
                    }
                }
                state.operationError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (game != null) {
                if (game.status == GameStatus.IN_PROGRESS) game.current?.let { CurrentCard(it) }
                LineScore(game)
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    OutlinedTextField(
                        value = state.seedInput,
                        onValueChange = onSeedChange,
                        modifier = Modifier.weight(1f).testTag(MatchTags.SEED_INPUT),
                        enabled = !state.isBusy,
                        label = { Text("seed") },
                        singleLine = true,
                        isError = state.seedError != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    )
                    OutlinedButton(
                        onClick = onNewGame,
                        enabled = !state.isBusy,
                        modifier = Modifier.padding(top = 8.dp).height(48.dp).testTag(MatchTags.NEW_GAME),
                    ) { Text("新規") }
                }
                state.seedError?.let {
                    Text(
                        it,
                        Modifier.testTag(MatchTags.SEED_ERROR),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            if (game != null && game.recent.isNotEmpty()) {
                Column(Modifier.testTag(MatchTags.RECENT), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("直近", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    game.recent.takeLast(12).asReversed().forEach { entry ->
                        Text(entry, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                "全員共通の仮設定。公式能力・個人成績は未実装。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AdvanceButton(label: String, tag: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp).testTag(tag),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
        shape = RoundedCornerShape(12.dp),
    ) { Text(label, maxLines = 1) }
}

@Composable
private fun ScoreCard(game: GamePresentation) {
    Card(
        Modifier.fillMaxWidth().testTag(MatchTags.SCORE),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val status = when (game.status) {
                GameStatus.IN_PROGRESS -> game.current?.let { "${it.inning}回${it.side.japanese}" } ?: "試合中"
                GameStatus.COMPLETED -> "試合終了"
                GameStatus.ABORTED -> "未完了"
            }
            Text(status, Modifier.testTag(MatchTags.STATUS), style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TeamScore("ビジター", game.scoreAway, Modifier.weight(1f))
                Text("—", color = MaterialTheme.colorScheme.primary, fontSize = 24.sp)
                TeamScore("ホーム", game.scoreHome, Modifier.weight(1f))
            }
            if (game.status != GameStatus.IN_PROGRESS) {
                Column(Modifier.testTag(MatchTags.OUTCOME), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    game.outcome?.let { Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    game.reason?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}

@Composable
private fun TeamScore(label: String, score: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(score, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CurrentCard(current: CurrentPresentation) {
    Surface(
        Modifier.fillMaxWidth().testTag(MatchTags.CURRENT),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${current.team} · ${current.batterSlot}番 ${current.batterName}", style = MaterialTheme.typography.titleMedium)
            Text(
                "B ${current.balls}     S ${current.strikes}     O ${current.outs}",
                Modifier.testTag(MatchTags.COUNT).semantics {
                    contentDescription = "ボール${current.balls}、ストライク${current.strikes}、アウト${current.outs}"
                },
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
                fontWeight = FontWeight.Bold,
            )
            Row(
                Modifier.fillMaxWidth().testTag(MatchTags.BASES),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("一塁", "二塁", "三塁").forEachIndexed { index, name ->
                    val occupied = current.bases.getOrElse(index) { false }
                    Surface(
                        Modifier.weight(1f).semantics { contentDescription = "$name ${if (occupied) "走者あり" else "空"}" },
                        color = if (occupied) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("$name ${if (occupied) "●" else "—"}", Modifier.padding(vertical = 8.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun LineScore(game: GamePresentation) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(Modifier.fillMaxWidth().testTag(MatchTags.LINE_SCORE), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp).width(328.dp)) {
                ScoreRow("回", (1..9).map(Int::toString), "R", heading = true)
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                ScoreRow("ビジター", game.lineAway.map { it ?: "—" }, game.scoreAway)
                Spacer(Modifier.height(8.dp))
                ScoreRow("ホーム", game.lineHome.map { it ?: "—" }, game.scoreHome)
            }
        }
        Text(
            "${game.pitchesAccepted}球 · ${game.completedPa}打席",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ScoreRow(team: String, innings: List<String>, total: String, heading: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(team, Modifier.width(62.dp), style = MaterialTheme.typography.labelSmall, fontWeight = if (heading) FontWeight.Medium else FontWeight.Bold)
        innings.forEach { Text(it, Modifier.width(26.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall) }
        Text(total, Modifier.width(32.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

private val HalfSide.japanese: String get() = if (this == HalfSide.TOP) "表" else "裏"

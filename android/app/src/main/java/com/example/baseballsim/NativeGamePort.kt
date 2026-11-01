package com.example.baseballsim

import com.example.baseballsim.core.GameEvent
import com.example.baseballsim.core.GameSession
import com.example.baseballsim.core.Side
import com.example.baseballsim.core.Status

object NativeGamePortFactory : GamePortFactory {
    override fun create(seed: String): GamePort = NativeGamePort(GameSession(seed = seed))
}

/** Only translates the native engine's existing state and events into display text. */
internal class NativeGamePort(private val session: GameSession) : GamePort {
    override fun snapshot(): GamePresentation {
        val snapshot = session.snapshot
        val sequence = snapshot.sequence
        val teamIndex = if (sequence.side == Side.TOP) 0 else 1
        val slot = snapshot.battingIndices[teamIndex] + 1
        return GamePresentation(
            seed = session.seed,
            status = when (snapshot.status) {
                Status.IN_PROGRESS -> GameStatus.IN_PROGRESS
                Status.COMPLETED -> GameStatus.COMPLETED
                Status.ABORTED -> GameStatus.ABORTED
            },
            // The engine score includes any dedicated walk-off terminal increment.
            scoreAway = snapshot.score.away.toString(),
            scoreHome = snapshot.score.home.toString(),
            lineAway = (0 until 9).map { snapshot.lineScore[it * 2]?.toString() },
            lineHome = (0 until 9).map { snapshot.lineScore[it * 2 + 1]?.toString() },
            pitchesAccepted = snapshot.pitchesAccepted,
            completedPa = snapshot.completedPa,
            current = if (snapshot.status == Status.IN_PROGRESS) {
                CurrentPresentation(
                    inning = sequence.inning,
                    side = if (sequence.side == Side.TOP) HalfSide.TOP else HalfSide.BOTTOM,
                    team = if (teamIndex == 0) "ビジター" else "ホーム",
                    batterName = if (teamIndex == 0) "Away $slot" else "Home $slot",
                    batterSlot = slot,
                    balls = sequence.count?.balls ?: 0,
                    strikes = sequence.count?.strikes ?: 0,
                    outs = sequence.half.outs,
                    bases = sequence.half.bases.toList(),
                )
            } else null,
            recent = session.events.asReversed().asSequence().mapNotNull(::eventText).take(12).toList().asReversed(),
            outcome = when (snapshot.outcome) {
                "away_win" -> "ビジターの勝利"
                "home_win" -> "ホームの勝利"
                "draw" -> "引き分け"
                else -> null
            },
            reason = snapshot.reason?.let(::reasonText),
            walkoff = snapshot.reason?.startsWith("walkoff_") == true,
        ).detached()
    }

    override fun advanceOnePitch(): GamePresentation {
        session.advanceOnePitch()
        return snapshot()
    }
}

private fun eventText(event: GameEvent): String? = when {
    event.kind == "action" && event.pitchEvent != null -> {
        val label = when (event.pitchEvent) {
            "called_ball" -> "ボール"
            "called_strike_caught" -> "見逃しストライク"
            "swinging_strike_caught" -> "空振り"
            "ordinary_foul_uncaught" -> "ファウル"
            "fair_ball_handoff" -> "フェア"
            "bunt_foul_uncaught" -> "バントファウル"
            "foul_fly_caught" -> "ファウル捕球"
            "foul_tip_caught" -> "ファウルチップ"
            else -> "投球"
        }
        "${event.pitchesAccepted}球目 · $label${batterText(event)}"
    }
    event.kind == "plate_appearance_completed" -> {
        val label = when (event.category) {
            "out_hold" -> "打者アウト"
            "safe_hit" -> "セーフ到達"
            "home_run" -> "本塁打"
            "walk" -> "四球"
            "strikeout" -> "三振"
            else -> "打席終了"
        }
        label + (if (event.runsAdded > 0) " · ${event.runsAdded}得点" else "") + batterText(event)
    }
    event.kind == "game_end" || event.kind == "abort" -> event.reason?.let(::reasonText)
    else -> null
}

private fun batterText(event: GameEvent): String {
    val slot = event.batterId?.substringAfterLast('_')?.toIntOrNull() ?: return ""
    return when (event.team) {
        "away" -> " · Away $slot"
        "home" -> " · Home $slot"
        else -> ""
    }
}

private fun reasonText(reason: String): String = when (reason) {
    "home_lead_after_top9" -> "9回表終了・9回裏は省略"
    "away_lead_after_bottom9" -> "9回裏終了"
    "tie_after_nine" -> "9回同点・延長なし"
    "walkoff_walk" -> "サヨナラ四球"
    "walkoff_safe_hit" -> "サヨナラ・セーフ到達"
    "walkoff_home_run" -> "サヨナラ本塁打・全得点"
    "pitch_limit" -> "投球数の上限に到達"
    "source_exhausted" -> "入力不足で未完了"
    "source_error" -> "入力処理のエラーで未完了"
    "invalid_pitch_input" -> "投球入力が不正"
    "invalid_fair_result" -> "フェア候補が不正・投球は未受理"
    else -> "終了理由を確認できません"
}

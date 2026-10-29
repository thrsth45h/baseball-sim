# Phase 4c: 投球と半回の明示接続

合成アクションで旧4a/4bへ明示委任する限定検算。

| 固定列 | stage | count status | outs | 一二三塁 | 半回得点 |
|---|---|---|---:|---|---:|
| empty_between | between_batters | null | 0 | 000 | 0 |
| begin_only | pitching | active | 0 | 000 | 0 |
| one_ball | pitching | active | 0 | 000 | 0 |
| walk_await | awaiting_resolution | walk_awarded | 0 | 000 | 0 |
| walk_applied | between_batters | null | 0 | 100 | 0 |
| second_base_walk | between_batters | null | 0 | 110 | 0 |
| loaded_walk | between_batters | null | 2 | 111 | 8 |
| strikeout_await | awaiting_resolution | strikeout_out | 0 | 000 | 0 |
| strikeout_hold | between_batters | null | 1 | 101 | 2 |
| third_out_strikeout | half_complete | null | 3 | 111 | 7 |
| caught_foul_out | between_batters | null | 2 | 001 | 0 |
| fair_await | awaiting_resolution | fair_play_pending | 0 | 010 | 7 |
| fair_out | between_batters | null | 1 | 101 | 0 |
| grand_slam | between_batters | null | 1 | 000 | 11 |
| next_batter | pitching | active | 0 | 100 | 0 |
| three_retired | half_complete | null | 3 | 000 | 0 |
| ordinary_fouls | pitching | active | 0 | 000 | 0 |
| bunt_strikeout | between_batters | null | 1 | 000 | 0 |

制御12セル/互換12セル、18列/82action/100分割は試験範囲であり、リーグ成績やモデル精度ではない。
ResolveのW/X/Hは追加事実が完了・裁定済みという明示入力。statusだけから自動選択しない。
halfは最後に確定反映したsnapshotで、現実の瞬間状態や打席終了時点を表すものではない。
beginからresolveまで盗塁/暴投等の未対応イベントなしと置く合成前提。省略された実世界事実は検出できない。
解決後も次打者は明示Begin。三アウトで残塁/得点を保持して停止し、次半回へ自動遷移しない。
連続Resolve拒否は古いsnapshotの再投入・外部二重送信・並行更新のexactly-once保証ではない。
旧型/stepとsource5パスを許可して利用する一方、旧fixture/結果・研究/3dはrunへ入力しない。
source_kind=synthetic_fixture; scope=partial_pitch_half_orchestration。
connected_to_phase4a=true; connected_to_phase4b=true; connected_to_statcast=false; connected_to_phase3d=false。
probability_model=false; official_stats_generated=false; ability_calibrated=false; android_integrated=false; product_adoption=false。
一般走塁・完全試合・能力・Android/UI/セーブの採用は別設計。D029/D031/D032はOPEN。

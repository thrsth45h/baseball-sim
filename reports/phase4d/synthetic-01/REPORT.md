# Phase 4d: 表裏切替と導出得点の限定参照

終了判定のない有限合成半回列。試合の最終得点/勝者の判定器ではない。

| 固定列 | 回/側 | stage | away | home |
|---|---|---|---:|---:|
| empty_top | 1/top | between_batters | 0 | 0 |
| begin_top | 1/top | pitching | 0 | 0 |
| bottom_ball | 2/bottom | pitching | 3 | 5 |
| walk_pending | 1/top | awaiting_resolution | 9 | 3 |
| loaded_walk_top | 1/top | between_batters | 19 | 13 |
| grand_slam_bottom | 2/bottom | between_batters | 11 | 24 |
| third_out_wait | 1/top | awaiting_resolution | 18 | 13 |
| closed_no_advance | 1/top | half_complete | 18 | 13 |
| top_switch | 1/bottom | between_batters | 18 | 13 |
| bottom_switch | 3/top | between_batters | 11 | 17 |
| zero_switch | 1/bottom | between_batters | 0 | 0 |
| next_begin | 1/bottom | pitching | 18 | 13 |
| two_scoring_halves | 2/top | between_batters | 1 | 1 |
| two_zero_halves | 2/top | between_batters | 0 | 0 |
| walk_close_switch | 1/bottom | between_batters | 19 | 13 |
| switch_then_pending | 1/bottom | awaiting_resolution | 15 | 13 |

16制御セル/16列/100action/116分割は検査範囲であり、リーグ成績や予測精度ではない。
表示scoreは切替済み累計と現在halfから導出し、Advance前後で不変。旧snapshotの残塁/得点は変更しない。
Advanceはhalf_completeだけ。結果待ちの第三ストライクでは不可。切替後のBeginも明示する。
9回/10回の自動終了・延長走者・勝者を作らない。実試合で次半回を行うべきという判断ではない。
旧4c.stepへ旧3actionを1回ずつ委任し、旧ソース7パスをimport/hashで利用。旧I/O/fixture/結果・研究モデルはrunへ入力しない。
強い合成前提とhalf snapshotを引継ぎ、省略実世界事実や古いsnapshot再投入の永続重複排除を保証しない。
source_kind=synthetic_fixture; scope=partial_half_sequence_controller; connected_to_phase4a/phase4b/phase4c=true。
connected_to_statcast/connected_to_phase3d/probability_model/game_end_rules_implemented=false。
official_stats_generated/ability_calibrated/android_integrated/product_adoption=false。
一般走塁・完全試合・試合終了/延長/勝敗・公式記録・研究/能力・Android採用は別設計。D029/D031/D032はOPEN。

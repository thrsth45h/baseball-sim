# Phase 4b: 塁・アウト・得点の限定参照

完了・裁定済みの合成3プレーを適用した検算。発生確率やリーグ得点率を推定していない。

| 固定列 | outs | 一二三塁の占有 | 半回得点 |
|---|---:|---|---:|
| empty | 0 | 000 | 0 |
| walk_empty | 0 | 100 | 0 |
| walk_only_second | 0 | 110 | 0 |
| walk_only_third | 0 | 101 | 0 |
| walk_first_third | 0 | 111 | 0 |
| walk_second_third | 1 | 111 | 7 |
| walk_loaded | 2 | 111 | 8 |
| out_holds_runners | 1 | 101 | 2 |
| third_out_strands | 3 | 111 | 7 |
| solo_home_run | 2 | 000 | 8 |
| two_run_home_run | 0 | 000 | 2 |
| grand_slam | 1 | 000 | 4 |
| four_walks | 0 | 111 | 1 |
| walks_then_home_run | 0 | 000 | 5 |
| three_outs | 3 | 000 | 0 |
| mixed_half | 3 | 100 | 3 |

72静的遷移・16列/30イベント/46分割位置は試験範囲で、リーグ得点や公式個人成績ではない。
四球は追加進塁なし、Xは打者のみアウト/走者据置き、Hは合法本塁打と全正規触塁/得点の確定が前提。
第三アウト後も残塁/得点を保持して停止し、次半回へ自動リセットしない。
任意の外部開始局面を受けるが、過去履歴の合法性や到達可能性を証明しない。
4a/Statcast/3d・確率・能力・走塁判断・公式成績・Android/UI/セーブへ接続しない。
Pythonは検算用。D029/D031/D032はOPENで、完全な試合生成や製品採用ではない。
source_kind=synthetic_fixture; scope=partial_base_out_rules; probability_model=false。
connected_to_phase4a=false; connected_to_statcast=false; connected_to_phase3d=false。
official_stats_generated=false; ability_calibrated=false; android_integrated=false; product_adoption=false。

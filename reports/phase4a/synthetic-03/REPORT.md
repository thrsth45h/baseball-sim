# Phase 4a: カウントと限定終端の参照実装

裁定済みの合成イベントを順に適用した。発生確率やリーグ成績を推定していない。

| 固定列 | b | s | 受理イベント数n | status |
|---|---:|---:|---:|---|
| empty | 0 | 0 | 0 | active |
| one_ball | 1 | 0 | 1 | active |
| one_called_strike | 0 | 1 | 1 | active |
| four_balls | 4 | 0 | 4 | walk_awarded |
| mixed_walk | 4 | 2 | 7 | walk_awarded |
| called_strikeout | 0 | 3 | 3 | strikeout_out |
| swinging_strikeout | 0 | 3 | 3 | strikeout_out |
| foul_tip_strikeout | 0 | 3 | 3 | strikeout_out |
| fouls_to_two | 0 | 2 | 2 | active |
| two_strike_fouls | 0 | 2 | 5 | active |
| three_bunt_fouls | 0 | 3 | 3 | strikeout_out |
| bunt_foul_after_two | 0 | 3 | 3 | strikeout_out |
| caught_foul_first | 0 | 0 | 1 | caught_foul_out |
| caught_foul_after_two | 0 | 2 | 3 | caught_foul_out |
| fair_full_count | 3 | 2 | 6 | fair_play_pending |
| long_foul_run | 0 | 2 | 34 | active |

96遷移表と16列は試験用fixture。通常ファウル、合法チップ、未捕球バント、捕球飛球を区別する。
未捕球ストライク等は拒否し、未対応をアウトや成功率0に置き換えない。
fair_play_pendingは受渡し停止であり、安打/アウトや規則上の打席未終了を意味しない。
メモリ上の途中処理一致は製品のセーブ形式やDBの採用ではない。
実データ/既存研究/能力/走者/得点/公式成績/UI/セーブへ接続していない。
Pythonはルール検算用。Android言語/DBを選定せずD029/D031/D032はOPEN。
source_kind=synthetic_fixture; scope=partial_count_rules; probability_model=false。
connected_to_statcast=false; connected_to_phase3d=false; official_stats_generated=false。
ability_calibrated=false; android_integrated=false; product_adoption=false。

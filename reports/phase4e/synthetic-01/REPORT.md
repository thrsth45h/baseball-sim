# Phase 4e: 確定済み全員safe安打と明示行先

安打価値と各行先は裁定済みの入力で、予測・裁定した結果ではない。

| 固定列 | stage | away | home |
|---|---|---:|---:|
| single_empty | between_batters | 0 | 0 |
| double_empty | between_batters | 0 | 0 |
| triple_empty | between_batters | 0 | 0 |
| single_first_second | between_batters | 0 | 0 |
| single_first_third | between_batters | 0 | 0 |
| single_second_holds | between_batters | 18 | 13 |
| single_third_holds | between_batters | 0 | 0 |
| single_third_scores | between_batters | 1 | 0 |
| double_first_third | between_batters | 0 | 0 |
| double_first_scores | between_batters | 1 | 0 |
| double_third_holds | between_batters | 0 | 0 |
| loaded_single_one | between_batters | 19 | 13 |
| loaded_single_two | between_batters | 20 | 13 |
| loaded_single_three | between_batters | 21 | 13 |
| loaded_triple_bottom | between_batters | 11 | 23 |
| fair_pending | awaiting_resolution | 0 | 0 |
| chained_hits | between_batters | 1 | 0 |
| hit_retire_switch | between_batters | 19 | 13 |

制御20セル/48受理パターン/18列60action78分割は検査範囲で、リーグ成績やモデル精度ではない。
打者最終塁は外部裁定済み安打価値1/2/3と等しい対象条件。最終塁から公式安打価値を判定しない。
各走者の据置き・複数得点を許し、None不一致・逆走・最終位置衝突/順序逆転を拒否する。途中触塁/追越し等の合法性は証明しない。
fair待ちの新hitだけがcountを消費。outs/回/側/closedを保持し、既存走者の本塁到達数を攻撃側へ加点。次打者Beginは明示。
旧state/4actionはそのまま、旧parser/stepへ委任。旧9sourceのimport/hashと旧I/O/fixture/結果・研究非利用を区別する。
省略実世界事実や古いsnapshot再投入の永続重複排除を保証せず、halfは最後に確定反映したsnapshot。
source_kind=synthetic_fixture; scope=partial_completed_safe_hit_projection; connected_to_phase4a/phase4b/phase4c/phase4d=true。
connected_to_statcast/connected_to_phase3d/probability_model/game_end_rules_implemented=false。
official_stats_generated/ability_calibrated/android_integrated/product_adoption=false。
旧4dの9回以降の空塁切替/終了判定なしを継承し、サヨナラ・勝敗や公式記録を扱わない。D029/D031/D032はOPEN。

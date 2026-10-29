# Phase 4c — 投球と半回の明示接続

[承認PR #25](https://github.com/thrsth45h/baseball-sim/pull/25)・[設計v0.1](../../docs/PHASE4C_DESIGN.md)、承認main `06256f8a4f0f8795709f56365f1ea212e2090092`に基づく合成検算用参照。2026-10-30の本人回答「設計PR #25の範囲で進める（推奨）」で限定接続とworker実装を承認した。D044のみ当該範囲をCONFIRMED、D029/D031/D032はOPEN。source/tests/fixtures/CI固定commitは`a1d2cbc4ae9f949babb2f350be9ed41193f477b5`。

旧4aのCountState/stepと旧4bのHalfInningState/stepへ明示的に委任する。カウント終端だけから走者移動や本塁打を選ばず、旧コード・契約・fixture・テスト・CI・過去結果と旧flagsを変更しない。

## 入力と確定時点

beginからresolveまで、対応終端プレー以外に盗塁・暴投/捕逸・走者アウト・得点・妨害等がないと設定した合成入力だけを扱う。明示された未対応入力は拒否するが、実世界で省略された事実を検出できるものではない。実データからの自動変換はない。

| 不変action | 正確なJSON | 適用する段階 |
|---|---|---|
| `BeginBatter()` | `{"kind":"begin_batter"}` | between_battersだけ。旧CountStateゼロへ開始 |
| `Pitch(event)` | `{"kind":"pitch","event":"called_ball"}`等 | pitchingだけ。旧4a.stepでcountを更新 |
| `Resolve(completed_play)` | `{"kind":"resolve","completed_play":"walk_forced_only"}`等 | awaiting_resolutionだけ。互換を確認して旧4b.stepを1回適用、count=Noneへ消費 |

Pitchは旧8投球、Resolveは旧W/X/Hの正確な文字列だけ。Wは追加進塁なし四球、Xは打者のみアウト/他走者据置き/得点なし、Hは合法フェンス越えフェア本塁打と全正規触塁・得点の確定という強い条件を、外部から明示した場合に限る。追加進塁/アウト・失策・アピール・サヨナラ等はないという契約であり、status単独から得られる事実ではない。

| 待機status | 許可するResolve |
|---|---|
| walk_awarded | W |
| strikeout_out / caught_foul_out | X |
| fair_play_pending | XまたはH |

一般のフェア打球の予測器ではなく、単打等をX/Hへ補完しない。解決後も次打者は明示Beginが必要。三アウトでは残塁/得点を保持し、次半回へ自動リセットしない。

`PitchHalfState(count,half)`は旧CountStateまたはNoneと旧HalfInningStateの厳密な型だけを受ける不変値。half.outs3ならcount=Noneを必須とする。stageはbetween_batters/pitching/awaiting_resolution/half_completeを導出し、独立status欄は作らない。任意の有効な外部snapshotを受けるが、過去履歴や全到達可能性を証明しない。

halfは**最後に確定反映したプレー後のsnapshot**で、現実の球/走者の瞬間位置や実際の時点のアウト数を表すものではない。awaiting_resolutionは接続処理の未反映境界であり、規則上の打席がまだ終わっていないという断定ではない。連続Resolveは拒否するが、古いsnapshotへ純粋関数を再適用する操作や外部二重送信の永続重複排除・exactly-onceは保証しない。

JSONの状態は正確なcount/halfの2キー。旧型への変換をfrom_mappingで明示し、action_from_mappingはkindごとの厳密なキー集合を要求する。fixtures.parse_jsonは重複キー・NaN/Infinityを拒否。stepへ辞書・略号を暗黙変換しない。

replayは厳密なlist/tupleだけを受け、途中stageのまま列が尽きても、その状態を返す。空列は完了halfにも許す。不正tailでは元入力を変えず部分結果を返さない。不変traceの正確なキーはaction/before/after/runs_addedで番号なし。全分割位置で一括と最終状態/trace連結が一致する。製品セーブ形式の採用ではない。

## Fixtureと検証結果

[制御12セル](../../prototypes/phase4c/fixtures/control.json)、[互換12セル](../../prototypes/phase4c/fixtures/compatibility.json)、[固定18列](../../prototypes/phase4c/fixtures/scenarios.json)は、設計から静的に記載した言語非依存JSON。生産関数の出力を正解として生成していない。固定列はID/開始/正確なactionとpayload/最終/stageを設計から別pinし、バント三振投球を見逃し三振へ置換して同じ最終状態になる場合も拒否する。

新4cは**28 test method成功、成功subTest3020を別集計**、failure/error/skip0。代表2,304投球、互換288試行=120受理/168拒否、half完了96拒否、18列/82action/100分割位置は検査範囲で、method数へ足さない。旧4aは別に33 method/326 subTest、旧4bは別に32 method/515 subTestが成功。[新テスト記録](test-results.json)、[旧4a回帰](phase4a-regression.json)、[旧4b回帰](phase4b-regression.json)、[worker検証](VALIDATION.md)を参照。研究575テストは今回の件数へ数えない。

正式[synthetic-01](synthetic-01/REPORT.md)とignoredのsynthetic-repeat-02は、plan.json/traces.json/REPORT.md/SHA256SUMS.jsonの4成果物が全てバイト一致。[計画/同一性](synthetic-01/plan.json)、[全trace](synthetic-01/traces.json)、[再現性](reproducibility.json)、[時点つき保護記録](verification.json)を保存する。人工例の得点/割合はリーグ成績・能力効果・モデル品質ではない。worker自己検証と責任者の独立レビューを区別する。

## 再実行と読取りの境界

Python3.11/3.12の標準ライブラリだけ。ゲームrepo rootから実行し、新しい出力先を選ぶ。

```powershell
python -m unittest discover -s prototypes/phase4c/tests -v
python -m unittest discover -s prototypes/phase4a/tests -v
python -m unittest discover -s prototypes/phase4b/tests -v
python -m prototypes.phase4c run --output data/prototypes/phase4c/my-run-01
python -m prototypes.phase4c run --output data/prototypes/phase4c/my-repeat-02
```

Windows実junction回帰は作成を許可する権限で実行する。Linux CIは実directory symlinkを使い、作成失敗をskip成功にしない。新しい一時領域を作り、既存テスト/実行資料を削除しない。

新出力はcheckoutのdata/prototypes/phase4cまたはreports/phase4cの新しい子だけ。既存先・namespace自体・旧領域・外部・入力重複を拒否し、symlink/junctionで宣言namespaceを再定義させない。固定run以外や外部入力/raw/model/seed/resumeのCLIはない。source/fixture同一性と全期待照合を出力予約前に終える。

| 処理 | 許可する旧資料の利用 |
|---|---|
| 新4c run | shared prototypes initializer、4aのinitializer/model.py、4bのinitializer/model.pyの**5ソース**の明示import/同一性hash。旧fixture・結果・研究データ/学習モデルは計算へ入力しない |
| 旧4a/4b回帰 | 従来の旧fixtureを使う検証を維持し、件数を別集計 |
| 公開verify.py | 旧5ソースの現在/FIXED Git同一性に加え、旧4a/4b静的遷移fixtureのGit blobをtrace検算用に読む。旧ルール関数/研究モデルはimportせず、raw/学習成果物も開かない |

source identityは全新runtime/fixtureと許可旧5ソースのUTF-8をCRLF/CR→LFへ正規化したSHA256、Python版を記録する。主要4成果物に時刻/絶対パスは入れない。正式run/テスト記録はPython3.12.14で、別Python版間のmetadataバイト一致は主張しない。

helperの旧保護は承認baseline `06256f8`→固定source `a1d2cbc`の履歴差分で、README案内以外92パスが実装時点で不変だったことを検査する。現在の全prototypes/依存source/新旧CIは別途固定sourceへ厳密照合し、後続旧文書変更はcurrent_checkout_changes_since_source_commitへ参考情報として分離する。後続文書を保護例外へ追加せず、現在も全旧文書不変とは主張しない。既存根拠JSONを上書きしない。

次はこのcheckoutに保持した正式2runと対応するテスト記録の検算例。別Python版の新runには同じ版・固定sourceでの新しいテスト記録を別ファイルへ用意する。

```powershell
New-Item -ItemType Directory -Path reports/phase4c/new-verification -ErrorAction Stop | Out-Null
python reports/phase4c/verify.py --first reports/phase4c/synthetic-01 --second data/prototypes/phase4c/synthetic-repeat-02 --tests reports/phase4c/test-results.json --regression-a reports/phase4c/phase4a-regression.json --regression-b reports/phase4c/phase4b-regression.json --output reports/phase4c/new-verification
```

## 未接続・未採用

新flagsはsource_kind=synthetic_fixture、scope=partial_pitch_half_orchestration、connected_to_phase4a=true、connected_to_phase4b=true。connected_to_statcast/connected_to_phase3d/probability_model/official_stats_generated/ability_calibrated/android_integrated/product_adoptionは全てfalse。旧4a/4bの凍結flagsは変更しない。一般走塁/単打/併殺/第三アウト得点、公式個人成績、完全試合、研究/能力、Android/UI/DB/セーブと製品採用は別設計である。

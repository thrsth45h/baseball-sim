# Phase 4d — 表裏切替と両チーム得点の限定参照

[承認PR #28](https://github.com/thrsth45h/baseball-sim/pull/28)・[設計v0.1](../../docs/PHASE4D_DESIGN.md)、承認main `b9f9b5830f9247691b4d05b0f81ad8fe84c12f13`に基づくPython標準ライブラリ参照。2026-10-30の本人回答「設計PR #28の範囲で進める（推奨）」で限定半回列制御とworker実装を承認した。D045限定CONFIRMED、D029/D031/D032はOPEN。source/tests/fixtures/CI固定commitは`2fadced5b5da7b8387466641fd063180c5628e17`。

これは**終了判定のない有限の合成半回列controller**。新しいプレーや確率は増やさず、試合の最終得点・勝者を判定しない。9回表でホームがリードしていてもAdvanceを受け、10回以降も空塁へ切替えるが、実際の試合で次半回を行うべきという判断やMLB延長走者配置ではない。架空リーグ規約も採用しない。

## 状態と明示切替

不変な`HalfSequenceState(inning,side,closed_away_runs,closed_home_runs,current)`を使う。inningは厳密intの1以上、sideは厳密strのtop/bottom、closed累計は厳密な非負int、currentは旧4cの厳密PitchHalfState。bool/float/subclass/暗黙変換を拒否する。任意の構造上有効な外部snapshotを受けるが、回数/過去得点の歴史的合法性は証明しない。

保存するclosed累計には現在の半回を含めない。導出scoreはtopでaway=closedAway+current.half.runs/home=closedHome、bottomでは逆。`.score`は新しいaway/home辞書、`.batting_team`はaway/home、`.stage`はcurrent.stageを返す。JSONのstateは正確な5保存キーだけで、導出score/stageを重複保存しない。

旧BeginBatter/Pitch/Resolve型はそのままaliasし、旧kindのJSON変換も旧4c parserへ委任する。旧3actionのstepは旧4c.stepをちょうど1回呼んでcurrentだけ置換し、回数/側/closed累計を保持する。旧count/走者規則を新接続層へコピーしない。

新しい不変`AdvanceHalf()`のJSONは正確に`{"kind":"advance_half"}`。half_completeだけで、現在half得点を攻撃側のclosed累計へ移し、top n→bottom n / bottom n→top n+1へ切替え、新しいcurrentをcount=None/outs0/空塁/runs0へする。**表示scoreはAdvance前後で不変**。旧snapshotの残塁/得点は保ち、新snapshotだけをリセットする。次打者Beginは自動挿入しない。

第三ストライクの結果待ちだけでは切替不可。明示Resolveでhalf_completeになってからAdvanceを受ける。返却された次半回への連続Advanceは拒否するが、同じ古い完了snapshotへの純粋関数再適用は同じ値を返す。通信再送/古いcheckpoint再投入/並行更新の永続重複排除・exactly-onceは保証しない。

replayは厳密list/tupleだけを受け、空列は全stageに許す。途中/結果待ち/半回完了で列が尽きてもそのまま返し、不正tailでは元入力を変えず部分成功を公開しない。不変traceの正確な5キーはaction/before/after/away_runs_added/home_runs_added。差分は導出scoreのafter−beforeで、Advance/Begin/Pitchは両方0、受理Resolveだけ攻撃側へ増分を持つ。旧4c traceを変更せず、新traceにも番号を付けない。

4cの強い合成前提を引継ぎ、begin〜resolveに未対応の盗塁/暴投/走者アウト/得点等はない。W/X/Hの正規触塁・裁定と追加条件は外部で完了済みという入力で、statusだけから補完しない。halfは最後に確定反映したsnapshotであり、一般の野球時刻状態ではない。省略された実世界事実も検出できない。

## Fixtureと検証

[制御16セル](../../prototypes/phase4d/fixtures/control.json)と[固定16列](../../prototypes/phase4d/fixtures/scenarios.json)は設計から静的記載した言語非依存JSON。生産stepの出力から正解を作らず、ID/明示開始/展開した正確なactionとpayload/最終/score/stageを別pinした。top_switchのC×3をM×3へ置換して最終状態が同じになる場合も、出力作成前に拒否する。

新4dは**26 test method成功、成功subTest320は別集計**、failure/error/skip0。96代表Advance、16列/100action/116分割、旧12具体actionの半回完了拒否、実junction4ケースは検査範囲で、method数へ加算しない。旧4cは別に28/3020、旧4aは別に33/326、旧4bは別に32/515が成功。[新テスト](test-results.json)、[旧4c回帰](phase4c-regression.json)、[旧4a回帰](phase4a-regression.json)、[旧4b回帰](phase4b-regression.json)、[worker検証](VALIDATION.md)を参照。研究575テストは今回の件数へ数えない。

正式[synthetic-01](synthetic-01/REPORT.md)とignored synthetic-repeat-02は、plan.json/traces.json/REPORT.md/SHA256SUMS.jsonの4成果物が全てバイト一致。[計画/同一性](synthetic-01/plan.json)、[全trace](synthetic-01/traces.json)、[再現性](reproducibility.json)、[時点つき保護記録](verification.json)を保存する。人工得点や列の割合はリーグ成績・能力効果・モデル品質ではない。worker自己検証と責任者の独立レビューは別工程である。

## 再実行・読取りと旧保護

Python3.11/3.12の標準ライブラリだけ。ゲームrepo rootで、新しい出力先を選ぶ。

```powershell
python -m unittest discover -s prototypes/phase4d/tests -v
python -m unittest discover -s prototypes/phase4c/tests -v
python -m unittest discover -s prototypes/phase4a/tests -v
python -m unittest discover -s prototypes/phase4b/tests -v
python -m prototypes.phase4d run --output data/prototypes/phase4d/my-run-01
python -m prototypes.phase4d run --output data/prototypes/phase4d/my-repeat-02
```

実junction試験はWindowsで作成を許可する権限を使い、Linux CIでは実directory symlinkを使う。権限不足をskip成功にしない。既存出力/履歴は削除せず、新しい一時領域を作る。CLIは固定runだけで外部/raw/model/seed/resumeは受けない。出力はこのcheckoutのdata/prototypes/phase4dまたはreports/phase4dの新しい子だけ。既存先・namespace自体・旧領域・外部・入力重複とnamespace全体/子junction迂回を拒否する。全固定期待とsource同一性を出力予約前に照合する。

新runで許可する旧sourceはshared initializerと4a/b/cそれぞれのinitializer/model.pyの**7パス**。型/stepの明示importと同一性hashで使い、旧I/O/runner/fixture/結果・研究モデルはimport/入力にしない。旧suiteは従来fixtureを回帰検証に使い別集計する。公開helperは別の検算として、固定source commitの旧4a/4b静的transitions.jsonと旧4c compatibility.jsonのGit blobも読む。旧ルール関数はhelperへimportせず、raw/研究/学習成果物を開かない。この検証読取りと新runを混同しない。

同一性には全新runtime/fixtures/属性と旧7source、UTF-8のCRLF/CR→LF正規化SHA256・Python版を記録する。主要4成果物に時刻/絶対パスなし。正式run/テストはPython3.12.14で、別Python版間のmetadata一致は主張しない。

helperの歴史的旧保護は承認baseline `b9f9b58`→固定source `2fadced`のGit差分で、README案内以外122パスが実装時点で不変だったことを検査する。現在の全prototypes/依存/tests/新旧CIは別途固定sourceへ厳密照合し、後続旧文書変更はcurrent_checkout_changes_since_source_commitへ参考情報として分離する。後続文書を例外へ追加せず、現在も全旧文書不変とは記録しない。根拠JSONを上書きしない。

このcheckoutに保持した正式2runと対応する4suite記録の検算例は次のとおり。別Python版で新規runする場合は、同じ版・固定sourceのテスト記録を新しいファイルへ用意する。

```powershell
New-Item -ItemType Directory -Path reports/phase4d/new-verification -ErrorAction Stop | Out-Null
python reports/phase4d/verify.py --first reports/phase4d/synthetic-01 --second data/prototypes/phase4d/synthetic-repeat-02 --tests reports/phase4d/test-results.json --regression-c reports/phase4d/phase4c-regression.json --regression-a reports/phase4d/phase4a-regression.json --regression-b reports/phase4d/phase4b-regression.json --output reports/phase4d/new-verification
```

新flagsはsource_kind=synthetic_fixture、scope=partial_half_sequence_controller、connected_to_phase4a/phase4b/phase4c=true。他のconnected_to_statcast/connected_to_phase3d/probability_model/game_end_rules_implemented/official_stats_generated/ability_calibrated/android_integrated/product_adoptionはfalseで旧flagsは凍結する。一般走塁/試合終了/延長/サヨナラ/勝敗、公式記録、研究/能力、Android/DB/UI/セーブ・製品採用は別設計。

# Phase 4e — 裁定済み全員safe安打と明示行先

[設計PR #31](https://github.com/thrsth45h/baseball-sim/pull/31)・[設計v0.1](../../docs/PHASE4E_DESIGN.md)、承認main `e1af536153c36ab34be8199a3707ff0c1c9ba0fb`に基づくPython標準ライブラリ参照。本人の範囲承認と設計マージは2026-10-30、今回の実装検証は2026-10-31日本時間。D046は限定CONFIRMED、D029/D031/D032はOPEN。source/tests/fixtures/CI固定commitは`88d239ce33c8330402fe17d8a9f3ee4dab9c34e8`。

## 入力する裁定と更新

新しい不変`ResolveSafeHit(hit_bases,runner_destinations)`を旧4dのfair待ちへ明示する。hit_basesは厳密intの1/2/3という**外部裁定済み安打価値**で、今回対象の打者最終塁でもある。最終塁から公式安打価値を判定する機能ではない。打者の送球間/失策等の追加進塁はなく、安打で獲得した塁へ留まる条件に限る。

runner_destinationsはプレー前の一/二/三塁スロットに対応する厳密tuple3要素。Noneは当初空塁、厳密int1…3は最終塁、4は本塁。constructorは形/型/範囲だけを検査し、局面に依存する検査はstepで行う。JSONは正確に`{"kind":"resolve_safe_hit","hit_bases":1,"runner_destinations":[2,null,4]}`の3キーで、parserが厳密listからtupleへ明示変換する。

stageがawaiting_resolutionかつcount.statusがfair_play_pendingの場合だけ受ける。占有とNoneの正確対応、当初塁より後ろへ戻らないこと、打者→当初一塁→二塁→三塁の存在する人の最終行先が増加することを確認する。本塁4同士だけ同率を許す。据置き・複数得点を許し、架空走者/省略、逆走、非本塁の衝突と最終順序逆転を拒否する。

検査後は打者をhit_basesへ置き、各既存走者を指定先へ移す。既存走者の本塁到達数0…3だけhalf.runsへ加え、outs/回/側/closed累計は保持、count=Noneへ消費する。打者は本塁を行先にしない。加点は攻撃側だけへ帰属し、次打者Beginは引続き明示する。返却stateへの連続hitは拒否するが、同じ古いfair待ちsnapshotへの純粋再適用は同じ値を返す。永続重複排除・exactly-onceは保証しない。

全員が正規触塁してsafe、得点も裁定済み、途中の追越し/アウト/失策/妨害/アピール/サヨナラなしという強い入力条件を置く。begin〜resolveの未対応の盗塁/暴投/走者アウト/得点等なしも継承する。最終位置だけでは途中履歴や触塁を証明できず、省略された実世界事実や偽った裁定を検出できない。安打/走塁の確率、守備/打球生成、一般裁定器や実データ変換は作っていない。

旧HalfSequenceStateと旧BeginBatter/Pitch/Resolve/AdvanceHalfは厳密なalias。旧parser/stepへ旧actionを各1回委任し、新hitで旧stepは呼ばない。旧保存5欄・導出score/stage/batting_teamと旧ルールは変更しない。新TraceEntry/ReplayResultを別に作り、不変traceは正確なaction/before/after/away_runs_added/home_runs_addedの5キー、番号なし。差分は導出scoreのafter−beforeで非負厳密int。replayは厳密list/tuple、空列全stageと途中状態を保ち、不正tailでは元state/列を変えず部分結果を公開しない。

## 固定表と検証

[制御20セル](../../prototypes/phase4e/fixtures/control.json)、[48受理パターン](../../prototypes/phase4e/fixtures/patterns.json)、[固定18列](../../prototypes/phase4e/fixtures/scenarios.json)は言語非依存JSON。48表は設計の明示行先候補から、生産stepを呼ばず記載した。18列は承認設計表から静的転記し、loaderへ別にID/開始/正確actionと全payload/最終/score/stageをpin。hit_retire_switchの旧O→P・RX維持で最終が同じになる置換も、出力作成前に拒否する。

新4eは**24 test method成功、成功subTest18,112は別集計**、failure/error/skip0。型正payload375×占有8×side2×outs3の18,000試行は288受理/17,712拒否、基礎3,000組は48受理/2,952拒否。有限の契約列挙であり、安打/進塁の頻度ではない。18列/60action/78分割や実junction4ケースもmethod件数へ加算しない。旧4d26/320、旧4c28/3020、旧4a33/326、旧4b32/515は各別集計で成功。研究575テストは今回へ数えない。

[新テスト記録](test-results.json)、[旧4d回帰](phase4d-regression.json)、[旧4c回帰](phase4c-regression.json)、[旧4a回帰](phase4a-regression.json)、[旧4b回帰](phase4b-regression.json)、[worker検証](VALIDATION.md)を参照。worker自己検証は責任者の独立レビューとは区別する。

正式[synthetic-01](synthetic-01/REPORT.md)とignored synthetic-repeat-02のplan.json/traces.json/REPORT.md/SHA256SUMS.jsonは全てバイト一致。[計画/同一性](synthetic-01/plan.json)、[全trace](synthetic-01/traces.json)、[再現性](reproducibility.json)、[時点つき保護記録](verification.json)を保存する。合成得点や受理割合をリーグ成績・能力効果・モデル品質と解釈しない。

## 再実行と読取り境界

Python3.11/3.12の標準ライブラリだけ。ゲームrepo rootで新しい出力先を選ぶ。

```powershell
python -m unittest discover -s prototypes/phase4e/tests -v
python -m unittest discover -s prototypes/phase4d/tests -v
python -m unittest discover -s prototypes/phase4c/tests -v
python -m unittest discover -s prototypes/phase4a/tests -v
python -m unittest discover -s prototypes/phase4b/tests -v
python -m prototypes.phase4e run --output data/prototypes/phase4e/my-run-01
python -m prototypes.phase4e run --output data/prototypes/phase4e/my-repeat-02
```

Windows実junction試験は作成を許可する権限で実行し、Linux CIでは実directory symlinkを使う。権限不足をskip成功にしない。過去のテスト/出力資料を削除せず、新しい一時領域を使う。CLIは固定runのみで外部/raw/model/seed/resumeなし。出力はcheckoutのdata/prototypes/phase4eまたはreports/phase4eの新しい子だけ。既存先/namespace自体/旧領域/外部/入力重複とnamespace全体/子junction迂回を拒否する。全入力・固定期待・identityを出力予約前に照合する。

新runが使う旧sourceはshared initializerと4a/b/c/d各initializer/model.pyの**9パス**。旧型/parser/stepの明示importと同一性hashにだけ使い、旧I/O/runner/fixture/結果・研究/学習モデルは新計算へ入力しない。旧suiteは従来fixtureを回帰に使う別処理。公開helperも別の検算として、固定coreの旧4a/b静的遷移と旧4c互換Git blob、新4e48表を読み、120trace/6run hashを再照合する。helperへ旧ルール関数や研究モデルはimportせず、raw/学習成果物も開かない。

identityは全新runtime/fixtures/属性と旧9sourceのUTF-8をCRLF/CR→LFへ正規化したSHA256、Python版を記録する。主要4成果物に時刻/絶対パスは入れない。正式run/テストはPython3.12.14で、異なる版のmetadataバイト一致は主張しない。検証日10/31は補助記録に置き、主要4成果物の時刻非依存を維持する。

helperの歴史的旧保護は承認baseline `e1af536`→固定source `88d239c`のGit差分で、README案内以外152パスが実装時点で不変だったことを検査する。現在の全prototypes/依存/tests/新旧CIは別途固定sourceへ厳密照合し、後続旧文書の変更はcurrent_checkout_changes_since_source_commitへ参考情報として分離する。後続文書を例外へ追加せず、現在も全旧文書不変とは記録しない。根拠JSONは上書きしない。

このcheckoutに保持した正式2runと5suite記録の検算例は次のとおり。別Python版の新runには同じ版・固定sourceの新しいテスト記録を別ファイルへ用意する。

```powershell
New-Item -ItemType Directory -Path reports/phase4e/new-verification -ErrorAction Stop | Out-Null
python reports/phase4e/verify.py --first reports/phase4e/synthetic-01 --second data/prototypes/phase4e/synthetic-repeat-02 --tests reports/phase4e/test-results.json --regression-d reports/phase4e/phase4d-regression.json --regression-c reports/phase4e/phase4c-regression.json --regression-a reports/phase4e/phase4a-regression.json --regression-b reports/phase4e/phase4b-regression.json --output reports/phase4e/new-verification
```

source_kind=synthetic_fixture、scope=partial_completed_safe_hit_projection、connected_to_phase4a/phase4b/phase4c/phase4d=true。他のconnected_to_statcast/connected_to_phase3d/probability_model/game_end_rules_implemented/official_stats_generated/ability_calibrated/android_integrated/product_adoptionはfalse、旧flagsは凍結。9回以降の終了判定なし/空塁切替を旧4dから継承するが、実試合で次半回を行うという裁定ではない。サヨナラ/勝敗/公式記録・一般走塁・研究/能力・Android/UI/DB/セーブ採用は別設計。

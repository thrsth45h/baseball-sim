# Phase 4a — カウントと限定終端のルール参照

[承認設計PR #19](https://github.com/thrsth45h/baseball-sim/pull/19)、ゲームmain `1c5b738671a452305a4be874c57f77cd8ad5db10`の[設計v0.1](https://github.com/thrsth45h/baseball-sim/blob/1c5b738671a452305a4be874c57f77cd8ad5db10/docs/PHASE4A_DESIGN.md)を実装した。本人は2026-10-29に「設計PR #19の範囲で進める（推奨）」と回答した。D042は限定参照範囲のみCONFIRMED、D029/D031/D032はOPEN。source/tests/fixtures/CI固定commitは`922a2a9c1cb5a76e9d984fc5bd5c9fef2952b7ae`。

これは**裁定済みの合成イベントをカウントへ適用する検算用Python参照実装**。完全な1打席/試合の生成器、Androidの採用言語/アーキテクチャ/DB、製品セーブではない。Statcast研究や3dの到達機会をイベントへ変換せず、確率・能力・走者/得点・公式成績・UIへ接続しない。

## 入力と出力の意味

`CountState(balls,strikes,pitches_seen,status)`は不変値。startは(0,0,0,active)。8イベントは設計で指定した通常ボール、捕球済み見逃し/空振りストライク、合法ファウルチップ、通常未捕球ファウル、未捕球ファウルバント、捕球ファウル飛球、フェア打球の受渡し。曖昧なstrike/foul・未捕球ストライク・死球・妨害・自動判定等は拒否し、未対応をアウトや成功率0にしない。

通常の二ストライク後のファウルは継続、チップ/未捕球バントは第三ストライク、捕球ファウル飛球は三振と区別した終端になる。`fair_play_pending`はカウンタの受渡し停止で、安打/アウトや規則上の打席未終了を表すものではない。

受理したイベントごとにnを1増やし、traceの順番号は入力直前のn+1。nは今回の部分集合で受理したイベント数であり、公式投球数ではない。途中のメモリ上の状態から処理しても全traceが連結一致する。次打者/得点/アウト数へ自動遷移せず、四球/三振後も自動リセットしない。

## Fixtureと検証

[静的96遷移表](../../prototypes/phase4a/fixtures/transitions.json)と[固定16列](../../prototypes/phase4a/fixtures/scenarios.json)は言語非依存JSON。設計§4の列と§6の列から記載し、生産step/replayを呼んだ結果をそのまま正解として採用していない。worker自身の別の設計列式による照合を含み、責任者の独立期待値検査は別工程。

unittestは**33 test method成功**、fail/error/skip0。成功subTestは別集計326で、33へ加算しない。96遷移・16固定列・全95分割位置・実junction4ケースも検査範囲として別記した。[検証記録](VALIDATION.md)、[構造化テスト結果](test-results.json)を参照。研究repoの575テストを今回のルール検証件数に数えていない。

責任者レビューで、初版loaderが同じ最終カウントになるイベント置換を拒否しないP2を指摘された。正規の16列自体やカウント計算は当初から設計と一致していたが、固定入力の将来のdriftを検出する防護が不足していた。設計から独立に記載した16イベント列をloaderでpinし、同一最終状態になる5置換/順序変更をloaderと実JSONの両方で拒否する回帰を追加。訂正前公開12ファイル一式と旧実行結果はignoredの`data/prototypes/phase4a/review-history/p2-01/`等に保持した。

正式[synthetic-03](synthetic-03/REPORT.md)と非公開repeat-04のplan/traces/REPORT/SHA索引の4成果物が全てバイト一致。[全trace](synthetic-03/traces.json)、[計画/ソース同一性](synthetic-03/plan.json)、[再現性](reproducibility.json)、[既存保護](verification.json)を参照。固定列の割合をK%/BB%/打率やモデル品質として解釈しない。

## 再実行

Python3.11または3.12の標準ライブラリだけを使う。ゲームrepo rootから実行し、既存出力を再利用しない。

```powershell
python -m unittest discover -s prototypes/phase4a/tests -v
python -m prototypes.phase4a run --output data/prototypes/phase4a/my-run-01
python -m prototypes.phase4a run --output data/prototypes/phase4a/my-repeat-04
```

Windowsの実junction回帰は、実行環境がjunctionの作成を許可する権限で実行する。制限により作成が拒否されるとテストは失敗し、skipで成功に置き換えない。Linux CIでは実directory symlinkを使う。テストは新しい専用一時領域を作り、過去のテスト資料や既存データを削除しない。

run以外・raw-root/model/seed/resume/外部入力のCLIはない。新出力はこのcheckoutのdata/prototypes/phase4aかreports/phase4aの子だけ。既存先・旧namespace・checkout外・入力重複を拒否する。許可位置自体をresolveで転送先へ変えず、symlink/junctionからの迂回も拒否する。

source identityは全新runtime source・fixture・package initializerとPython版を記録。UTF-8テキストのCRLF/CRをLFへ正規化してSHA256を計算する。実行時刻/絶対パスは主要成果物に入れない。同じPython環境の2runのバイト一致を確認し、3.11/3.12間のPython版metadataのバイト一致を主張しない。

`verify.py`は新runを実行せず、2run・静的表に沿うtrace・固定source Git blob・旧ゲームファイル保護・構造化unittest結果を照合する。研究repoやraw/モデルは開かない。既存の検証JSONを上書きせず、出力先の新しいフォルダを選ぶ。

```powershell
New-Item -ItemType Directory -Path reports/phase4a/new-verification -ErrorAction Stop | Out-Null
python reports/phase4a/verify.py --first reports/phase4a/synthetic-03 --second data/prototypes/phase4a/synthetic-repeat-04 --tests data/prototypes/phase4a/tests-fix-02.json --output reports/phase4a/new-verification
```

## 未採用

`source_kind=synthetic_fixture`、`scope=partial_count_rules`、`probability_model=false`、`connected_to_statcast=false`、`connected_to_phase3d=false`、`official_stats_generated=false`、`ability_calibrated=false`、`android_integrated=false`、`product_adoption=false`。未対応規則を廃止したものではない。架空リーグ全規約・既存研究接続・公式成績・能力・UI/セーブ・Android技術・製品採用は別判断とする。

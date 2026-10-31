# 1試合を動かす統合試作（Phase 4f）

ゲームrepoのrootで次を実行すると、仮の2チーム各9人が1球ずつ進行し、試合終了・スコア・終了理由を読める。Python3.11/3.12の標準ライブラリだけを使う。出力先には新しい名前を選ぶ。

```powershell
python -m prototypes.phase4f run --seed 42 --output data/prototypes/phase4f/my-game-01
```

公開例の[REPORT](game-42-01/REPORT.md)は **仮Away 4–2 仮Home、259球、9回裏三アウトでビジター勝利**。完了打席はAway44/Home38。[イニング別・打者別の結果](game-42-01/result.json)、[1球/actionと完了打席のログ](game-42-01/events.json)、[仮設定・乱数・同一性](game-42-01/plan.json)を保存している。

全員共通の未較正な整数重みtoy-v1と、一定進塁の仮設定で動く。MLBの成績を再現したモデル、能力1〜20の効果、投球真値→知覚→接触→打球物理の実装ではない。研究モデル・Android画面・12球団日程・経営・製品セーブには接続していない。今回の目的は、交換可能な仮入力から1試合の進行と終了をつなぐことである。

## 終了と表示の範囲

1〜8回の三アウトは表裏/次回へ進み、打順は半回をまたいで持ち越す。9回表終了時ホーム先行なら裏を省略し、9回裏の勝越しはサヨナラで終了する。非HRは勝越しに必要な走者だけ、正規触塁済みフェンス越えHRは全員分を加点する。9回裏三アウトでビジター先行ならビジター勝利、同点なら試作用引分で、延長は行わない。この引分条件は製品リーグ規約の採用ではない。

サヨナラの結果解決は旧Resolve/ResolveSafeHitへ渡す前に専用終端へ分岐する。結果の`last_projected_sequence`は**終端プレー解決直前の旧snapshot**で、結果待ちcountと当初塁配置を残す。最終scoreはこの投影＋専用終端増分で一度だけ計上し、終端後の旧count/塁配置を作らない。safe候補2/3塁分を公式二塁打/三塁打として裁定しない。

イニングの`null`/表示`—`は未実行、0は実行した無得点。サヨナラ最終halfは途中終了と表示する。チーム/打者の表示は完了打席・safe到達打球（HR含む）・四球・三振・HRの**イベント件数**で、公式AB/RBI/打率/長打価値/投手成績やSavant指標ではない。fair候補には全員safe・正規触塁等の強い合成条件があり、一般の走塁・失策・併殺・妨害/アピールは扱わない。

投球上限（既定10,000）、入力不足/不正は`aborted`で勝敗/引分を付けず、部分scoreと受理済みログを保存する。fair候補が不足/不正なら、その球のPitch/投球数/完了打席を受理しない。既に発行したBeginは保持する。上限ちょうどの球で正常終了したときはcompletedを優先する。CLI終了コードは正常完了0、未完了2、利用エラー非0。

## 実測と再現性

固定source/tests/profile/fixtures/CIは`a5fda901bc0a8cca9ae0eefc1d076fd8171c73a6`、設計正本は[PR #34](https://github.com/thrsth45h/baseball-sim/pull/34)・[v0.1](../../docs/PHASE4F_DESIGN.md)、承認main`409bbf6d40ee04a52b6e91546892a532d7c3d2d2`。2026-10-31日本時間、Windows/Python3.12.14で実測した。

| seed | Away–Home | 受理球数 | 完了打席 Away/Home | CLI秒 | 5成果物bytes |
|---:|---:|---:|---:|---:|---:|
| 42 | 4–2 | 259 | 44/38 | 0.1903 | 677,945 |
| 0 | 0–3 | 211 | 30/36 | 0.1924 | 556,735 |
| 1 | 4–3 | 280 | 39/44 | 0.2111 | 712,509 |
| 2 | 4–3 | 256 | 43/38 | 0.2117 | 670,074 |
| 2026 | 7–2 | 285 | 44/38 | 0.1943 | 717,219 |

seed0は9回裏省略、他は9回裏三アウトで終了した。[実測記録](run-summary.json)は各1回のローカル値で、CLIプロセス起動時間を含む。速度保証や成績の較正に使わない。seed42の公開runと新しいignored repeatは**主要5成果物すべてバイト一致**。[再現性](reproducibility.json)にhashを保存した。各試合に専用Random(seed)を1個持ち、投球1draw/fair時だけ追加1draw。seed42は259+57=316draw。時刻/絶対パスを主要5成果物へ入れず、Python版をidentityへ記録する。同じ版での一致であり、別言語/将来Python版のRNG互換を保証しない。

## テスト・検証の再実行

新4fは**23 test method、成功subTest13は別集計**、旧4e24/18112、4d26/320、4c28/3020、4a33/326、4b32/515を別コマンド/別記録で実行し、すべてfailure/error/skip0だった。研究575テストを今回へ加算していない。[worker検証](VALIDATION.md)と[新テスト記録](test-results.json)を参照。workerの自己検証を責任者の独立レビューと呼ばない。

```powershell
python -m unittest discover -s prototypes/phase4f/tests -v
python -m unittest discover -s prototypes/phase4e/tests -v
python -m unittest discover -s prototypes/phase4d/tests -v
python -m unittest discover -s prototypes/phase4c/tests -v
python -m unittest discover -s prototypes/phase4a/tests -v
python -m unittest discover -s prototypes/phase4b/tests -v
python -m prototypes.phase4f run --seed 42 --output data/prototypes/phase4f/my-repeat-02
```

専用CIはPython3.11/3.12で6suiteを別step実行する。Windowsの実junction試験には作成権限が必要で、Linuxでは実directory symlinkを使う。権限不足をskip成功にしない。既存の出力/テスト領域を削除・再利用しない。出力は固定namespace`data/prototypes/phase4f`または`reports/phase4f`の新しい子だけで、旧領域/外部/既存先/入力重複・namespace全体/子junction迂回を拒否する。

`reports/phase4f/run_tests.py --phase phase4f --output data/prototypes/phase4f/new-tests/phase4f.json`でmethod/subTestを分けた新記録を作れる。旧5suiteは`--phase`と出力名をそれぞれ変えて**別コマンド**で実行する。helper例では、このcheckoutに保持した固定sourceの6記録を指定する。

```powershell
python reports/phase4f/verify.py --first reports/phase4f/game-42-01 --second data/prototypes/phase4f/game-42-repeat-02 --tests reports/phase4f/test-results.json --regression-e reports/phase4f/phase4e-regression.json --regression-d reports/phase4f/phase4d-regression.json --regression-c reports/phase4f/phase4c-regression.json --regression-a reports/phase4f/phase4a-regression.json --regression-b reports/phase4f/phase4b-regression.json --output reports/phase4f/new-verification
```

## 読取り・保護と未採用範囲

新runが使う旧sourceはshared initializer＋4a〜4e各initializer/modelの11パスだけで、型/旧parser/stepと同一性hashに使う。旧I/O/runner/fixtures/結果・研究/raw/学習モデルを新runへ入力しない。旧回帰suiteのfixture使用は別処理。新runtime/profile/fixtures/属性と旧11sourceをUTF-8・CRLF/CR→LF正規化SHA256で結合する。

公開helperは旧ルール関数をimportせず、旧静的fixtureも読まない。旧sourceは固定core/currentの同一性hashにだけ使い、新toy profileの固定Git blob、主要5成果物hash、ログから別に数えた得点/打順/イベント件数と所有RNGのdraw/閾値を照合する。この集計は全野球遷移の独立oracleではなく、責任者による別worktree実行/独立照合は別工程である。[保護記録](verification.json)の歴史的保護はbaseline`409bbf6`→固定core`a5fda90`でREADME案内以外184パス不変。現在の全prototypes/依存/tests/CIを固定coreへ厳密照合し、以後の旧文書変更は参考欄へ分離する。現在も旧全文書不変と誤記しない。

source_kind=synthetic_toy_policy、probability_model=true（未検証toy weights）、game_end_rules_implemented=true（限定終了条件）、connected_to_phase4a〜e=true。上流/知覚/物理/研究モデル/MLB較正/能力較正/公式成績/Savant/Android/製品採用はfalse。旧flagsは変更せず、D029/D030/D031/D032はOPENのまま保持する。

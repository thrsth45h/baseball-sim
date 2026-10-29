# Phase 4a — カウントと限定された打席終端の参照実装

- 版: `phase4a-design-v0.1`、2026-10-29 日本時間。
- 状態: **Proposed / 未承認**。今回の「次へ」は案の検討指示として扱う。
- 基準: ゲームmain `110526fa500ecfeddc058241bc7da46d5648bb66`、研究main `cd8217a44788cf830c5554b71b04a27a98ce620c`。
- 関連: D004、D022–D023、D029/D031/D032 OPEN、D042 OPEN、[ADR-0008](adr/0008-phase4a-count-reference.md)、[設計点検](PHASE4A_REVIEW.md)。
- 設計PR: [ゲームPR #19](https://github.com/thrsth45h/baseball-sim/pull/19)（Draft・未マージ）。承認・設計マージまでworkerの実装を開始しない。

## 1. 次に作るものと、今回作らないもの

**合成の裁定済み投球イベントを1個ずつ受け、ボール/ストライク数と、継続・限定終端・後続処理への受渡しを更新する。** ゲーム側のルール契約を実行して点検できる小さな参照実装を提案する。

入力は試験用に決めたイベント列。投球/スイング/打球/捕球の発生確率を作らず、StatcastタグやPhase 3dの到達機会を裁定済みイベントへ変換しない。既存モデルはつながず、能力値・EV/LA・守備・走者/得点/イニング・公式成績・UI・DB/セーブも含めない。結果を先に抽選してEV等を後付けする生成器でもない。

完全な1打席エンジンや、完成ゲームの採用実装とは呼ばない。既存研究はそのまま保持する。参照するのは下記のMLB規則の部分集合であり、架空リーグの全規約をMLBと同じにする判断ではない。

## 2. この工程を推奨する理由・代替案

3dまでに検証したのは真の人工軌道を既知とする幾何的存在で、実際の捕球確率/守備判断ではない。一方、投球結果からカウントを進める処理は、将来どの確率モデルを選んでも必要になる。

| 案 | 利点・反論と判断 |
|---|---|
| 幾何的到達を仮の捕球確率へ変える | 前進はするが、未較正の係数が結果を支配する。今回は保留 |
| 守備者の不完全な観測・経路選択を作る | 完全情報の限界を検討できるが、観測/意思決定/身体運動の仮定が増える。別設計 |
| 旧3bと3dを直ちにつなぐ | 選択された打球母集団・方向・支持範囲の課題が残る。接続処理で黙って埋めない |
| 限定ルールを合成イベントで検証する | 未較正の確率を増やさず、試合処理の土台を具体化できる。今回の推奨 |

## 3. 規則の出典と範囲

[MLB公式の規則案内](https://www.mlb.com/glossary/rules)からリンクされる[2026 Official Baseball Rules](https://mktg.mlbstatic.com/mlb/official-information/2026-official-baseball-rules.pdf)を確認した。適用箇所は5.04(c)、5.05(a)(1–2)/5.05(b)(1)、5.09(a)(1–4)、9.15(a)(4)、STRIKE/FOUL TIPの定義。下表は独自の入力/出力契約として整理したもの。規則PDFや本文の長文をリポジトリへ複製しない。

重要な反例は、未捕球の第三ストライクでは打者が常にアウトになるわけではないこと。通常の未捕球ファウル・合法捕球したファウルチップ・未捕球ファウルバント・捕球されたファウル飛球も区別が必要。この参照実装で事実を推測せず、必要な事実が確定した入力だけを扱う。

## 4. 受ける8イベント

1入力は、規則に沿う投球1球について裁定済みの事実を表す。妨害・判定取消等はないという試験契約。下記の文字列を正確に使い、曖昧な`strike`/`foul`を補完しない。

| 略号 | event | active状態からの更新 |
|---|---|---|
| B | `called_ball` | 打者接触のない通常のボール判定。b<3はb+1で継続。b=3はb=4、`walk_awarded` |
| C | `called_strike_caught` | 合法捕球済み。s<2はs+1で継続、s=2はs=3、`strikeout_out` |
| M | `swinging_strike_caught` | 合法捕球済み。Cと同じカウント更新 |
| T | `foul_tip_caught` | 規則上のファウルチップとして合法捕球済み。Cと同じ更新 |
| F | `ordinary_foul_uncaught` | バント/捕球飛球ではない。s<2はs+1、s=2は不変で継続 |
| U | `bunt_foul_uncaught` | 未捕球のファウルバント。s<2はs+1、s=2はs=3、`strikeout_out` |
| O | `foul_fly_caught` | ファウルチップを除く合法捕球済みのファウル飛球。b/sを保ち`caught_foul_out`。バントでもこちらで扱う |
| P | `fair_ball_handoff` | フェアが確定した打球。b/sを保ち`fair_play_pending`、後続へ受渡し停止 |

受理した全イベントでnを1増やす。nはこの参照実装が受理した投球イベント数で、リーグ全体の公式投球数ではない。

Pから安打/アウト/本塁打を決めず、`fair_play_pending`はこのカウンタの受渡し境界を示す。「規則上、打席がまだ終わっていない」と断定するラベルではない。Oを二ストライク後のバント失敗による三振へ変換しない。TとFも同じタグにしない。

未捕球のストライク（第三以外も含む）、捕球不明のストライク/ファウル、死球、妨害、故意四球、投球時計/自動ボール・ストライク、判定取消・再試合、盗塁/走者プレー等は**未対応として拒否**する。例外規則を廃止したり、成功率0と定義したりする意味ではない。Statcastの`description/events`からこの型への自動マッピングも作らない。

## 5. 状態・純粋関数の契約

`CountState`は`balls=b`、`strikes=s`、`pitches_seen=n`、`status`の4項目を持つ不変値。startは(0,0,0,active)。statusは下表の5つのみ。整数はbool/小数/文字列を拒否し、status/eventも未知値・余分な項目を拒否する。明示的な型/境界検査は最適化で消えるassertに頼らない。

| status | b/s | nの整合条件 |
|---|---|---|
| `active` | 0≤b≤3、0≤s≤2 | s<2ならn=b+s、s=2ならn≥b+2 |
| `walk_awarded` | b=4、0≤s≤2 | s<2ならn=4+s、s=2ならn≥6 |
| `strikeout_out` | 0≤b≤3、s=3 | n≥b+3 |
| `caught_foul_out` / `fair_play_pending` | 0≤b≤3、0≤s≤2 | s<2ならn=b+s+1、s=2ならn≥b+3 |

nは非負整数。この整合条件は**今回の8イベントだけをstartから受理した場合**の到達可能性であり、実際の野球の全状態に課す制約ではない。履歴からどの投球があったかまで逆算できるという主張もしない。

純粋な`step(state,event)`は1球分の新しい状態を返し、元の状態を変えない。active以外への追加投球は例外で拒否する。四球/三振後のカウントを自動リセットせず、次打者・アウト数・得点へ自動遷移しない。新しい対象は明示的なstartから始める。

`replay(start_state,events)`は有限のlist/tupleを同じstepで順に処理し、前後状態/イベント/順番のtraceを返す。文字列/辞書/長さ不明のiteratorを投球列として暗黙変換しない。空列は同じ状態、途中で列が尽きればactiveのまま。便宜的な球数上限で三振や打球へ変換しない。不正入力/終端後入力では結果を捏造せず例外。状態/入力を変更せず、無効な全体列を公開成功traceとして保存しない。

同じ列を一括で処理した結果と、任意の途中状態から残りを処理した結果は同じ。これは**メモリ上の途中処理**の検証であり、製品のセーブ形式・再開ファイル・DB設計の採用ではない。RNG・時刻・隠れた可変global状態をstepへ入れない。

traceの順番号はその入力直前のn+1とする。分割処理で1から振り直さず、prefixのtraceと残りのtraceを連結した全履歴も、一括処理と完全一致させる。

## 6. 言語非依存の表と固定16列

全12 activeカウント(b=0…3,s=0…2)×8イベントの**96遷移**を静的なJSON表へ保存する。表の開始nはb+sとし、ループしてnが増えた同じカウントへの適用も別テストする。実装自身を呼んだ出力だけを正解表として採用せず、本設計/規則/独立点検と照合する。

以下は全てstartから。括弧内は最終(b,s,n,status)。略号は§4を使い、繰返し表記は入力JSONでは展開した列とする。

| ID | 列 | 最終状態 |
|---|---|---|
| empty | 空列 | (0,0,0,active) |
| one_ball | B | (1,0,1,active) |
| one_called_strike | C | (0,1,1,active) |
| four_balls | B×4 | (4,0,4,walk_awarded) |
| mixed_walk | C,B,M,B,F,B,B | (4,2,7,walk_awarded) |
| called_strikeout | C×3 | (0,3,3,strikeout_out) |
| swinging_strikeout | M×3 | (0,3,3,strikeout_out) |
| foul_tip_strikeout | C,M,T | (0,3,3,strikeout_out) |
| fouls_to_two | F,F | (0,2,2,active) |
| two_strike_fouls | C,M,F,F,F | (0,2,5,active) |
| three_bunt_fouls | U×3 | (0,3,3,strikeout_out) |
| bunt_foul_after_two | C,M,U | (0,3,3,strikeout_out) |
| caught_foul_first | O | (0,0,1,caught_foul_out) |
| caught_foul_after_two | U,U,O | (0,2,3,caught_foul_out) |
| fair_full_count | B,B,B,C,M,P | (3,2,6,fair_play_pending) |
| long_foul_run | C,M,F×32 | (0,2,34,active) |

これはルールの試験用fixtureで、発生確率・リーグ分布の標本ではない。列ごとのtraceは保持するが、K%/BB%/打率等の公式成績やモデル品質の改善を報告しない。

## 7. 配置・実行・出力

ゲームrepoの新規`prototypes/phase4a/`にPython標準ライブラリだけの参照実装、`fixtures/`に96遷移表/16列、`tests/`にunittestを置く。これは移植前に契約を点検するための実行言語で、**Android言語/DB/アーキテクチャの選定ではない。D032はOPEN。** 研究repoのコード・結果・575テストは変更せず、新試作からimportもしない。

CLIはゲームrepo rootで`python -m prototypes.phase4a run --output data/prototypes/phase4a/run-01`。固定fixtureだけを使い、外部入力/raw-root/model/seed/resumeのオプションは作らない。新規出力先は`data/prototypes/phase4a/`か`reports/phase4a/`の子のみ。既存先・旧namespace・checkout外・入力重複を拒否し、symlink/junctionで許可位置を再定義させない。workerはroot README案内・`.gitignore`の`data/prototypes/`追記・専用CI以外、旧ファイルを変更しない。大量生成物/元PDF/原データをGitへ入れない。

出力は`plan.json`、`traces.json`、`REPORT.md`、`SHA256SUMS.json`。planは設計版/規則版/範囲/固定入力/実装とfixtureの同一性を記録。新ソース/fixture・Python版を含め、テキスト同一性は改行差の扱いも明記する。tracesは全16列の各前後状態と、予測せず入力したイベントを保持。実行時刻/絶対パスを主要出力へ入れず、同じ環境の2出力先で4成果物のバイト一致を確認する。処理速度を測定してもAndroid性能とはしない。

`source_kind=synthetic_fixture`、`scope=partial_count_rules`、`probability_model=false`、`connected_to_statcast=false`、`connected_to_phase3d=false`、`official_stats_generated=false`、`ability_calibrated=false`、`android_integrated=false`、`product_adoption=false`を明示する。

## 8. 受入れ検証・承認

1. 96遷移と16列を規則/設計の表へ独立照合する。全12カウントで通常ファウル・チップ・未捕球バント・捕球飛球の違い、四球、捕球済み第三ストライクを検査。Pの後に安打/アウトや次打者を自動決定しない。
2. 通常ファウルを二ストライク後に1,000回加えてもactive/(0,2,1002)。UやTは第三ストライク、Oはcaught_foul_outであることを検査する。球数による強制終端を作らない。
3. 全statusの整合、bool/不正型/範囲外/矛盾したn/未知項目の拒否、終端後の全8イベント拒否、未捕球第三ストライク等の未対応入力拒否を確認。例外の前後で元入力/状態が変わらないことを検査する。
4. 全fixtureの全分割位置で、一括とprefix途中状態からの処理が一致。ケース/実行順を変えても同じ。RNG・旧データ/学習モデル/3dへの非依存を監視/コード点検する。fixture読取りと旧データ読取りは区別する。
5. 新出力・上書き拒否と実symlink/junctionによる旧領域/checkout外の迂回拒否を検証。新旧ソース/Git差分と出力hashを確認し、既存変更/履歴を保護する。
6. 専用unittestと、新規`.github/workflows/phase4a-tests.yml`のPython3.11/3.12 CIを実行。実測の自動テスト件数と96遷移/16列の検査数は別記し、subTest回数をtest method数に混ぜない。研究repo不変を確認するが、未接続の研究575テストを新ルールの証明として数えない。
7. 責任者が別worktreeで全コード/テスト・独立期待表・再実行を確認し、問題はworkerへ直接修正依頼する。仕様変更が必要なら本人へ戻す。成功後は既存の継続許可でPRをマージし、完了記録を更新する。

**相談対象は、以上の限定ルール範囲・規則版・ゲーム側Python参照実装/CIとworker実装開始。未承認のためD042 OPEN/ADR Proposedを維持する。** 本人承認後に設計をマージして委任する。D029/D031/D032はOPEN、製品採用・研究接続・完全な試合ルール・公式成績/セーブ/UIの採用は別判断とする。

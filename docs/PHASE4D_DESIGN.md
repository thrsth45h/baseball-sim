# Phase 4d — 表裏の切替と両チーム得点の限定参照

- 版: `phase4d-design-v0.1`、2026-10-30 日本時間。
- 状態: **Accepted**。2026-10-30、本人が本PRを示す確認に「設計PR #28の範囲で進める（推奨）」と回答。限定半回列制御・得点表現・明示Advance・固定表/列・ゲーム側Python参照/CI・旧型/step利用とworker開始を承認した。「次へ」だけを承認とは扱っていない。D045限定CONFIRMED、[ADR-0011](adr/0011-phase4d-half-sequence.md) Accepted。
- 基準: ゲームmain `91d5e1f40730e09d4970e0960a10b3c658fc65b8`、研究main `cd8217a44788cf830c5554b71b04a27a98ce620c`。
- 関連: D004/D022、D042–D044の承認済み限定契約、D029/D031/D032 OPEN、[設計点検](PHASE4D_REVIEW.md)。設計PR: [ゲームPR #28](https://github.com/thrsth45h/baseball-sim/pull/28)（本人の範囲承認済み、設計マージ後にworkerへ委任）。

## 1. 目的・代替案と追加価値

Phase 4cは一つの半回で三アウトになると停止する。次はその状態を保持したまま、**三アウト後の明示的な表裏切替と両チームの得点保持**を検算する。新しいプレーの種類や確率、野球としての再現性は増えないが、半回を重ねる際の得点重複・リセット漏れ・加算先の逆転を検出できる。

| 案 | 利点・反証と判断 |
|---|---|
| 半回列の明示制御（推奨） | 旧4cを変更せず、攻撃側・得点・半回リセットを小さく確認できる。完成試合へは別工程が必要 |
| 単打/二塁打/併殺等を先に追加 | プレーの幅は広がるが、走者ごとの進塁・アウト時系列の契約が必要。「単打なら全員1塁進む」で埋めない |
| 9回/延長/サヨナラ/勝敗まで実装 | 見た目は試合に近いが、終了条件・未実施の裏・得点確定時点・走者配置の判断が増える。今回の半回制御に混ぜない |
| Androidで画面まで作る | 利用体験を早く試せるが、言語/保存方式等のD032が未決定。技術採用を暗黙に決めない |
| 現状維持 | 新しい仮定はないが、4c単独では半回を跨ぐ状態保護を検査できない |

ゲームrepoの新しいPython標準ライブラリ参照を提案する。終了判定のない**有限の合成半回列のcontroller**であり、試合エンジンや最終得点/勝者の判定器とは呼ばない。小さく完了させ、対応プレー拡張や試合終了を次に設計するための境界とする。

## 2. 規則の根拠と独自の試験契約

[MLB 2026 Official Baseball Rules](https://mktg.mlbstatic.com/mlb/official-information/2026-official-baseball-rules.pdf)の5.01(a)と5.09(e)を確認した。通常の開始でビジターが攻撃し、三アウトで攻守が交代する部分を参照する。PDFや本文の長文を複製しない。`top/bottom`、明示Advance、得点の保存表現は本試作のAPI契約である。

7.01等の試合終了・延長の全規則は採用しない。**例えば9回表終了時にホームがリードしていても、このcontrollerはAdvanceを受理する。これは実際の試合で裏を行うべきだという判定ではない。** 回数が10以上でも空塁へ切り替えるが、MLB延長の走者配置を再現したものではない。架空リーグの9回制・延長・引分規約を確定しない。終了判定を外に置く将来のadapterも今回は作らない。

4cの強い合成入力条件を全て引き継ぐ。begin〜resolveに未対応の盗塁/暴投/走者アウト/得点等がなく、W/X/Hの追加条件と触塁・裁定が外部で完了した列に限る。実データを自動変換しない。省略された実世界イベントを検出せず、halfは最後に確定反映したsnapshotである。

## 3. 不変状態と得点を保存する一箇所

`HalfSequenceState(inning, side, closed_away_runs, closed_home_runs, current)`を新しい不変値とする。

| 欄 | 厳密な型・意味 |
|---|---|
| inning | Pythonの厳密なint、1以上。上限を固定しない |
| side | 厳密なstrで`top`か`bottom`。topはaway、bottomはhomeの攻撃 |
| closed_away_runs / closed_home_runs | 厳密なint、0以上。**現在の半回を含まない**、切替済み半回の累計 |
| current | 旧4cの厳密な`PitchHalfState`。旧count/half条件をそのまま保持 |

bool/float/subclass/暗黙変換を受理しない。初期例は(1,top,0,0,current=(None,空のHalfInningState))。外部開始snapshotを受けるが、閉じた半回の得点や回数の履歴を復元/証明しない。1回表で過去得点が非ゼロ等の構造上有効な値も、歴史的に正しいという意味ではない。走者ID・打順・投手/打者ID・永続イベントID・半回別得点表は追加しない。

表示得点は保存せず導出する。現在half.runsをr、closed累計をA/Hとすると、topではaway=A+r/home=H、bottomではaway=A/home=H+r。`.score`は毎回新しい`{away:int,home:int}`、`.batting_team`はaway/home、`.stage`はcurrent.stageを返す。JSONのstateは正確に上記5キーで、currentは旧4cの2キー。導出score/stageはstateへ保存しない。`from_mapping`で明示変換し、`as_dict`は独立したJSON値を返す。

## 4. 旧3actionと新しいAdvanceHalf

旧4cの正確なBeginBatter/Pitch/Resolve型とJSONを**そのまま利用**する。新しいimmutable `AdvanceHalf()`だけを追加し、JSONは正確な`{"kind":"advance_half"}`。新action_from_mappingはこの1種類を検査し、旧3kindの変換は旧4cのparserへ委任する。余分/不足/重複キー、非有限値・未知kind/payload・subclassを拒否する。stepで辞書や略号へ暗黙変換しない。

| current.stage × kind | begin_batter | pitch | resolve | advance_half |
|---|---|---|---|---|
| between_batters | 旧4cへ | 拒否 | 拒否 | 拒否 |
| pitching | 拒否 | 旧4cへ | 拒否 | 拒否 |
| awaiting_resolution | 拒否 | 拒否 | 旧4cの互換条件つき | 拒否 |
| half_complete | 拒否 | 拒否 | 拒否 | 切替 |

制御16セル（4制御許可/12拒否）。旧Resolveの互換12組中5許可は変えない。旧3actionでは**旧4c.stepをちょうど1回**呼び、inning/side/closed累計を保持してcurrentだけ返却値へ置き換える。旧countや走者ルールを複写しない。不正な旧actionも旧4c条件で拒否し、Advanceで旧stepを呼ばない。

Advanceはcurrent.stage=half_completeだけで受理し、次を一つの純粋更新として行う。

1. current.half.runsを現在の攻撃側のclosed累計へ1回だけ移す。他方は保持する。
2. top n→bottom n、bottom n→top n+1。
3. 新しいcurrentをcount=None/outs0/空塁/runs0へリセットする。stageはbetween_batters。次打者のBeginは自動挿入しない。

**Advance前後で両チームの表示scoreは完全に不変。** Resolve時点で表示へ含めた得点を、切替で再加算しない。三アウト直後の旧snapshotは残塁/得点を保持し、Advanceした新snapshotだけが空になる。旧値の破壊的変更や残塁を得点へ変える処理はない。

返却された次半回へ連続Advanceを適用すると拒否する。同じ古い完了snapshotに純粋関数を再適用すれば同じ成功値になる。古いcheckpoint再投入・通信再送・並行更新の永続重複排除/exactly-onceではない。

## 5. step/replayとtrace

新stepは正確なstate/action型を受けて新stateを返す純粋関数。replayは厳密なlist/tupleだけを受け、不変result(final_state,trace tuple)を返す。空列は全4stageで有効。途中の投球/結果待ち/半回完了で列が尽きてもそのまま返し、Advanceや確定を補完しない。無効tailでは部分結果を公開せず、元のstate/action列を変えない。

新traceの正確なキーは`action/before/after/away_runs_added/home_runs_added`。得点差分は**導出scoreのafter−before**で、厳密な非負整数。Advance/Begin/Pitchは両方0、受理Resolveだけが現在攻撃側に旧4b由来の増分を持つ。他方は0。Advanceによるhalf.runs減少を負の得点と数えない。旧4cのtrace形式は変更しない。ローカル連番を付けず、全分割位置でprefix+suffixのtraceと一括結果を完全一致させる。製品セーブ形式の採用ではない。

## 6. 静的fixture — 制御16セルと固定16列

以下は設計から記載する正本であり、生産stepの出力から正解を生成しない。state略記を`[回,表裏,closedAway,closedHome; count/outs/一二三塁/runs]`とする。表=t、裏=b、countの`—`=None、A0=(0,0,0,active)、A1=(1,0,1,active)、AW=(4,0,4,walk_awarded)、AK=(0,3,3,strikeout_out)、AP=(0,0,1,fair_play_pending)。略号はJSON/API入力に使わない。

G/B/C/M/T/F/U/O/P/RW/RX/RHは4c設計と同じ正式action。Aだけ新AdvanceHalf。`D`は3action `G O RX`の表中省略、D×3は9actionへ展開する。空currentは`—/0/000/0`。初期Z=`[1,t,0,0;空]`。

| ID | 初期state | action列 | 最終state | 最終score away:home |
|---|---|---|---|---|
| empty_top | Z | 空 | Z | 0:0 |
| begin_top | Z | G | [1,t,0,0;A0/0/000/0] | 0:0 |
| bottom_ball | [2,b,3,5;空] | G B | [2,b,3,5;A1/0/000/0] | 3:5 |
| walk_pending | [1,t,2,3;—/0/111/7] | G B×4 | [1,t,2,3;AW/0/111/7] | 9:3 |
| loaded_walk_top | [1,t,11,13;—/2/111/7] | G B×4 RW | [1,t,11,13;—/2/111/8] | 19:13 |
| grand_slam_bottom | [2,b,11,13;—/1/111/7] | G P RH | [2,b,11,13;—/1/000/11] | 11:24 |
| third_out_wait | [1,t,11,13;—/2/111/7] | G C×3 | [1,t,11,13;AK/2/111/7] | 18:13 |
| closed_no_advance | [1,t,11,13;—/2/111/7] | G C×3 RX | [1,t,11,13;—/3/111/7] | 18:13 |
| top_switch | [1,t,11,13;—/2/111/7] | G C×3 RX A | [1,b,18,13;空] | 18:13 |
| bottom_switch | [2,b,11,13;—/3/101/4] | A | [3,t,11,17;空] | 11:17 |
| zero_switch | [1,t,0,0;—/3/111/0] | A | [1,b,0,0;空] | 0:0 |
| next_begin | [1,t,11,13;—/2/111/7] | G C×3 RX A G | [1,b,18,13;A0/0/000/0] | 18:13 |
| two_scoring_halves | Z | G P RH D×3 A G P RH D×3 A | [2,t,1,1;空] | 1:1 |
| two_zero_halves | Z | D×3 A D×3 A | [2,t,0,0;空] | 0:0 |
| walk_close_switch | [1,t,11,13;—/2/111/7] | G B×4 RW D A | [1,b,19,13;空] | 19:13 |
| switch_then_pending | [1,t,11,13;—/3/101/4] | A G P | [1,b,15,13;AP/0/000/0] | 15:13 |

計16列/100action、空/両端を含む全116分割位置。各行の最終stageはcount/outsから4c同様に導出する。fixtureにはID/明示初期/展開した正確action列/expected_final/expected_score/expected_stageを持たせ、全てを別pinする。同じ最終状態になる投球payload置換、開始側/得点変更も検出する。順序を逆に実行しても各traceが一致する。

## 7. 配置・依存と保存物

承認後だけ、新規`prototypes/phase4d/`と`reports/phase4d/`、専用`.github/workflows/phase4d-tests.yml`へ実装する。workerによる既存変更はroot READMEの案内だけ。旧4a/4b/4c全コード・型/契約・fixture・テスト・CI・結果・flagsと研究repoを保持する。完了文書は責任者の別PR。

明示importと同一性hashに使う旧sourceは7パス: `prototypes/__init__.py`、`prototypes/phase4a/{__init__,model}.py`、`prototypes/phase4b/{__init__,model}.py`、`prototypes/phase4c/{__init__,model}.py`。新計算で旧I/O/runner/fixture/結果や研究モデルをimport/入力にしない。新旧sourceを読むことと、旧データ/学習モデル非利用を区別して記録する。既存suiteの回帰検証では従来fixtureを使い、別集計とする。

CLIは固定合成fixtureのみ: `python -m prototypes.phase4d run --output data/prototypes/phase4d/run-01`。外部/raw/model/seed/resumeは作らない。新出力はcheckout直下のdata/prototypes/phase4dまたはreports/phase4dの新しい子だけ。旧namespace/既存/外部/入力重複、namespace全体・子symlink/junctionの迂回を拒否し、旧出力を削除/上書きしない。

4成果物plan.json/traces.json/REPORT.md/SHA256SUMS.jsonを出す。planに承認design/main、強い入力条件、終了判定非対応、固定16表/16列、全新runtime/fixtures/属性と旧依存7sourceのLF正規化UTF-8 hash、Python版を記録。時刻/絶対パスは主要4成果物へ入れず、同じPython環境の2runでバイト一致を確認。全固定期待・source同一性の照合を出力予約より先に終える。

flags: source_kind=synthetic_fixture、scope=partial_half_sequence_controller、connected_to_phase4a/phase4b/phase4c=true。connected_to_statcast/connected_to_phase3d/probability_model/game_end_rules_implemented/official_stats_generated/ability_calibrated/android_integrated/product_adoption=false。旧flagsは凍結したまま。

補助検証は旧4cと同様、承認baseline→固定実装sourceの歴史的な旧ファイル保護、現在の新旧source/依存/tests/CI同一性、後続文書の参考差分を分ける。補助検算で旧静的fixture等を読む場合は用途/固定commit/パスを明記し、新runが使うと混同しない。研究repoとraw/学習成果物は開かない。公開hashとGit blobはcommit後に別検査する。

## 8. 受入れ条件と反例

1. 制御16セルを独立照合し、旧Resolve互換5組を維持。旧3actionで旧4c.step1回、Advanceで0回を検査。両sideで同じcurrentへ適用した旧stepの結果とcurrentが一致し、closed累計・回数・sideが不変。他方score不変、得点差は攻撃側だけへ帰属する。
2. 代表Advance96組: inning1/2×side2×完了halfの占有8×half.runs=0/7/11、closed累計(11,13)。全組で表示score不変、回/表裏の更新、空current、次打者Begin必須、旧snapshot残塁不変を確認。巨大回/closed得点/half得点でも厳密整数の加算とbottomのみ回+1を別検査。有限組合せで全回数/全得点の網羅とは呼ばない。
3. 未完了のAdvanceをbetween/pitching/awaitingで両sideとも拒否。第三アウトのcount終端だけではAdvance不可。Resolve後half_completeで初めて切替可。全12旧具体action（G+8投球+3Resolve）は完了halfで拒否。連続Advance拒否と同じ古いsnapshot再適用の純粋性を区別。
4. 固定16列/100action/116分割の全state/score/stage/traceを独立期待値へ照合。表で得点→切替→裏で得点→切替、無得点、満塁残塁、結果待ちで停止を含む。Begin/Pitch/Advanceのscore差0とResolveだけの攻撃側加算が主要不変条件。巨大整数・長い半回列でも漂移しないことを確認。
5. strict型/JSON/余分不足重複キー/非有限値/subclass/未知action、-O時validation、不変state/action/result/独立JSON、空replay全stage、無効tailで入力不変/部分結果非公開を検査。新traceに番号を付けない。固定fixtureの同一最終への置換を出力前に拒否する。
6. 新runの監視で許可7sourceの読取りと未許可旧/研究入力・RNG/時計の非依存を区別。2run4成果物、公開hash/Git blob、旧ファイル保護、出力防護の実junction/symlinkを確認。権限不足をskip成功にしない。
7. 新4dと旧4c28/旧4a33/旧4b32 methodを別コマンド・別集計し、Python3.11/3.12 CIを対象headで確認。subTest/組合せ数をmethodへ足さず、研究575テストを今回の成功数へ加算しない。責任者は別worktreeと実装未参照の期待値でレビューし、必要な修正をworkerへ直接依頼する。
8. 9回/10回で自動終了/延長走者/勝者を作らない境界を明示的に検査し、正規の試合規則に適合したと称さない。ゲームの試合終了・公式記録・リーグ規約の採用は別設計。

## 9. 本人へ判断を求める範囲

**2026-10-30、本人が本PRを示す確認に「設計PR #28の範囲で進める（推奨）」と回答。限定半回列制御・得点表現・明示Advance・固定表/列・ゲーム側Python参照/CI・旧型/step利用とworker開始を承認した。** D045は限定CONFIRMED/ADR Accepted。設計をマージ後に専用workerへ委任する。一般走塁・試合終了/延長/サヨナラ・勝敗・研究/能力・Android採用は承認へ含めない。

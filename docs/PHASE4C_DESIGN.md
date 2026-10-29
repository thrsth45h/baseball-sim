# Phase 4c — 投球カウントと半回状態の明示的な接続

- 版: `phase4c-design-v0.1`、2026-10-30 日本時間。
- 状態: **Accepted**。2026-10-30、本人が本PRを示す確認に「設計PR #25の範囲で進める（推奨）」と回答。以下の限定接続・強い入力条件・固定表/列・ゲーム側Python参照/CI・旧型/step利用とworker実装開始を承認した。「次へ」だけを承認とは扱っていない。
- 基準: ゲームmain `197add4704dc1f8fe64d41037ede25cf73ba6876`、研究main `cd8217a44788cf830c5554b71b04a27a98ce620c`。
- 関連: D004/D022、D042/D043の既存限定契約、D044 CONFIRMED（4c限定接続のみ）、D029/D031/D032 OPEN、[ADR-0010](adr/0010-phase4c-pitch-half-reference.md)、[設計点検](PHASE4C_REVIEW.md)。
- 設計PR: [ゲームPR #25](https://github.com/thrsth45h/baseball-sim/pull/25)はmain `06256f8a4f0f8795709f56365f1ea212e2090092`へマージ済み。worker実装・責任者の独立レビュー後、[実装PR #26](https://github.com/thrsth45h/baseball-sim/pull/26)をmain `40e1d1d54e9728bbee04c3f65300e57972b9c629`へマージ済み。旧4a/4bの型・stepへ明示Begin/Pitch/Resolveで委任し、結果待ちではhalfを反映せず、明示確定後にcount=Noneへ消費する。次打者も明示Beginを要求し、三アウトで残塁/得点を保持して停止する。 [独立レビュー](PHASE4C_REVIEW.md)・[再現手順](../reports/phase4c/README.md)を参照。以下は承認した契約を保持する。

## 1. 目的・代替案・今回の範囲

**既存4aの投球カウントと4bの塁・アウト・半回得点を、新しい合成アクション列でつなぐ検算用参照**を提案する。打者開始、裁定済み投球、完了プレーの確定を別の入力にし、カウント終端だけから走者の動きや本塁打を推測しない。未確定の結果を受け取るまで停止し、次の打者も明示して開始する。

| 案 | 利点・反論 |
|---|---|
| 4a statusから4bを自動選択 | 短いが、走者据置きや本塁打の追加事実を捏造する。採らない |
| 明示確定による限定接続（推奨） | 既存ルールを変えず、1球から次打者までの処理順と重複適用拒否を検証できる。ただし発生確率や一般の走塁を作るものではない |
| 1打者分の投球列と完了プレーを一括適用 | 公開状態は減るが、投球途中/結果待ち/消費の境界を検証しにくい。今回の目的では明示段階を選ぶ |
| 先に走者ID・一般の進塁/第三アウト得点を追加 | 将来必要だが、時系列・触塁/アピール等まで範囲が広がる。別設計 |
| 単打・球場/守備・研究モデルも同時に接続 | 試合に近づく一方、母集団・未較正係数・裁定の未解決事項が重なる。今回は保留 |

ゲーム側の新規Python標準ライブラリ参照とする。既存4a/4bの型・純粋stepを利用し、旧コード/契約/fixture/結果は変更しない。新しい野球規則を追加せず、正本は[4a契約](PHASE4A_DESIGN.md)・[4b契約](PHASE4B_DESIGN.md)。完全な半回/試合生成、確率・能力・研究/3d接続、走塁判断、公式個人成績、Android/UI/セーブは含めない。

## 2. 合成入力の前提と確定時点

beginからresolveまで、対応する終端プレー以外の盗塁・暴投/捕逸・走者アウト・得点・妨害等がないと設定した試験列を扱う。4aイベント単独ではこの前提を保証しない。明示された未対応アクションは拒否するが、実世界で省略されたイベントを検出できるとは主張しない。実データをこの列へ自動変換しない。

W/X/Hは4bと同じ**完了・裁定済み**の強い条件。Wは正規触塁・強制進塁のみ、Xは打者のみアウト/他走者の最終塁据置き/得点なし、Hは合法なフェンス越えフェア本塁打と全員の正規触塁・全得点確定。追加進塁/アウト・失策・アピール・サヨナラ等なし。4aの`walk_awarded/strikeout_out/caught_foul_out/fair_play_pending`だけでは、この追加条件は得られない。

halfは**最後に確定反映したプレー後のsnapshot**。投球/結果待ちの間に現実の球や走者の瞬間位置、実際の時点のアウト数を表すものではない。`awaiting_resolution`も接続処理の未反映境界であり、規則上の打席がまだ終わっていないと断定するラベルではない。

## 3. 不変状態と4段階

`PitchHalfState(count, half)`は、countが旧4aの厳密な`CountState`またはNone、halfが旧4bの厳密な`HalfInningState`。暗黙変換や別の型のduck typingをしない。旧型自身のvalidationをそのまま用い、half.outs=3ならcount=Noneを必須とする。局面は外部開始/途中snapshotとして渡せるが、組合せの過去履歴や全到達可能性を証明しない。

| 導出stage | 条件 |
|---|---|
| `between_batters` | half.outs<3、count=None |
| `pitching` | half.outs<3、count.status=`active` |
| `awaiting_resolution` | half.outs<3、countが4aの4停止statusのいずれか |
| `half_complete` | half.outs=3、count=None |

stageは上記2値から導出し、独立の変更可能なstatusを持たない。JSONは正確に`count/half`の2キー、countはnullまたは4aの4キー、halfは4bの3キー（basesはboolean配列）。`from_mapping`で旧型へ明示変換し、`as_dict`は独立した新しいJSON値を返す。余分/不足/重複キー・NaN/Infinity・型違反を拒否する。

初期例は`PitchHalfState(count=None, half=HalfInningState())`。外部開始局面では半回に走者や得点があってもよい。イニング番号・表裏・相手得点・打順・走者ID・永続イベントIDは追加しない。

## 4. 3アクションと制御表

新しい不変な`BeginBatter()`、`Pitch(event)`、`Resolve(completed_play)`を用いる。event/completed_playは厳密な文字列で、それぞれ旧4aの8種、旧4bのW/X/Hだけ。未知値・文字列subclass等を暗黙受理しない。

| アクション | 正確なJSON | 受理時の更新 |
|---|---|---|
| G: `BeginBatter()` | `{"kind":"begin_batter"}` | betweenのみ。countを旧4aの(0,0,0,active)へ開始、half保持 |
| `Pitch(event)` | `{"kind":"pitch","event":"called_ball"}`等 | pitchingのみ。旧4a.stepでcountだけ更新、half保持 |
| RW/RX/RH: `Resolve(completed_play)` | `{"kind":"resolve","completed_play":"walk_forced_only"}`等 | awaitingのみ。下記互換表を確認し旧4b.stepでhalfを1回更新、count=Noneとして消費 |

`action_from_mapping`は厳密なJSON objectのkindとキー集合を確認して上記型を作る。未使用/余分キーを無視しない。APIのstepにはその型だけを渡し、辞書/略号/単なるstatus文字列をアクションへ暗黙変換しない。

| stage \ action kind | begin_batter | pitch | resolve |
|---|---|---|---|
| between_batters | 開始 | 拒否 | 拒否 |
| pitching | 拒否 | 4aへ委任 | 拒否 |
| awaiting_resolution | 拒否 | 拒否 | 互換表の確認後4bへ委任 |
| half_complete | 拒否 | 拒否 | 拒否 |

4×3の12セル。解決後にもGを自動挿入しない。三アウトになった時はcount=None、残塁/得点を保って停止し、次の半回を自動開始しない。未確定のfair打球に安打/アウトを補完しない。

### 12セルの確定互換表

W/X/Hの正式文字列は4bと同じ`walk_forced_only/batter_out_runners_hold/legal_over_fence_home_run`。下表は野球全体の合法性表ではなく、今回の**入力契約の受理範囲**。

| 待機中のcount.status | W | X | H |
|---|---|---|---|
| walk_awarded | 許可 | 拒否 | 拒否 |
| strikeout_out | 拒否 | 許可 | 拒否 |
| caught_foul_out | 拒否 | 許可 | 拒否 |
| fair_play_pending | 拒否 | 許可 | 許可 |

5許可/7拒否。P後のXはフェアで打者アウト/他走者据置きまで外部で確定した場合、Hは合法本塁打まで確定した場合だけ。一般のフェア打球の分岐予測器ではなく、単打等の未対応結果を勝手にX/Hへ分類しない。W/X/Hは入力が明示して初めて選ぶ。

Resolve後の返却状態はcount=Noneなので、同じ進行列への連続Resolveを拒否する。ただし純粋関数を同じ古いsnapshotへ再適用したり、過去のcheckpointを再投入したりする操作を、永続IDで重複排除する仕組みではない。外部の二重送信・並行更新・セーブ復帰のexactly-once保証は別設計とする。

## 5. step/replayとtrace

`step(state,action)->PitchHalfState`は純粋関数。Begin/Pitchでhalfを変えず、Resolve以外で得点/アウトを追加しない。各受理Resolveは旧4b.stepを1回適用し、解決済みのカウントを再利用しない。未知/不正入力と段階違反では元の状態を変えず例外にする。

`replay(start_state,actions)`は厳密な有限list/tupleだけを受け、不変なresultのfinal_stateとtrace tupleを返す。有効な列がpitching/awaiting_resolution/between_battersで尽きても、その途中状態を返し、勝手に確定・終了しない。空列は任意の有効な状態（半回完了を含む）を保つ。無効tail/終端後入力では部分成功結果を公開しない。各traceの正確なキーは`action/before/after/runs_added`、actionは上記JSON、before/afterは2キーの状態、runs_addedはafter.half.runs−before.half.runs。ローカル番号は付けず、全分割位置でprefix trace+残りtraceと一括traceを完全一致させる。メモリ上の途中処理は製品セーブ形式の採用ではない。

halfの`outs+runs+占有塁数`はBegin/Pitchでは不変、成功したResolveだけでちょうど1増える。人数保存だけでは誤った移動先を検出できないため、旧4bの静的表と独立期待値も照合する。

## 6. 言語非依存の制御表・互換表・固定18列

新fixturesに上記制御12セル、互換12セル、以下の固定18列を静的JSONで置く。生産step/replayの出力をそのまま正解にせず、承認設計と独立期待値へ照合する。固定列はID/明示開始/正確なaction列/最終状態を独立pinし、同じ最終状態になるaction置換・payload/開始局面の変更も検出する。

全開始countはNone。half略記は(outs,一二三塁占有,runs)。B/C/M/T/F/U/O/Pは4aの正式8投球event、Gは開始、RW/RX/RHは明示確定。略号や繰返しはJSONでは正式アクションへ展開する。最終count略記はA0=(0,0,0,active)、A1=(1,0,1,active)、AW=(4,0,4,walk_awarded)、AK=(0,3,3,strikeout_out)、AP=(0,0,1,fair_play_pending)、AF=(0,2,5,active)、—=nullで、API入力ではない。

| ID | 開始half | action列 | 最終count | 最終half | 導出stage |
|---|---|---|---|---|---|
| empty_between | (0,000,0) | 空 | — | (0,000,0) | between_batters |
| begin_only | (0,000,0) | G | A0 | (0,000,0) | pitching |
| one_ball | (0,000,0) | G,B | A1 | (0,000,0) | pitching |
| walk_await | (0,000,0) | G,B×4 | AW | (0,000,0) | awaiting_resolution |
| walk_applied | (0,000,0) | G,B×4,RW | — | (0,100,0) | between_batters |
| second_base_walk | (0,010,0) | G,B×4,RW | — | (0,110,0) | between_batters |
| loaded_walk | (2,111,7) | G,B×4,RW | — | (2,111,8) | between_batters |
| strikeout_await | (0,000,0) | G,C×3 | AK | (0,000,0) | awaiting_resolution |
| strikeout_hold | (0,101,2) | G,M×3,RX | — | (1,101,2) | between_batters |
| third_out_strikeout | (2,111,7) | G,C×3,RX | — | (3,111,7) | half_complete |
| caught_foul_out | (1,001,0) | G,O,RX | — | (2,001,0) | between_batters |
| fair_await | (0,010,7) | G,P | AP | (0,010,7) | awaiting_resolution |
| fair_out | (0,101,0) | G,P,RX | — | (1,101,0) | between_batters |
| grand_slam | (1,111,7) | G,P,RH | — | (1,000,11) | between_batters |
| next_batter | (0,000,0) | G,B×4,RW,G | A0 | (0,100,0) | pitching |
| three_retired | (0,000,0) | G,C×3,RX,G,M×3,RX,G,O,RX | — | (3,000,0) | half_complete |
| ordinary_fouls | (0,000,0) | G,C,M,F×3 | AF | (0,000,0) | pitching |
| bunt_strikeout | (0,000,0) | G,U×3,RX | — | (1,000,0) | between_batters |

計82アクション、空/両端を含む全100分割位置。fixture数や人工得点をリーグ成績や確率モデルの品質と解釈しない。

## 7. 配置・依存境界・再現出力

承認後、ゲーム側の新規`prototypes/phase4c/`へPython標準ライブラリ参照/fixtures/tests、新規`.github/workflows/phase4c-tests.yml`へPython3.11/3.12の専用CIを置く。新CIでは新4c・旧4a33 method・旧4b32 methodを別コマンドで実行する。旧4a/4b全コード・fixture・テスト・CI・公開結果と研究repoは変更せず、workerの旧変更はroot README案内のみ。既存`data/prototypes/`除外を用い、完了文書更新は責任者が別PRで扱う。

新計算は旧4a.modelのCountState/stepと旧4b.modelのHalfInningState/stepへ明示委任する。モデル式を新4cへ複写して別ルールにしない。依存する旧sourceは、shared `prototypes/__init__.py`、`prototypes/phase4a/{__init__,model}.py`、`prototypes/phase4b/{__init__,model}.py`の5パス。計算用の明示importと、同一性確認用のsourceバイト読取りを、旧データ/研究モデルの読取りとは区別して記録する。研究/3d・旧学習成果物はimport/入力に使わない。

CLI案は`python -m prototypes.phase4c run --output data/prototypes/phase4c/run-01`。固定合成fixtureのみで外部/raw/model/seed/resumeオプションはない。新出力先はcheckout直下の`data/prototypes/phase4c/`か`reports/phase4c/`の新しい子だけ。既存先/旧namespace/外部/入力重複と実symlink/junctionの迂回を拒否する。

4成果物`plan.json/traces.json/REPORT.md/SHA256SUMS.json`を出す。planには設計/依存契約の基準・強い入力前提・固定表/列・全新runtime/fixtureと上記旧依存source5パスのLF正規化UTF-8 hash・Python版を記録。全入力/同一性/期待表の照合を出力予約前に完了し、時刻/絶対パスを主要4成果物へ入れず、同じPython環境の2runバイト一致を確認する。

`source_kind=synthetic_fixture`、`scope=partial_pitch_half_orchestration`、`connected_to_phase4a=true`、`connected_to_phase4b=true`、`connected_to_statcast=false`、`connected_to_phase3d=false`、`probability_model=false`、`official_stats_generated=false`、`ability_calibrated=false`、`android_integrated=false`、`product_adoption=false`を明示する。新4cが旧モジュールへ接続する事実と、旧4a/4b自身の凍結flags/過去結果を変更しないことを両立させる。

補助検証では、旧保護を承認baseline→固定実装sourceの履歴、現在runtime/tests/依存source/CI同一性を別検査、後続文書変更を参考欄とする。既存worktree/ignoredの実行資料を上書き・削除しない。

## 8. 受入れ条件

1. 制御12セル/互換12セルを独立照合。24種のopen half（outs0/1/2×8占有、runs=7）で互換12組を検査し、288試行=120受理/168拒否。停止count代表値はwalk=(4,0,4)、strikeout=(0,3,3)、caught_foul/fair_pending=(0,0,1)と各正確なstatus。受理後は旧4bの対応結果と一致しcount=None。拒否時は状態不変。
2. 12 active count（b0…3/s0…2、n=b+s）×8投球×上記24 halfの2,304組で、countが旧4aと一致しhalf不変。有限の代表値の検査で、ループ後の任意nや全runsを網羅したとは呼ばない。開始24組はcountだけゼロへ。half完了8占有×全12具体actionの96拒否、half.outs3/countありの矛盾した構成も拒否する。
3. 終端前Resolve、終端後Pitch/Begin、解決後の連続Resolve、Begin前Pitchを拒否。Pのままの停止と明示X/Hだけの更新、第三アウト待ちと解決後の残塁/得点保持、次打者Gの必要性を検証する。
4. 固定18列/82actionと全100分割位置の最終状態/trace連結、順序変更に対する独立性、外部開始snapshot/空列、不変state/action/result/JSONを確認。無効tailは部分成功を公開しない。固定fixtureの置換は出力前に拒否する。
5. G,C,M,F×1,000後もcount=(0,2,1002,active)でhalf不変。巨大半回得点の+1/+4、投球以外でpitches_seenが増えないこと、Beginでのリセット、Begin/Pitchで得点/アウトを作らないことを確認する。
6. 厳密型/JSON/重複・余分キー/未知actionやpayload/段階違反、最適化でも残るvalidationを検査。実世界の省略された事実や一般のルール合法性まで検査できるとは称さない。
7. 新runのRNG/時計/研究入力への非依存、許可した旧型/stepと依存source読取りの範囲を監視/点検。2run4成果物、公開hash/Git blob、出力防護/実junction・symlinkを確認する。権限不足をskip成功にしない。
8. 新旧3suiteと対象headのPython3.11/3.12 CIを確認。test method/subTest/組合せ数は別記し、旧研究575テストを新4cの成功件数へ加算しない。責任者が別worktreeと独立期待値でレビューし、問題はworkerへ直接修正依頼する。

## 9. 本人が承認した範囲

**2026-10-30、本人が「設計PR #25の範囲で進める（推奨）」と回答し、上記の明示3action/4stage/互換5組、強い合成入力条件、固定表/18列、ゲーム側Python参照/専用CI、旧4a/4b型・stepへの接続とworker実装開始を承認した。** D044を限定接続範囲のみCONFIRMED、ADRをAcceptedとし、設計マージ後にworkerへ委任する。一般走塁/単打/併殺/第三アウト得点/公式個人成績/完全試合、研究/能力、Android/セーブ/製品採用は別判断、D029/D031/D032はOPENを維持する。

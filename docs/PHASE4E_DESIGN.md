# Phase 4e — 確定済み安打と明示された走者進塁の限定参照

- 版: `phase4e-design-v0.1`、2026-10-30 日本時間。
- 状態: **Proposed／未承認**。「つぎへ」は設計を進める指示であり、以下の仕様採用ではない。D046 OPEN、[ADR-0012](adr/0012-phase4e-completed-safe-hits.md) Proposed。
- 基準: ゲームmain `7343b6179aedbdc6f42c3aa22d887dac7bcdbf1a`、研究main `cd8217a44788cf830c5554b71b04a27a98ce620c`。
- 関連: D004/D022/D023、D042–D045の既存限定契約、D029/D031/D032 OPEN、[設計点検](PHASE4E_REVIEW.md)。設計PRは作成後に記録する。

## 1. 目的・代替案と範囲

旧4dは四球・走者据置きの打者アウト・合法本塁打だけで半回列を検算する。次は**単打/二塁打/三塁打と裁定済みで、打者と全走者がsafeとなった完了プレー**を追加する。各走者の行き先は明示入力とし、進塁判断や確率は生成しない。

| 案 | 利点・反論 |
|---|---|
| 安打ごとに全員1/2/3塁進む | 簡単だが、同じ単打で一塁走者が二塁/三塁/本塁へ進む違いや据置きを消す。採らない |
| 安打価値と走者行先を明示する限定action（推奨） | 既存の投球/半回/表裏にプレーを増やせる。実際にどの安打や進塁が起きるかは別モデルが必要 |
| 自由なafter stateを外部から受ける | 汎用性はあるが、走者消失/捏造や不正な加点を受けやすい。入力を安打と当初走者の行先へ限定する |
| 走者ID・アウト/併殺・失策・時系列も追加 | 将来必要だが第三アウト得点等の責任が増える。今回の全員safeという条件に混ぜない |
| 試合終了/勝敗、またはAndroidを先行 | 4dを試合へ近づける別候補。対応プレーが狭い現段階では、まず安打後の局面を増やす。技術/リーグ規約は未確定 |

ゲームrepoの新規Python標準ライブラリ参照を提案する。旧4a/b/c/dを変更せず新4eから利用する。得点分布の学習、走塁判断、守備/打球生成、能力係数、実データ接続、試合終了・公式個人成績・Android/UI/DB/セーブや製品採用は含めない。

## 2. 規則の参照と強い合成入力条件

[MLB 2026 Official Baseball Rules](https://mktg.mlbstatic.com/mlb/official-information/2026-official-baseball-rules.pdf)の5.06(a)/(b)、5.09(b)(9)、9.05/9.06(a)/(b)を確認した。塁占有・正規触塁・追越しと安打価値の区別を参照し、規則本文の長文は複製しない。

特に9.06(b)では、打者が他走者への送球中に先の塁へ進んだ場合、最終塁と安打価値が一致しない例がある。**本試作は安打価値を最終塁から判定しない。** 安打価値1/2/3は外部の裁定済み入力。今回は打者がその安打で獲得した塁に留まり、打者の送球間/失策等の追加進塁がないケースだけを対象とする。打者の最終塁も入力の安打価値と等しいという強い条件である。

全員が指定先へ正規に触塁してsafe、プレーの得点も確定済みで、途中の追越し・未触塁・アウト・失策・妨害・アピール・サヨナラはないと外部で確定した試験入力を用いる。走者の据置きも許す。4c以来のbegin〜resolve間の未対応の盗塁/暴投/走者アウト/得点等がない条件も継承する。実データからの自動変換や野球裁定器を作らない。

最終塁から、途中の正規触塁・追越し・失策の有無は分からない。以下の検査は最終位置の矛盾を拒否するもので、全履歴の合法性を証明しない。明示されなかった実世界イベントや偽った裁定を検出できるとは称さない。

## 3. 旧stateを保持し、新actionを追加する

stateは旧4dの厳密な`HalfSequenceState`をそのままaliasする。保存5欄・導出score/stage/batting_team・旧current/count/half validationを変更しない。新しい独立scoreやstatus、走者ID・打順・個人成績は追加しない。

新しい不変actionは`ResolveSafeHit(hit_bases, runner_destinations)`。

| 欄 | 厳密な型と意味 |
|---|---|
| hit_bases | 厳密intの1/2/3。外部で裁定済みの安打価値で、対象条件内の打者最終塁でもある。bool/float/subclass不可 |
| runner_destinations | 厳密tupleの3要素。**プレー前**の一/二/三塁スロットごとの行先。各要素はNoneまたは厳密intの1…4、4=本塁。Noneは当初空塁を意味する |

constructorは上記の形/型/範囲だけを検査する。占有との照合や逆走等はstateが必要なのでstepで検査する。JSONは正確に`{"kind":"resolve_safe_hit","hit_bases":1,"runner_destinations":[2,null,4]}`の3キー。parserは厳密dict・厳密str kindとキー集合、厳密list3要素を確認してtupleへ明示変換する。余分/不足/重複キー、非有限値・未知kind・型違反を拒否し、値を補完しない。APIのstepへ辞書/略号を暗黙変換しない。

旧BeginBatter/Pitch/Resolve/AdvanceHalfの型は旧4dのものをそのままaliasし、旧kindのJSON変換は旧4d.parserへ委任する。旧4actionのstepも旧4d.stepをちょうど1回呼ぶ。旧ルールを複写せず、新hit actionで旧stepを呼ばない。

## 4. 新hitの受理条件と更新

新hitは`stage=awaiting_resolution`かつ`current.count.status=fair_play_pending`だけで受理する。walk/K/caught-foul待ち、投球中、打者間、半回完了では拒否。旧Resolveの互換5組は変えない。fair入力のPだけでは安打とは決めず、新しい明示actionを待つ。

| stage × kind | begin | pitch | resolve W/X/H | advance_half | resolve_safe_hit |
|---|---|---|---|---|---|
| between_batters | 旧へ | 拒否 | 拒否 | 拒否 | 拒否 |
| pitching | 拒否 | 旧へ | 拒否 | 拒否 | 拒否 |
| awaiting_resolution | 拒否 | 拒否 | 旧互換条件つき | 拒否 | fair待ち/行先条件つき |
| half_complete | 拒否 | 拒否 | 拒否 | 旧へ | 拒否 |

制御20セル（5制御許可/15拒否）。新hitでは次を全て満たす必要がある。

1. 当初のoccupiedと行先のNoneが正確に対応する。空塁の架空走者、占有走者の省略を拒否する。
2. 当初塁iの走者の行先dはi以上。**据置きを許し**、当初塁より後方の最終位置を拒否する。
3. 打者の行先hit_bases、当初一塁→二塁→三塁の存在する走者の行先を順に並べる。隣接する行先a,bは`a<b`または`a=b=4`だけを許す。非本塁の衝突や後続走者だけが先の塁へ出る最終配置を拒否し、複数の本塁到達を許す。これは当初走者間と打者の相対順序の条件であり、途中経路の観測ではない。

全検査後、一つの純粋更新として、打者と各既存走者の行先1…3を占有塁にし、行先4の既存走者数だけhalf.runsへ加算する。outsは不変、count=Noneへ消費する。回数/side/closed累計は保持し、scoreの増分は現在の攻撃側だけへ帰属する。人数収支は`after占有数 + (after.half.runs-before.half.runs) = before占有数+1`。この収支だけで移動先の正しさを保証せず、独立した識別走者の期待値とも照合する。

次打者Beginは明示のまま。解決後の返却stateへの連続ResolveSafeHitは拒否する。同じ古いfair待ちsnapshotへの再適用は純粋関数として同じ成功値を返す。古いcheckpoint/通信再送/並行更新の永続重複排除は保証しない。新hit自身はoutsを増やさず、半回完了を作らない。

## 5. step/replayとtrace

新stepは旧4dの厳密stateと上記5種類の厳密actionだけを受ける。新replayは厳密list/tuple、空列は全4stage、途中で尽きた状態を保持、不正tailは元state/列を変えず部分結果非公開とする。

新TraceEntry/ReplayResultは不変で、旧4d形式と同じ正確な`action/before/after/away_runs_added/home_runs_added`を使うが、actionには新hitも許す。旧TraceEntryのvalidation/型は変更しない。得点差は導出scoreのafter−before、非負厳密int、番号なし。新hitは0…3得点、打者自身は本塁を行先にしない。新TraceEntryのaction JSONは前節の3キー。全分割位置で最終state/scoreとprefix+suffix traceを一括処理へ完全一致させる。製品セーブ形式の採用ではない。

## 6. 固定表と18列

新fixtureに制御20セル、受理する**48種類**の行先パターン表と下記18列を置く。48表はouts0/runs0のhalf before、hit_bases、行先tupleのJSON配列、half after、runs_addedを正確に保存し、生産stepから正解を生成しない。占有maskごとのhit_bases=1/2/3の受理数は次のとおり（maskは一二三塁）。

| mask | 1 | 2 | 3 |
|---|---:|---:|---:|
| 000 | 1 | 1 | 1 |
| 001 | 2 | 2 | 1 |
| 010 | 3 | 2 | 1 |
| 011 | 4 | 2 | 1 |
| 100 | 3 | 2 | 1 |
| 101 | 4 | 2 | 1 |
| 110 | 4 | 2 | 1 |
| 111 | 4 | 2 | 1 |

state略記は旧4d同様`[回,t/b,closedAway,closedHome;count/outs/一二三塁/runs]`。`—`=None、AP=(0,0,1,fair_play_pending)、空current=`—/0/000/0`、Z=`[1,t,0,0;空]`。`S1/2/3(d1,d2,d3)`は表中だけの新hit略号、`-`=None、数字4=本塁。G/P/O/RX/Aは旧4d設計どおりで、API/JSONには正式actionへ展開する。

| ID | 初期state | action列 | 最終state | 最終score away:home |
|---|---|---|---|---|
| single_empty | Z | G P S1(-,-,-) | [1,t,0,0;—/0/100/0] | 0:0 |
| double_empty | Z | G P S2(-,-,-) | [1,t,0,0;—/0/010/0] | 0:0 |
| triple_empty | Z | G P S3(-,-,-) | [1,t,0,0;—/0/001/0] | 0:0 |
| single_first_second | [1,t,0,0;—/0/100/0] | G P S1(2,-,-) | [1,t,0,0;—/0/110/0] | 0:0 |
| single_first_third | [1,t,0,0;—/0/100/0] | G P S1(3,-,-) | [1,t,0,0;—/0/101/0] | 0:0 |
| single_second_holds | [2,t,11,13;—/1/010/7] | G P S1(-,2,-) | [2,t,11,13;—/1/110/7] | 18:13 |
| single_third_holds | [1,t,0,0;—/0/001/0] | G P S1(-,-,3) | [1,t,0,0;—/0/101/0] | 0:0 |
| single_third_scores | [1,t,0,0;—/0/001/0] | G P S1(-,-,4) | [1,t,0,0;—/0/100/1] | 1:0 |
| double_first_third | [1,t,0,0;—/0/100/0] | G P S2(3,-,-) | [1,t,0,0;—/0/011/0] | 0:0 |
| double_first_scores | [1,t,0,0;—/0/100/0] | G P S2(4,-,-) | [1,t,0,0;—/0/010/1] | 1:0 |
| double_third_holds | [1,t,0,0;—/1/001/0] | G P S2(-,-,3) | [1,t,0,0;—/1/011/0] | 0:0 |
| loaded_single_one | [1,t,11,13;—/2/111/7] | G P S1(2,3,4) | [1,t,11,13;—/2/111/8] | 19:13 |
| loaded_single_two | [1,t,11,13;—/2/111/7] | G P S1(3,4,4) | [1,t,11,13;—/2/101/9] | 20:13 |
| loaded_single_three | [1,t,11,13;—/2/111/7] | G P S1(4,4,4) | [1,t,11,13;—/2/100/10] | 21:13 |
| loaded_triple_bottom | [2,b,11,13;—/1/111/7] | G P S3(4,4,4) | [2,b,11,13;—/1/001/10] | 11:23 |
| fair_pending | Z | G P | [1,t,0,0;AP/0/000/0] | 0:0 |
| chained_hits | Z | G P S1(-,-,-) G P S2(4,-,-) | [1,t,0,0;—/0/010/1] | 1:0 |
| hit_retire_switch | [1,t,11,13;—/2/001/7] | G P S1(-,-,4) G O RX A | [1,b,19,13;空] | 19:13 |

計18列/60action、空/両端を含む全78分割位置。fixtureのID/明示開始/正確actionと全payload/最終state/expected_score/expected_stageを設計から別pinする。同じ最終状態になる旧投球O→PかつRX据置き等の置換やpayloadの変更も検出し、固定fixtureの自由な置換を認めない。一般replayで合法な別列まで禁止するものではない。

## 7. 配置・依存・成果物

承認後だけ新`prototypes/phase4e/`、`reports/phase4e/`と`.github/workflows/phase4e-tests.yml`へ実装する。workerによる既存変更はroot README案内のみ、旧4a/b/c/dの全code/型/契約/fixture/tests/CI/結果/flagsと研究repoは保持する。完了文書は責任者の別PR。

依存する旧sourceは9パス: shared `prototypes/__init__.py`、`prototypes/phase4a,b,c,d`それぞれの`__init__.py/model.py`。旧型/parser/stepの明示importと同一性hash読取りを許し、旧I/O/runner/fixture/結果・研究モデルは新runの入力にしない。既存suiteは旧fixtureを回帰検証に使い別集計する。新source/fixtures/属性と旧9sourceをUTF-8、CRLF/CR→LFで正規化したhash、Python版をidentityへ含める。

CLIは固定合成fixtureだけ: `python -m prototypes.phase4e run --output data/prototypes/phase4e/run-01`。外部/raw/model/seed/resumeオプションは作らない。新出力はcheckout直下data/prototypes/phase4eまたはreports/phase4eの新しい子だけ。既存/旧namespace/外部/入力重複と実symlink/junctionでのnamespace全体・子の迂回を拒否し、全入力/期待表/identity照合を出力予約前に終える。

主要4成果物plan.json/traces.json/REPORT.md/SHA256SUMS.jsonを出し、同一Python環境の2runバイト一致を確認する。planに設計/承認基準、強い入力条件、裁定を入力として扱う限界、制御20/48表/18列、identity/依存契約を記録。時刻/絶対パスは主要4成果物に入れない。

flagsはsource_kind=synthetic_fixture、scope=partial_completed_safe_hit_projection、connected_to_phase4a/phase4b/phase4c/phase4d=true、connected_to_statcast/connected_to_phase3d/probability_model/game_end_rules_implemented/official_stats_generated/ability_calibrated/android_integrated/product_adoption=false。安打価値は入力であって、公式個人成績を生成/判定したという意味ではない。旧flagsは凍結する。

補助検証は承認baseline→固定実装source時点の旧保護と現在source/依存/tests/CI一致、後続文書の参考差分を分離する。旧静的表等を検算に読む場合は固定commit/用途/パスを明記し、新runの旧データ利用と混同しない。raw/研究/学習成果物は開かず、公開hash/Git blobはcommit後にも確認する。既存worktree/ignored出力を削除/上書きしない。

## 8. 受入れ条件

1. 制御20セルと48受理パターン表を独立照合。新hitはfair待ちだけ。walk/K/caught-foul待ちとbetween/pitching/closedを拒否し、旧Resolveの互換5組と旧4actionを維持。旧action/parserの1回委任、新hitで旧step0回をspy等で確認する。
2. 型の正しいpayload全375種類（hit_bases3×各行先None/1/2/3/4の5^3）×占有8の3,000組を検査し、48受理/2,952拒否を確認。両side×outs0/1/2へ展開した18,000試行は288受理/17,712拒否。runs=7、closed=(11,13)、回2、代表fair count=(0,0,1,fair_play_pending)とし、有限域の検査であることを明記する。全型不正入力は別検査。
3. 受理ではcount消費、outs/回/side/closed保持、人数収支、正確な移動先と加点先を、識別走者による独立期待値へ照合。拒否で元state不変。二塁/三塁据置き、満塁単打の1/2/3得点、複数本塁到達を含む。None不一致・打者/走者間衝突・逆走・後続だけ先の位置を拒否する。
4. 固定18列/60action/78分割の全state/score/stage/trace、逆順ケース実行・混在旧action、結果待ちで停止、解決後の連続新hit拒否と古いsnapshot再適用の純粋性を確認。巨大得点/長い列・異なるfair countのnでも、打者を二重に数えず攻撃側だけ加点する。
5. 厳密型/JSON/subclass/重複・余分不足キー/非有限値、-Oで残るvalidation、不変値と独立JSON、空replay全stage、無効tailの部分結果非公開。fixtureの同じ最終への置換やscore/開始側/payload変更は出力前に拒否する。
6. 許可9sourceの利用と未許可旧/研究入力・RNG/時計非依存を監視。2run4成果物、公開hash/Git blob・旧保護、出力防護の実junction/symlinkを確認し、権限不足をskip成功にしない。
7. 新4eと旧4d26/旧4c28/旧4a33/旧4b32 methodを別コマンド・別集計し、対象headのPython3.11/3.12 CIを確認。subTest/組合せ数をmethodへ足さず、研究575テストも今回へ加算しない。責任者が別worktree/実装未参照期待値で独立レビューし、修正をworkerへ直接依頼する。
8. 旧4dの終了判定なし/9回以降空塁切替を引き継ぐ境界を説明し、サヨナラや公式記録・一般の走塁裁定まで扱ったと称さない。

## 9. 本人へ判断を求める範囲

上記の完了・全員safe・打者最終塁=裁定済み安打価値という限定条件、明示行先の検査と反映、旧4d型/stepを使う新action、固定表/列、ゲーム側Python参照/専用CIとworker開始の承認を求める。**現在は未承認・生産実装なし。** 承認後だけD046を限定CONFIRMED/ADR Acceptedとし、設計マージ後workerへ委任する。走塁/安打の確率・裁定、アウト/失策/第三アウト得点、試合終了/勝敗、研究/能力・Android採用へ拡張しない。

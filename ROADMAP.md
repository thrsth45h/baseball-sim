# 開発ロードマップ（提案）

**各段階の実装・検証・マージ・製品採用を区別**する。下記の「完了」は研究工程の完了であり、完成ゲームへの組み込みではない。

## Phase 1a — Statcastスイング研究基盤（pilot完了）

別リポジトリ: https://github.com/thrsth45h/statcast-swing-model-v1

- 2023/2024/2025各年の6/1〜7を対象に計78,680球、273試合のpilotを実施。
- 研究基盤、モデル候補M0/M1/M2、データ取得・品質監査・バージョン付き推論JSONを実装。
- 正確な選球眼の測定や能力13/17への換算は未達。

## Phase 1b — 通年モデル検証（レビュー合格・mainにマージ済み）

PR #1: https://github.com/thrsth45h/statcast-swing-model-v1/pull/1

- 2023〜2025年の7,289試合・約214.5万投球行を取得し、公式球団別投球数とも一致することを確認したとの報告。
- 2023学習、2024検証、2025評価を維持。2025年6月のpilot既閲覧という限界を記録。
- 331打者でaとdの年度間順位相関を確認。ただしdが認知的な選球眼とは実証されていない。
- GitHub Actions（最新commit `2341496`）は成功、112テストの結果が報告された。モデル未確定・製品用の1〜20係数未設定。
- **再開checkpointの入力不一致を見逃す問題は修正済み。2026-10-28再レビュー後にPR #1をsquashマージ（merge SHA `f3b65653d90b004ea8bfaf233b94231876233980`）**。
- 詳細: [docs/STATCAST_STATUS.md](docs/STATCAST_STATUS.md)。

## Phase 1c — 知覚と積極性の分離（合成プロトタイプ完了）

- スイング積極性はあくまで打撃傾向であり、選球眼は球の見極め・振る価値を識別する技能であるという既存仕様を保持。
- M2の位置反応dと選球眼を自動的に同一視しない。球種、打者戦略、期待打球価値、状況別の判断など、観測だけでは識別できない問題を明示する。
- まずプロダクト内での動作を一貫して定義し、統計モデルが支える部分と設計上の仮定を分離する。
- 下位プロ選手への尺度外挿と1〜20の閾値、未知選手とスカウト誤差は、検証可能な基準が整ってから決定。
- [研究PR #2](https://github.com/thrsth45h/statcast-swing-model-v1/pull/2)をmainへマージ済み（`6d82eddaa51d72126c011e14a0d511634d58e300`）。不完全な知覚と積極性を分けた合成研究用実装。製品用の能力係数や認知妥当性は未検証。

## Phase 1d — 識別検証・仮定への感度・実データ接続（合成研究・D6実入力監査完了）

- [研究設計](docs/PHASE1D_DESIGN.md)、[ADR-0001](docs/adr/0001-phase1d-research-scope.md) を参照。研究範囲の承認・設計マージは[ゲームPR #4](https://github.com/thrsth45h/baseball-sim/pull/4)本文に記録。元文書のProposedは作成時点の表記で、能力係数・製品採用は未承認。
- 合成の生成条件と推論仮定を分け、同程度に振る条件での識別、分散項の効果、識別不能な反例、仮定を崩した感度を検証する計画。
- 比較用の周辺確率・Statcastの入力情報境界・M0/M1/M2との比較契約を定義する。今回の範囲にMLBへの新規学習・公開能力1〜20の換算・ゲーム統合は含めない。
- 合成研究の完成と認知妥当性・製品採用を区別し、既存研究・結果を維持する。
- [研究PR #3](https://github.com/thrsth45h/statcast-swing-model-v1/pull/3)をmainへマージ済み（`cba081a13d53684e037738b53a65bdce2abea9a3`）。240テスト、7世界×3seedの完全実行と再現性をレビュー側でも確認。
- D6実入力監査も[研究PR #4](https://github.com/thrsth45h/statcast-swing-model-v1/pull/4)で完了・マージ済み（`4bf94dee60de9e33cfd7f16e7439cac34a36a6fc`）。処理済み3ファイルとraw3 chunkを来歴・ハッシュ・スキーマ・先頭標本で確認。[実行手順](docs/PHASE1D_DATA_AUDIT_HANDOFF.md)は実施済み手順の記録として残す。
- 真正な早期観測・主観的知覚は未確認。次の実装範囲は設計判断で具体化する。合成W0式の実CSVへの流用、能力換算、ゲーム採用を承認したものではない。

## Phase 2 — 空振り・コンタクト

2026-10-28の追加確認で、旧タグfair_in_playは物理的フェアを保証せず、5description母集団にも記録上の犠牲バントが含まれると判明した。[Phase 2b分類・入力監査v0.1](docs/PHASE2B_DESIGN.md)はworker実装・責任者の独立レビュー・修正後、[研究PR #6](https://github.com/thrsth45h/statcast-swing-model-v1/pull/6)をマージ済み（`2af9657854b70a3dd1ce3db7dd8ae296cdd8745d`）。旧数値・ラベルを保存し、別冊訂正を追加した。D037は監査範囲のみCONFIRMED。新しい分岐予測の学習・ゲーム接続の実装は別承認とする。

- 最初の[Phase 2a研究設計v0.1](docs/PHASE2A_DESIGN.md)は**worker実装・責任者の独立レビュー・修正を経て研究実装をマージ済み**。D036、[承認PR #7](https://github.com/thrsth45h/baseball-sim/pull/7)、[実装PR #5](https://github.com/thrsth45h/statcast-swing-model-v1/pull/5)、[ADR-0002](docs/adr/0002-phase2a-contact-research.md)を参照。研究main `e5a0c18ba2b9f1cdec068c3822a6fb863c0fec25`。製品採用・能力較正を承認したものではない。
- Phase 2aは272テスト・Python 3.11/3.12 CI成功。責任者の全量再実行で11成果物がworker版と一致。C2の予測損失改善とECE悪化を併記し、既閲覧2025を未使用holdoutと扱わない。
- Phase 2aは指定5description適格行の接触／空振り二値予測と結果タグ保持。Phase 2b監査は計300テスト・最終headのCI成功、責任者の別実行と独立期待値435項目が一致。結果分岐の予測モデル、三振回避能力の較正、ゲームへの統合は別段階とする。

- スイングを条件とした空振り・接触、ファウル・インプレー分岐の確率を推定。
- 三振回避・球種ごとの空振り性能・投打左右などの影響を設計。スイング選択バイアスに注意。
- 球数・K%・BB%の分布は、必要なスイング・判定・投球・打席遷移が揃った段階で確認する。2a単独の合格条件にはしない。

## Phase 3 — 打球生成

最初の[Phase 3a設計v0.1](docs/PHASE3A_DESIGN.md)は2026-10-29に研究範囲を承認（D038 CONFIRMED）、worker実装・責任者の独立レビュー・修正後に[研究PR #7](https://github.com/thrsth45h/statcast-swing-model-v1/pull/7)をマージ済み。研究main `c155a6c0af899f2946dbcc4292c680a121fe781b`。数値のあるhit_into_play記録集合の72区分について、同じ周辺を持つ独立J0／同時J1を2023固定後に比較し、区分抽選まで実装した。

全368テスト・Python3.11/3.12 CI成功。全145登録の必要9列と入力同一性を確認し、責任者の別実行・独立期待値1,137項目と整合。J1のカテゴリ損失改善は限定記録集合の結果であり、連続値・方向・物理フェア・守備・能力係数・ゲーム組込みの完成ではない。次はこれらの接続条件を設計・相談し、新しい実装仕様は別承認とする。

[Phase 3b設計v0.1](docs/PHASE3B_DESIGN.md)は、2026-10-29に本人が[設計PR #13](https://github.com/thrsth45h/baseball-sim/pull/13)の研究範囲・仮設定とworker実装を承認（D039は研究範囲のみCONFIRMED）。worker実装・責任者レビュー後、[研究PR #8](https://github.com/thrsth45h/statcast-swing-model-v1/pull/8)をmain `35c9fe6fbe2b01c02b7253ed336526e6960cd4fe`へマージ済み。同じ限定記録集合の粗72区分確率を揃え、細900区分の追加情報を比較し、明示したセル内仮定でEV/LA数値を抽選する。

全439テスト・Python3.11/3.12の対象head PR CI成功。責任者の全量再実行は12成果物が提出版とバイト一致し、事前凍結した独立期待値とも1,232項目が一致した。全体の予測損失は改善したが、2024のEV≥120の6件では悪化、2025の3件は差0。5単位内部の真の形・尾部尺度・小数精度の妥当性は未検証。全接触・能力・方向・守備・ゲーム統合は別段階であり、次の新しい実装仕様は未承認。

[Phase 3c設計v0.1](docs/PHASE3C_DESIGN.md)は、本人が2026-10-29に[設計PR #15](https://github.com/thrsth45h/baseball-sim/pull/15)の範囲・仮設定とworker実装を承認（D040は合成研究範囲のみCONFIRMED）。worker実装・責任者の独立レビュー/報告訂正確認後、[研究PR #9](https://github.com/thrsth45h/statcast-swing-model-v1/pull/9)をmain `015b9fdcd18565270b8ce888bd703aead8c63864`へマージ済み。固定人工条件→無空気軌道→地面到達→単一守備者の移動距離余裕までを動かす。

全497テスト・最終headのPython3.11/3.12 PR CI成功。別worktreeの4主要成果物がバイト一致し、独立した高精度期待値と12例/396点を照合した。検証での旧成果物hash読取りと、計算で旧モデルを使わないことを区別して報告を訂正。新しいraw監査や学習は行っておらず、実データ/3b接続・飛行中捕球・公式記録・能力・ゲーム採用は未実施。次の新しい実装範囲は設計相談とする。

[Phase 3d設計](docs/PHASE3D_DESIGN.md)は2026-10-29に本人が[設計PR #17](https://github.com/thrsth45h/baseball-sim/pull/17)の範囲・仮設定とworker実装開始を承認（D041は合成研究のみCONFIRMED）。worker実装・責任者レビュー/修正確認後、[研究PR #10](https://github.com/thrsth45h/statcast-swing-model-v1/pull/10)をmain `cd8217a44788cf830c5554b71b04a27a98ce620c`へマージした。固定人工の直交初速/高さと単一守備者の移動条件から、反応後・地面到達前の幾何的到達時刻集合を連続時間で求め、未解消をfalseにしない。

全575テスト・対象headのPython3.11/3.12 PR CI成功。責任者の別worktreeの4主要成果物がバイト一致し、独立期待値と20例/14区間・追加数理oracle等940項目が一致。旧namespaceへの出力迂回P2を実junctionで再現し、worker修正と4回帰を確認した。旧358パス中357不変、公開13ファイルと新17/旧228hashをGit blobも照合。真の未来軌道を既知とした経路存在の研究であり、実際の捕球/認知・実データ/3b・能力・公式記録・ゲームへの接続は未実施。次の新しい実装範囲は設計相談とする。

- 巧打力・パワー・打球角度/方向傾向からEV/LA/方向を相関のある分布で生成。
- 打球品質抑制、Hard-Hit%、Barrel%、ゴロ/フライ、選手の多様性をMLB基準で検証。

## Phase 4 — 試合エンジン・野球ルール

最初の[Phase 4a設計v0.1](docs/PHASE4A_DESIGN.md)は2026-10-29に本人が[設計PR #19](https://github.com/thrsth45h/baseball-sim/pull/19)の範囲とworker実装開始を承認（D042は限定参照範囲のみCONFIRMED）。worker実装・責任者レビュー/修正確認後、[実装PR #20](https://github.com/thrsth45h/baseball-sim/pull/20)をmain `4d3a92392d5adb4be9ddd2db4ef16ab3ca3a9977`へマージした。裁定済み合成8イベントのカウント/限定終端/打球受渡しを扱うPython標準ライブラリの参照実装、言語非依存96遷移表/16列、専用CIが完了。全33 test method・Python3.11/3.12 CI、独立期待値との10,289項目照合と4成果物の再実行一致を確認した。[レビュー](docs/PHASE4A_REVIEW.md)を参照。

完了範囲は限定ルールの検算。完全な1打席/試合の生成、既存研究・確率・能力・走者/得点・公式成績・UI/セーブの接続は含めない。次工程の対象と受入れ条件は設計相談で決め、重要な新仕様の承認後に実装する。D029/D031/D032はOPENを維持する。

[Phase 4b設計](docs/PHASE4B_DESIGN.md)・[ADR-0009](docs/adr/0009-phase4b-base-out-reference.md)は、2026-10-29に本人が[設計PR #22](https://github.com/thrsth45h/baseball-sim/pull/22)の範囲とworker実装開始を承認（D043限定CONFIRMED/ADR Accepted）。worker実装・責任者の独立レビュー後、[実装PR #23](https://github.com/thrsth45h/baseball-sim/pull/23)をmain `aff963e93408460a4d157313e151e4f9b96c9955`へマージした。確定済み合成3プレーの塁占有・アウト・半回得点を扱い、72表/16列・三アウト停止/残塁を検証。新4b32テスト・旧4a33テスト（別集計）、Python3.11/3.12 CI、独立期待値との1,578比較項目/138期待拒否、4成果物一致を確認した。[レビュー](docs/PHASE4B_REVIEW.md)を参照。

次の新しい実装仕様は未承認。走塁判断・単打/併殺・時系列の得点可否・4a/研究接続・完全な試合は含めず、次工程の対象と受入れ条件を設計して本人と相談する。D029/D031/D032はOPENを維持する。

[Phase 4c設計](docs/PHASE4C_DESIGN.md)・[ADR-0010](docs/adr/0010-phase4c-pitch-half-reference.md)は2026-10-30に本人が[設計PR #25](https://github.com/thrsth45h/baseball-sim/pull/25)の範囲を承認（D044限定CONFIRMED/Accepted）。worker実装・責任者の独立レビュー後、[実装PR #26](https://github.com/thrsth45h/baseball-sim/pull/26)をmain `40e1d1d54e9728bbee04c3f65300e57972b9c629`へマージ済み。旧4a/4bの型・stepへ明示Begin/Pitch/Resolveで委任し、結果待ちではhalfを反映せず、明示確定後にcount=Noneへ消費する。次打者も明示Beginを要求し、三アウトで残塁/得点を保持して停止する。 新4c28 test method/成功subTest3020、旧4a33/326・旧4b32/515を別集計し、責任者も別worktreeで全て成功、failure/error/skip0。対象headのPython3.11/3.12全CI成功。実装未参照で別計算した凍結期待値との11,468比較項目/439期待拒否が一致し、18列/82action/100分割、代表2,304投球、互換288試行（120受理/168拒否）、半回完了96拒否等を照合した。監視下runの許可外入力/RNG・時計利用0、主要4成果物は責任者の2runとworker公開版でバイト一致。公開helperも164trace/6run hashを独立再検算した。 [レビュー](docs/PHASE4C_REVIEW.md)・[実行手順](reports/phase4c/README.md)を参照。強い合成入力条件内の接続参照で、halfは最後に確定反映したsnapshot。省略された現実イベントの検出や古いsnapshot/通信再送の永続重複排除を保証しない。一般走塁/単打/併殺/第三アウト得点、公式個人成績、完全試合、研究/確率/能力、Android/UI/DB/セーブ・製品採用は未実施。D029/D031/D032はOPEN。次の新しい実装仕様は未承認で、設計相談から決める。

- 投球選択・制球位置・球速と変化量、フレーミング、ブロッキング、疲労、球場、守備・走塁。
- 1球単位のカウント、走者、アウト、得点、併殺、延長、交代、盗塁等。
- 公式記録と分析ログ、同じ乱数条件による結果再現のテスト。

[Phase 4d設計](docs/PHASE4D_DESIGN.md)・[ADR-0011](docs/adr/0011-phase4d-half-sequence.md)は本人の[設計PR #28](https://github.com/thrsth45h/baseball-sim/pull/28)範囲承認後、worker実装・責任者の独立レビュー後、[実装PR #29](https://github.com/thrsth45h/baseball-sim/pull/29)をmain `27c1c9df0d6286faf77334157eec2b236f94e68d`へマージ済み。旧4c.stepを再利用し、三アウト後の明示Advanceで表裏を切り替え、次半回だけを空の状態へ初期化する。切替済み累計と現在halfからscoreを導出し、Advance前後で両チームの合計を保持する。 新4d26 test method/成功subTest320、旧4c28/3020・旧4a33/326・旧4b32/515を別集計し、責任者も別worktreeで全て成功、failure/error/skip0。対象headのPython3.11/3.12全CI成功。実装未参照の独立した得点ledgerと21,612比較項目/709期待拒否が一致。16列/100action/116分割、96Advance、両side合計4,608投球・576互換試行（240受理/336拒否）、完了後192拒否を確認。追加1,000半回/14,500actionの最終state/score・全trace hashも一致した。主要4成果物は責任者の監視下2runとworker公開版でバイト一致し、許可外入力/RNG・時計利用0。公開helperも200trace/6run hashを独立再検算した。 [レビュー](docs/PHASE4D_REVIEW.md)・[再現手順](reports/phase4d/README.md)を参照。終了判定のない有限合成列のcontrollerであり、9回/延長/サヨナラ・勝敗・最終得点、一般走塁/公式記録・完全試合、研究モデル/能力、Android/UI/DB/セーブ・製品採用は未実施。強い合成入力条件とhalf snapshotの意味を維持し、省略された現実イベントの検出や古いsnapshot/通信再送の永続重複排除は保証しない。D029/D031/D032はOPEN。次の重要な実装仕様は未承認で、設計相談から決める。

[Phase 4e設計](docs/PHASE4E_DESIGN.md)・[ADR-0012](docs/adr/0012-phase4e-completed-safe-hits.md)は本人の[設計PR #31](https://github.com/thrsth45h/baseball-sim/pull/31)範囲承認済み（D046限定CONFIRMED/Accepted）。worker実装・責任者の独立レビュー後、[実装PR #32](https://github.com/thrsth45h/baseball-sim/pull/32)をmain `167a428a0bea8419b0b42ae4c70f4a11e6dcd0ce`へマージ済み。旧4d state/4actionとparser/stepを再利用し、fair待ちに裁定済み単打/二塁打/三塁打と明示走者行先を反映する。走者据置き・複数得点を許し、占有と行先の不一致・最終位置の衝突/逆走/順序矛盾を拒否する。打者は安打価値と等しい塁へ留まり、既存走者の本塁到達数だけ攻撃側へ加点してcountを消費する。 新4e24/18112、旧4d26/320、旧4c28/3020、旧4a33/326、旧4b32/515（test method/成功subTestは別集計）を責任者も別worktreeで実行し、全failure/error/skip0。対象headのPython3.11/3.12 CIも成功。実装未参照の識別走者・得点ledgerと39,275比較項目/18,046期待拒否を照合した。48受理表、固定18列/60action/78分割、有限18,000試行の288受理/17,712拒否が一致。追加の長い混在列3,150actionの最終state/scoreと全trace hashも一致した。主要4成果物は責任者の監視下2runとworker公開版でバイト一致、許可外入力/RNG・時計利用0。公開helperも120trace/6run hashを独立再検算した。 [レビュー](docs/PHASE4E_REVIEW.md)を参照。全員safe・正規触塁/裁定/得点が外部で確定し、打者最終塁と安打価値が等しい強い合成条件に限る。最終位置から途中履歴の合法性を証明せず、安打価値や走塁を予測/裁定しない。安打/進塁の確率、アウト/失策/第三アウト得点、試合終了/勝敗/サヨナラ・公式個人成績、研究モデル/能力・Android/UI/DB/セーブ・製品採用は未実施。新snapshotへの連続hitは拒否するが、古いsnapshotの純粋再適用・通信再送の永続重複排除を保証しない。D029/D031/D032はOPEN。次の重要な新仕様は設計相談と本人承認から決める。

## 次の到達目標 — 1試合を最後まで動かす統合試作（Phase 4f案）

本人は2026-10-31、研究の細分追加より、仮の2チーム・1球単位・試合終了・簡単な結果表示をPythonで先に通す方向に「じゃあ、それで次に進んで」と回答。[設計案](docs/PHASE4F_DESIGN.md)・[ADR-0013](docs/adr/0013-phase4f-single-game-prototype.md)はD047限定CONFIRMED/Accepted。2026-10-31、本人は方向に「じゃあ、それで次に進んで」、提示済み設計PR #34の具体案を再確認した質問には「いちいちきくな」と回答した。提示済み仕様による進行・責任者判断・都度確認の省略を指示したものとして記録し、この限定試作を実装へ進める。全細目を本人が個別精査したという記録にはしない。 設計マージ後にworker実装へ進む。

次の完成条件は、seed指定の1コマンドで9人ずつの合成2チームが1試合を完走し、得点と終了理由を読めること。既存の限定ルールと交換可能な一球入力stubを接続し、打順/終了/ログからの再集計/同seed再現をまとめて検証する。一つの実装工程として扱い、小機能ごとに新しい研究Phaseへ分けることは目的にしない。本人の進行/確認省略指示に基づき、設計マージ・worker実装・責任者の独立レビューへ進む。

D022の上流生成・MLB妥当性・能力較正は試作で完成とは扱わない。Android画面/12球団/経営はこの試作の後続として別に設計する。研究/旧4a–4eの結果は保持し、D029/D030/D031/D032もOPEN。

## Phase 5 — Androidゲームと長期ペナント

- UI、試合自動進行、セーブ、CPU監督、一・二軍、スカウト、育成、ドラフト、FA、トレード、球団財政。
- MLB水準のリーグ分布、能力の因果効果、長期的な選手層、容量と性能を大量シミュレーションで検証。

## 品質ゲート

- 予測：Log Loss/Brier、校正、未知選手、年度間安定性と不確実性。
- 野球：ルール・公式記録の不変条件、能力の直接・間接効果、リーグ平均と選手別分布。
- 開発：再現可能な乱数、実行途中からの再開時の入力同一性、セーブ互換性、テストと独立レビュー。

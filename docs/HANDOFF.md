# Codex間の引き継ぎ

2026-10-31 日本時間。GitHubで確認できた研究・承認・マージ状態を記す。仕様の確定性は[決定台帳](../DECISIONS.md)、運用は[WORKFLOW.md](WORKFLOW.md)を優先する。

## 参照先

- 仕様の正本：[DECISIONS.md](../DECISIONS.md)、[GAME_DESIGN.md](../GAME_DESIGN.md)
- 運用の正本：[docs/WORKFLOW.md](WORKFLOW.md)、[AGENTS.md](../AGENTS.md)
- 統計研究：[statcast-swing-model-v1](https://github.com/thrsth45h/statcast-swing-model-v1)（Phase 1b [PR #1](https://github.com/thrsth45h/statcast-swing-model-v1/pull/1) はマージ済み、squash SHA `f3b65653d90b004ea8bfaf233b94231876233980`）

## 現在の到達点

- **Phase 4eは実装・独立レビュー・マージまで完了。** worker実装・責任者の独立レビュー後、[実装PR #32](https://github.com/thrsth45h/baseball-sim/pull/32)をmain `167a428a0bea8419b0b42ae4c70f4a11e6dcd0ce`へマージ済み。旧4d state/4actionとparser/stepを再利用し、fair待ちに裁定済み単打/二塁打/三塁打と明示走者行先を反映する。走者据置き・複数得点を許し、占有と行先の不一致・最終位置の衝突/逆走/順序矛盾を拒否する。打者は安打価値と等しい塁へ留まり、既存走者の本塁到達数だけ攻撃側へ加点してcountを消費する。 D046限定CONFIRMED。
- 新4e24/18112、旧4d26/320、旧4c28/3020、旧4a33/326、旧4b32/515（test method/成功subTestは別集計）を責任者も別worktreeで実行し、全failure/error/skip0。対象headのPython3.11/3.12 CIも成功。実装未参照の識別走者・得点ledgerと39,275比較項目/18,046期待拒否を照合した。48受理表、固定18列/60action/78分割、有限18,000試行の288受理/17,712拒否が一致。追加の長い混在列3,150actionの最終state/scoreと全trace hashも一致した。主要4成果物は責任者の監視下2runとworker公開版でバイト一致、許可外入力/RNG・時計利用0。公開helperも120trace/6run hashを独立再検算した。 [レビュー](PHASE4E_REVIEW.md)・[公開手順](../reports/phase4e/README.md)を参照。
- 最終実装head `df9a5d5d7a137bd8ad57b1b212e12042bec40df9`、source/tests/fixtures/CI固定 `88d239ce33c8330402fe17d8a9f3ee4dab9c34e8`。実装時点で旧153パス中README案内以外152不変。公開16ファイル191,604bytes・18hashをworking/Git blob双方で照合。後続完了文書7件の変更は歴史的旧保護と区別する。研究main `cd8217a44788cf830c5554b71b04a27a98ce620c`/tree/作業状態は不変。研究575テストを新4eへ加算しない。 worker `work/phase4e-safe-hit-worker`、レビュー `work/phase4e-review-88d239c`とignored実行/検証資料を保持。
- **Phase 4dは実装・独立レビュー・マージまで完了。** worker実装・責任者の独立レビュー後、[実装PR #29](https://github.com/thrsth45h/baseball-sim/pull/29)をmain `27c1c9df0d6286faf77334157eec2b236f94e68d`へマージ済み。旧4c.stepを再利用し、三アウト後の明示Advanceで表裏を切り替え、次半回だけを空の状態へ初期化する。切替済み累計と現在halfからscoreを導出し、Advance前後で両チームの合計を保持する。 D045限定CONFIRMED。
- 新4d26 test method/成功subTest320、旧4c28/3020・旧4a33/326・旧4b32/515を別集計し、責任者も別worktreeで全て成功、failure/error/skip0。対象headのPython3.11/3.12全CI成功。実装未参照の独立した得点ledgerと21,612比較項目/709期待拒否が一致。16列/100action/116分割、96Advance、両side合計4,608投球・576互換試行（240受理/336拒否）、完了後192拒否を確認。追加1,000半回/14,500actionの最終state/score・全trace hashも一致した。主要4成果物は責任者の監視下2runとworker公開版でバイト一致し、許可外入力/RNG・時計利用0。公開helperも200trace/6run hashを独立再検算した。 [レビュー](PHASE4D_REVIEW.md)・[公開手順](../reports/phase4d/README.md)を参照。
- 最終実装head `898de25e62a3e81cf1ba2c0661a13e8fa4d5e0d1`、source/tests/fixtures/CI固定 `2fadced5b5da7b8387466641fd063180c5628e17`。実装時点で旧123パス中README案内以外122不変。公開15ファイル216,421bytes・17hashをworking/Git blob双方で照合。後続完了文書7件の変更は歴史的旧保護と区別する。研究main `cd8217a44788cf830c5554b71b04a27a98ce620c`/tree/作業状態は不変。研究575テストを新4dへ加算しない。 worker `work/phase4d-half-sequence-worker`、レビュー `work/phase4d-review-2fadced`とignored実行/検証資料を保持。
- **Phase 4cは限定接続の実装・独立レビュー・マージまで完了。** worker実装・責任者の独立レビュー後、[実装PR #26](https://github.com/thrsth45h/baseball-sim/pull/26)をmain `40e1d1d54e9728bbee04c3f65300e57972b9c629`へマージ済み。旧4a/4bの型・stepへ明示Begin/Pitch/Resolveで委任し、結果待ちではhalfを反映せず、明示確定後にcount=Noneへ消費する。次打者も明示Beginを要求し、三アウトで残塁/得点を保持して停止する。 D044は承認範囲のみCONFIRMED。
- 新4c28 test method/成功subTest3020、旧4a33/326・旧4b32/515を別集計し、責任者も別worktreeで全て成功、failure/error/skip0。対象headのPython3.11/3.12全CI成功。実装未参照で別計算した凍結期待値との11,468比較項目/439期待拒否が一致し、18列/82action/100分割、代表2,304投球、互換288試行（120受理/168拒否）、半回完了96拒否等を照合した。監視下runの許可外入力/RNG・時計利用0、主要4成果物は責任者の2runとworker公開版でバイト一致。公開helperも164trace/6run hashを独立再検算した。 [レビュー](PHASE4C_REVIEW.md)・[公開手順](../reports/phase4c/README.md)を参照。
- 最終実装head `baba99eaf56ebe264b0d1162e019a1cd5b16f331`、source/tests/fixtures/CI固定 `a1d2cbc4ae9f949babb2f350be9ed41193f477b5`。旧93パス中README案内以外92不変という実装時点の保護と、後続完了文書7件の変更を区別する。公開14ファイル151,315bytes・16hashをworking/Gitで照合。worker `work/phase4c-pitch-half-worker`、レビュー `work/phase4c-review-a1d2cbc`とignored実行/検証資料を保持。研究main `cd8217a44788cf830c5554b71b04a27a98ce620c`/tree/作業状態は不変。研究575テストを新4cへ加算しない。
- **Phase 4bは限定参照のworker実装・独立レビュー・マージまで完了。** 本人の「設計PR #22の範囲で進める（推奨）」に基づき設計main `59ae0e2f726b88d5729d026a0e293916c33a73e6`から委任。[実装PR #23](https://github.com/thrsth45h/baseball-sim/pull/23)は2026-10-29にmain `aff963e93408460a4d157313e151e4f9b96c9955`へマージ済み。確定済み合成W/X/Hから塁占有・アウト・半回得点を更新し、三アウトで残塁/得点を保持して停止する。4a/研究接続・走塁判断・公式個人成績・完全試合・Android採用は含めない。D043は限定参照のみCONFIRMED、D029/D031/D032はOPEN。
- 新4b32 test method/成功subTest515（別集計）、旧4a33 method/326（別集計）を責任者も別worktreeで実行、failure/error/skip0。対象headのPython3.11/3.12 CI成功。worker未参照の識別走者による独立期待値と1,578比較項目/138期待拒否が一致し、72遷移・16列30イベント/46分割等を確認。4成果物がworker版とバイト一致し、公開helperも独立再実行した。[レビュー](PHASE4B_REVIEW.md)・[再現手順](../reports/phase4b/README.md)を参照。
- 最終実装head `ab6f1f2915416e5797b87947bfa815e075a7efc5`、source/tests/fixtures/CI固定 `7f98536c8659d6fa6bd0bbe94ab6e48e8ee4fb3b`。実装時点で旧65パス中README案内以外64不変、公開13ファイル62,329bytes・15hashをworking/Gitで照合。後続完了文書の変更とは区別する。worker `work/phase4b-base-out-worker`、レビュー `work/phase4b-review-7f98536`とignored実行/テスト/補助検証資料を保持。研究main `cd8217a44788cf830c5554b71b04a27a98ce620c`は不変、研究575テストを4bへ加算していない。
- **Phase 4aはゲーム側の限定ルール参照実装・独立レビュー・修正・マージまで完了。** 本人の「設計PR #19の範囲で進める（推奨）」に基づき[設計PR #19](https://github.com/thrsth45h/baseball-sim/pull/19)をmain `1c5b738671a452305a4be874c57f77cd8ad5db10`へマージしてworkerへ委任。[実装PR #20](https://github.com/thrsth45h/baseball-sim/pull/20)はmain `4d3a92392d5adb4be9ddd2db4ef16ab3ca3a9977`へマージ済み。裁定済み8合成イベントのカウント/限定終端/打球受渡しだけを扱い、完全な打席/試合やAndroid技術の採用ではない。D042は限定参照のみCONFIRMED、D029/D031/D032はOPEN。
- Phase 4aは全33 test method成功（subTest326は別集計、failure/error/skip0）と最終実装headのPython3.11/3.12 CI成功。責任者も別worktreeで実行し、4主要成果物が提出版とバイト一致。worker未参照の別式による凍結期待値との10,289項目照合で、96遷移・16列/79イベント・95分割位置・状態境界/拒否等が一致。[レビュー](PHASE4A_REVIEW.md)、[公開手順](../reports/phase4a/README.md)を参照。研究575テストを4aへ加算していない。
- 固定16列の同一最終状態への置換を見逃すP2をworkerへ直接修正依頼し、5置換の拒否を確認した。最終実装head `9ca76f0ff0c2d13cbd00298766f7b9f80b67d1d1`、source/tests/fixtures/CI固定 `922a2a9c1cb5a76e9d984fc5bd5c9fef2952b7ae`。旧35文書保護は設計baseline→固定実装時点の確認とし、以後の完了文書更新を別記するhelper修正を加えた。worker `work/phase4a-count-worker`、レビュー `work/phase4a-review-4d3aab6`、ignoredの旧公開版/実行/検証資料は保持。研究repoはmain `cd8217a44788cf830c5554b71b04a27a98ce620c`のまま変更しない。
- [Phase 3d設計v0.1](PHASE3D_DESIGN.md)・[ADR-0007](adr/0007-phase3d-inflight-reach.md)も合成研究実装完了。2026-10-29、本人が「設計PR #17の範囲・仮設定で進める（推奨）」と承認し、[設計PR #17](https://github.com/thrsth45h/baseball-sim/pull/17)をmain `863e78756448003363087994692ff85a2d79a428`へマージ後workerへ委任。責任者の独立レビューと修正確認後、[研究PR #10](https://github.com/thrsth45h/statcast-swing-model-v1/pull/10)をmain `cd8217a44788cf830c5554b71b04a27a98ce620c`へマージした。D041は合成研究のみCONFIRMED。真の軌道を既知とした飛行中の幾何的到達時刻集合であり、実際の捕球/認知・実データ/3b・能力・公式記録・ゲーム採用は含めない。
- Phase 3dは全575テスト・最終headのPython3.11/3.12 PR CI成功。責任者も別worktreeで全575テスト/CLIとhelperを実行し、4主要成果物が提出版とバイト一致。worker未参照の160桁Decimal/厳密符号による独立期待値と、20例/14区間・追加数理oracle等940項目/91回の端点照合が一致。追加16不正入力拒否と新runの監視対象旧入力/RNG試行0も確認。[詳細](STATCAST_STATUS.md)、[レビュー](PHASE3D_REVIEW.md)を参照。
- 初版のnamespace転送による旧領域への出力を実Windows junctionで再現し、workerへ直接修正依頼。4回帰・責任者の旧junction再確認で拒否を確認した。公開13ファイル106,377bytes、新17/旧228hash、旧358パス中README案内以外357不変をworking/Git双方で確認。worker領域`work/phase3d-inflight-worker`、レビュー領域`work/phase3d-review-710e999`、ignoredの旧資料/再実行を保持。最終レビューhead `238e63468c8bcb2679654f5fdde83b109c1e2a98`は研究mainと同じtree、source/tests固定は`0b13877a12ac59b5cb4ea1d70f9c376f5f9b9261`。
- [Phase 3c設計v0.1](PHASE3C_DESIGN.md)・[ADR-0006](adr/0006-phase3c-synthetic-flight.md)も合成研究実装完了。2026-10-29、本人が「設計PR #15の範囲・仮設定で進める（推奨）」と承認し、[設計PR #15](https://github.com/thrsth45h/baseball-sim/pull/15)をマージ後にworkerへ直接委任。責任者レビュー・報告訂正の確認後、[研究PR #9](https://github.com/thrsth45h/statcast-swing-model-v1/pull/9)をmain `015b9fdcd18565270b8ce888bd703aead8c63864`へマージした。D040は合成研究範囲のみCONFIRMED。実データ/3b接続・捕球・能力・ゲーム採用は含めない。
- Phase 3cは全497テスト・最終headのPython3.11/3.12 PR CI成功。責任者も別worktreeで全497テストとrunを実行し、4主要成果物が提出版とバイト一致。workerの新実装/結果を参照しない100桁Decimal・三角級数・二分法の期待値と、12例/396点・1,716数値を含む1,786項目が一致した。追加16不正入力の拒否と旧データ/モデル/RNG禁止下のrunも確認。[詳細](STATCAST_STATUS.md)を参照。
- 検証helperの旧読取り否定フラグを指摘し、raw非読取り・旧成果物のhash用バイト読取り・3c計算での旧モデル不使用へ分離した。workerが訂正し、責任者がhelperを再実行して確認。公開13ファイル、新17/旧211hash、旧338パス中README案内以外337不変を確認した。Phase 3c作業領域`work/phase3c-synthetic-worker`とレビュー領域`work/phase3c-review-6cebf8b`、ignoredの旧資料/再実行は保持。最終レビューheadは`577bdd9a624ab6aed90b2d2ae9df1eba1201a9cd`でPhase 3c完了時の研究mainと同じtree。
- [Phase 3b設計v0.1](PHASE3B_DESIGN.md)・[ADR-0005](adr/0005-phase3b-numeric-generator.md)も研究実装完了。2026-10-29、本人が「設計PR #13の研究範囲・仮設定で進める（推奨）」と承認し、[設計PR #13](https://github.com/thrsth45h/baseball-sim/pull/13)をマージ後、workerへ直接委任。責任者の独立レビュー後に[研究PR #8](https://github.com/thrsth45h/statcast-swing-model-v1/pull/8)をmain `35c9fe6fbe2b01c02b7253ed336526e6960cd4fe`へマージした。D039は研究範囲のみCONFIRMEDで、能力・物理的妥当性・ゲーム採用へ承認を拡張しない。
- Phase 3bは全439テスト・Python3.11/3.12のPR CI成功。責任者の別実行で12成果物がバイト一致し、worker未参照の事前凍結期待値と1,232項目が一致。公開46ファイルと新77/旧134のhash登録をGit blobも含めて確認。旧285パス中、README案内以外284は不変。2024尾部6件での悪化・2025尾部3件の差0も報告した。[詳細](STATCAST_STATUS.md)を参照。
- 実データに基づく方向生成・全接触への外挿・実際の守備/捕球・能力・ゲーム統合の新しい実装は未承認。合成の軌道/距離余裕と飛行中の幾何的機会は、前掲Phase 3c/3dとして別に承認した。Phase 3bの作業領域`work/phase3b-numeric-worker`とレビュー領域`work/phase3b-review-97c741d`、ignoredの再実行出力は保持している。レビュー領域の最終HEADは`c2be7b0c4ee959e96d9e56c1ba62bed6b55d8242`で、Phase 3b完了時の研究mainと同じtree。
- Phase 1bの2023〜2025年通年研究、Phase 1cの合成知覚プロトタイプ、Phase 1dの合成識別研究・入力監査機能が研究側mainへマージ済み。
- Phase 1dは別の実装担当によるコードを設計・レビュー担当が点検。レビュー側でも240テスト成功、完全実行の設定・数値集計・積分結果の3ファイルが提出版とバイト単位で一致。対象headのPython 3.11／3.12 CI成功を確認した。
- 主要ΔAUC=0.13308019、95%区間[0.12948814, 0.13627906]は固定した合成課題内の結果。MLB選手の能力差・認知妥当性・製品係数の実測値ではない。
- D6の実データ入力監査も[研究PR #4](https://github.com/thrsth45h/statcast-swing-model-v1/pull/4)で完了・マージ済み。2023〜2025年の処理済み3ファイルと選定raw3 chunkの来歴・ハッシュ・スキーマ・先頭標本を確認。旧 `not_run:no_explicit_input` 記録は履歴として保存し、新しい監査結果と区別する。
- レビュー側でも元ファイル6件と関連来歴計15件、raw選定規則、提出49ファイルのGit blob hashを照合し、12通りの監査関数の再実行結果が提出版と一致。既存149パスに変更・削除なし、対象headのPython 3.11／3.12 CI成功を確認した。
- Androidアプリ本体、公開能力1〜20の較正、ゲームへのモデル採用は未完了。既存M0/M1/M2・Phase 1b/1cの結果・推論契約は維持する。
- Phase 2aもworker実装・責任者の独立コードレビュー・修正を経て[研究PR #5](https://github.com/thrsth45h/statcast-swing-model-v1/pull/5)をマージ済み。全272テストとPython 3.11／3.12 CI成功。責任者の別チェックアウトで全量学習・評価を再実行し、数値・モデル等11ファイルがworker版とバイト一致。別式による特徴・候補選択・指標・区間も1e-12以内で一致した。
- レビューで公開reference JSONのcheckout改行依存による誤拒否を再現し、workerへ直接修正依頼。元入力の生バイトhash保護を保ち、全metadata内容による参照照合と回帰テストを追加した。ユーザーによる仲介は行っていない。
- Phase 2bの分類・入力欠損監査もworker実装・責任者の独立レビュー・修正を経て[研究PR #6](https://github.com/thrsth45h/statcast-swing-model-v1/pull/6)をマージ済み。研究main `2af9657854b70a3dd1ce3db7dd8ae296cdd8745d`。全300テスト・最終headのPython 3.11/3.12 CI成功、責任者の別実行と独立期待値435項目も一致。旧数値・モデル・ラベル・旧Phase2a文書とhash索引を保存し、別冊訂正を追加した。
- Phase 3aの固定72区分研究もworker実装・責任者の独立レビュー・修正を経て[研究PR #7](https://github.com/thrsth45h/statcast-swing-model-v1/pull/7)をマージ済み。研究main `c155a6c0af899f2946dbcc4292c680a121fe781b`。全368テスト・最終headのPython 3.11/3.12 CI成功。区分抽選までの研究で、能力値・連続打球・物理分岐・ゲーム採用は未達。

## 確認済みPRとcommit

| 対象 | URL | mainへのマージcommit |
|---|---|---|
| 運用導入 | [ゲームPR #1](https://github.com/thrsth45h/baseball-sim/pull/1) | `546c606` |
| Phase 1c設計文書 | [ゲームPR #2](https://github.com/thrsth45h/baseball-sim/pull/2) | `568730e066c47ebcda2fba5a103f682271623a25` |
| Phase 1b通年研究 | [研究PR #1](https://github.com/thrsth45h/statcast-swing-model-v1/pull/1) | `f3b65653d90b004ea8bfaf233b94231876233980` |
| Phase 1c合成プロトタイプ | [研究PR #2](https://github.com/thrsth45h/statcast-swing-model-v1/pull/2) | `6d82eddaa51d72126c011e14a0d511634d58e300` |
| Phase 1d研究設計v1.1 | [ゲームPR #4](https://github.com/thrsth45h/baseball-sim/pull/4) | `cecc3c372ae45c22561b97a59f731f463849f198` |
| Phase 1d研究実装 | [研究PR #3](https://github.com/thrsth45h/statcast-swing-model-v1/pull/3) | `cba081a13d53684e037738b53a65bdce2abea9a3` |
| D6既存実データ入力監査 | [研究PR #4](https://github.com/thrsth45h/statcast-swing-model-v1/pull/4) | `4bf94dee60de9e33cfd7f16e7439cac34a36a6fc` |
| Phase 2a設計・研究実装承認 | [ゲームPR #7](https://github.com/thrsth45h/baseball-sim/pull/7) | `0804552fe4ae691506d00285bf9315375fbada66` |
| Phase 2a接触予測研究 | [研究PR #5](https://github.com/thrsth45h/statcast-swing-model-v1/pull/5) | `e5a0c18ba2b9f1cdec068c3822a6fb863c0fec25` |
| Phase 2b分類監査の設計・承認 | [ゲームPR #9](https://github.com/thrsth45h/baseball-sim/pull/9) | `3782e1e6facd291de1ab095725ccc887c583d129` |
| Phase 2b分類・欠損監査と訂正 | [研究PR #6](https://github.com/thrsth45h/statcast-swing-model-v1/pull/6) | `2af9657854b70a3dd1ce3db7dd8ae296cdd8745d` |
| Phase 3a設計・研究実装承認 | [ゲームPR #11](https://github.com/thrsth45h/baseball-sim/pull/11) | `1ff2f4ae74616daca14f3ebb8c4b7d732f6486e7` |
| Phase 3a固定72区分の分布・抽選 | [研究PR #7](https://github.com/thrsth45h/statcast-swing-model-v1/pull/7) | `c155a6c0af899f2946dbcc4292c680a121fe781b` |
| Phase 3b設計・研究仮設定・実装承認 | [ゲームPR #13](https://github.com/thrsth45h/baseball-sim/pull/13) | `ebf37343da4932f9bd483753b691c366c510a3f2` |
| Phase 3b区分内情報比較・EV/LA数値抽選 | [研究PR #8](https://github.com/thrsth45h/statcast-swing-model-v1/pull/8) | `35c9fe6fbe2b01c02b7253ed336526e6960cd4fe` |
| Phase 3c合成研究の設計・仮設定・実装承認 | [ゲームPR #15](https://github.com/thrsth45h/baseball-sim/pull/15) | `ab7cce68673d2a55f96424107d7d5e2597e32571` |
| Phase 3c合成軌道・地面到達・移動余裕 | [研究PR #9](https://github.com/thrsth45h/statcast-swing-model-v1/pull/9) | `015b9fdcd18565270b8ce888bd703aead8c63864` |
| Phase 3d合成研究の設計・仮設定・実装承認 | [ゲームPR #17](https://github.com/thrsth45h/baseball-sim/pull/17) | `863e78756448003363087994692ff85a2d79a428` |
| Phase 3d飛行中の幾何的到達時刻集合 | [研究PR #10](https://github.com/thrsth45h/statcast-swing-model-v1/pull/10) | `cd8217a44788cf830c5554b71b04a27a98ce620c` |
| Phase 4a限定ルール参照の設計・実装承認 | [ゲームPR #19](https://github.com/thrsth45h/baseball-sim/pull/19) | `1c5b738671a452305a4be874c57f77cd8ad5db10` |
| Phase 4aカウント/限定終端の参照実装 | [ゲームPR #20](https://github.com/thrsth45h/baseball-sim/pull/20) | `4d3a92392d5adb4be9ddd2db4ef16ab3ca3a9977` |
| Phase 4a補助検証の時点訂正・完了記録 | [ゲームPR #21](https://github.com/thrsth45h/baseball-sim/pull/21) | `340602d94466d4af1f07ea559d458650d2e5c85e` |
| Phase 4b限定参照の設計・実装承認 | [ゲームPR #22](https://github.com/thrsth45h/baseball-sim/pull/22) | `59ae0e2f726b88d5729d026a0e293916c33a73e6` |
| Phase 4b塁・アウト・半回得点の参照実装 | [ゲームPR #23](https://github.com/thrsth45h/baseball-sim/pull/23) | `aff963e93408460a4d157313e151e4f9b96c9955` |
| Phase 4b完了記録・独立レビューの引き継ぎ | [ゲームPR #24](https://github.com/thrsth45h/baseball-sim/pull/24) | `197add4704dc1f8fe64d41037ede25cf73ba6876` |
| Phase 4c限定接続の設計・実装承認 | [ゲームPR #25](https://github.com/thrsth45h/baseball-sim/pull/25) | `06256f8a4f0f8795709f56365f1ea212e2090092` |
| Phase 4c投球/半回snapshotの明示接続 | [ゲームPR #26](https://github.com/thrsth45h/baseball-sim/pull/26) | `40e1d1d54e9728bbee04c3f65300e57972b9c629` |
| Phase 4c完了記録 | [ゲームPR #27](https://github.com/thrsth45h/baseball-sim/pull/27) | `91d5e1f40730e09d4970e0960a10b3c658fc65b8` |
| Phase 4d半回列制御の設計・実装承認 | [ゲームPR #28](https://github.com/thrsth45h/baseball-sim/pull/28) | `b9f9b5830f9247691b4d05b0f81ad8fe84c12f13` |
| Phase 4d表裏切替と両チーム得点 | [ゲームPR #29](https://github.com/thrsth45h/baseball-sim/pull/29) | `27c1c9df0d6286faf77334157eec2b236f94e68d` |
| Phase 4d完了記録 | [ゲームPR #30](https://github.com/thrsth45h/baseball-sim/pull/30) | `7343b6179aedbdc6f42c3aa22d887dac7bcdbf1a` |
| Phase 4e確定済み安打の設計・実装承認 | [ゲームPR #31](https://github.com/thrsth45h/baseball-sim/pull/31) | `e1af536153c36ab34be8199a3707ff0c1c9ba0fb` |
| Phase 4e確定済み安打と明示走者行先 | [ゲームPR #32](https://github.com/thrsth45h/baseball-sim/pull/32) | `167a428a0bea8419b0b42ae4c70f4a11e6dcd0ce` |

Phase 1d設計書・ADRのProposedは作成時の表記。研究範囲の採用・マージ記録はゲームPR #4本文にある。研究実装の独立レビュー・ユーザー許可・マージ記録は研究PR #3本文にある。これは製品採用・能力係数の承認ではない。

ゲームPR #3はPhase 1d完了とD6手順の引き継ぎ更新としてマージ済み（`664beeda425f80b61deb136ddd434a244cdcf933`）。以前の「Stage Aが次工程」「Phase 1c実装未登録」という記載は更新済み。

## 次工程

2026-10-31、本人は「1試合を最後まで動かす統合試作」を優先する方向に「じゃあ、それで次に進んで」と回答。最新main `00b31a313bdc141ed7184965fd921adec219ed24`からdesign/phase4f-single-game-prototypeへ分岐し、[Phase 4f案](PHASE4F_DESIGN.md)・[ADR-0013](adr/0013-phase4f-single-game-prototype.md)を[設計PR #34](https://github.com/thrsth45h/baseball-sim/pull/34)へ作成した。**方向は承認済み、具体的な仮確率/一定進塁・9回打切り/裏省略/サヨナラ・出力/未完了停止の仕様は未承認、D047 OPEN/Proposed。** 新4fコード/テスト/結果は作成しない。

案の完成条件は、Pythonの1コマンドで合成2チーム各9打順が1球ずつ進み、終了・得点・終了理由が読めること。旧4eの非サヨナラ前提を維持し、サヨナラは新4f専用終端へ先に分岐。上流の投球/知覚/打球生成はstub内で未実装、EV/LA/Savant値や能力係数を後付けしない。具体設計を本人が承認後、設計マージ→専用worker→責任者レビューへ進む。既存worker/review/ignored資料を保持し、D029/D030/D031/D032はOPEN、正式規約/研究モデル/Android採用は含めない。

2026-10-28の再開時、限定rawと既存適格入力の一対一照合から、hit_into_playに記録上の犠牲バント4件とファウル地域のfield_out 62件を確認した。「通常スイングだけ」「fair_in_playがフェアを保証」という従来説明を訂正し、旧二値ターゲット・機械可読結果・数値を保存した。限定標本の証拠・根拠hash・代替案は[Phase 2b設計](PHASE2B_DESIGN.md)を参照。

**分類・入力監査は実装・独立レビュー・マージまで完了。[設計PR #9](https://github.com/thrsth45h/baseball-sim/pull/9)、D037 CONFIRMED／ADR-0003 Accepted。** 2026-10-28、平易な説明後の本人回答「勝手に進めて良い」により、旧結果を保存した監査と説明訂正を承認。責任者がworkerへ直接委任し、レビュー指摘を直接修正依頼して[研究PR #6](https://github.com/thrsth45h/statcast-swing-model-v1/pull/6)を統合した。新しい予測学習・能力較正・ゲーム統合は含めない。

年度hit_into_play内のsac_bunt接頭辞記録は2023/2024/2025で429 / 452 / 560件。raw標本は14,707 / 690 / 631行で、適格対応14,629 / 683 / 623、非対応78 / 7 / 8。対応hit_into_play内のfoul territory記述のfield_outは62 / 3 / 4件。全バントや物理的な真値の完全判定ではなく、標本比率を年全体へ外挿しない。

責任者は修正版を別チェックアウトで再実行し、提出版とplan/audit/REPORTがバイト一致。旧220パス中、既存変更はroot READMEの案内のみで219パス不変。旧Phase2a索引と掲載12ファイル、新公開索引9件のworking/Git blob hashも検証した。観察フラグの非該当を「原因未確認」と呼んでいた表現はworkerが修正し、全非対応行で原因確定はnot_establishedを明記した。

成果物は[監査レポート](https://github.com/thrsth45h/statcast-swing-model-v1/blob/2af9657854b70a3dd1ce3db7dd8ae296cdd8745d/reports/phase2b/label-audit/REPORT.md)と[別冊訂正](https://github.com/thrsth45h/statcast-swing-model-v1/blob/2af9657854b70a3dd1ce3db7dd8ae296cdd8745d/reports/phase2a/ERRATA.md)。workerは専用branch `impl/phase2b-label-audit`、レビュー済みhead `378e6de884c5c42cce67af5bb4f8dea497d051a5`。再現runは同worktreeのGit除外 `data/phase2b/replay-02` に保持。

**Phase 3aは区分分布・区分抽選の研究実装とレビュー・マージまで完了。** 次は、数値のある記録集合から、連続値・方向・守備前の物理生成へどう接続するかを設計・相談する。次工程の実装仕様は未承認。分類完全性、欠損の無作為性、直接測定か推定かの個別判定、能力較正を研究完了から推論しない。

正本は[設計PR #11](https://github.com/thrsth45h/baseball-sim/pull/11)、[Phase 3a設計v0.1](PHASE3A_DESIGN.md)、[設計点検](PHASE3A_REVIEW.md)、[ADR-0004](adr/0004-phase3a-binned-joint-study.md)。2026-10-29、具体的な研究対象・入力145登録・区分抽選・worker実装を説明した質問に本人が「この研究範囲で進める（推奨）」を選択して承認。D038は研究範囲のみCONFIRMED。「次へ」だけを承認とは扱っていない。責任者がworkerへ直接委任し、指摘を直接修正依頼して再レビューした。能力換算・連続値・方向・物理フェア・守備・得点・ゲーム統合は含めない。

研究PR #7のレビュー済みheadは `de576db07e56e89c3230b9136f9bd6cc7bb24c5b`。登録145ファイル・2,145,111行のhash/必要9列/キー/日付/Rと年度間重複を確認。新しい数値適格記録集合の採用数は2023/2024/2025で123,886 / 123,832 / 124,450。旧processedの位置等の適格条件を継承せず、旧研究の分母は保存した。

2023だけでJ0/J1を固定し、2024は診断、既閲覧2025のカテゴリLog LossはJ0=3.748952105439、J1=3.643953700041。主差−0.104998405397、固定500回の対応game bootstrap区間[−0.107178779512, −0.102401600251]。同じ72区分の比較であり、Phase 2aの二値損失と直接比較しない。周辺割合を揃えたモデルの組合せの比較で、能力や物理的な因果効果ではない。

責任者側の別実装も2023モデルを未来年度の読取り前に保存・hash固定して検算し、責任者がそのコードを点検。修正版の別チェックアウト実行との1,137項目が一致（最大差6.25e-17以内）、公開10成果物は責任者実行とバイト一致。公開hash68登録・旧索引と既存236パスの保持も確認。モデル配列の取り違えを整合hash付きでも拒否する意味検査をworkerに追加させ、全368テストで再確認した。

公開は[研究報告](https://github.com/thrsth45h/statcast-swing-model-v1/blob/c155a6c0af899f2946dbcc4292c680a121fe781b/reports/phase3a/real-03/evaluate/REPORT.md)、[再現手順](https://github.com/thrsth45h/statcast-swing-model-v1/blob/c155a6c0af899f2946dbcc4292c680a121fe781b/reports/phase3a/README.md)。workerの専用branch `impl/phase3a-binned-joint`、修正版03を公開し、同worktreeのGit除外 `data/phase3a/real-repeat-04` を再現実行として保持。旧01/02もGit除外に保存し、最終公開bundleへ混在させていない。

ユーザーは2026-10-28にPhase 2の方向を選択。その後、設計PR #7と具体的なv0.1の範囲を示した「workerに研究用実装を委任してよいですか？」への **「うん」** を、研究仕様と実装開始の明示承認として記録した。D036、[承認PR #7](https://github.com/thrsth45h/baseball-sim/pull/7)を参照。

Phase 2aの正本は[設計v0.1](PHASE2A_DESIGN.md)、[ADR-0002](adr/0002-phase2a-contact-research.md)、[設計時点検](PHASE2A_REVIEW.md)。指定5description適格行への二値接触予測、4タグ保持、C0/C1/C2比較、2023学習・2024選択・2025既閲覧年度への再現比較。**能力較正・ゲーム統合は含めない。** 実装・責任者のレビュー記録は研究PR #5を参照。workerの専用ブランチは `impl/phase2a-contact`。その実装時点では元の199追跡パスは内容不変。

2024はlambda10/C2を選択。2025の主要ΔLog Loss=C2−C1は−0.005506800386609、固定500回の試合対応bootstrap区間は[−0.005927426032991, −0.005080158586316]。C2のLog Loss/Brier/AUC改善と、ECE悪化（C1=0.00908137、C2=0.01041255）を両方報告した。既閲覧年度の観測スイングに条件付けた予測で、認知的技能や公開三振回避能力の真値ではない。

フルモデルと再実行成果物は責任者が用意した専用作業領域のGit除外 `data/phase2a/real-fit-04` / `05`、`real-evaluate-04` / `05` に保存。公開は約210KBの集計・設定・hash・人工例のみ。新しいworkerへデータを渡す際は責任者が実パスと同一性を確認し、ユーザーに取次ぎを求めない。

[D6監査レポート](https://github.com/thrsth45h/statcast-swing-model-v1/blob/4bf94dee60de9e33cfd7f16e7439cac34a36a6fc/reports/phase1d/real-input-audit/REPORT.md)と、実施した[監査手順](PHASE1D_DATA_AUDIT_HANDOFF.md)を参照。rawは119列、processedは27列。rawに軌道係数6列はあるが、推定に用いた時間窓や打者の主観的知覚の観測は立証できず、`not_verified / no_independent_early_observation` を維持する。

入力の同一性とスキーマは確認できたが、全raw145 chunkの現物・全年度の値を再検査したわけではない。事後的な行動予測比較の入力列はある一方、新方式のMLB用潜在観測・周辺確率・座標/スコア契約は未整備。人工W0のGaussian式を実CSVへ流用しない。

Phase 2bの通常ファウル・ファウルチップ・インプレー分岐は、範囲と評価・接続方法を相談してから別設計PRにする。Phase 2a完了を理由に選球眼の認知能力推定・公開能力較正・打席遷移・製品採用を開始しない。

## 継続する運用指示

2026-10-28、ユーザー本人が運用を変更。**この責任者が設計・相談・レビュー・進行管理を担当し、仕様の承認後はworkerサブエージェントへ実装を直接委任する。** 実装完了後は責任者が独立してコードと検証を確認し、問題があればworkerへ直接修正させる。ユーザーによるエージェント間の仲介・依頼文転送は不要。重要な設計判断・仕様変更は引き続きユーザーの承認を得る。

責任者・workerとも、作業前にブランチ・HEAD・未コミット/未追跡の変更を確認し、既存変更を上書きしない。書込み領域を分離する。詳細は[運用](WORKFLOW.md)を参照。過去の監査手順文書に残る「実装担当へ送る依頼文」は当時の記録であり、今後ユーザーに転送を求める指示ではない。

ユーザー本人は2026-10-28に **「以降問題なかったらマージまでやってください」** と指示した。研究PR #3のマージ記録にも転記済み。

今後は、承認済み範囲のPRにレビュー・必要な検証上の問題がなければ、都度のマージ確認を求めずマージまで進める。新仕様の採否・能力係数・製品採用をCodexが独断で承認する指示ではない。問題がある場合は解決してから判断する。

## 残る未解決事項

- 選球眼と積極性の認知的な識別、感覚誤差・判断期限・固定スコアの外的根拠。
- 公開能力1〜20の効果幅・分布・相関・較正アンカー。M2 dを選球眼の正解にしない。
- 2025年は研究で既閲覧。後続比較で未使用holdoutと呼ばない。2026年以降の座標・ゾーン定義差は別途検証が必要。
- 未知打者、入力欠損・分類、商用利用・派生物の権利確認、後続のコンタクト・打球・試合モデル。

詳細は[研究状況](STATCAST_STATUS.md)、[Phase 1d設計](PHASE1D_DESIGN.md)、[ロードマップ](../ROADMAP.md)を参照。

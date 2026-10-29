# Codex間の引き継ぎ

2026-10-29 日本時間。GitHubで確認できた研究・承認・マージ状態を記す。仕様の確定性は[決定台帳](../DECISIONS.md)、運用は[WORKFLOW.md](WORKFLOW.md)を優先する。

## 参照先

- 仕様の正本：[DECISIONS.md](../DECISIONS.md)、[GAME_DESIGN.md](../GAME_DESIGN.md)
- 運用の正本：[docs/WORKFLOW.md](WORKFLOW.md)、[AGENTS.md](../AGENTS.md)
- 統計研究：[statcast-swing-model-v1](https://github.com/thrsth45h/statcast-swing-model-v1)（Phase 1b [PR #1](https://github.com/thrsth45h/statcast-swing-model-v1/pull/1) はマージ済み、squash SHA `f3b65653d90b004ea8bfaf233b94231876233980`）

## 現在の到達点

- [Phase 3c設計v0.1](PHASE3C_DESIGN.md)・[ADR-0006](adr/0006-phase3c-synthetic-flight.md)も合成研究実装完了。2026-10-29、本人が「設計PR #15の範囲・仮設定で進める（推奨）」と承認し、[設計PR #15](https://github.com/thrsth45h/baseball-sim/pull/15)をマージ後にworkerへ直接委任。責任者レビュー・報告訂正の確認後、[研究PR #9](https://github.com/thrsth45h/statcast-swing-model-v1/pull/9)をmain `015b9fdcd18565270b8ce888bd703aead8c63864`へマージした。D040は合成研究範囲のみCONFIRMED。実データ/3b接続・捕球・能力・ゲーム採用は含めない。
- Phase 3cは全497テスト・最終headのPython3.11/3.12 PR CI成功。責任者も別worktreeで全497テストとrunを実行し、4主要成果物が提出版とバイト一致。workerの新実装/結果を参照しない100桁Decimal・三角級数・二分法の期待値と、12例/396点・1,716数値を含む1,786項目が一致した。追加16不正入力の拒否と旧データ/モデル/RNG禁止下のrunも確認。[詳細](STATCAST_STATUS.md)を参照。
- 検証helperの旧読取り否定フラグを指摘し、raw非読取り・旧成果物のhash用バイト読取り・3c計算での旧モデル不使用へ分離した。workerが訂正し、責任者がhelperを再実行して確認。公開13ファイル、新17/旧211hash、旧338パス中README案内以外337不変を確認した。Phase 3c作業領域`work/phase3c-synthetic-worker`とレビュー領域`work/phase3c-review-6cebf8b`、ignoredの旧資料/再実行は保持。最終レビューheadは`577bdd9a624ab6aed90b2d2ae9df1eba1201a9cd`で研究mainと同じtree。
- [Phase 3b設計v0.1](PHASE3B_DESIGN.md)・[ADR-0005](adr/0005-phase3b-numeric-generator.md)も研究実装完了。2026-10-29、本人が「設計PR #13の研究範囲・仮設定で進める（推奨）」と承認し、[設計PR #13](https://github.com/thrsth45h/baseball-sim/pull/13)をマージ後、workerへ直接委任。責任者の独立レビュー後に[研究PR #8](https://github.com/thrsth45h/statcast-swing-model-v1/pull/8)をmain `35c9fe6fbe2b01c02b7253ed336526e6960cd4fe`へマージした。D039は研究範囲のみCONFIRMEDで、能力・物理的妥当性・ゲーム採用へ承認を拡張しない。
- Phase 3bは全439テスト・Python3.11/3.12のPR CI成功。責任者の別実行で12成果物がバイト一致し、worker未参照の事前凍結期待値と1,232項目が一致。公開46ファイルと新77/旧134のhash登録をGit blobも含めて確認。旧285パス中、README案内以外284は不変。2024尾部6件での悪化・2025尾部3件の差0も報告した。[詳細](STATCAST_STATUS.md)を参照。
- 実データに基づく方向生成・全接触への外挿・実際の守備/捕球・能力・ゲーム統合の新しい実装は未承認。合成の軌道・距離余裕だけは前掲Phase 3cとして別に承認した。Phase 3bの作業領域`work/phase3b-numeric-worker`とレビュー領域`work/phase3b-review-97c741d`、ignoredの再実行出力は保持している。レビュー領域の最終HEADは`c2be7b0c4ee959e96d9e56c1ba62bed6b55d8242`で、Phase 3b完了時の研究mainと同じtree。
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

Phase 1d設計書・ADRのProposedは作成時の表記。研究範囲の採用・マージ記録はゲームPR #4本文にある。研究実装の独立レビュー・ユーザー許可・マージ記録は研究PR #3本文にある。これは製品採用・能力係数の承認ではない。

ゲームPR #3はPhase 1d完了とD6手順の引き継ぎ更新としてマージ済み（`664beeda425f80b61deb136ddd434a244cdcf933`）。以前の「Stage Aが次工程」「Phase 1c実装未登録」という記載は更新済み。

## 次工程

2026-10-29、Phase 3c完了後の「つぎへ」を受け、[Phase 3d案v0.1](PHASE3D_DESIGN.md)・[設計点検](PHASE3D_REVIEW.md)・[ADR-0007](adr/0007-phase3d-inflight-reach.md)を作成中。固定人工軌道の飛行中に、守備者1人が指定高さ/移動予算内へ到達できる時刻集合を調べる提案。D041 OPEN・ADR Proposed、本人の詳細仕様承認は未受領。設計PR作成後、合成範囲・仮設定・worker実装開始の承認を得てから設計をマージし、研究側へ委任する。実装は未着手で、既存3c以下や実データ/3b・能力・ゲーム本体を変更しない。作業ブランチ`design/phase3d-inflight-reach`は最新main `586ff3f8440db9064b657d2121e26ea1018cbd7a`から作成。開始時に両repoのclean状態・最新main・未マージPRなしを確認済み。

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

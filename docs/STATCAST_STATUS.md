# Statcastスイング判断モデル：研究進捗（2026-10-29）

研究コードの正本：https://github.com/thrsth45h/statcast-swing-model-v1

- Phase 1a 基盤コミット: `cdd658a3d8c4e11d03d69810a269c2493f3f5782`
- **Phase 1b PR #1（マージ済み）**: https://github.com/thrsth45h/statcast-swing-model-v1/pull/1
- レビュー対象PR head: `2341496d77be35e34313ce52d99a3a5f42a5578b`
- 初回レビュー: https://github.com/thrsth45h/statcast-swing-model-v1/pull/1#issuecomment-6445194797
- 再レビュー（マージ可）: https://github.com/thrsth45h/statcast-swing-model-v1/pull/1#issuecomment-6446407835

**注意**：以下はリポジトリのコードと提出済みレポートを監査した結果。私自身がStatcast原データから全期間のモデルを再学習したものではない。Phase 1bは**mainへマージ済み**だが、ゲーム向け能力係数は**未確定**。

最新のPhase 1c/1d完了状況と次工程は本書末尾、および[引き継ぎ](HANDOFF.md)を参照。以下のPhase 1a/1bの数値は既存研究の記録として維持する。

## Phase 1a（完了：小規模pilot）

- 各年6/1〜7のみ：2023学習25,605、2024検証26,109、2025テスト26,966（計78,680対象投球）。
- null、count/location、M0、M1、M2の基盤、データ取得/分類/品質監査、移植用JSON推論、再現性テストを実装。
- pilot時点でM2の年度間安定性未確認。正確な選球眼への解釈と1〜20変換は留保。

## Phase 1b（研究検証済み・PR #1 mainにマージ済み）

2023学習・2024validation・2025評価を維持し、ゲームの成績環境をMLB Statcast基準とする。2025/6/1〜7のpilotは既閲覧なので、完全に未使用のholdoutとは表現しない。

| 年度 | 完了対象試合 / 照合試合 | 元CSVの投球行 | 研究対象の適格投球 |
|---|---:|---:|---:|
| 2023 train | 2,430 / 2,430 | 720,684 | 716,355 |
| 2024 validation | 2,429 / 2,429 | 711,899 | 707,669 |
| 2025 evaluate | 2,430 / 2,430 | 712,528 | 708,307 |

合計7,289試合、元CSV 2,145,111行。MLB公式Stats APIの球団別物理的投球数とも各年30球団すべて一致したとの報告。ただし、個々の投球を独立した全投球台帳と一対一照合したわけではない。

### 2025年の予測

| モデル | Log Loss | Brier |
|---|---:|---:|
| M0（文脈） | 0.513376 | 0.171607 |
| M1（打者切片 a） | 0.509228 | 0.169965 |
| M2（a＋位置反応 d） | 0.507943 | 0.169461 |

M2−M1 Log Loss差は約 -0.001285、試合bootstrapによる95%区間は[-0.001421,-0.001153]。報告された独立年度の打者順位相関は、331人を対象にM1 a=0.840、M2 a=0.846、M2 d=0.771。

M2の「投球位置への反応」は比較的安定し、予測に追加情報を持つことが示唆される。ただし、**幾何的な球の中央への反応は、その球を振る価値を理解する認知的「選球眼」と同一ではない**。球種・配球・打者戦略・身体的選好などとの識別が必要。研究用の推奨モデルは引き続きM1、M2の製品採用は未承認。

未知打者（2025年142,096球）のLog Loss: M0=0.517417、M1=0.517648、M2=0.517620。未知打者にとってM1/M2はM0より良好とは限らない。

### 工学・検証状態

- Python 3.11/3.12のGitHub ActionsはPR先端コミットでも成功。
- ローカルの自動テスト結果として112件成功が報告され、PR最新コミットのGitHub ActionsでもPython 3.11/3.12両方が成功。
- 最大常駐メモリ約2.17GiB。学習・推論はまだAndroidゲームに移植していない。
- データ欠損・分類を変更した7種類の感度分析でもM2の予測改善の方向は一貫。ただし観察上の因果説明ではない。
- 元の投球の**最終到達地点**を与えたオフライン予測であり、打者がスイング決断時に持つ情報を再現するゲーム用モデルではない。

## 修正済み：再開時のチェックポイント入力同一性（再レビュー2026-10-28）

初回監査では、学習候補の再開時に2023/2024年の入力差し替えが検知されない危険を指摘した。コミット `2341496` により、実データから再計算したfingerprint、Parquet SHA-256、設定・プロトコル・特徴定義・実装/ライブラリ版と、チェックポイントごとの依存関係/ペイロードハッシュが照合されるよう修正。感度分析・bootstrap・既存holdoutの再確認にも保護を追加した。

データ変更の再現テスト、変更時の拒否、正常再開と新規学習の一致、旧形式の拒否を確認。112件のテストと最新CI成功を確認し、**マージ可能**と判断し、PR #1をmainへsquashマージ済み（merge SHA `f3b65653d90b004ea8bfaf233b94231876233980`）。旧形式結果は閲覧可能だが、旧キャッシュからの再開は拒否される。新しい通年再学習は行っていない。

## 今後の研究・製品設計

1. Phase 1bの再開時の入力同一性修正はレビュー済みでmainへマージ済み。研究成果をゲーム用係数として扱わない。
2. スイング判断モデルにおける「積極性」と「選球眼」の概念分離を、空間的な中心嗜好と混同せず再検討。ゲーム向け1〜20の能力尺度は未確定を維持する。
3. 未知打者のcold-startポリシーの検証。学習済みMLB選手IDをそのまま架空選手へ移植しない。
4. 2025 pilotによる評価汚染、2026年以降のStatcast座標定義差、商用利用/派生係数再配布条件を別途扱う。
5. 空振り・コンタクト、打球品質、球種別投球、守備・走塁へ順次進む。データの出典と独自の仮説を明確に区別する。

## Phase 1c・1d（合成研究をmainへマージ済み）

- Phase 1c: [研究PR #2](https://github.com/thrsth45h/statcast-swing-model-v1/pull/2)、main `6d82eddaa51d72126c011e14a0d511634d58e300`。不完全な知覚とスイング積極性を分離。研究用係数は未較正。
- Phase 1d設計: [ゲームPR #4](https://github.com/thrsth45h/baseball-sim/pull/4)、main `cecc3c372ae45c22561b97a59f731f463849f198`、設計版v1.1。研究範囲の承認記録はPR本文にある。
- Phase 1d実装: [研究PR #3](https://github.com/thrsth45h/statcast-swing-model-v1/pull/3)、main `cba081a13d53684e037738b53a65bdce2abea9a3`。生成世界と推論仮定、知覚精度と積極性を分離し、7世界×3seed、各seed/worldの調整4,096球・評価8,192球で検証した。
- 設計・レビュー担当側でも240テスト成功、Python 3.11／3.12 CI成功を確認。完全実行のsummary.json・plan.json・marginal.jsonが提出版とバイト単位で一致し、別式によるAUCと非心χ²による24ケースの積分も照合した。
- 唯一の主要比較のΔAUC=0.13308019、95%区間[0.12948814, 0.13627906]は固定合成課題の結果。MLBの認知能力・公開能力の実測差・得点改善として使わない。
- P0/P1は固定rで定数差。同率調整後の確率も同値で、P1や12カウントを独立した識別改善の証拠として数えない。
- M0/M1/M2、Phase 1b通年結果、Phase 1c実装・結果・推論契約は変更していない。

## D6既存実データの入力監査（完了・mainへマージ済み）

当初のPhase 1d実行時は明示入力がなく `not_run:no_explicit_input` だった。その後、[D6監査の実行手順](PHASE1D_DATA_AUDIT_HANDOFF.md)に従う実ファイル監査を[研究PR #4](https://github.com/thrsth45h/statcast-swing-model-v1/pull/4)で実施し、mainへマージ済み（`4bf94dee60de9e33cfd7f16e7439cac34a36a6fc`）。旧未実施記録は履歴として保存し、新結果は `reports/phase1d/real-input-audit/` に分離した。

レビュー側でも2023〜2025年の処理済み3ファイルと選択raw3 chunkのハッシュ・スキーマ・raw選定規則、関連する来歴計15ファイルを照合。監査関数のheader/sample計12通りの再実行結果が提出版と一致した。提出49ファイルのGit blob hash、既存149パスへの非変更、対象headのPython 3.11／3.12 CI成功も確認した。今回のレビューで全240テストや全年度の値を再実行・再検査したとは扱わない。

原データの軌道係数と処理済みデータの必要列を区別する。係数の定義地点だけでは推定に使用した観測時間区間を検証できず、真正な早期観測や主観的知覚の測定と認定しない。事後的な条件付き行動予測と、真正な早期情報の制約下での比較は別の研究課題として扱う。

raw3標本は119列、processedは27列で、軌道係数はrawに存在する。事後的な行動予測比較に必要な既存入力列はあるが、新方式のMLB向け潜在観測・周辺確率・座標/スコア契約は未整備。W0の人工Gaussian式を実CSVへ流用しない。

監査は `audit_completed_with_limitations`。全raw145 chunkの現物・値や全年度の値を再検査したわけではない。このD6監査では新規取得・学習・予測損失計算・公開能力較正を行っていない。以降の接触研究は次節のPhase 2aとして別に承認・実装した。2025は既閲覧、2026年以降の仕様差と商用利用・派生物の権利は別に確認が必要。認知妥当性・能力係数・製品採用は引き続き未検証・未確定。

## Phase 2a（研究実装・独立レビュー完了、mainへマージ済み）

> 後続の意味確認: 下記と旧研究レポートの「通常スイング」表現は、指定5descriptionの適格行と読み替える。限定rawの照合でhit_into_play内に記録上の犠牲バント4件とファウル地域field_out 62件を確認し、旧fair_in_playタグも物理的なフェアを保証しないと分かった。接触二値の数値は固定母集団の結果として保存する。全年度の混入率は未確認。[証拠・訂正と承認済みPhase 2b監査](PHASE2B_DESIGN.md)を参照。監査・説明訂正のworker実装開始を承認済みで、分岐予測の学習・ゲーム統合は含めない。

[承認設計PR #7](https://github.com/thrsth45h/baseball-sim/pull/7)、`phase2a-design-v0.1`、D036に基づきworkerが実装。責任者が独立レビューし、指摘をworkerが修正後、[研究PR #5](https://github.com/thrsth45h/statcast-swing-model-v1/pull/5)をマージした。研究mainは `e5a0c18ba2b9f1cdec068c3822a6fb863c0fec25`。

- 対象は既存適格投球内の通常スイング。2023/2024/2025で339,927 / 338,829 / 337,593スイング。旧y_swingを保ち、接触二値と4タグを分けた。
- 2023学習、2024で全候補からlambda10/C2を選択して凍結、既閲覧2025で再現比較。C2は固定C1の生logitへの打者残差で、50対象スイング未満・未知・キーなしはC1と同値。
- 2025のC1/C2 Log Lossは0.450549517723 / 0.445042717337。主要差は−0.005506800386609、試合対応500回bootstrap区間[−0.005927426032991, −0.005080158586316]。学習・選択を固定した試合標本変動で、再推定の不確実性を含めない。
- C2はBrier/AUCも改善した一方、10固定ビンECEはC1=0.00908137からC2=0.01041255へ悪化。肯定的な指標だけで採用しない。
- worker全272テスト・最終headのPython 3.11/3.12 CI成功。責任者は別チェックアウトで全量fit/evaluateを再実行し、数値・モデル等11ファイルがworker最終版とバイト一致。Phase 2a関数を呼ばないNumPy計算でも特徴・最適化条件・2024選択・2025指標/区間を1e-12以内で照合した。
- 独立レビューで公開参照JSONのcheckout改行だけによる入力誤拒否を再現し、workerが修正。元入力の生バイトhash保護は保ち、参照の実質改変拒否・元入力の改行変更拒否もテスト済み。旧199追跡パスは内容不変。
- `trained_on_real_data=true`、`ability_calibrated=false`、`cognitive_identification=not_established`、`product_adoption=false`。スイング選択・対戦・戦略の影響が残る条件付き予測で、公開三振回避能力の真値や因果的技能の推定ではない。

詳細は[研究レポート](https://github.com/thrsth45h/statcast-swing-model-v1/blob/e5a0c18ba2b9f1cdec068c3822a6fb863c0fec25/reports/phase2a/REPORT.md)とPRの独立レビュー記録を参照。次のPhase 2b結果分岐、打席K/BB/球数、公開能力較正、ゲーム統合は未実装・未承認。

## Phase 2b分類・欠損監査（完了・mainへマージ済み）

[承認設計PR #9](https://github.com/thrsth45h/baseball-sim/pull/9)、`phase2b-label-audit-v0.1`、D037に基づきworkerへ委任。責任者の独立レビュー・修正確認後に[研究PR #6](https://github.com/thrsth45h/statcast-swing-model-v1/pull/6)をマージした。研究main `2af9657854b70a3dd1ce3db7dd8ae296cdd8745d`。監査実装の完了であり、物理的な分類の完成ではない。

- 既存processed 3年とD6指定raw各年1chunkの生バイトhash・来歴を照合。指定列を全行集計し、年度の旧5description分母・タグを保存した。hit_into_play内のsac_bunt接頭辞記録は429 / 452 / 560件。
- raw標本は2023/2024/2025で14,707 / 690 / 631行。適格対応14,629 / 683 / 623、非対応78 / 7 / 8。対応行のdescription/eventsはnull安全に一致。hit_into_play内のfoul territory記述field_outは62 / 3 / 4件。
- raw hit_into_playのEV/LA両有限は2,464 / 123 / 88、両欠損13 / 0 / 2件。2023にはEV欠損・LA有限3件もある。有限値を全て直接実測と呼ばず、元データの一部推定値の存在も記した。限定標本から年全体の測定率・混入率・欠損の無作為性を推定しない。
- 全300テスト、最終headのPython 3.11/3.12 CI成功。責任者も別スナップショットで全300テストと修正版の新28テスト、実入力の監査再実行を確認。Phase2b関数を使わず事前に作った期待値と集計・入力hash・成果物hash435項目が一致し、提出版のplan/audit/REPORTとバイト一致した。
- 観察フラグへの該当／非該当を除外原因の確定と混同する表現を指摘し、workerが修正。全非対応行の原因確定はnot_establishedのまま。レビューによる追加の分類・除外は行っていない。
- 旧220パス中219は不変、既存変更はroot READMEの案内のみ。旧Phase2a REPORT/READMEはhash索引の対象なので保持し、[別冊訂正](https://github.com/thrsth45h/statcast-swing-model-v1/blob/2af9657854b70a3dd1ce3db7dd8ae296cdd8745d/reports/phase2a/ERRATA.md)を追加。旧索引と掲載12ファイル、新公開索引9件も照合した。旧コード・モデル・数値・ラベル・凍結契約は変更していない。

[監査レポートと集計](https://github.com/thrsth45h/statcast-swing-model-v1/blob/2af9657854b70a3dd1ce3db7dd8ae296cdd8745d/reports/phase2b/label-audit/REPORT.md)を参照。全バント除外、物理的フェア／ファウルの正解、記録3分類の新規学習、能力較正、ゲーム統合は未完了・未承認。D029はOPENを維持する。

## Phase 3a固定72区分の分布・抽選（研究完了・mainへマージ済み）

[承認設計PR #11](https://github.com/thrsth45h/baseball-sim/pull/11)、`phase3a-design-v0.1`、D038に基づきworkerが実装。責任者の独立レビュー・修正確認後、[研究PR #7](https://github.com/thrsth45h/statcast-swing-model-v1/pull/7)をmain `c155a6c0af899f2946dbcc4292c680a121fe781b`へマージした。

対象は登録rawのR・hit_into_play・EV/LA数値適格の記録集合で、旧processedの位置・球速等の条件は継承しない。旧研究の母集団・コード・モデル・数値・凍結文書・索引は保存。バント・捕球されたファウル等の完全除外や、全接触の物理分布を示す研究ではない。

| 年 | 登録ファイル | 登録行 | hit_into_play | 欠損による除外 | 採用 |
|---|---:|---:|---:|---:|---:|
| 2023 | 47 | 720,684 | 124,246 | 360 | 123,886 |
| 2024 | 49 | 711,899 | 124,207 | 375 | 123,832 |
| 2025 | 49 | 712,528 | 124,899 | 449 | 124,450 |

変換不能・非有限・範囲外による排他的除外は各年0。全145登録のhash・行数・schema・必要9列の読取り、全rawのキー/重複・日付/年度/R、対象EV/LAの分母と除外、年度間重複を確認。必要列以外の全データ値を再検証したとは扱わない。

- 2023だけで72セルとepsilon0.01の研究用平滑化を固定。J0はJ1と同じ速度・角度の周辺分布を持つ。2024で候補選択・設定変更せず、既閲覧2025を再現比較した。
- 2025カテゴリLog LossはJ0=3.748952105439、J1=3.643953700041。主差J1−J0は−0.104998405397、2,430対象試合の対応付き500回bootstrap区間[−0.107178779512, −0.102401600251]、無効0。固定モデルの条件付き区間で、再学習・将来年度の不確実性を含まない。
- 全368テスト・最終headのPython3.11/3.12 CI成功。責任者も別Git worktreeで全368テスト・fit/evaluate/sampleを実行。workerを参照しない別実装の期待値と1,137項目が一致し、公開10成果物は責任者の再実行とバイト一致した。
- レビューでは、hashを整合させてJ0/J1を取り違えた確率表を受理する問題を人工例で再現。workerが保存count/N・固定epsilonからの閉形式、同一周辺・J0積構造・恒等式を検証する処理と4回帰を追加。raw再推定を評価へ入れず修正し、03/04で全工程を再実行した。
- 既存237パス中236は不変、既存変更はroot READMEの案内のみ。公開hash68登録をworking/Git blob両方で照合。旧Phase2a/2bの索引も保持した。個票・個人パス・選手ID・実連続ペアを公開していない。

この結果は、限定記録集合における粗い組合せの年度間予測情報を示す。区分内の連続値・方向・物理フェア・捕球・得点・個人能力・ゲームへの十分性は示さない。epsilonはNで消えない研究仮設定で、測定誤差や能力係数ではない。`trained_on_real_data=true`と、能力較正・認知識別・製品採用の未達を分ける。D029はOPEN。

[報告・集計](https://github.com/thrsth45h/statcast-swing-model-v1/blob/c155a6c0af899f2946dbcc4292c680a121fe781b/reports/phase3a/real-03/evaluate/REPORT.md)、[再現手順と限界](https://github.com/thrsth45h/statcast-swing-model-v1/blob/c155a6c0af899f2946dbcc4292c680a121fe781b/reports/phase3a/README.md)を参照。次工程は記録集合から物理生成への接続条件の設計相談で、新しい実装仕様は未承認。

## Phase 3b区分内情報とEV/LA数値抽選（研究完了・mainへマージ済み）

2026-10-29、本人が[設計PR #13](https://github.com/thrsth45h/baseball-sim/pull/13)の研究範囲・仮設定を承認。ゲームmain `ebf37343da4932f9bd483753b691c366c510a3f2`へ設計をマージしてworkerへ実装委任し、責任者の独立レビュー後に[研究PR #8](https://github.com/thrsth45h/statcast-swing-model-v1/pull/8)をmain `35c9fe6fbe2b01c02b7253ed336526e6960cd4fe`へ統合した。D039は研究範囲のみCONFIRMED、D029はOPEN。

対象はPhase 3aと同じ登録rawのR・hit_into_play・数値適格EV/LA。全145ファイル・2,145,111行を年度別に照合し、採用数123,886 / 123,832 / 124,450件と粗72実件数は旧3a集計に一致。2023だけfit・凍結後に2024診断/既閲覧2025比較を行い、後続年度で設定を変えなかった。

H0/H1は同じ粗72確率を持ち、5mph×5度の900区分内の形を学習する追加情報を比較する。合計擬似度数1・有限セル一様・120mph以上の指数尺度5は承認された未較正の研究仮設定。H0は旧J1と別の比較基準であり、旧72カテゴリ損失と今回900カテゴリ損失は直接比較しない。

| 年 | H0 900カテゴリLog Loss | H1 900カテゴリLog Loss |
|---|---:|---:|
| 2024 | 5.554248183913798 | 5.427856763523376 |
| 2025 | 5.553957263969765 | 5.430982389501479 |

唯一の主比較、2025のH1−H0は**−0.12297487446828548**、2,430対象試合の対応付き500回bootstrap区間**[−0.1258096290410404, −0.12012170733822683]**、無効0。固定した学習の条件付き区間で、再学習・将来年度全体の不確実性を含まない。900カテゴリの追加情報が示された結果であり、セル内一様性・尾部尺度・小数精度・丸め前の真値の検証ではない。

EV≥120は2023学習1件、2024が6件、2025が3件。2024尾部の条件付き平均損失はH0=16.49236412227722、H1=18.529519902111446で悪化し、全体差への寄与は+0.00009870578428035858。2025尾部は両者18.529519902111446で差0。全体改善を尾部の学習成功と扱わず、細かい周辺・組合せ・平滑化の影響も分離できていないことを明記した。

- 全439テストと対象headのPython3.11/3.12 PR CI成功。責任者も別Git worktreeで全439テスト・全量fit/evaluate/sampleを実行し、主要12成果物が提出版とバイト一致。
- workerの新実装・成果物を見ずに事前凍結した独立集計・別式の期待値と1,232項目が一致。年別900件数・確率・指標・閾値・尾部寄与・区間・145入力識別に加え、各100,000抽選の数値列hash・集計・開始終了RNG状態を照合。単一/分割・状態復帰でも同じ数値を生成する。
- 公開46ファイルのGit blob、新77/旧134のhash登録をworking/Git双方で確認。旧285パス中284不変で、変更はroot READMEの案内2行だけ。旧コード・結果・推論契約を変更していない。
- 最大RSS約328.3MiB、初回fit/evaluate/sampleは約1.50/5.07/0.10秒。研究環境の測定で、Android性能ではない。合成smokeは別記録で実学習と混同しない。

[研究報告・再現手順](https://github.com/thrsth45h/statcast-swing-model-v1/blob/35c9fe6fbe2b01c02b7253ed336526e6960cd4fe/reports/phase3b/README.md)、[worker検証記録](https://github.com/thrsth45h/statcast-swing-model-v1/blob/35c9fe6fbe2b01c02b7253ed336526e6960cd4fe/reports/phase3b/VALIDATION.md)、[責任者レビュー](PHASE3B_REVIEW.md)を参照。数値を返せる限定記録集合の研究生成器であり、全接触・能力1〜20・方向・物理フェア・守備・走塁・安打・得点・ゲーム採用は未達。次の実装範囲は別の設計相談とする。

## Phase 3c合成軌道・地面到達・移動距離余裕（研究完了・mainへマージ済み）

2026-10-29、本人が[設計PR #15](https://github.com/thrsth45h/baseball-sim/pull/15)の範囲・仮設定を承認。ゲームmain `ab7cce68673d2a55f96424107d7d5e2597e32571`へ設計をマージしてworkerへ実装を委任し、責任者の独立レビュー・報告訂正確認後に[研究PR #9](https://github.com/thrsth45h/statcast-swing-model-v1/pull/9)をmain `015b9fdcd18565270b8ce888bd703aead8c63864`へ統合した。D040は合成研究範囲のみCONFIRMED、D029はOPEN。

固定12人工例から、方向・速度・高さ→無空気の軌道→最初の地面到達→守備者1人の移動距離予算と余裕を計算する。投球/スイング・実データ/旧3bには接続しない。地面イベントは解析式、各33点の軌道は表示用であり、離散点から判定しない。分類は距離差の正負と境界だけで、捕球/公式安打・アウト・フェアは判定しない。人工例の割合も捕球率やリーグ成績ではない。

- 全497テスト（既存439＋新58）と最終headのPython3.11/3.12 PR CI成功。責任者も別worktreeで全497テスト、run、情報アクセス監視下のrunを確認した。
- workerの新実装/結果を参照しない独立の100桁Decimal・三角級数・地面正根の二分法で期待値を作り、照合前に固定。12例/396表示点・距離予算・分類など、1,716数値を含む1,786項目が一致した。最大絶対差は時刻3.56e-15秒、位置/距離予算3.42e-13m、初速7.11e-15m/s未満。これは計算の照合誤差であり、実測精度ではない。
- 追加16不正入力probeが拒否され、監視した新runで旧データ/モデル読取り試行0、RNG試行0を確認。4主要成果物は責任者の再実行と提出版がバイト一致。
- 公開13ファイルのGit blob、新17/旧211hashをworking/Git双方で照合。旧338パス中337不変で、旧ファイル変更はroot READMEの案内2行だけ。旧コード・結果・推論契約・凍結索引を保持した。
- 各run約0.292秒、最大RSS約30.24MiB。研究環境の測定で、Android性能ではない。

レビューでhelperの読取り否定フラグの不正確さを指摘し、workerが訂正した。役割の違いは以下のとおり。

| 役割 | 読取りと用途 |
|---|---|
| 新3c run | 固定人工入力と実装同一性だけ。旧データ/モデルを計算に使わず、RNGなし |
| 既存の回帰テスト | 従来のfixtureや旧成果物を使う検証を維持 |
| 公開検証helper | raw入力は開かないが、旧model.json等の成果物バイトをhash保護のために読む。旧モデルの推論を3c計算へ接続しない |

修正版helper・生成JSON・README/VALIDATIONが一致し、責任者もhelperを再実行して旧211hashを確認。旧公開資料はignoredの履歴へ保持し、計算ソース/テストと4主要成果物は不変である。

[研究説明・再現手順](https://github.com/thrsth45h/statcast-swing-model-v1/blob/015b9fdcd18565270b8ce888bd703aead8c63864/reports/phase3c/README.md)、[worker検証記録](https://github.com/thrsth45h/statcast-swing-model-v1/blob/015b9fdcd18565270b8ce888bd703aead8c63864/reports/phase3c/VALIDATION.md)、[責任者レビュー](PHASE3C_REVIEW.md)を参照。無空気・点状守備者・理想化した真の地面情報は合成条件であり、MLBの飛距離/守備認知/捕球妥当性、実データ/3b接続、能力・公式記録・ゲーム採用は未検証・未実施。次の実装範囲は設計相談とする。

## Phase 3d（合成の飛行中到達機会・レビュー後mainへマージ済み）

2026-10-29、本人が「設計PR #17の範囲・仮設定で進める（推奨）」と回答し、[設計v0.1](PHASE3D_DESIGN.md)・[ADR-0007](adr/0007-phase3d-inflight-reach.md)の合成研究範囲・仮設定とworker実装開始を承認した。設計をゲームmain `863e78756448003363087994692ff85a2d79a428`へマージして委任し、責任者の独立レビュー・P2修正確認後、[研究PR #10](https://github.com/thrsth45h/statcast-swing-model-v1/pull/10)をmain `cd8217a44788cf830c5554b71b04a27a98ce620c`へマージした。最終レビューhead `238e63468c8bcb2679654f5fdde83b109c1e2a98`、source/tests固定 `0b13877a12ac59b5cb4ea1d70f9c376f5f9b9261`。D041は合成研究のみCONFIRMED、D029 OPEN。

固定した直交初速/初期高さと守備者1人の位置/速度/反応/高さ上限から、反応後・地面到達前・指定高さ内・水平移動予算内の時刻集合Kを求める。正規化binary64を正確な有理数、g=980665/100000として解釈し、二次不等式の区間交差を行う。等号/単一時刻/2高さ区間と地面openを保持。512精密化で未解消ならindeterminate/nullを返す。公開根包囲の幅1e−12秒は身体半径・区間合体の閾値・物理精度ではない。

- workerと責任者の別worktreeで全575テスト（旧497＋新78、失敗/エラー/skip0）成功。責任者実行44.21秒、既存の不正日付fixtureの警告1件。最終headのPR CI run `40852403627`はPython3.11/3.12とも成功。
- 固定20例は全resolved。非空13/空7/合計14区間は人工検証例の内訳で、捕球率ではない。極小の非空区間は約1.1871e−13秒、地面終端openを保持。最小正subnormal高さではindeterminate/nullとなる経路も検証した。
- worker未参照の独立担当が、160桁Decimalの中心/半径と整数多項式の厳密符号/有理包囲によるセル分割で期待値を作り、照合前に凍結。20例・追加13多項式oracleと共通無理根の人工例を含む940項目、91回の端点照合が一致。root ID・canonical表示包囲・開閉・最初時刻も確認した。凍結がworker実装より時間的に先だったという主張はしない。
- 追加16不正入力拒否と、監視した新runの旧入力読取り/RNG試行0を確認。旧3cとの追加5条件の位置/地面時刻も許容範囲内。真の全軌道を既知とする幾何的存在で、守備者がその経路を選ぶという主張ではない。
- 責任者の別実行は4主要成果物がworker版とバイト一致。公開13ファイル106,377bytes、新17/旧228hashをworking/Git blob双方で照合し、旧358パス中README案内以外357不変。helperも責任者が別実行して確認した。
- workerの正式runは約0.2953秒、peak RSS約30.47MiB。研究環境の測定でAndroid性能ではない。

責任者が初版のnamespace転送による旧領域への出力を検証専用の実Windows junctionで再現し、workerへ直接修正依頼した。許可namespaceの宣言位置を保持して解決後の出力を検査する修正と、namespace全体/子aliasから旧領域/checkout外へ向かう4回帰を追加。責任者も全テストと旧再現junctionで拒否を確認した。訂正前公開13ファイル一式をignored履歴へ保持し、新source同一性でsynthetic-03/repeat-04へ再実行。旧3cは変更していない。

新runは旧データ/モデルを計算に使わず、既存回帰テストは従来fixtureを使う。公開helperはraw入力を開かないが、旧model.json等をhash保護のためバイト読取りする。この三つの役割を区別して記録した。

[研究説明・再現手順](https://github.com/thrsth45h/statcast-swing-model-v1/blob/cd8217a44788cf830c5554b71b04a27a98ce620c/reports/phase3d/README.md)、[worker検証記録](https://github.com/thrsth45h/statcast-swing-model-v1/blob/cd8217a44788cf830c5554b71b04a27a98ce620c/reports/phase3d/VALIDATION.md)、[責任者レビュー](PHASE3D_REVIEW.md)を参照。空気抵抗なし・半径0・即時の最大速度・反応前禁止・高さ上限/入力範囲は未較正の人工仮定。実際の捕球/守備認知・MLB妥当性・実データ/3b接続・能力・公式記録・ゲーム採用は未検証・未実施。次の新しい実装範囲は設計相談とする。

## Phase 4a（ゲーム側の限定ルール参照・レビュー後mainへマージ済み）

2026-10-29、本人が「設計PR #19の範囲で進める（推奨）」と回答し、[設計v0.1](PHASE4A_DESIGN.md)・[ADR-0008](adr/0008-phase4a-count-reference.md)の限定ルール範囲・規則版・ゲーム側Python参照実装/CIとworker実装開始を承認した。設計main `1c5b738671a452305a4be874c57f77cd8ad5db10`からworkerへ委任。責任者の独立レビューと修正確認後、[ゲーム実装PR #20](https://github.com/thrsth45h/baseball-sim/pull/20)をmain `4d3a92392d5adb4be9ddd2db4ef16ab3ca3a9977`へマージした。

裁定済み合成8イベントからカウント/限定終端/打球受渡しを更新する。言語非依存96遷移表/16列を保持し、全33 test method成功（subTest326別集計、failure/error/skip0）とPython3.11/3.12 CI成功を確認。責任者の別実行では4成果物がworker版とバイト一致し、worker未参照の別式による凍結期待値との10,289項目が一致した。固定列の同一最終状態への置換防護もworkerに修正させ、5置換の拒否を確認した。[レビュー詳細](PHASE4A_REVIEW.md)と[再現手順](../reports/phase4a/README.md)を参照。

実装PR時点の旧ゲーム35パス不変と、以後の完了文書更新は区別する。公開helperの履歴保護は設計baseline→固定source `922a2a9c1cb5a76e9d984fc5bd5c9fef2952b7ae`を対象とし、現在のsource/tests/fixtures/CI照合は維持する。研究repoはmain `cd8217a44788cf830c5554b71b04a27a98ce620c`のまま変更なし、研究575テストを今回の成功件数へ加算していない。

Statcast研究や3dの到達機会をイベントへ変換せず、確率・走者/得点・能力・公式成績・Android/セーブには接続していない。完全な打席/試合生成や製品採用は未達。D042は限定参照のみCONFIRMED、D029/D031/D032はOPEN。次工程の実装仕様は設計相談で決める。

## Phase 4b（ゲーム側の塁・アウト・得点参照、レビュー後mainへマージ済み）

2026-10-29、本人が[設計PR #22](https://github.com/thrsth45h/baseball-sim/pull/22)の範囲とworker実装開始を承認。設計main `59ae0e2f726b88d5729d026a0e293916c33a73e6`からworkerへ委任し、責任者の独立レビュー後に[実装PR #23](https://github.com/thrsth45h/baseball-sim/pull/23)をmain `aff963e93408460a4d157313e151e4f9b96c9955`へマージした。[設計v0.1](PHASE4B_DESIGN.md)・[ADR-0009](adr/0009-phase4b-base-out-reference.md)のD043限定CONFIRMED/Acceptedを維持。

確定済みの四球/走者据置きアウト/合法本塁打から塁占有・アウト・半回得点を更新し、三アウト後は残塁/得点を保持して停止する。新4b32 test method（subTest515別）、旧4a33 method（subTest326別）が成功し、Python3.11/3.12 CIも成功。責任者の別実行・識別走者の独立期待値との1,578比較項目/138期待拒否が一致し、4成果物もworker版とバイト一致した。[レビュー](PHASE4B_REVIEW.md)と[公開手順](../reports/phase4b/README.md)を参照。

実装PR時点で旧65パス中README案内以外64不変、公開13ファイル62,329bytes・15hashのworking/Git blob一致を確認した。以後の完了文書更新は履歴保護の例外へ追加せず、現在の変更として区別する。研究repoはmain `cd8217a44788cf830c5554b71b04a27a98ce620c`のまま不変で、研究575テストを今回の件数へ加算していない。

限定ルールの検算であり、研究モデルや得点分布の研究成果ではない。4aやStatcast/3dとの接続、走塁判断、能力、公式個人成績、完全試合、Android/セーブ・製品採用は含めない。D029/D031/D032はOPEN、次工程の実装仕様は設計相談で決める。

## Phase 4c（ゲーム側の限定接続、レビュー後mainへマージ済み）

2026-10-30、本人が[設計PR #25](https://github.com/thrsth45h/baseball-sim/pull/25)の範囲を承認（D044限定接続CONFIRMED/ADR Accepted）。worker実装・責任者の独立レビュー後、[実装PR #26](https://github.com/thrsth45h/baseball-sim/pull/26)をmain `40e1d1d54e9728bbee04c3f65300e57972b9c629`へマージ済み。旧4a/4bの型・stepへ明示Begin/Pitch/Resolveで委任し、結果待ちではhalfを反映せず、明示確定後にcount=Noneへ消費する。次打者も明示Beginを要求し、三アウトで残塁/得点を保持して停止する。

新4c28 test method/成功subTest3020、旧4a33/326・旧4b32/515を別集計し、責任者も別worktreeで全て成功、failure/error/skip0。対象headのPython3.11/3.12全CI成功。実装未参照で別計算した凍結期待値との11,468比較項目/439期待拒否が一致し、18列/82action/100分割、代表2,304投球、互換288試行（120受理/168拒否）、半回完了96拒否等を照合した。監視下runの許可外入力/RNG・時計利用0、主要4成果物は責任者の2runとworker公開版でバイト一致。公開helperも164trace/6run hashを独立再検算した。 [レビュー](PHASE4C_REVIEW.md)・[公開手順](../reports/phase4c/README.md)を参照。

新runは許可した旧5sourceをimport/hash読取りし、旧fixture/結果・研究モデルは入力しない。helperだけは固定Gitの旧96/72静的表も検算用に読む。実装時点で旧93パス中README案内以外92不変、公開14ファイル151,315bytes・16hashをworking/Git blob双方で照合。後続完了文書7件の変更はこの履歴保護と区別する。研究main `cd8217a44788cf830c5554b71b04a27a98ce620c`とtree/作業状態は不変、研究575テストを新4cの件数へ加算しない。

強い合成入力条件内の接続参照で、halfは最後に確定反映したsnapshot。省略された現実イベントの検出や古いsnapshot/通信再送の永続重複排除を保証しない。一般走塁/単打/併殺/第三アウト得点、公式個人成績、完全試合、研究/確率/能力、Android/UI/DB/セーブ・製品採用は未実施。D029/D031/D032はOPEN。次の新しい実装仕様は未承認で、設計相談から決める。

## Phase 4d（ゲーム側の半回列制御、レビュー後mainへマージ済み）

worker実装・責任者の独立レビュー後、[実装PR #29](https://github.com/thrsth45h/baseball-sim/pull/29)をmain `27c1c9df0d6286faf77334157eec2b236f94e68d`へマージ済み。旧4c.stepを再利用し、三アウト後の明示Advanceで表裏を切り替え、次半回だけを空の状態へ初期化する。切替済み累計と現在halfからscoreを導出し、Advance前後で両チームの合計を保持する。 D045は限定範囲のみCONFIRMED。

新4d26 test method/成功subTest320、旧4c28/3020・旧4a33/326・旧4b32/515を別集計し、責任者も別worktreeで全て成功、failure/error/skip0。対象headのPython3.11/3.12全CI成功。実装未参照の独立した得点ledgerと21,612比較項目/709期待拒否が一致。16列/100action/116分割、96Advance、両side合計4,608投球・576互換試行（240受理/336拒否）、完了後192拒否を確認。追加1,000半回/14,500actionの最終state/score・全trace hashも一致した。主要4成果物は責任者の監視下2runとworker公開版でバイト一致し、許可外入力/RNG・時計利用0。公開helperも200trace/6run hashを独立再検算した。 [レビュー](PHASE4D_REVIEW.md)・[公開手順](../reports/phase4d/README.md)を参照。

最終実装head `898de25e62a3e81cf1ba2c0661a13e8fa4d5e0d1`、source/tests/fixtures/CI固定 `2fadced5b5da7b8387466641fd063180c5628e17`。実装時点で旧123パス中README案内以外122不変。公開15ファイル216,421bytes・17hashをworking/Git blob双方で照合。後続完了文書7件の変更は歴史的旧保護と区別する。研究main `cd8217a44788cf830c5554b71b04a27a98ce620c`/tree/作業状態は不変。研究575テストを新4dへ加算しない。

新runは許可旧7sourceのimport/hashを利用する。helperだけは固定Gitの旧4a/b静的遷移・旧4c互換表も検算用に読み、研究/学習モデルを使わないことと区別する。

終了判定のない有限合成列のcontrollerであり、9回/延長/サヨナラ・勝敗・最終得点、一般走塁/公式記録・完全試合、研究モデル/能力、Android/UI/DB/セーブ・製品採用は未実施。強い合成入力条件とhalf snapshotの意味を維持し、省略された現実イベントの検出や古いsnapshot/通信再送の永続重複排除は保証しない。D029/D031/D032はOPEN。次の重要な実装仕様は未承認で、設計相談から決める。

## Phase 4e案（裁定済み安打と明示進塁、承認済み）

[Phase 4e案](PHASE4E_DESIGN.md)・[ADR-0012](adr/0012-phase4e-completed-safe-hits.md)はD046限定CONFIRMED/Accepted。2026-10-30、本人が設計PR #31を示した確認に「設計PR #31の範囲で進める（推奨）」と回答。全員safe・打者最終塁と安打価値が等しい限定条件、明示行先の検査と反映、旧型/step利用、固定表/列、ゲーム側Python参照/CIとworker開始を承認した。新ResolveSafeHitで、打者最終塁が裁定済み安打価値と等しく全員safeの完了プレーだけを反映する。安打価値も各走者の行先も入力であり、Statcast/打球から予測・裁定しない。最終位置の矛盾は拒否するが途中の触塁/追越し等の履歴は証明しない。旧研究/4a–4d結果を保持する。生産実装は設計マージ後にworkerへ委任し、走塁判断/確率・一般裁定・試合終了/公式成績・能力・Android採用は別設計。

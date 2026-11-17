# Baseball Sim — 架空日本12球団ペナント／GMシミュレーション

**このリポジトリがゲーム本体の仕様と開発の正本です。** Statcastモデルの研究は別リポジトリに置きます。

2026-10-27時点のChatGPTでの議論をCodexへ引き継ぐための初期文書です。設計案を完成機能と混同しないでください。

## コンセプト

- Android向け、データとテキスト中心の日本を舞台にした架空12球団・2リーグのペナント／GMシミュレーション。
- 地域配置はNPBを参考にするが、球団・選手は架空。球団名、都市、ロゴ、カラー、球場などの編集を構想。
- **Pennant Spiritsを機能構成・年間の球団運営の主な設計基準にする**（2026-11-17本人承認、D059）。[機能対応表・完成像・開発順序](docs/PENNANT_SPIRITS_BASELINE.md)を正本とする。FM/OOTP等は個別機能、Baseball Savantは分析の追加参考とする。
- **内部は1球ごと、プレイヤー操作は原則1打席ごと**。自動スキップでも同じ試合エンジンで結果を生成。
- **MLB Statcastの公開データのみで詳細な統計モデルを研究**。完成ゲームの統計環境はMLB水準で構わない。DELTA/1.02の有料データやNPB向けの強制補正は不要。

## Codexが最初に読む順番

1. [AGENTS.md](AGENTS.md) — 作業原則・禁止事項
2. [DECISIONS.md](DECISIONS.md) — 確定事項、暫定案、未決定事項
3. [GAME_DESIGN.md](GAME_DESIGN.md) — サブシステムとゲーム全体像
4. [ROADMAP.md](ROADMAP.md) — 作業順序・品質ゲート
5. [docs/STATCAST_STATUS.md](docs/STATCAST_STATUS.md) — 統計研究の進捗と限界

## 開発の進め方

- [設計・実装・レビュー運用](docs/WORKFLOW.md)：責任者がユーザーと仕様を相談し、承認後にworkerへ実装を委任。責任者がレビュー・修正指示・統合を行い、ユーザーによる依頼文の転送は不要。
- [現在の引き継ぎ状況](docs/HANDOFF.md)：承認済み設計、関連PR、未解決事項を確認。
- [球団ホーム・選手一覧/詳細](docs/club-home/README.md)：段階Aの限定UIを実装。固定108人の詳細と、既存の日程/記録/観戦/設定をつなぐ。[設計と承認範囲](docs/CLUB_HOME_DESIGN.md)。
- [打順・出場選手の試作版](docs/lineup-roster/README.md)：本人承認のD061に基づき、新規世界の出場9＋控え3を試合/人物別記録へ接続。旧108人世界も維持する。[承認設計](docs/LINEUP_ROSTER_DESIGN.md)。
- [ADR（設計決定記録）のひな型](docs/adr/0000-template.md)：各案の根拠・反証・トレードオフ・承認記録。
- [AGENTS.md](AGENTS.md)：Codexが守る作業ルール、UIの簡潔さに関する方針。

設計案ができたことと、ユーザーが設計を採用したことを混同しない。**未承認の設計PRから実装を開始せず、採用済み設計をmainで共有してから実装PRを作る**。

## 関連リポジトリ

**Statcast Phase 1研究**：https://github.com/thrsth45h/statcast-swing-model-v1

監査した初期コミット：cdd658a3d8c4e11d03d69810a269c2493f3f5782

研究コードと解析結果の正本は上記に置く。大量の生データや学習成果物は本ゲームの設計リポジトリへ複製しない。

## 現在の状態

**各球団12打者から出場9人を選び、打順を変えられるAndroid技術検証版**を実装しました。[APK取得・操作](docs/lineup-roster/README.md)・[検証記録](docs/lineup-roster/VALIDATION.md)・[PR #51](https://github.com/thrsth45h/baseball-sim/pull/51)。新規v2は144人、旧v1は固定108人のまま継続し、試合前の編成を人物別の記録へ反映します。source976eeabのapp39/core28/通常device53＋別v2全シーズン1は失敗/スキップ0。API35で旧新それぞれ実720試合、実UI全144人、配布APKの両形式force-stop復旧を確認し、責任者の独立コード・期待値・成果物照合を完了、最終統合待ちです。全員同じ仮確率で戦力差はまだなく、投手・途中交代・能力・年次・経営は追加していません。

**球団ホームから固定108人の選手詳細へ移動するAndroid版**を実装し、CIと最終保存データの独立照合を完了しました。[APK取得・操作](docs/club-home/README.md)・[検証記録](docs/club-home/VALIDATION.md)・[PR #49](https://github.com/thrsth45h/baseball-sim/pull/49)。閲覧だけでは操作球団・試合・保存を変えず、日程/順位/記録/観戦/設定へ移動できます。source53938afのapp36/core22/device46 methodは失敗/スキップ0、API35で実720試合と配布APKの実force-stop復旧を確認。責任者の独立コード・成果物・画面レビュー後、main `c745d65`へ統合済みです。単年度・固定9人・仮確率の限定試作で、能力/編成/経営/次年度は今回追加していません。

**Androidで12球団の限定ペナントを動かす技術検証APK**に、保存の限定性能改善を追加しました。[旧性能改善版のAPK・操作手順](docs/pennant-performance/README.md)・[検証記録](docs/pennant-performance/VALIDATION.md)。固定9人・各120試合、観戦/日程スキップ、順位/限定記録、端末内の保存/復旧を備え、API35の隔離CIで実720試合を11分1.123秒で完走し、最終保存データの既存期待値との全byte一致と実プロセス強制終了からの復旧を確認しました。同一emulatorで主要保存操作は21.3〜23.4%短縮しましたが、実機や全シーズンの改善率ではありません。旧18分30.228秒の[初版記録](docs/pennant-android/VALIDATION.md)は別runnerの履歴として保持します。単年度の限定試作で、能力・采配・球団経営・次年度移行や製品版の長期保存は未実装です。旧単独1試合モードも残しています。[性能改善PR #46](https://github.com/thrsth45h/baseball-sim/pull/46)。

**Androidでオフラインの1試合を操作できる技術検証APK**を作成しました。最低API26（Android8.0）。PCサーバやPythonは不要です。[検証済みAPKの取得・操作手順](docs/android-first-match/README.md)と[ビルド/テスト・未検証事項](docs/android-first-match/VALIDATION.md)を参照してください。12球団ペナントや球団経営を備えた製品版は未実装です。

**PCブラウザで1試合を操作するローカル画面**を起動できます。表示された`http://127.0.0.1:...`を自分で開き、「1球」「1打席」「最後まで」で進めます。seedを変えて新しい試合も開始できます。

```powershell
python -m prototypes.match_view
```

終了はターミナルでCtrl+C。[操作手順と範囲](docs/local-match-view/README.md)・[検証状況](docs/local-match-view/VALIDATION.md)を参照してください。このPC試作と、上記のAndroid技術検証版は別の表示・操作層です。

**1試合の統合試作（Phase 4f）**は、仮の2チーム各9人が1球ずつ自動進行し、終了・スコア・終了理由を表示します。

```powershell
python -m prototypes.phase4f run --seed 42 --output data/prototypes/phase4f/game-01
```

[seed42の公開試合REPORT](reports/phase4f/game-42-01/REPORT.md)と[実行手順・仮設定と限界](reports/phase4f/README.md)を参照してください。未較正toy設定の試作で、Android・球団経営・正式能力係数の採用ではありません。出力先は毎回新しい名前を選びます。

Phase 4aの限定ルール参照実装は[実行手順・検証と限界](reports/phase4a/README.md)を参照してください。裁定済み合成イベントでカウント等を検算するPython試作であり、Android技術や完全な試合エンジンの採用ではありません。

Phase 4bの塁・アウト・得点の限定参照は[実行手順・検証と限界](reports/phase4b/README.md)を参照してください。完了・裁定済み合成3プレーの検算であり、4a接続・走塁判断・完全な試合生成は含みません。

Phase 4cの投球と半回の明示接続は[実行手順・検証と限界](reports/phase4c/README.md)を参照してください。旧4a/4bへ明示委任する合成試作で、結果や走者移動をstatusから自動選択しません。

Phase 4dの表裏切替と得点保持は[実行手順・検証と限界](reports/phase4d/README.md)を参照してください。有限合成半回列の検算で、試合終了・延長・勝敗は判定しません。

Phase 4eの裁定済み全員safe安打と明示行先は[実行手順・検証と限界](reports/phase4e/README.md)を参照してください。安打価値・各走者の行先は入力で、走塁判断や公式安打の裁定は行いません。

このリポジトリは**設計引き継ぎとゲーム開発のための初期状態**です。Androidの1試合/限定ペナント技術検証版を追加しましたが、経営・長期ペナントを備えた製品本体や正式な1〜20の係数較正は未完成です。仕様の確定性は必ず決定台帳を優先してください。

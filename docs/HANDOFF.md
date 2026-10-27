# Codex間の引き継ぎ

本ファイルはチャットのコピーではなく、**GitHubで確認できる現在の状態**を記す。フェーズやPRが変わるごとに更新する。

## 参照先

- 仕様の正本：[DECISIONS.md](../DECISIONS.md)、[GAME_DESIGN.md](../GAME_DESIGN.md)
- 運用の正本：[docs/WORKFLOW.md](WORKFLOW.md)、[AGENTS.md](../AGENTS.md)
- 統計研究：[statcast-swing-model-v1](https://github.com/thrsth45h/statcast-swing-model-v1)（Phase 1b [PR #1](https://github.com/thrsth45h/statcast-swing-model-v1/pull/1) はマージ済み、squash SHA `f3b65653d90b004ea8bfaf233b94231876233980`）

## 状態（2026-10-28、PR #1・#2マージ後）

- **Phase 1c**：[設計PR #2](https://github.com/thrsth45h/baseball-sim/pull/2) をmainへマージ済み（squash SHA `568730e066c47ebcda2fba5a103f682271623a25`）。[docs/PHASE1C_DESIGN.md](PHASE1C_DESIGN.md) は設計提案・レビュー引き継ぎの文書。**ゲーム用モデル方式の正式採用・能力係数・実装の承認ではない**。
- ゲーム本体：仕様・設計文書が存在。Androidアプリと公開能力1〜20の確率係数はまだ未実装・未較正。
- 次工程：**Stage A（実装前の設計整理）**。Phase 1c設計と[レビューコメント](https://github.com/thrsth45h/baseball-sim/pull/2#issuecomment-6447618536)を読み、選球眼の作用の二重計上防止・実測と知覚仮定の境界・誤差/閾値の識別制約・最小研究プロトタイプ仕様・事前評価基準を設計PRにまとめる。採用承認後に研究用実装PRへ進む。
- 別途の研究上の課題：位置反応`d`の構成概念妥当性、積極性と選球眼の識別、能力変換係数とcold start。

## 各PRの引き継ぎ欄

| 項目 | URL / SHA / 状態 |
|---|---|
| Phase 1cの提案文書（採用未承認） | [baseball-sim PR #2](https://github.com/thrsth45h/baseball-sim/pull/2) マージ済み、SHA `568730e` |
| Phase 1c実装PR | **未登録** |
| 次にレビューするPR | Stage Aの設計PR（未作成） |
| 運用ルール導入 | [baseball-sim PR #1](https://github.com/thrsth45h/baseball-sim/pull/1) マージ済み、SHA `546c606` |
| 最終マージ済み研究PR | 上記Phase 1b PR #1 |

PRの状態が変わった場合は本表も更新する。各チャットの終了前に「何を確定したか」「何を提案中か」「どのPRを次の担当が読むか」をここへ残す。

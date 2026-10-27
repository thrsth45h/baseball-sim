# Codex間の引き継ぎ

本ファイルはチャットのコピーではなく、**GitHubで確認できる現在の状態**を記す。フェーズやPRが変わるごとに更新する。

## 参照先

- 仕様の正本：[DECISIONS.md](../DECISIONS.md)、[GAME_DESIGN.md](../GAME_DESIGN.md)
- 運用の正本：[docs/WORKFLOW.md](WORKFLOW.md)、[AGENTS.md](../AGENTS.md)
- 統計研究：[statcast-swing-model-v1](https://github.com/thrsth45h/statcast-swing-model-v1)（Phase 1b [PR #1](https://github.com/thrsth45h/statcast-swing-model-v1/pull/1) はマージ済み、squash SHA `f3b65653d90b004ea8bfaf233b94231876233980`）

## 状態（2026-10-28確認時点）

- **Phase 1c**：先行Codexチャットで設計が終了したという報告はあるが、`baseball-sim` の `main` には `docs/PHASE1C_DESIGN.md` が**まだ存在しない**。その設計案の内容・承認状況は**未共有・未確認**。勝手に採用済みとして実装しない。
- ゲーム本体：仕様・設計文書が存在。Androidアプリと公開能力1〜20の確率係数はまだ未実装・未較正。
- 次工程：先行チャットでのPhase 1c設計結果を **設計PR（Proposed）** として文書化する。レビューとユーザーの採用承認後にmainへマージし、開発チャットが実装PRを開始する。
- 別途の研究上の課題：位置反応`d`の構成概念妥当性、積極性と選球眼の識別、能力変換係数とcold start。

## 各PRの引き継ぎ欄

| 項目 | URL / SHA / 状態 |
|---|---|
| 承認済みPhase 1c設計PR | **未登録** |
| Phase 1c実装PR | **未登録** |
| 次にレビューするPR | 新たな設計PRが作られたら追加 |
| 最終マージ済み研究PR | 上記Phase 1b PR #1 |

PRの状態が変わった場合は本表も更新する。各チャットの終了前に「何を確定したか」「何を提案中か」「どのPRを次の担当が読むか」をここへ残す。

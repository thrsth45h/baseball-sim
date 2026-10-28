# Codex間の引き継ぎ

2026-10-28 日本時間。GitHubで確認できた研究・承認・マージ状態を記す。仕様の確定性は[決定台帳](../DECISIONS.md)、運用は[WORKFLOW.md](WORKFLOW.md)を優先する。

## 参照先

- 仕様の正本：[DECISIONS.md](../DECISIONS.md)、[GAME_DESIGN.md](../GAME_DESIGN.md)
- 運用の正本：[docs/WORKFLOW.md](WORKFLOW.md)、[AGENTS.md](../AGENTS.md)
- 統計研究：[statcast-swing-model-v1](https://github.com/thrsth45h/statcast-swing-model-v1)（Phase 1b [PR #1](https://github.com/thrsth45h/statcast-swing-model-v1/pull/1) はマージ済み、squash SHA `f3b65653d90b004ea8bfaf233b94231876233980`）

## 現在の到達点

- Phase 1bの2023〜2025年通年研究、Phase 1cの合成知覚プロトタイプ、Phase 1dの合成識別研究・入力監査機能が研究側mainへマージ済み。
- Phase 1dは別の実装担当によるコードを設計・レビュー担当が点検。レビュー側でも240テスト成功、完全実行の設定・数値集計・積分結果の3ファイルが提出版とバイト単位で一致。対象headのPython 3.11／3.12 CI成功を確認した。
- 主要ΔAUC=0.13308019、95%区間[0.12948814, 0.13627906]は固定した合成課題内の結果。MLB選手の能力差・認知妥当性・製品係数の実測値ではない。
- 実データ監査機能は実装済みだが、Phase 1d実行時は明示入力がなかったため `not_run:no_explicit_input`。実ファイルの監査完了と混同しない。
- Androidアプリ本体、公開能力1〜20の較正、ゲームへのモデル採用は未完了。既存M0/M1/M2・Phase 1b/1cの結果・推論契約は維持する。

## 確認済みPRとcommit

| 対象 | URL | mainへのマージcommit |
|---|---|---|
| 運用導入 | [ゲームPR #1](https://github.com/thrsth45h/baseball-sim/pull/1) | `546c606` |
| Phase 1c設計文書 | [ゲームPR #2](https://github.com/thrsth45h/baseball-sim/pull/2) | `568730e066c47ebcda2fba5a103f682271623a25` |
| Phase 1b通年研究 | [研究PR #1](https://github.com/thrsth45h/statcast-swing-model-v1/pull/1) | `f3b65653d90b004ea8bfaf233b94231876233980` |
| Phase 1c合成プロトタイプ | [研究PR #2](https://github.com/thrsth45h/statcast-swing-model-v1/pull/2) | `6d82eddaa51d72126c011e14a0d511634d58e300` |
| Phase 1d研究設計v1.1 | [ゲームPR #4](https://github.com/thrsth45h/baseball-sim/pull/4) | `cecc3c372ae45c22561b97a59f731f463849f198` |
| Phase 1d研究実装 | [研究PR #3](https://github.com/thrsth45h/statcast-swing-model-v1/pull/3) | `cba081a13d53684e037738b53a65bdce2abea9a3` |

Phase 1d設計書・ADRのProposedは作成時の表記。研究範囲の採用・マージ記録はゲームPR #4本文にある。研究実装の独立レビュー・ユーザー許可・マージ記録は研究PR #3本文にある。これは製品採用・能力係数の承認ではない。

ゲームPR #3は本引き継ぎ更新のPR。以前の「Stage Aが次工程」「Phase 1c実装未登録」という記載を現在の事実へ更新する。マージ済みかはPRの最新状態を確認すること。

## 次工程

**承認済みPhase 1d D6の、既存実データに対する入力監査を行う。新たなモデルを実装する段階ではない。**

[実データ監査の引き継ぎ・依頼文](PHASE1D_DATA_AUDIT_HANDOFF.md)を実装担当へ渡す。Phase 1bで取得済みの原データと処理済みデータを来歴付きで明示指定し、既存の `phase1d audit-input` を実行する。自動取得・再学習・旧結果上書きは行わない。

監査では、処理済みファイルだけを見て早期観測がないと結論しない。前処理では列を絞っているため、取得manifestに登録されたrawファイルも対象にする。軌道係数の存在と、判断時点までの真正な観測・主観的知覚の観測は区別する。

監査後は設計側で、同じ入力による事後的な行動予測比較の準備状況と、早期情報制約下の比較が可能かを別々に評価する。真正な早期観測が確認できない場合も正当な監査結果として記録し、能力係数を推定して穴埋めしない。

## 継続する運用指示

ユーザー本人は2026-10-28に **「以降問題なかったらマージまでやってください」** と指示した。研究PR #3のマージ記録にも転記済み。

今後は、承認済み範囲のPRにレビュー・必要な検証上の問題がなければ、都度のマージ確認を求めずマージまで進める。新仕様の採否・能力係数・製品採用をCodexが独断で承認する指示ではない。問題がある場合は解決してから判断する。

## 残る未解決事項

- 選球眼と積極性の認知的な識別、感覚誤差・判断期限・固定スコアの外的根拠。
- 公開能力1〜20の効果幅・分布・相関・較正アンカー。M2 dを選球眼の正解にしない。
- 2025年は研究で既閲覧。後続比較で未使用holdoutと呼ばない。2026年以降の座標・ゾーン定義差は別途検証が必要。
- 未知打者、入力欠損・分類、商用利用・派生物の権利確認、後続のコンタクト・打球・試合モデル。

詳細は[研究状況](STATCAST_STATUS.md)、[Phase 1d設計](PHASE1D_DESIGN.md)、[ロードマップ](../ROADMAP.md)を参照。

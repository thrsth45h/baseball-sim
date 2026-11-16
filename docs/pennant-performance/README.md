# 保存・日程スキップの限定性能改善

[設計PR #45](https://github.com/thrsth45h/baseball-sim/pull/45)・[正本](../PENNANT_PERFORMANCE_DESIGN.md)・[ADR-0016](../adr/0016-pennant-store-performance.md)に基づくPhase 5aの内部変更。基準runtimeはmain `7ebb4a46a319ffde5e390a86b8f0a3a0352e4866`。新しいゲーム仕様、製品DB、次年度/能力/経営の実装ではない。

通常commitの新規書込み成功時だけ、操作内の確定byteから検証済みdecoded状態を再利用する。書込み後は別の新鮮な読取transactionでhead/receipt/ledger全件を取得し、全永続列・全payload byteが今回の証拠と一致した場合だけ返す。期待receiptは操作自身の既知値から構成し、読んだDBの値を期待値へ採用しない。操作をまたぐキャッシュはない。

不一致は同tokenでもStoreConflictにして、Coordinatorの曖昧な応答回復が成功へ変換しないようにする。公開readの破損検査は従来のStoreCorruptionのまま。create/saveSlot/loadSlot/duplicate/no-op/例外後の再読は旧厳密経路を保持し、transaction内でduplicateに流れた場合も新しい証拠経路は使わない。保存頻度・start marker・atomic性・schema/codec/core/RNGは変更しない。

2026-11-17報告：[固定source3c1180bの検証](VALIDATION.md)で全11workflow/19jobs、app31/core22/device40、実720試合・実force-stop復旧が成功。同一emulatorの主要保存操作は21.3〜23.4%短縮し、全10工学ゲートを満たした。実720試合は11分1.123秒。旧18分30.228秒は別runnerの歴史的参考で、全シーズンの因果的改善率の分母にはしない。[実装PR #46](https://github.com/thrsth45h/baseball-sim/pull/46)。

## APKの取得と操作

1. GitHubへログインし、[検証済み配布ZIP](https://github.com/thrsth45h/baseball-sim/actions/runs/42849348028/artifacts/13779089307)を取得する。期限は **2026-11-25 00:59:40 JST**。
2. ZIP内の `app-debug.apk` をAndroid端末へ手動で導入する。API26以上が宣言上の対象で、実行確認は隔離CIのAPI35のみ。
3. 起動後、画面上部の「ペナント」→ seed/球団を選択 →「新シーズン」。観戦の「1球」「1打席」「試合終了まで」または日程スキップで進める。旧「1試合」モードも保持する。

APKはsource `3c1180b642a93d99bf9d955c05b0182fe0efa733`、10,876,017byte、SHA256 `9edf3b5c1fadd2399217536072adc59ad1abf3d71c5cd611ab8a59524fb8997d`。versionCode2 / 0.2-pennant-tech。PCサーバ・Python・通信は不要。別runnerのdebug署名が異なる場合は旧版から保存維持更新できず、アンインストールは端末内セーブを削除するため注意する。自動的な削除/再導入は行わない。

## 検証資料

[検証記録](VALIDATION.md)・[CI証拠](CI_EVIDENCE.json)・[全308標本CSV](BENCHMARK_SAMPLES.csv)・[全10比較と範囲](BENCHMARK_SUMMARY.json)・[共通条件/復元規則](BENCHMARK_METADATA.json)。計測専用コピーを配布APKと混同せず、局所比較を実機や長期DBの速度保証へ外挿しない。物理端末、API26実行、電源断、長期互換、次年度移行は未検証または対象外。

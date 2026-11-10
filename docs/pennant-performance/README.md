# 保存・日程スキップの限定性能改善

[設計PR #45](https://github.com/thrsth45h/baseball-sim/pull/45)・[正本](../PENNANT_PERFORMANCE_DESIGN.md)・[ADR-0016](../adr/0016-pennant-store-performance.md)に基づくPhase 5aの内部変更。基準runtimeはmain `7ebb4a46a319ffde5e390a86b8f0a3a0352e4866`。新しいゲーム仕様、製品DB、次年度/能力/経営の実装ではない。

通常commitの新規書込み成功時だけ、操作内の確定byteから検証済みdecoded状態を再利用する。書込み後は別の新鮮な読取transactionでhead/receipt/ledger全件を取得し、全永続列・全payload byteが今回の証拠と一致した場合だけ返す。期待receiptは操作自身の既知値から構成し、読んだDBの値を期待値へ採用しない。操作をまたぐキャッシュはない。

不一致は同tokenでもStoreConflictにして、Coordinatorの曖昧な応答回復が成功へ変換しないようにする。公開readの破損検査は従来のStoreCorruptionのまま。create/saveSlot/loadSlot/duplicate/no-op/例外後の再読は旧厳密経路を保持し、transaction内でduplicateに流れた場合も新しい証拠経路は使わない。保存頻度・start marker・atomic性・schema/codec/core/RNGは変更しない。

[検証記録](VALIDATION.md)で実施/未実施・計測source・CI・採否を区別する。計測専用の生成コピーを配布APKのコードと混同しない。過去の18分30.228秒は別runnerの歴史的参考で、新しい改善率の分母にはしない。

# 限定性能改善の検証記録

2026-11-11、worker実装中。**CI・性能計測は未完了で、改善はまだ立証していない。** 設計基準は[正本](../PENNANT_PERFORMANCE_DESIGN.md)。この文書は結果回収後にsource/CI/全標本/採否を固定して更新する。

## 実装した検証

既存テストを残し、配布側のRoomSeasonStoreを直接対象とする反例を追加した。head全7列、receipt全5列、結果全7列の個別改変、prefix/末尾/件数/順序、同token payload/hash同時改変、receipt fingerprintのみ改変を扱う。Coordinator経由での成功callback抑止、同世代の後続revision競合、transaction内duplicateの旧経路、入力の防御freeze/返却decoded値の所有性、通常/終端/BLOCKEDのbaseline一致も検査する。

これらのテストコードを追加したことと、実機/CIで合格したことは別である。既存の実720試合、実プロセスforce-stop復旧、core/app回帰は本来のproductionクラスを使う。重いABBAは専用CIに分け、通常device suiteから明示除外する。

## 未完了

- CIのcompile/unit/device/実720/配布APK force-stop復旧。
- 同一emulatorの固定baseline/candidate ABBAと全標本回収、暫定80%/110%ゲートの判定。
- 責任者の独立コード/成果物レビュー、最終source固定とAPK報告。

ローカルGradle/SDK/IDEの導入や実行、ユーザーPCの画面・入力操作は行わない。物理端末、電源断、長期保存互換、次年度移行は今回も対象外。

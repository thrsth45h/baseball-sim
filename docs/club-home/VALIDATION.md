# 球団ホーム版の検証記録

実装基準は設計統合 `d134204b2c215adc484735d3ae2e956789add3ed`。現段階ではCI検証中であり、以下を成功済みとは扱わない。

## 対象

UIの8ページ化、既定ペナント、固定108人の公開件数表示、world/generation結合のUI復元、公開StoreTokenによる古い確認イベント拒否。core/season、RNG、日程、集計、Coordinator/Store、schema1、codecのproduction実装は変更しない。

## CIで確認するもの

- 既存純Kotlin/VM/保存故障・競合/単独1試合の回帰、lint、APKビルド。
- API35で閲覧のActions追加呼出し0、停止予約と画面選択保持、回転/戻る/世代境界、108人と5表示値、320dpと文字1.5倍の実PNG。
- 凍結済み旧writerによるschema1 fixtureの読み取りと閲覧前後のDB内容保持。これは実旧APKからの更新試験ではない。
- 既存の実720試合と最終frame、配布APKを同じinstallのままforce-stopした後の同checkpoint/次1球復旧。

API26は最低APIの宣言値。実機、製品版の長期保存互換、ゲーム能力の妥当性は今回の検証対象ではない。Android/Gradle/SDK/JDKのローカル実行は行わない。

完了時に対象head、CI run、artifactの期限、APK hash/source、責任者の独立frame/PNG照合を追記する。

# 球団ホーム版の検証記録

2026-11-17（日本時間）。[実装PR #49](https://github.com/thrsth45h/baseball-sim/pull/49)、承認設計統合 `d134204b2c215adc484735d3ae2e956789add3ed`。実装・テスト・配布APKの固定sourceは **`53938af62cc2a1be51fc33ccc66a3a822364e7c4`**、tree `8db52152cd941dd645d5b8c4247a1ee0b0de6b3a`。後続の文書だけの報告commitとは区別する。[操作・APK取得](README.md)・[CI証拠](CI_EVIDENCE.json)。

## 対象

UIの8ページ化、既定ペナント、固定108人の公開件数表示、world/generation結合のUI復元、公開StoreTokenによる古い確認イベント拒否。core/season、RNG、日程、集計、Coordinator/Store、schema1、codecのproduction実装は変更しない。

## CIの結果

固定sourceの全11workflow/19jobsと全stepのsuccessまたは意図したskippedを確認した。[通常Android CI](https://github.com/thrsth45h/baseball-sim/actions/runs/43995880058)、[配布APKと実プロセス復旧](https://github.com/thrsth45h/baseball-sim/actions/runs/43995879941)、[既存保存benchmark](https://github.com/thrsth45h/baseball-sim/actions/runs/43995880044)。CIの成功と、後述の旧性能ゲート未達は区別する。

| 検査 | method数 | failure/error/skip |
|---|---:|---:|
| app JVM | 36 | 0/0/0 |
| 純Kotlin core/season | 22 | 0/0/0 |
| API35通常device | 46 | 0/0/0 |
| host実プロセス復旧 | 1 | 0/0/0 |
| host旧局所profile（fixture起点） | 1 | 0/0/0 |
| host専用ABBA | 1 | 0/0/0 |

prepareは意図的にkillするsetupで成功methodへ数えない。profile/ABBAのfixture投入を実720transaction完走へ加算しない。lintはerror 0 / warning 8（既存の依存更新通知7件・application icon1件）。今回、依存更新は行わない。

## 閲覧・復元と既存セーブ

- 公開stateの0試合/途中/720試合の全108 ID・所属・打順・全5件数を照合。実UIのTextノードから途中/最終720の各108人の値をJSONへ出力し、期待stateの値を出力へコピーしない。同じ選手詳細を開いたままrevision更新で5件数へ追従する。
- fake Actionsの初期read後、閲覧・戻る・復元だけでは追加呼出し0。bulk中の閲覧/回転で同じViewModel/処理を維持し、STOPが使え、停止後も詳細ページを保持する。実ActivityのrecreateとAndroid戻る、旧1試合モードの復元も検査する。
- world/generationと通常revisionを区別し、一覧のスクロール復元/入口での初期化、初回読込中・失敗後再読、同seed別world/load世代でのリセット、古い確認ダイアログの解除を検査。未知・壊れたUI復元値はDBへ補正を書かず現在の操作球団のホームへ戻す。
- 凍結済み旧writerでschema1の記録済みDBを作成し、現Storeで読み取り、静止境界の閲覧前後で全6table/全byte・公開状態・操作球団・paused checkpoint/RNG/sourceを保持する。続く次1球と再保存も照合。これは**実旧APKからの更新試験ではない**。
- 新規/load確認の公開StoreToken比較と受付claimはViewModelの同じlock内。UIへ未公開の外部DB更新を検出する新保証ではない。core/season/store/Coordinator/schema/codecは変更しない。

[device成果物](https://github.com/thrsth45h/baseball-sim/actions/runs/43995880058/artifacts/14291031151)に、上記観測JSON、XML、ホーム/一覧/詳細/設定/エラー/停止/BLOCKED等のPNGを保持する。320dp・文字1.5倍では縦移動し、既存の順位/記録表は横スクロールできる。

## 実Activity/Roomの720試合

seed42を新規作成し、実UIの「シーズン終了まで」で720試合を進めた。全12球団各120試合、day141、108人、activeなし、完了後の再読でSeasonState全値一致を確認した。

| 計測 | 値 |
|---|---:|
| UI操作開始から完了/検算出力まで | 594,484ms（9分54.484秒） |
| 最終frame | 1,585,652byte |
| DB / WAL | 5,046,272 / 3,065,312byte |

最終frame SHA256は `0bde5d0de901771b13e75adc64aab9d21a5854c8f7aed9e34aafb72a3881bbde`。旧11分1.123秒とは別runnerであり、今回の差からUIや保存の因果的改善率を求めない。実機の速度保証でもない。

## 配布APKの実force-stop復旧

同じ配布APK/test APK/DBのまま、prepare PID4214の生存、am force-stop後の不在、新PID4298での復旧を確認した。再導入・データ消去を挟まない。同world/generation/revision3、GameId `S1-D002-L1-R01-M1-G1`、公開5球/advance5とcheckpointを復元し、次の1球だけ進めた。

保存checkpoint SHA256は `18c8ff07180fbc60ed5935c01ace7b94d60c7433dbc8fee5e6273e46e0e246f7`、次1球は `f57af0454af300bb99316126cb7a489da6a0240566691e4891159d1562e3262c`。[復旧証拠](https://github.com/thrsth45h/baseball-sim/actions/runs/43995879941/artifacts/14289312283)・[実際に検証した配布APK](https://github.com/thrsth45h/baseball-sim/actions/runs/43995879941/artifacts/14289816997)。APKのhash/期限は[取得手順](README.md)を正本にする。通常device側の別runnerビルドを、配布APKと同じbyteとは扱わない。

## 既存benchmarkの性能ゲート

保存の正しさのJUnitは成功し、308標本（測定264・warm-up44）を保持した。一方、旧性能改善の工学ゲートは今回 **未達**。prefix600のresultの新/旧中央値比が **0.8211277569** で、旧基準の0.8以下を満たさず、`performance_gate_passed=false` だった。CI成功だけから全性能ゲート通過と報告しない。

今回の限定UI設計は速度改善を採用条件にしていない。保存/coreのprotected sourceは基準mainの15ファイルと不変で、新しい性能改善の採用や実機への外挿をしない。通過を探す再実行や保存実装の範囲拡張も行わない。過去PR #46の別source・別実行での採否記録は履歴として保持する。

## 独立レビューと限界

責任者は別回収したartifactのSHA256/CRC/path、実XMLのapp36/core22/device46（既存40保持）と失敗/スキップ0、配布APKのsource/hash/実プロセス復旧を照合した。実720の最終frame全byte、全12順位、最終720試合時の実UIに表示された全108人の所属/打順/5件数が、以前に固定した独立期待値と一致した。期待frameの固定は成功したAndroid全試合出力より前で、Android UI実装より前ではない。

PNG22枚を回収し、責任者は主要8枚（320dp/文字1.5倍のホーム・一覧・詳細・設定・エラー・STOP・BLOCKED試合、および実bulk中の詳細）を独立して視覚確認した。文字/ID/数値/主要操作は読め、到達可能で、スクロール端の意図した見切れ以外に必須の表示問題はなかった。実プロセス復旧のPNGも確認した。別readonly reviewerと責任者のコード点検を含め、残る必須指摘はない。この文書段階でPRのマージ済みとは扱わず、最終headと統合は責任者が確認する。

API26は最低APIの宣言値で実行未検証。物理端末、電源断、製品版の長期保存互換、実旧APK更新、能力/采配/経営/次年度は未検証または範囲外。Android/Gradle/SDK/JDKのローカル実行、ユーザーPCの画面・入力・既存環境の操作は行っていない。

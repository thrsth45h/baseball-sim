# Android初版の検証記録

2026-11-01日本時間。[承認設計](../ANDROID_FIRST_MATCH_DESIGN.md)・[実装PR #40](https://github.com/thrsth45h/baseball-sim/pull/40)。**実ビルド/最終検証source headは`2b0708294887f23fced10cd71a726dbd5fde030a`、treeは`e323a47782d9c92c5b2d3c34cb54a963f24ae66f`**。このsourceから作ったAPKとCI成果物を固定して報告する。本書等を追加する後続report commitでsource自己SHAを書き換え続けない。

## 実行した検証

[CI run 41223833834](https://github.com/thrsth45h/baseball-sim/actions/runs/41223833834)、Ubuntu24.04/Temurin17.0.21+9。両jobが明示source headをcheckoutし、Wrapper hashとSDK36/BuildTools35を確認した。

| 検査 | 件数・結果 |
|---|---|
| 純Kotlin core JVM | 7 method、failure/error/skip0 |
| app JVM | VM7＋Threading2＋adapter3＝12 method、failure/error/skip0 |
| Compose instrumented | API35/x86_64/google_apis、6 method、failure/error/skip0、24.016秒 |
| Android lint | 0 error、9 warnings（下記） |
| Debug APK assemble | 成功、10,395,218bytes |
| merged debug manifests | 3ファイルすべてINTERNET権限なし |
| PNG | initial/completed/narrow320の3枚を実回収 |

fixtureの49ケース・2,600 advanceや各モードの組合せをmethod数へ足していない。旧Python/研究の件数もこの表へ加算しない。対象2b headの既存Python/Web回帰を含む全8workflow/16jobsの成功も責任者が確認した。SDK/Gradle/IDE/JDKをユーザーPCへ導入せず、可視ウィンドウ/物理入力/既存プロファイルも操作していない。

## 互換・乱数・入力

Kotlinモデルを1ファイルも作る前に、別担当の固定参照を[受領・commit](FIXTURE_RECEIPT.md)した。49ケースの同じ入力をnativeへ渡し、2,600回すべてのadvance後snapshotと最終結果を比較する。カウント/ファウル/四球/K、限定safe行先、打順/表裏/得点表、9回裏省略、引分、サヨナラ四球/safe/HR、cap/source不足/不正の未受理を含む。旧flagsや実装identityはnativeへ偽装しない。

不正入力の凍結列はsanitized abort markerなので、marker再生だけでnativeの原入力検査を証明したとは扱わない。native別テストで欠落fair・占有矛盾・nonfairにfair/fairDrawを付ける不正などを検査する。後者2例は旧契約どおりinvalid_pitch_inputで、その球は未受理、Begin後のcountは保持する。seedは正規ASCII十進文字列/unsigned64範囲を検査し、型付き状態の不正値/到達不能count/可変コレクション流入も拒否/独立化する。得点/回数の加算はchecked Longで、silent overflowをしない。

live ToyPitchSource＋sm64-v1を実際に動かす4seedも、全投球入力・全snapshot・毎advanceのlogical/raw/rejection/stateを凍結参照と比較した。独立raw60値と別draw60値、採用閾値4294967200の強制棄却例3列も同じproductionループで検査する。旧Python Random5試合は記録入力の遷移互換であり、native同seedの一致条件にはしない。

| native sm64-v1 seed | Away–Home | 受理球 | draw100/raw |
|---:|---:|---:|---:|
| 0 | 3–3 | 276 | 330/330 |
| 42 | 7–3 | 239 | 300/300 |
| 2026 | 0–5 | 243 | 293/293 |
| 18446744073709551615 | 2–9 | 275 | 326/326 |

4試合では棄却0だったため、試合だけで棄却経路を検証したとは呼ばない。sm64-v1は今回の技術検証契約で永久の製品RNGではなく、仮重み/一定進塁のMLB適合や能力効果も検証していない。

## 操作と画面

1球/1打席/最後まで/混在は共通advance入口を通り、結果/ログが同seedで一致する。別試合の交互実行も干渉しない。ViewModelは実行中を同期的に取得してnewを含む連打を捨て、計算・初期生成/読取りをUI Thread外の計算dispatcherへ出す。VM unitは単一の計算Threadを注入し、factory＋snapshot＋9advanceの11呼出しすべてがUI Threadと異なる実Threadであることを確認し、初版のcoroutine debugによるThread名変化への誤依存を修正した。cleared世代の遅い結果が新状態へ公開されないことも検査する。

API35の画面なしCI emulatorでは、実Activityで3操作→終了、invalid seedで状態保持、ActivityScenario.recreateによる同ViewModel/同試合保持を検査。別のCompose表示テストでbusy/abortedと320dp幅を確認した。320dpは合成Presentationを置いた狭幅UI検査で、スマホ実機ではない。得点表9/Rは内部の横スクロール領域にあり、外側のはみ出しと区別する。

最終2b sourceの`initial.png`（1080×1920）、`seed42-completed.png`（1080×1920）、`narrow320.png`（840×1920）はCI追加出力artifactに実在する。worker/責任者が目視した初版150の同3枚と完全byte一致し、親は独立に2bのbytes/hashを照合した。7–3/239球/78PA、停止時のcount/塁非表示、ボタン無効を確認した。PNG確認を、実機インストールや物理入力の検証とは扱わない。

## 独立確認とCI修正

責任者は別に取得した初版150 sourceのportable core bundleを、既存JDK21.0.2で再実行し7 method成功（1.017秒）。その後2bまでcore runtime/testsは不変で、2bのCI17実行も成功した。最終2bで親がJDK21再実行したとは記録しない。親は別JDK SplittableRandomのraw64と旧Pythonへの接続でも独立参照の4native試合を照合した。さらに2bのunit/device artifactを独立に取得しZIP SHA256・XMLのcore7/app12/device6全failure/error/skip0とPNG3枚のbyte一致を確認した。

初回CIのSDK manager PATH不足はCI内SDKの明示pathで修正。app unitのThread名exact比較は実Thread同一性へ修正し、11呼出しの非UI検査を保った。lintで見つかったwindowLightNavigationBarのAPI27要件はvalues-v27へ分離してmin26を維持し、lint抑制を使わなかった。

残るlint9 warningsは互換pinより新版がある通知7件、dataExtractionRules後継設定の提案1件、専用launcher icon未指定1件。baselineや無効化で隠していない。製品の保存/配信/見た目の仕上げが完了したという主張にはしない。

## APKと証拠の取得

[APK artifact 13051121143](https://github.com/thrsth45h/baseball-sim/actions/runs/41223833834/artifacts/13051121143)はCI upload logの実際のURL。ZIP SHA256 `4ed47b5d7979dcddad1a14e7ebf8e26ebf7d15b4a2728de3098f8b493364a68e`、APK SHA256 `c3ffe9a70d75a572437dd91e2b3720ddee2cf8c47e1a506f04d0f084e632c90c`。worker/親がそれぞれ取得してhash/source-infoのhead/tree・shaファイル・APK ZIP CRC/classes.dex/Manifest/libpythonなしを照合した。dependency-merged3manifestのINTERNETなしも確認した。

- [device結果/PNG](https://github.com/thrsth45h/baseball-sim/actions/runs/41223833834/artifacts/13051910131)
- [unit/lint結果](https://github.com/thrsth45h/baseball-sim/actions/runs/41223833834/artifacts/13050911813)
- [純coreのportable監査bundle](https://github.com/thrsth45h/baseball-sim/actions/runs/41223833834/artifacts/13051345951)

保持14日。APK期限は**2026-11-14 17:24:04 UTC＝2026-11-15 02:24:04 JST**。API列挙のUTCと日本時間を混同しない。source-infoと実APKhashは同梱し、別runのAPKバイト一致や永続署名互換を保証しない。機械可読の記録は[CI_EVIDENCE.json](CI_EVIDENCE.json)。

## 未検証・対象外

minAPI26は宣言/ビルド/lintで確認し、実行したemulatorはAPI35だけ。実機インストール/動作/性能/読み上げ、API26での実行、実際の端末回転は未検証（Activity再生成は試験済み）。プロセス終了後の製品セーブ/復帰は含めない。

旧Python/旧reports/旧CI/研究/台帳/長期方針を変更しない。後続report commitの既存変更はroot READMEの入口とHANDOFFの完了記録だけ。次工程は責任者がペナント基本機能の**設計案を提示するだけ**で、詳細未承認の大規模コードへ自動着手しない。

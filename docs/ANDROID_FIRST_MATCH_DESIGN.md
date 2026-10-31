# Android最初の1試合アプリ案

2026-10-31。基準main `602b2d448ec25eae92756cb3e66cd9959c99d541`。**Proposed / 未承認・未実装**。D032はOPEN。ユーザーの「つぎへ」を受けて責任者が次の候補を整理したもので、Android技術採用の承認とは扱わない。

## 完成条件

Androidへインストールできるdebug APKをCIで作り、PCサーバ・通信なしで仮2チームの1試合を操作する。リプレイだけの表示ではなく、端末内で1球ごとに生成・進行する。最初の成果物はインストール用APKと検証結果で、Play公開/署名鍵/配信アカウントは対象外。

## 比較と推奨

| 案 | 利点 | 負担・反例 | 今回の判断案 |
|---|---|---|---|
| Kotlin＋Jetpack Compose | Androidの公式推奨UIとライフサイクルへ直接接続。計算部分をJVMだけで検査可能 | Pythonの限定ルールを移植・照合する必要がある。iOS等とのUI共有は別途必要 | Androidを確定対象とする現計画に推奨 |
| Flutter | Android/iOS/desktop等で画面コードを共有する選択肢 | Dartへ計算部分を移す必要は残り、現在未計画の他OS対応を先に背負う | 他OSも初期対象になれば再検討 |
| WebView＋現在の画面 | HTML/CSSの再利用が可能 | 今の画面はPCのPython HTTPサーバに依存し、そのままAPKに入れても単体動作しない。Python同梱または別の計算接続が必要 | 今のサーバを製品依存にしない |

推奨は責任者の判断。公式ガイドは推奨であり、このゲームの完成や性能を保証する証拠ではない。[Androidの構成推奨](https://developer.android.com/topic/architecture/recommendations)・[ViewModel](https://developer.android.com/topic/libraries/architecture/viewmodel)・[Flutter構造](https://docs.flutter.dev/resources/architectural-overview)を参照した。

## 実装範囲案

- `android/`配下へGradle wrapper付き独立project。`core`はAndroid非依存の純Kotlin、`app`は単一Activity＋Compose Material 3＋ViewModel。最初は1画面で、Navigation/DI framework/DBを追加しない。依存・Gradle/AGP/Kotlin/Compose/SDKの互換を公式文書で確認して固定し、動的な`+`版を使わない。
- 初版の最低対応はAPI26（Android8.0）を提案。compile/targetは実装時の安定SDKと互換ツールで固定し、値と根拠を記録する。ユーザー端末での対応・性能は未確認。
- 既存4a–4fの限定ルールを意味で移植する。カウント、四球/三振/ファウル、限定全員safe安打と一定進塁、打順持越し、9回裏省略、サヨナラ、9回同点引分、上限10,000球で未完了という範囲を変えない。未較正の同じtoy重みを使用する。
- UIは現在のPC試作を参考に、1球/1打席/最後まで、新しいseedの試合、回表裏・打者・B/S/O・塁占有・得点表・直近ログを表示する。公式個人成績や未実装能力を表示しない。停止時にはカウント/塁を隠し、サヨナラ専用終端の確定得点を使う。
- 3操作は同一の逐球入口を使う。表示の再描画で進行・抽選しない。処理中の操作は受理しない（連打をキューに積まない）。計算はUI thread外で逐次実行し、immutable UiStateを公開する。試合ごとの世代IDで古い計算結果が新しい試合へ反映されるのを防ぐ。
- ViewModelが試合・乱数・実行中状態を所有し、画面回転等のActivity再生成で試合を新規生成/二重進行しない。プロセス終了後の自動復帰は含めず、次起動は初期seed42の新試合。ViewModelは永続セーブではない。
- INTERNET権限・WebView・Python runtime・PCサーバへの接続を使わず、完全オフライン。表示用seedはASCII十進文字列、0..18446744073709551615を厳密検証し、不正入力で試合/RNGを変えない。

## 乱数とPython互換を分ける

Pythonの`random.Random`とKotlin標準乱数の同seed一致は保証されない。[Kotlin標準Random(seed)](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.random/-random.html)は実装量が最小だが、公式に将来のruntime版で系列が変わり得るとされている。今回は小さい算術を版付きで固定して変更を検出しやすくする案を選ぶ。その分、独立test vectorと棄却境界の検証責任が増す。移植検証を乱数差に隠さないため、次の2系統で確認する。

1. **遷移互換**：既存Pythonから合成の明示投球/プレー列と期待状態を移植前に生成・凍結する。Kotlinへ同じ入力列を与え、受理球数、カウント/塁/アウト、得点表、打順、完了打席件数、終了理由/終端増分を逐球で照合する。Python旧全JSONのflagsや実装identityをKotlinへ偽装しない。fixtureは新領域へ置き、旧結果を更新しない。
2. **Android内の再現**：小さい整数アルゴリズムを`sm64-v1`として固定する案。seedを64bit unsigned stateとし、呼出しごとに`0x9E3779B97F4A7C15`を加算、xor右shift30・乗算`0xBF58476D1CE4E5B9`、xor右shift27・乗算`0x94D049BB133111EB`、xor右shift31の順。各算術はmod 2^64、右shiftはlogical。永続state sには加算後の値だけを保持し、z←sを作ってzだけをmixする。mix出力をsへ戻さない。出力の上位32bitを非負整数u（0..2^32−1）とし、u<4,294,967,200ならu mod100、以上なら次の出力で再試行。初期seedを先にmixしない。試合専用stateを1個持ち、投球drawを1つ、fair時のみ追加drawを1つ要求する（棄却によるraw出力回数は別集計）。時計・global RNGを使わない。

この算術形の参照は[OpenJDK SplittableRandomのseed加算/mix64](https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/util/SplittableRandom.java)。新コードは明記した算術から独立に実装し、JDKのsourceをコピーせず、split/nextIntの互換を称さない。製品の最終乱数方式を確定するものではなく、初版の版付き試作契約。独立計算のraw/draw test vector、seed0/42/最大値、棄却境界を確認する。**Androidのseed42がPython例の4対2になるという受入れ条件は置かない。** Android同版・同seedでは全3操作/混在と再起動後の新試合が同じ結果/ログになることを要求する。

## 検証と作業順序

1. 本人承認後にADRをAcceptedとし、D032のうちKotlin/Composeとこの最小構成の採用だけを明記。DB/製品セーブ/長年ログ・能力/研究採用はOPENを維持する。設計PR統合後、worker専用worktreeへ実装を委任する。
2. Python旧source/結果を保持し、独立fixtureを先に凍結。普通の完走、裏省略、同点、サヨナラ四球/安打/HR、打順持越し、上限/不足/不正の状態不変を含む。固定入力で遷移互換、別にsm64-v1の既知値/棄却、seed再現、試合間非干渉を検証する。
3. JVM unit test・Android lint・debug APK assembleをCI実行。Compose操作テストは試験用エミュレータをCI/隔離環境で動かし、1球/1打席/最後まで・終了/不正seed・Activity再生成・処理中の連打拒否・狭幅表示を確認する。CIのemulator開始すらできない場合は理由を報告し、APKビルドだけを画面テスト成功と扱わない。
4. 責任者が別実行・コードレビュー・隔離スクリーンショットで検証し、問題をworkerへ戻す。debug APKをCI artifactへ保存し、PRへリンク・保持期限・取得方法を記録。実機へのインストール/操作はユーザーの任意確認であり、こちらで操作しない。

**ユーザーPCの既存画面・入力を操作しない。** IDE/SDKをユーザー環境へインストールせず、可視エミュレータも起動しない。現環境はJDK21の実体を確認できたが、標準SDK path/ANDROID_HOME/adb/GradleではAndroid構築環境を確認できていない。CIで必要な環境を用意し、ローカルPCの構築済みとは称さない。

12球団ペナント、経営、交代、一般走塁、能力1〜20、MLB係数、研究モデル接続、DB/セーブ、Play公開はこの最初のAPKへ含めない。

# Android技術検証版の固定構成

2026-11-01。設計PR #39の承認範囲で、互換表が重なる安定版を固定する。最新版という主張ではなく、CIで実ビルド・検査する出発点である。ローカルPCへSDK/IDE/Gradle/JDKを導入せず、ユーザーの画面/入力を操作しない。

| 要素 | 固定版 |
|---|---|
| Gradle | 8.13 |
| Android Gradle Plugin | 8.11.1 |
| Kotlin / Compose Compiler plugin | 2.2.21 / 2.2.21 |
| Compose BOM | 2025.10.01（UI/runtime 1.9.4、Material3 1.4.0） |
| Activity Compose | 1.11.0 |
| Lifecycle Compose/ViewModel | 2.9.4 |
| Coroutines Android/test | 1.10.2 |
| compile / target / min SDK | 36 / 36 / 26 |
| SDK Build Tools | 35.0.0 |
| CI JDK | Eclipse Temurin 17.0.21+9 |
| CI runner | ubuntu-24.04（提供イメージ自体は更新される） |

[AGP 8.11公式互換表](https://developer.android.com/build/releases/agp-8-11-0-release-notes)はGradle8.13/JDK17/最大API36を指定。[Kotlin公式互換表](https://kotlinlang.org/docs/gradle-configure-project.html)の2.2.20–2.2.21はGradle7.6.3–8.14、AGP7.3.1–8.11.1に完全対応する。Compiler pluginを[Kotlinと同版](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler)に揃え、BOMでcompiler版を決めない。

Google公式Mavenの[BOM POM](https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/2025.10.01/compose-bom-2025.10.01.pom)を読取り、上記UI/runtime/Material3版を確認した。[Activity1.11.0](https://developer.android.com/jetpack/androidx/releases/activity#1.11.0)はAPI36でコンパイルされ、[Lifecycle2.9.4](https://developer.android.com/jetpack/androidx/releases/lifecycle#2.9.4)は安定リリース。JDKは[Temurin17.0.21+9公式release](https://github.com/adoptium/temurin17-binaries/releases/tag/jdk-17.0.21%2B9)を使う。

## Wrapper

`gradlew`・`gradlew.bat`・43,705bytesの`gradle-wrapper.jar`はGradle公式`v8.13.0`タグの配布用sourceから取得する。JARはビルド起動に必要な小さい第三者バイナリとして追跡し、ローカルで実行/インストールはしない。[公式checksum](https://gradle.org/release-checksums/)との照合をCIの最初に行う。

- JAR SHA256: `81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f`
- `gradle-8.13-bin.zip` SHA256: `20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78`
- distribution URLと`distributionSha256Sum`はwrapper.propertiesへ固定。[Gradleの検証手順](https://docs.gradle.org/current/userguide/gradle_wrapper.html#sec:verification)に従う。公式wrapper source内のライセンス表記を保持する。

## CIと取得経路

新しいandroid-first-match workflowでcore JVM test、ViewModel JVM test、lint、debug APK assembleを別stepにする。別jobのAPI35/x86_64/google_apisエミュレータは`-no-window`でCI runner内だけに起動し、Compose instrumented testsを行う。公開[Android Emulator Runnerの設定](https://github.com/ReactiveCircus/android-emulator-runner)を使い、全Actionをcommit SHAへ固定する。これをユーザーPC上で実行する手順にはしない。

APK/hash、unit/lint結果、device結果をそれぞれGitHub Actions artifactへ14日保持する。最終head/run/artifact ID、SHA256と取得方法は実際のCI後に記録する。まだ実行していないビルドやエミュレータを成功と称さない。[GitHubのartifact方式](https://docs.github.com/en/actions/tutorials/store-and-share-data)を使い、APKをGitのsourceや旧reportsへ入れない。

連携ツールの`fetch_workflow_run_artifacts`で列挙、`download_workflow_artifact`でZIP取得、`fetch_workflow_job_logs`でログ確認ができる。一般のGitHub fetchはUTF-8のみなのでAPK/ZIPバイナリ取得には使わない。認証tokenの抽出・接続設定の変更や、手動tokenのCI埋込みをしない。

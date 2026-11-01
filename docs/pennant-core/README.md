# Phase 5a 第1PR：純Kotlinの最小ペナント

[承認設計PR #41](https://github.com/thrsth45h/baseball-sim/pull/41)の3実装PRのうち、[第1PR #42](https://github.com/thrsth45h/baseball-sim/pull/42)。固定12球団/108人、日程720試合、試合seed、既存coreによる進行、順位/限定イベント集計、保存用の値DTOとcodecを追加する。**Room保存とAndroidのペナント画面は後続PR。ここだけでAPK完成とは扱わない。**

正本は[PENNANT_BASIC_DESIGN.md](../PENNANT_BASIC_DESIGN.md)。日程/主催/IDと独立参照の具体契約は[test resourceのCONTRACT](../../android/core/src/test/resources/pennant-reference/CONTRACT.md)、実装前の受領は[FIXTURE_RECEIPT.md](FIXTURE_RECEIPT.md)、結果は[VALIDATION.md](VALIDATION.md)。120試合・固定打順・toy確率・sm64-v1は今回の試作用で、製品の永久規約やMLB妥当性を決めない。

## 利用する境界

新コードは`com.example.baseballsim.season`。旧coreの4ファイル・7テスト・固定参照を変更せず呼び出す。

```kotlin
val owner = SeasonSession(SeasonState.initial("42", "L1-T01"))
owner.beginNext()                    // activeの0 advance境界。まだ投球しない
val startCandidate = owner.state    // 後続storeは先にこの開始markerをcommitする
owner.advanceOnePitch()
val candidate = owner.state         // immutable。保存する前に確定表示しない

val bytes = CheckpointCodec.encodeSeason(candidate)
val decoded = CheckpointCodec.decodeSeason(bytes)
val restored = SeasonSession(decoded) // activeなら全snapshot/RNG/sourceをseed再生で照合
restored.advancePlateAppearance()
```

- `PennantSchedule`は固定球団/選手・日程・140日calendarを持つ。`allocate(masterSeed)`で正規ordinal順の720seedを作る。球団の注目先はseed割当に影響しない。
- `SeasonSession`の1球/1打席/現在試合終了は同じ既存core入口へ委任する。`openNextSelected`、`advanceDay`、`finishSeason`はメモリ内進行の入口。`stopRequested`は試合外なら新しい試合を始めず、activeがあれば現在の1試合確定後に止まる。
- 後続storeは、bulk helperを丸ごと計算して最後だけ保存せず、各試合の開始markerと完了をそれぞれ永続化する。DBの世代/revision/operationId、commit後公開、保存失敗時の候補破棄はstoreの担当で、本PRはその実装ではない。
- `SeasonState`は確定結果台帳から順位と全108人の限定記録を作る。完了結果の同値再受理は同じstateを返し、cursor/activeも変えない。全要約値の異なる再受理は拒否する。
- `GameCheckpoint`は呼出し数・全snapshot・初期値・source/RNG消費を保持する。production再生は正規日程/割当seed、初期zero、上限10000、既知の版、固定Toyに限定。任意initial/ScriptedSourceは合成診断用で、production復旧へ混入させない。
- 保存済みABORTEDは`BLOCKED`の診断として保持し、再生・再進行しない。受理0球でもBegin後のcountとadvance呼出し数を失わない。完了済みactiveだけが未登録の場合は、再生照合後の操作で追加投球せず一度だけ結果へ反映する。

`decodeSeason`は形式・版・正規plan/seed・日付cursor・全台帳・再生成集計を検査する。**decodeだけで進行中のRNG再生まで検証したとは扱わない**。`SeasonSession(decoded)`の照合成功後に操作可能にする。未完了診断は再開を許可せず表示用に保持する。

## 明示codec

`CheckpointCodec.encodeSeason/decodeSeason`、`encodeGame/decodeGame`、`encodeSummary/decodeSummary`を公開する。既存coreへ保存annotationを追加せず、値を明示的に変換する。形式はversion付きbig-endian/UTF-8、配列長と文字列長/総bytesに上限があり、未知magic/版・不正値・trailing bytesを拒否する。Javaオブジェクト直列化や`toString/hashCode`の保存ではない。

seasonのcodecには開幕前plan720件・seed、結果台帳、active、日付/注目球団、再生成できる順位/108人集計も含め、decode時に整合を照合する。summary codecは1試合の全要約値だけを独立保存するための境界で、結果行ごとにseason全体を保存する必要はない。保存magicは本試作の互換管理で、永久の製品セーブ仕様ではない。

## CIとSDK不要の監査

ユーザーPCへSDK/JDK/Gradleを導入せず、ビルドはCIで行う。既存Android workflowの`:core:test :core:exportAuditBundle`に新テストが含まれる。専用`pennant-core-tests`は同じbuilddirで通常実行と`--rerun-tasks`を行い、2160試合の全値/集計を比較する。初期化するのはcanonical pathを検査した`android/core/build/pennant-audit`だけで、fixtureや保存済み資料は削除しない。

portable artifactの`classes/`と`lib/*.jar`を展開すれば、既存JDKで次の監査ができる。これは検証方法で、Androidアプリの実行方法ではない。

```powershell
java -cp 'bundle/classes;bundle/lib/*' org.junit.runner.JUnitCore com.example.baseballsim.core.CoreTest com.example.baseballsim.season.SeasonTest com.example.baseballsim.season.SeasonBoundaryTest com.example.baseballsim.season.SeasonFullRunTest
```

再実行のJSONを保存したい場合は、新規専用ディレクトリを`-Dpennant.audit.dir=...`へ指定する。JUnit直接実行はその出力を上書きせず、Gradle Testだけが自分の宣言済み生成dirを初期化する。artifactはsource headを名前と`SOURCE_HEAD.txt`へ結び付け、14日保持。取得リンク/実hash/測定値は検証記録に固定する。

## 限定記録

保存する打者値は完了打席・セーフ安打（HR含む）・四球・三振・本塁打のイベント数。正式AB/打率/出塁率/RBI/得点や投手成績は作らない。勝率はW/(W+L)の整数比で比較し、同率は1位・1位・3位、未定義は順位/勝率なし。ID順は表示安定化だけで優劣ではない。

一軍/二軍、交代・能力・経営、実在暦/交流戦、次年度移行、長期DB/schema互換、Androidでの保存/プロセス強制終了復旧、端末性能は本PRで実装・検証したことにしない。後続の保存PR/UI PRとAPK受入れまで継続する。

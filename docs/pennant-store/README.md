# Phase 5aの保存・復旧層

[承認済み設計](../PENNANT_BASIC_DESIGN.md)に従う第2実装。画面接続は第3PRで行う。旧core/season/既存画面は変更しない。

独立レビュー修正後のsource `6e9ee0be613808939a3a46dacba953c9597930cc`のCI検証は完了。[検証記録と未検証事項](VALIDATION.md)・[機械可読証拠](CI_EVIDENCE.json)を参照。旧f8cの成功記録と、追加された並行更新/操作IDの回帰検証を区別する。

## Android側の利用

`PennantDatabase.open(applicationContext)`で端末内DBを作り、`RoomSeasonStore(database)`を`SeasonCoordinator`へ渡す。DBはApplication相当のownerが保持し、1つのViewModelの画面回転で閉じたり再作成したりしない。

```kotlin
val coordinator = SeasonCoordinator(RoomSeasonStore(database))
val current = coordinator.read() ?: coordinator.create()
val next = coordinator.advance(SeasonAdvance.PITCH) // PLATE_APPEARANCE / GAMEも同じ入口
```

呼出し元は返された`StoredSeason`だけを公開する。`state`はimmutableなseason値で、`token`はworld/generation/revision、`operationId`は最後に保存された操作ID。UIが計算候補のSeasonSessionを保持して進めない。

- `read()`：保存内容/台帳/全集計を検査し、進行中なら固定seedからsnapshotとRNG/source消費まで再生照合。完了済みactiveの未集計だけが残る場合は追加投球せず一度だけ確定する。BLOCKEDは再生・再進行しない。
- `create(seed, selectedClubId, replacementConfirmed)`：初回作成。または明示確認後の検証シーズン置換。既存slotとBLOCKED診断を保持する。
- `selectClub(id)`：注目先だけ変更。乱数を消費しない。
- `advance(mode)`：開始markerを先に保存し、同じcoreで計算、保存後だけ返す。
- `advanceDay(...)` / `finishSeason(...)`：1試合ずつ保存。`stopRequested`は試合境界で確認し、activeがあるときはその試合まで確定。`onCommitted`は永続化済みの進捗だけを受け取る。
- `openNextSelected(...)`：前の未消化試合を同じcoreで確定して、注目球団のactive markerまで進む。
- `saveSlot()` / `loadSlot()`：試合外だけ。loadは新しいgenerationへatomicに切替える。

各進行操作は`tryLock`で排他し、重なった呼出しをqueueせず`SeasonBusy`で拒否する。ViewModelもbusy中の操作を無効化し、画面破棄後の古い処理を公開しない。callback自体が失敗・中断しても、既に確定した保存を巻き戻さない。

保存失敗時は計算済みSessionを保持しない。CoordinatorはNonCancellableの再読でcommit成否を確認する。応答を失った場合も、operationIdだけでなく期待するtoken遷移と候補stateが一致する場合だけ確定データを返す。StoreConflictは成功へ変換せず伝播する。storeの通常応答・duplicate・no-opも当該commitまたは既存no-op境界のworld/generation/revision/operationIdへ結び付け、別writerが更新/ロードした後の新しいheadを古いbulk処理が引き継がない。キャンセルされた呼出しは再読後もキャンセルを伝播し、破棄済み画面へ公開しない。その他の失敗は元データを残して返し、UIが自動新規作成へ進まないこと。

## 保存の正本

Room schema1に現在の進行データ、GameId別完了台帳、1つの手動slot、operation receipt、世代の再利用防止registry、未完了診断を持つ。結果キーは(world,generation,season,game)で一意。現在のpayloadは全plan720seed・全結果・集計・activeを明示binary codecで保存し、台帳の各summaryと一致検査する。serializerやJava object serializationは導入しない。

CPU計算と復旧はtransaction外・計算dispatcherで行う。transaction内では期待generation/revisionを確認し、台帳/集計を含むpayload/active/日程とoperation receiptを一緒に更新する。手動slotは同DBの論理snapshotであり、WAL中のDBファイルをcopyしない。置換/手動ロードで参照しなくなった作業世代の台帳を除くが、手動slotに保存した状態とBLOCKED診断は保持する。これは将来の年度別歴史を削除する製品規約ではない。

保存本文のhash不一致、未知版、再生不一致、台帳/集計の矛盾、未知schemaは停止する。fallbackToDestructiveMigrationは使わない。アプリ側では旧backup形式とAndroid12以降のcloud/device-transfer形式の両方でDBを除外し、allowBackup=falseも保持。OSや外部ツールのDB差替えを完全防止したとは称さない。

## 固定依存と実行範囲

Room runtime/ktx/compiler/testing **2.8.5**、KSP **2.2.21-2.0.5**を固定。Kotlin2.2.21/AGP8.11.1/Gradle8.13/min26は維持。[Room一次資料](https://developer.android.com/jetpack/androidx/releases/room)・[KSP正式版](https://github.com/google/ksp/releases/tag/2.2.21-2.0.5)。schemaはKSPが`build/reports/room-schemas`へ出力し、`app/schemas`の追跡版とのbyte一致をJVMで検査する。

ビルド・Roomの実DB試験はCIのみ。SDK/JDK/GradleをユーザーPCへ導入せず、可視画面/物理入力/既存環境は操作しない。DB close/open試験と実際のprocess kill試験は区別する。強制終了の実操作はAndroid接続の第3PRで検証し、突然の電源断や媒体故障の永続性は今回保証しない。journal/synchronous、最大720試合frameの容量と再読時間はdevice試験で実測し、未実測値を成功とは書かない。

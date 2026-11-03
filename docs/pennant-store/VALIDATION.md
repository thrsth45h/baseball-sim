# 保存・復旧層の検証記録

2026-11-02日本時間。[実装PR #43](https://github.com/thrsth45h/baseball-sim/pull/43)、[承認設計](../PENNANT_BASIC_DESIGN.md)。**独立レビュー修正後の検証sourceは`6e9ee0be613808939a3a46dacba953c9597930cc`**。[Android CI run 41462148728](https://github.com/thrsth45h/baseball-sim/actions/runs/41462148728)から結果XML・SQLite証拠・schemaを取得し、ZIP digest/CRCと内容を検査した。core main `e0a61b54e1fb852612c3d946230fa9ecb0adf914`は通常mergeで取込み済み。本書を追加する後続report headを実ビルドsourceと混同しない。

## 実行結果

| 検査 | 件数・結果 |
|---|---|
| 新app JVM | Coordinator6、schema/backup2＝8 method、全failure/error/skip0 |
| app JVM全体 | 既存12＋新8＝20 method、全failure/error/skip0 |
| core JVM回帰 | 22 method（既存core7＋season15）、全failure/error/skip0 |
| 新Room device | Store3＋Failure9＋Acknowledgement3＝15 method、全failure/error/skip0 |
| device全体 | 既存画面6＋新Room15＝21 method、52.160秒、全failure/error/skip0 |
| lint / APK | 0 error / 8 warnings、debug APK assemble成功 |
| 対象headのCI | 旧Python/Web・core専用を含む9workflow / 17jobs、全step成功 |

実行は使い捨てCIのAPI35/x86_64/google_apisエミュレーター。JUnit XMLのsuite名はMatchActivityTestでも、各testcaseのclassnameを数え、旧UI6と新Room15を分離した。障害注入の分岐数、720 fixture結果数、既存研究のテスト数をmethod数へ足さない。

lint8 warningsはGradleDependency5・NewerVersionAvailable2・MissingApplicationIcon1。baselineや抑制で隠していない。既存のmin26/コンパイラ/AGP等を維持し、Room/KSPの導入とschema生成をCIで実際に確認した。

## 確認した境界

- 実ファイルDBのclose/open後に、受理球・全snapshot・RNG/source消費を同じseedから照合できる。単なるsnapshot渡しでRNGを初期化したまま再開しない。
- 開始markerを保存せずに試合結果だけ登録する候補と、再生が一致しないRNG状態はDB変更前に拒否。
- transaction前・台帳更新後・head更新後の失敗で旧境界を保持。commit後の応答喪失は再読して二重加算せず、古い世代/revisionの更新も拒否。
- 別store/coordinatorのwriterを実DBで並行実行し、Aのcommit後の応答をlatchで止め、Bのloadまたは新world作成後にAを解放。AはBのheadを正常受領せずStoreConflictで停止し、Bのデータを保持する。duplicate/no-opの応答でも同じ境界を検査。
- operationId再利用のStoreConflictをcoordinatorが成功化しない。commit前失敗で古いheadの同じoperationIdだけが残っていても、期待token/stateと一致しなければ成功化しない。
- Coordinatorは計算済みSessionを保持せず、失敗後は最後の保存から再構築。キャンセル後も確定済みの球を巻き戻さない。重なるコマンドをqueueせず拒否する。
- 手動slotのsave/loadは試合外だけ。slot/head/結果台帳を一緒にrollbackし、ロードは新世代へ切替えて旧処理を拒否。
- 本文hash不一致、self-consistentな未知本文、台帳の不一致、未知DB版、headだけの欠損でデータを保持して停止。新規初期化や破壊的migrationに進まない。
- 合成の0球受理BLOCKED診断を永続保持し、再起動後も停止。確認付き新規シーズン開始で診断と既存manual slotを保持する。この例をToyが自然にその位置で例外を投げる証明とは呼ばない。
- 固定Toyで実際に発生したサヨナラの完了checkpointをDBに保存してclose/openし、追加投球なしで一度だけ結果確定。再読でも増分を再加算しない。
- schema1の6表が生成版/追跡版でbyte一致。旧backup形式とcloud/device-transfer形式のDB除外、allowBackup=falseを検査。

## 最大frameの実測

`pennant-store-evidence.json`から取得した値。

| 項目 | 値 |
|---|---:|
| 完了結果 | 720試合 |
| 保存frame | 1,585,652 bytes |
| close後DB | 4,689,920 bytes |
| journal_mode | wal |
| synchronous | 1（NORMAL） |
| fixture書込み | 256.022758 ms |
| close/open後の再読・検証 | 371.162032 ms |

これは正規の完了frameと720台帳行をDB fixtureとして挿入して、最大行サイズ・CursorWindowを含む再読を確認する試験。**720試合を1試合ずつtransactionで進めた処理時間や、実プロセス強制終了の試験ではない**。journal/synchronousの意味は[SQLite一次資料](https://www.sqlite.org/pragma.html#pragma_synchronous)を参照。通常のプロセス中断と突然の電源断/媒体故障を同一の保証として扱わない。

## 証拠と未検証事項

- [unit/lint/schema artifact 13150936025](https://github.com/thrsth45h/baseball-sim/actions/runs/41462148728/artifacts/13150936025)、ZIP SHA256 `4837c1cc339f6c91b057ff21b1a3525718ba199a92291106968cd30fa0915742`。
- [device XML/証拠JSON artifact 13149204263](https://github.com/thrsth45h/baseball-sim/actions/runs/41462148728/artifacts/13149204263)、ZIP SHA256 `c74e4eb88b56fedd17f43e67cce07ded8aa66ea35fe0302283d0d633c7c1ad37`。
- schema SHA256 `3af7cc7ed8cc59fd2d65666aa5464afb80598b5979428c3f88ae3e4d5f7004a8`、7,859bytes。[機械可読の記録](CI_EVIDENCE.json)。

本PRは保存層までで、ペナント画面や実OS process killのhost操作は未接続。DB close/openや障害注入を、force-stop成功と呼ばない。後続第3PRで画面からの全skip/混在/実際の再起動を検証する。実機・API26実行・突然の電源断・製品セーブ互換・長期年次保存は未検証。SDK/JDK/Gradleをローカル導入せず、ユーザーPCの画面/入力/既存環境も操作していない。

## 旧sourceとの区別

修正前`f8cdb474853a879c897045571a111b57630a1561`もrun41317384377でCI成功したが、その後の独立レビューで保存後の応答の世代混入と、operationId一致だけで競合を成功扱いにする2例が見つかった。6e9ee0bで成功応答をexact world/generation/revision/operationIdへ結び付け、coordinatorの期待state/token検査とStoreConflict非成功化を追加した。本書の件数・容量・時間は修正後6eの実測であり、f8の成功だけでこの修正を合格扱いにしていない。前後の時間差を性能改善の根拠にはしない。

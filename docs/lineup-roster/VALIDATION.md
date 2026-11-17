# 打順・出場選手の検証記録

2026-11-17 JST。承認設計main `bfb26e557ba56987fbdb468a10d62e5affc59caf`、[実装PR #51](https://github.com/thrsth45h/baseball-sim/pull/51)。**実装・CI・責任者の独立コード/成果物照合完了、最終統合待ち。** source976の全11workflow成功、21jobは20成功と旧ABBA比較の意図した1skip。[CI一覧・実件数・artifact](CI_EVIDENCE.json)を保持する。

| 検査 | JUnit method数 | 失敗/エラー/skip |
|---|---:|---|
| app JVM | 39 | 0/0/0 |
| 純Kotlin core/season | 28 | 0/0/0 |
| API35通常device（旧46を保持） | 53 | 0/0/0 |
| API35別jobの新v2全シーズン | 1 | 0/0/0 |
| host実プロセス復旧（v1/v2） | 2 | 0/0/0 |
| host旧局所profile | 1 | 0/0/0 |

prepare強制終了を成功methodに数えない。旧ABBAは前提不成立のため比較未実行で、上のmethod合格や性能合格へ加算しない。

## 検査の区別

純core/Rules/ToyPitchSource/RNG・SQL schema1は維持する。season/codec/Storeはv1/v2と出場者入力のため限定拡張する。旧固定参照・108人v1・既存保存故障/競合/receipt/台帳検査を保持し、新v2の144人と編成適用を追加する。

責任者は実装前に、過去の凍結coreの打順枠データから3seed×3編成操作列の人物別期待値を別に作った。workerは操作入力だけを読み、期待値を実装や成果物へコピーしない。CIの `pennant-audit/lineup/` は実際の各状態/summary/checkpointから9ケースの観測JSONを出力する。比較は責任者が別に行う。

独立v1 DB fixtureはworker開始後に、旧DDLと凍結frame片だけからPythonで構成したschema1であり、D061コードをimportしていない。実旧APKの出力や更新試験ではなく、9つの期待値を実装前に固定した時点とも区別する。gzip/DB/frameのSHAを照合し、現Storeの読取・次1球・再保存と旧新slotの相互loadを検査する。旧generated writerだけを互換性の根拠にしない。

元fixtureのgzip SHAは `8596765ca1ef34c0e33b619529acf3ce52541801888691796b0c0f13a0da272f`、展開したDBは `4247d3c43dbce76e505c7676eb419df03c6602adf9ce020340e74fef6dd557c4`、読取後のframeは `d208c4f99f28b5676a9a84b7dda113e186f8a84ce36e95b5c692366d6d87fc39`。Android test assetはgzipの同じ43,832byteを `one-game-v1.fixture.bin` の別名で梱包する。元MANIFESTを変えず、別のPACKAGING記録とGit binary指定を追加し、組立済みtest APK内のentryとgzip/DBのhashをCIで確認する。

## 受入条件と検査箇所

| 承認設計の条件 | 主な検査 |
|---|---|
| 144人・9出場/3控え・編集/取消/回転 | LineupStateTest、PennantLineupActivityTest、PennantLineupScreenTest。操作球団を変えた後の旧球団設定維持もcoreで検査 |
| 記録を人物へ帰属 | 全9ケースの実観測JSON、実UI/Roomの720試合と全144人の表示Text。独立期待値は責任者が別照合 |
| 未適用で保存不変・適用の単独性 | 実Activityの静止境界で全6tableの型付き全値/全byteを比較。fake Actionsの追加呼出し0、実Store CAS競合、VM連打/旧token/失敗時未公開を検査 |
| 開始入力の固定・不正保存の拒否 | coreのrestore/codec、実Storeの開始/進行/完了候補拒否、再hash済み不整合frameのcold read。完了履歴は後日のplanへ合わせ直さない |
| 進行単位の一致 | 同じseedと同じ試合境界の編成操作列で逐球/打席/試合/skip、checkpoint/RNG/source cursor/記録を照合 |
| 仮確率を据置き | 人物を入れ替えてもcoreの打順枠ごとの球/得点/順位が同じ。旧v1全frameは以前の固定byteと照合 |
| 旧新保存/復旧 | 独立構成v1 DB、両形式のslot相互load、新generation、同installの両形式host force-stop/次1球 |
| 旧新それぞれ実720試合 | 通常deviceはv1の既存assert保持。別device-lineup jobは新UIからv2を作り、実適用後720試合と144人を観測 |
| CI/APK/画面 | 純Kotlin・VM・保存/故障/競合・lint・APK、API35/320dp/文字1.5倍/回転/STOP/エラーの実操作とPNG |

## 検証中に直した問題

責任者の独立点検により、activeの出場者と保存planの一致をrestoreにも要求した。さらにAPPLYが通常readによる未受付試合の完了処理を起こさないよう、Coordinatorのstrict readと失敗時VMの暗黙reload抑止を追加し、実StoreとVMで回帰検査を置いた。

source `ce0963952904a1236711f98473a7c13cbca07054` の通常device全53件は2件失敗した。一つはbusy公開とfake execute開始の間で呼出し基準値を採っていたテストの競合で、最初のexecuteへ入るまで待ってから同じ「追加呼出し0」を確認するよう修正した。もう一つは `.gz` のasset pathをAndroidで開けなかったもので、上記のbyte不変の梱包別名と組立APK検査を追加した。この失敗runを最終合格へ数えず、source976で再実行し全53件が合格した。独立v1 DBの実Android読取とcross-format slot検査も成功。両修正でアプリ本体コードは変更していない。

## 進行と保存

- APPLYは期待tokenと実CASの一致を要求する。編成以外を同時に変更する候補、開始/進行/完了時の出場者の不一致、activeと保存plansの不一致を拒否する。
- 新APPLYは正常の `readLocked()` を使わず、tokenを照合するstrict Store readを使う。失敗後も通常readによる暗黙の完了処理を実行せず、以前の公開値とエラーを保持する。明示reloadは従来どおり。
- v1全720試合は、明示的に作成した0試合v1 fixtureから実Activity/Roomで進行する。新規UIがv2になったことを理由に、旧全frameの検査を144人へ置き換えない。
- v2全720試合は別の隔離device jobで行う。最初の13試合も実保存で進め、操作球団の2/3/4試合目の前に実UIで打順/控えを変更する。その後の707試合をUIの一括進行で完了させる。後半bulkの時間を全720試合の経過時間と呼ばない。
- 旧新の各形式について、配布APK/test APKを同じinstall/DBのままprepare→host force-stop→新PIDの復旧→次1球を検査する。prepareは意図的にkillするsetupで、成功JUnit methodに数えない。
- 全144人の実UI Text値を最終状態から別JSONへ観測し、人物ID/球団/現在配置/5件数を責任者が期待値と照合する。観測のために追加の720試合は生成しない。

## 旧性能比較

旧ABBAの共有依存不変という前提はD061のseason/codec変更で成立しない。`benchmark_applicability.py` は旧baseline blobと承認済みgeneratorのbyte一致を検査し、変更した依存path/hashを記録する。変更が承認範囲の依存だけなら `not_applicable` / `comparison_executed=false` / `performance_gate_passed=null` とし、比較jobは実行しない。欠落・破損や予想外の依存変更はエラーであり、性能成功への付替えはしない。

旧定義と元rawの履歴は保持する。新性能ゲートや改善率は採用しない。旧writer fixtureは現在のv1互換helperと組み合わされた検査用で、旧APKや旧ABBAと同じ実行ではない。

## 配布sourceと実プロセス復旧

検証・配布の固定sourceは `976eeab33f142f058ae02693e022aae7ea2a3e52` / tree `c3c8737f4416d72672d2db3c7588cf6a22c19806`。後続の報告文書commitとは区別する。純core4ソースとSQL schema関係2ファイルのGit byteは承認設計mainと同じ。app/core本体ソースは独立レビュー済み `2032e90810edb918bd6c961f8651d25a65185dc8` とbyte一致し、その後の変更はテスト/梱包/CIである。

[復旧run](https://github.com/thrsth45h/baseball-sim/actions/runs/44084842365)の[証拠](https://github.com/thrsth45h/baseball-sim/actions/runs/44084842365/artifacts/14328383499)と[同じ配布APK](https://github.com/thrsth45h/baseball-sim/actions/runs/44084842365/artifacts/14329935368)を責任者が別回収し、archive SHA/CRC/path、APK source/tree/hashを確認した。同じinstall/DBでv1、v2の順に保存し、hostからforce-stopを実行、PID不在と新PIDでの復旧/次1球を確認した。両形式とも5球の公開checkpointを復元し、v2は先頭P10・P01控えの開始入力も保持した。

| 形式 | kill前→復旧PID | 保存checkpoint SHA256 | 次1球 SHA256 |
|---|---|---|---|
| v1 | 4117→4292 | `18c8ff07180fbc60ed5935c01ace7b94d60c7433dbc8fee5e6273e46e0e246f7` | `f57af0454af300bb99316126cb7a489da6a0240566691e4891159d1562e3262c` |
| v2 | 4377→4468 | `99953430382b474c169ca839ed7366f191c0fed1c13918a23432d56ad61395c7` | `1146397d9f05df1905824e17cf983ba48064d9f6795f04b2cc8116cc9ea60711` |

v1の保存/次1球は以前の固定値と同じ。各recoverのJUnitはOK1、旧局所profileもOK1。prepareの強制終了を成功テストへ加算しない。APK hash/期限/署名の制約は[取得手順](README.md)を正本とする。通常deviceの別runner APKと配布APKが同じbyteとは扱わない。

## 新v2の実UI/Room720試合

source976の[別device job成果物](https://github.com/thrsth45h/baseball-sim/actions/runs/44084843151/artifacts/14329399242)は実instrumentation OK1。新規UIからseed42のv2を作り、最初の13試合を実保存し、その間に操作球団の2/3/4試合目の前で実際に打順/控えを変更した。残る707試合のbulk時間は **833,509ms（13分53.509秒）**。これは全720試合の経過時間ではなく、別runnerの旧計測との因果的な改善比較にも使わない。

責任者が実720 summaryのgame ID/seed/出場者/5件数/得点、全144人の最終集計を実装前に固定したseed42/order_bench期待値と照合した。最終frameは **1,776,565byte**、SHA256 `52e9ea9383a1ce841aa607ae54d65ec20db6c6e0ad806e074a500014fcb6beb0`。純coreの同条件最終frameと全byte一致し、完了後reloadでもstate全値が一致する。形式が異なる旧v1 frameとは比較byteを混同しない。

同じ完了状態を使い、実UIの全144人のTextノードからID/球団/現在の「n番」「控え」/5件数を観測した。期待stateの値をJSONへコピーせず、追加の720試合も生成していない。責任者が全144人の表示値を別の期待値と照合し一致した。

旧v1は[通常device成果物](https://github.com/thrsth45h/baseball-sim/actions/runs/44084843151/artifacts/14329744306)で別に実720試合を完走した。経過時間 **955,371ms（15分55.371秒）**、最終frame **1,585,652byte** / SHA256 `0bde5d0de901771b13e75adc64aab9d21a5854c8f7aed9e34aafb72a3881bbde`。責任者が以前に凍結した参照と全byte一致を確認した。旧46件の通常device methodもすべて保持している。実機速度の保証や旧runからの改善率は主張しない。

## 独立照合と画面

責任者はsource976の[core artifact](https://github.com/thrsth45h/baseball-sim/actions/runs/44084843151/artifacts/14328787537)を別取得し、archive digest/CRC/path/sourceを確認した後、workerへ見せていない事前固定期待値と9ケースを照合した。6,480試合の出場者/5件数/得点/core状態/RNG/source cursorと、1,296人分の最終集計が一致した。名前/JSON表現の対応以外に観測値を補正していない。[unit/lint artifact](https://github.com/thrsth45h/baseball-sim/actions/runs/44084843151/artifacts/14328743096)の全67methodは以前の集合を保持し失敗/エラー/skip 0、lint error 0 / warning 8。警告は既存の依存更新通知7件とapplication icon1件で、今回依存更新はしない。

画面はUIソースがbyte一致するCEsourceのPNGを責任者が実際に開いて確認した。対象7枚は編成320dp/文字1.5倍、同busy/STOP、適用後の編成、ホーム320dp/文字1.5倍、保存失敗保持、BLOCKED試合、完了シーズン。必須操作の到達/文字の重なりに残る必須指摘はない。「控えと交換」は狭幅で末尾文字が折り返すが、全字が表示される。完了PNGはスクロール途中なので、見切れた上部数値の視覚確認とは数えない。全端末/全PNGを検査したという主張ではない。

## 未検証

API26は宣言値で実行未検証。実機、電源断、実旧APK更新、製品の長期保存互換、能力の妥当性、投手/途中交代/一二軍/年次/経営は未検証または範囲外。Android/Gradle/SDK/JDKをローカル実行せず、ユーザーPCの画面・入力・既存Android環境も操作していない。最終報告commitとAPK source、未マージと統合済みを区別し、最終head/マージは責任者が確認する。次の未承認機能へ自動着手しない。

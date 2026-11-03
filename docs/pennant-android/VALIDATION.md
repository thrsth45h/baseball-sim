# Phase 5a Android接続の検証記録

2026-11-04 日本時間。[PR #44](https://github.com/thrsth45h/baseball-sim/pull/44)のworker検証完了。責任者が独立レビュー・最終統合を管理する。実装/検査/CI固定sourceは `7ec9d0e478447899a081c684d33b9c4635511b79`、tree `0e144a3376e0683b1b7a04deed1c74781935a209`。この後の完了報告は文書だけの変更として区別し、APKのsourceを報告commitへ付け替えない。[機械可読のCI証拠](CI_EVIDENCE.json)にrun/job/artifactを固定した。

## 結果

固定sourceの全10workflow/18jobsは成功し、全stepはsuccessまたは意図したskipped。[通常Android CI](https://github.com/thrsth45h/baseball-sim/actions/runs/41675777560)と[専用process CI](https://github.com/thrsth45h/baseball-sim/actions/runs/41675777477)を確認した。

| 検査 | method数 | failure/error/skip |
|---|---:|---:|
| app JVM（旧12・保存11・新VM8） | 31 | 0/0/0 |
| 純Kotlin core/season | 22 | 0/0/0 |
| API35通常device（旧6・保存17・新操作/表示8） | 31 | 0/0/0 |
| hostの復旧 | 1 | 0/0/0 |
| hostの局所profile | 1 | 0/0/0 |

prepareはhostが意図的にkillする生存setupであり、成功methodへ数えない。profileはfixture起点の局所計測であり、実720試合の完走件数へ加算しない。実XMLを回収確認。lintはerror 0 / warning 8（固定依存の更新案内等）で、依存更新をこの実装へ広げていない。

新VMは連打の同期拒否、読込失敗時の上書き禁止、書込失敗後の再読、古いcallback/世代の拒否、停止予約、試合中手動save/load禁止、LOAD/reloadで保存seedを採用した際の古い入力エラー解消を検査。実Activityは観戦3操作、回転で同じVM/状態、日程/順位/12球団108人の記録、実ボタンによる停止、手動slot/新世代、全skipを検査した。狭幅320dpでは表の範囲と「勝率」「本塁打」への実横スクロールを確認し、BLOCKEDの保存GameId/理由と読込失敗表示も確認した。

## 実Activity/Roomの720試合

seed42を新規作成してシーズン終了まで進め、全720試合・全12球団各120試合・day141・108人・activeなしを検査した。完了後の再読でもSeasonState全値が一致した。[device成果物](https://github.com/thrsth45h/baseball-sim/actions/runs/41675777560/artifacts/13251258753)の `pennant-real-season-42.bin` は最終SeasonState全値codec、JSONは全順位/打者件数を持つ。全投球ログは保存しない。

| 計測 | 値 |
|---|---:|
| 実UI操作開始から完了/検算出力まで | 1,110,228ms（18分30.228秒） |
| 最終frame | 1,585,652byte |
| DB / WAL | 5,046,272 / 3,065,312byte |
| 10秒間隔のheap標本最大 | 47,618,192byte |

frame SHA256は `0bde5d0de901771b13e75adc64aab9d21a5854c8f7aed9e34aafb72a3881bbde`。責任者が11/2に旧公開core613＋別JDK21で事前固定した期待frameと全byte一致を独立確認した。720要約/12順位/108記録/plan/seed/day141を含み、worker結果へ後合わせした基準ではない。heapは標本最大で、瞬間最大メモリやRSSではない。DB/WALはその時点の値で、peakや閉じた後の容量ではなく、再起動後のcheckpoint等で変わり得る。このdebug/厳密再検証の保存処理は遅く、「高速」を実証したとは扱わない。API35の一台のエミュレータ値を実機一般性能へ外挿しない。3master計2,160試合の全skip/混在一致・最大メモリ等は[純Kotlinの別検証](../pennant-core/VALIDATION.md)で、実720transactionと区別する。

## 実プロセス強制終了

配布APKとtest APKを一度だけ導入し、prepare/recoverの間は再導入・データ消去なし。公開済みcheckpointのprepare PID4194をhostのpidofで生存確認し、am force-stop後に不在を確認。新PID4298で同world/generation/revision3・GameId `S1-D002-L1-R01-M1-G1`・受理5球/advance5・checkpoint hashを復旧し、replay予測どおり次の1球だけを進めた。

保存hash `18c8ff07180fbc60ed5935c01ace7b94d60c7433dbc8fee5e6273e46e0e246f7` → 次球hash `f57af0454af300bb99316126cb7a489da6a0240566691e4891159d1562e3262c`。[host成果物](https://github.com/thrsth45h/baseball-sim/actions/runs/41675777477/artifacts/13251900282)に旧PID生存/不在・expected/recovery JSON・正常JUnitログ・PNGを保存。JUnit後に回復プロセスが自然終了すること自体は失敗とせず、テスト内の新PIDと新しい証拠で確認した。DB close/open、Activity.recreate、保存transaction障害注入とは別の証拠である。保存前/中の全kill窓、電源断、媒体故障を保証する試験ではない。

## 計算量修正と旧失敗

保存hashのFormatterを同じ小文字hex変換へ置換し、結果prefixを除いてから新規rowだけを変換した。保存byte/schema/codec、厳密検査の回数、CAS/replay/世代/操作ID拒否は維持し、キャッシュは導入しない。全byte/既知SHA/固定bufferと実DBの0/1row追加・既存prefix全byte保持/改変拒否を検査した。

baseline `cfe3e96` と固定sourceの局所profileは、正規メモリ内prefix0/100/300を別DBへfixture導入し、cold read1/warm3・marker/core/result各2回を分離測定した。prefix300のmarkerは旧約653ms/新約645–661ms、resultは旧約642–648ms/新約658–672ms。別runner単発で高速化は確認できず、改善率は主張しない。成果物のprofile-output/screenshotsの同じJSONコピーを二重試行として数えない。

旧f1b8/cfeの実720検査は20分上限で失敗。cfeは1,190.721秒で633試合まで継続前進し、heapとstackはcodec/hash作業の継続を示した。timeout後に停止が終わる前の後続3検査はreadbusy由来の表示エラーとなった。これを独立した保存破損や合格と扱わない。固定sourceは既存20分/test・30分/jobの範囲で全31検査を完了し、上限延長や検査省略は追加していない。

旧261のdeviceはSDK ZIP取得、旧c212/f1b8のhostはuserdata最低要求約7.2GiBに対する空き不足でテスト未到達。正式inputの2048M設定が記入されても最低値は残ったため、hostを通常device同様のboot→build順へ変更して解消した。要求2048Mと実df 6,082,144KiBを区別する。ユーザーPCへSDK等を導入しない。

## APK・表示確認・限界

[検証済み配布ZIP](https://github.com/thrsth45h/baseball-sim/actions/runs/41675777477/artifacts/13251715616)の `app-debug.apk` は10,876,017byte、versionCode2 / 0.2-pennant-tech。実SHA256は `178f1f4a32d5034f317c9573ff6147efbd470099a368d34d484639d03b18ca4f`。取得期限は **2026-11-18 01:40:13 JST**（14日保持）。source-info/pennant-source-info/offline-manifest/process検証を同梱し、回収APKの実hash/source/treeを照合した。配布APK自体をhostで操作し、通常31deviceは同sourceの別runnerビルドである。

実行環境はAPI35/google_apis/x86_64/pixel_2、emulator37.2.13.0 build16740729、Room2.8.5/KSP2.2.21-2.0.5。schema SHAは `3af7cc7ed8cc59fd2d65666aa5464afb80598b5979428c3f88ae3e4d5f7004a8`。INTERNET権限なしのmerged/packaged manifestを検査した。新規/進行中/順位/記録/完了/狭幅2枚/BLOCKED/process前後の実PNGを回収し、workerも完了・狭幅・復旧画面を目視した。責任者も進行中/完了/process前後/狭幅右端2表/BLOCKED/12番球団記録を独立目視し、必須問題なしと確認した。全frameの独立照合と合わせ、worker自己点検と区別する。

物理端末・API26実行・電源断/媒体故障・長期保存互換/次年度移行・能力/采配/経営/研究モデル採用は未検証または範囲外。debug署名が違えば旧版から保存維持更新できず、アンインストールすると端末内セーブが消える。[操作/取得手順](README.md)を参照。次工程は設計案の相談だけで、未承認の機能へ自動着手しない。

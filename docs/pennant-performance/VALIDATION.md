# 限定性能改善の検証記録

検証は2026-11-11、最終報告更新は2026-11-17（日本時間）。[実装PR #46](https://github.com/thrsth45h/baseball-sim/pull/46)は実装・CI・責任者の独立コード/成果物レビューを完了した。計測・テスト・配布APKの固定sourceは **`3c1180b642a93d99bf9d955c05b0182fe0efa733`**、tree `9022770806226fce23b59a76687b95a03081afb8`。後続の文書/dataだけの報告commitをAPK sourceへ付け替えない。[設計](../PENNANT_PERFORMANCE_DESIGN.md)・[CI証拠](CI_EVIDENCE.json)を参照。

## 結果と採否

固定sourceの全11workflow/19jobsと全stepのsuccessまたは意図したskippedを確認した。[通常Android CI](https://github.com/thrsth45h/baseball-sim/actions/runs/42849348224)、[配布APK/実プロセス復旧](https://github.com/thrsth45h/baseball-sim/actions/runs/42849348028)、[同条件ABBA](https://github.com/thrsth45h/baseball-sim/actions/runs/42849348455)。限定改善は正しさと事前固定の性能ゲートを満たしたため採用可能と判断し、責任者が最終headを確認して統合する。製品DB・能力・経営・次年度への採用判断は含まない。

| 検査 | method数 | failure/error/skip |
|---|---:|---:|
| app JVM | 31 | 0/0/0 |
| 純Kotlin core/season | 22 | 0/0/0 |
| API35通常device（既存31・新proof9） | 40 | 0/0/0 |
| host実プロセス復旧 | 1 | 0/0/0 |
| host旧局所profile（fixture起点） | 1 | 0/0/0 |
| host専用ABBA | 1 | 0/0/0 |

prepareは意図的にkillするsetupで、成功methodへ数えない。旧profile/ABBAのfixture投入を実720transaction完走へ加算しない。lintはerror 0 / warning 8。責任者も別回収した実XMLでapp31/core22・device40と新proof9の実行、失敗/スキップ0を照合した。

## 保存の正しさ

配布側RoomSeasonStoreを直接対象に、head全7列・receipt全5列・結果全7列、prefix/末尾/件数/順序、同token payload/hash同時改変を検査した。形式もhashも正しい別canonical stateへの差替え、receipt fingerprintだけの改変も拒否する。Coordinator経由では成功callback0回を検査し、曖昧な応答回復が不一致を成功へ変換しないようにした。

同世代の後続revisionとの競合、load/createの世代切替、通常/終端/BLOCKED、no-op/再送、transaction内duplicate、immutable入力と独立decoded返却値を確認した。既存のfault・取消・atomic・再生・破損・slot回帰を維持する。公開read/create/saveSlot/loadSlot/duplicate/no-op/例外後再読は従来経路のままで、新しい全byte照合は通常の新規commit成功応答だけに適用する。

責任者と別のreadonly reviewerが実コード/新9テストを点検し、必須問題なしと確認した。保存byte/schema/codec/core/RNG/toy確率/日程/集計/UI/保存頻度は不変。新鮮なDB照合や起動時検証の省略、操作をまたぐキャッシュ、複数試合のまとめ保存は追加していない。

## 同一emulatorの局所比較

baselineはmain `7ebb4a46a319ffde5e390a86b8f0a3a0352e4866`。同一API35 debug emulator・同一processで、prefix0/100/300/600/719のcold DB-read・warm read・marker・result、終端720のreadだけを比較した。毎標本が同じframe/token/receipt/ledger/operationを持つ独立DBから始まり、warm-up更新を測定へ持ち越さない。各操作はABBAを3組、各実装6標本。全308標本のうち測定264・warm-up44を保持した。

| 完了試合数 | 保存操作 | 旧中央値ms | 新中央値ms | 新/旧 |
|---:|---|---:|---:|---:|
| 300 | 開始marker | 611.584 | 468.424 | 0.76592 |
| 300 | 結果確定 | 604.715 | 474.267 | 0.78428 |
| 600 | 開始marker | 999.513 | 774.186 | 0.77456 |
| 600 | 結果確定 | 995.247 | 783.224 | 0.78696 |

主要保存操作の中央値は **21.3〜23.4%短縮**。300/600の4比較が事前の新/旧0.8以下、prefix0/100/719の残る6比較が1.1以下を満たし、全10ゲートが合格した。工学的な採否条件であり、実機・全シーズンの改善率や速度保証ではない。

[全標本CSV](BENCHMARK_SAMPLES.csv)・[全10比較と範囲](BENCHMARK_SUMMARY.json)・[共通条件/fixture/生成証拠](BENCHMARK_METADATA.json)をGitへ保存した。CSVは94,679byte、SHA256 `c9e809390d5a9b4e678cd38a63127e9dc1182b514674dbdd4415a05a8d1af285`。全20元sample項目を保持し、phases mapを5列へ展開、空欄はnullまたは不在phase、boolはtrue/falseで復元する。責任者は別回収rawに対し全308標本を型復元して全値一致を確認し、中央値・比率・ゲートも別Python集計で一致した。[CI原本](https://github.com/thrsth45h/baseball-sim/actions/runs/42849348455/artifacts/13781070734)が失効した後もCSVで監査できる。

計測は固定baseline blobのclass名だけを変更したtest-onlyコピーとcandidateの同じコピーへ、対称に5区分のタイマーを注入した。12 shared dependencies不変、タイマー除去後rename-only source完全一致をCI scriptと責任者の別Pythonで照合した。配布APKには計測用コピー/タイマー/variant flagを入れず、正しさ・実720・force-stopは本来のproductionクラスを検査した。

after_commitの区分中央値は300markerで209.968→67.406ms、300resultで193.545→64.347ms、600markerで335.931→115.782ms、600resultで329.270→117.073ms。削減対象と整合するが、区分ごとの中央値をtotal中央値へ単純加算しない。coldはRoom close/open後の最初のreadで、同一processのJIT/OS cacheはwarmになり得る。全標本の範囲・GC等のばらつきを隠さず、fixture準備とcore計算はcommit totalから除外した。

## 実Activity/Roomの720試合

seed42を新規作成し、実UIの「シーズン終了まで」で720試合を進めた。全12球団各120試合・day141・108人・activeなし、完了後再読でSeasonState全値一致を確認した。[device成果物](https://github.com/thrsth45h/baseball-sim/actions/runs/42849348224/artifacts/13779739998)に全frame・JSON・progress標本を保持する。

| 計測 | 値 |
|---|---:|
| UI操作開始から完了/検算出力まで | 661,123ms（11分1.123秒） |
| 最終frame | 1,585,652byte |
| DB / WAL | 5,050,368 / 3,065,312byte |
| heap標本最大（67標本） | 49,649,824byte |

最終frame SHA256は `0bde5d0de901771b13e75adc64aab9d21a5854c8f7aed9e34aafb72a3881bbde`。責任者が以前に別JDK/公開core613から固定した期待frameと、今回の全byteを独立照合した。固定は成功したAndroid全試合出力より前で、Android UI実装より前ではない。720要約・12順位・108記録・plan/seed/dayを含む一致である。

旧18分30.228秒は別runnerの歴史的参考で、この11分1.123秒との差から因果的改善率を計算しない。改善率は上記同一emulator ABBAだけから求めた。DB/WALは測定時点、heapは標本最大で、peak/RSSや閉じた後の容量ではない。全ledger読取りとframe書込みは残り、長期DBの計算量問題は未解決である。

## 配布APKの実force-stop復旧

同じ配布APK/test APK/DBのまま、prepare PID4240の生存、am force-stop後の不在、新PID4415での復旧を確認した。再導入・データ消去は挟まない。同world/generation/revision3・GameId `S1-D002-L1-R01-M1-G1`・公開5球/advance5・checkpointを復元し、次の1球だけが進んだ。[原本](https://github.com/thrsth45h/baseball-sim/actions/runs/42849348028/artifacts/13780760328)。

保存hash `18c8ff07180fbc60ed5935c01ace7b94d60c7433dbc8fee5e6273e46e0e246f7` → 次球hash `f57af0454af300bb99316126cb7a489da6a0240566691e4891159d1562e3262c` は既存期待値と同じ。責任者もPID・不在・hash・ログを別回収で照合した。JUnit後の自然終了を復旧失敗と扱わず、prepareの意図したkillを合格へ数えない。通常40deviceは同sourceの別runnerビルドで、配布APKそのものの操作証拠はこのhost検査である。

## APK・未検証事項

[検証済み配布ZIP](https://github.com/thrsth45h/baseball-sim/actions/runs/42849348028/artifacts/13779089307)の `app-debug.apk` は10,876,017byte、SHA256 `9edf3b5c1fadd2399217536072adc59ad1abf3d71c5cd611ab8a59524fb8997d`。versionCode2 / 0.2-pennant-tech、source3c1180b。取得期限は **2026-11-25 00:59:40 JST**。[取得/操作手順](README.md)。責任者がAPK hash/source/schema、INTERNET権限なし、DEXの新確認処理と計測用クラス不在も確認した。

検証環境はAPI35/google_apis/x86_64/pixel_2、Room2.8.5/KSP2.2.21-2.0.5。schema SHA256は `3af7cc7ed8cc59fd2d65666aa5464afb80598b5979428c3f88ae3e4d5f7004a8`。minAPI26は宣言値で、API26実行・物理端末・全kill窓・電源断/媒体故障・長期保存互換/次年度移行・能力/采配/経営/研究モデル採用は未検証または対象外。debug署名が異なれば保存を維持した更新はできず、アンインストールすると端末内セーブが消える。自動的な削除/再導入は行わない。

今回の報告更新ではruntime/tests/CIを変更せず、再生成したAPKを検証済みsourceと取り違えない。ユーザーPC画面/入力、ローカルGradle/SDK/IDE/JDKの導入・実行は行っていない。次工程は設計提案から扱い、未承認の大規模機能へ自動着手しない。

# Phase 5a 第1PRの検証記録

2026-11-01日本時間。[PR #42](https://github.com/thrsth45h/baseball-sim/pull/42)、承認設計main `8ef1415abccf6e214bd32082f5d07496e9035ad6`。**season runtime/test/fixture/CIの固定headは`61383b8c6a9d4bc93cfc31ff38fe993c17b4c0ab`**。後続report commitは本書等の文書だけで、実行したsource headと混同しない。

## テストと独立参照

[正式検証run 41316249615](https://github.com/thrsth45h/baseball-sim/actions/runs/41316249615)のartifactを実取得し、ZIP hash/CRC/source headと再実行のunit XMLを照合した。

| suite | test method | failure/error/skip | XML時間 |
|---|---:|---|---:|
| 新SeasonTest | 4 | 0/0/0 | 0.078秒 |
| 新SeasonBoundaryTest | 9 | 0/0/0 | 0.501秒 |
| 新SeasonFullRunTest | 2 | 0/0/0 | 29.888秒 |
| 旧CoreTest | 7 | 0/0/0 | 2.771秒 |

新規は15 method。49ケース等の旧core参照、720試合/2160割当、全モードの反復、assertion数をmethod数へ加算しない。研究/Pythonの件数もこの表へ足さない。

実装前に[受領コミットbf9a9b9](https://github.com/thrsth45h/baseball-sim/commit/bf9a9b9f5a93f775f02584935fb0952bc8427377)で独立参照を固定した。封印16項目とFREEZE_MANIFEST自身をraw bytesで保持し、testでも検算する。日程720全行・calendar140日・固定108選手・master0/42/2026の2160seed、手計算台帳12結果の順位2時点、疎な54打順入力から108人への全帰属が一致した。手入力の台帳を実GameSessionの結果と呼ばず、疎な帰属例を途中試合のskip許可とも解釈しない。

3masterそれぞれ720試合を全skip/1球・1打席を含む混在/注目球団変更と次自球団試合の操作で完走させ、全GameIdの要約・年度順位・108人集計が一致。actual `finishSeason(false)`でも全720試合と休養日/開催日を通り、同じ結果/集計・最終day141となった。master42は全1球と全1打席の進め方も比較した。全skip/混在/全1球/全1打席の各試合の全snapshotとsource/RNG消費も一致する。720試合分の全投球ログを保持せず、完了要約と検証用の終端checkpointを扱う。

同値再受理は台帳だけでなくcursor/activeを含む全stateが無変化。同score・同チーム合計でもBBの帰属が違う場合は衝突。サヨナラのscoreには旧coreが投影したterminalScoreDeltaを一度だけ含めた。理由/outcome矛盾、完了済み日を再び開くrestore、丸め前の整数比・同率/未定義順位、checked加算、公開コレクションの不変性も検査した。

最終独立レビューで見つかったサヨナラ要約の局所矛盾は613で訂正した。BBはdelta1/最終差1、safeはdelta1〜3/差1、HRはdelta1〜4/差がdelta以下、すべてdeltaが9回裏line以下であることを検査する。6不正構成のencode/accept拒否、正当な2run HRのreasonだけを改竄した2binary入力のdecode拒否を追加した。これは局所終端値の整合検査で、全投球履歴の正当性の証明ではない。凍結fixture/旧coreを変更していない。

checkpointの形式往復とseed再生は別の検査。productionの正規plan/seed・初期zero・上限10000・既知版・Toy以外の混入を拒否し、全snapshot/RNG/sourceを値で照合する。0球受理abortもadvance1回とBegin後の状態を保持し、ABORTEDはblocked診断で再開しない。非決定的source_errorの例は合成診断であり、通常Toy実行で実際に発生したとは記録しない。完了済みactiveが残る防御的な経路では追加投球せず結果を一度だけ反映する。停止予約済みの試合外では新しい試合を始めず、activeありなら現在試合の確定後に止まる。

## 親の独立確認

責任者は新season実装を見る前に、旧core＋別JDKで3master/2160試合の基準を固定した。その後、修正版613のartifactを別取得・ZIP SHA256/CRC/source head/audit SHAを照合し、全2160試合の終端snapshot/result/summary/RNG、年度36球団行/324選手行/順位が全一致したと報告した。worker自身の比較を独立レビューと呼んでいない。

責任者は既存JDK21.0.2でも修正版613のportable bundleの旧7＋新15＝22 methodを別実行し、全成功19.911秒。その出力JSONも別基準へ全一致した。これは613固定実装への別JVM検証であり、後続report headで同じ機能テストを新たに実行したという記録ではない。親のprivate期待値/検算ファイルをworkerは読んでいない。終端validator/回帰/actual finishSeason追加のread reviewも必須問題なしと報告を受領した。

## 同じbuilddirでの再実行

[正式run 41316249615](https://github.com/thrsth45h/baseball-sim/actions/runs/41316249615)、head613、job124876809690は直接JVM測定を含め全step成功。通常test/exportの後、同じbuilddirで`--rerun-tasks`を実行し、2160試合と全年度集計が厳密一致した。比較から除外したのは`skip_seconds`・`mixed_seconds`・`max_sampled_jvm_heap_bytes`だけで、試合値を正規化して書き換えていない。再実行XMLも新15/旧7、failure/error/skip0。

初版10eでは`:core:test`自体は成功したが、監査出力を読むexport taskの依存未宣言でGradle検査が失敗した。`dependsOn(tasks.test)`で修正した。その後、既存auditファイルで`CREATE_NEW`が失敗し得る境界に対し、Test自身の宣言済み生成dirだけを実行前に初期化した。canonical pathがproject root配下かつbuild直下のpennant-auditであることを確認し、fixture/既存資料/別出力は削除しない。旧版のCI結果とローカル取得証拠は別のignored childに保持した。

## 性能と容量の実測範囲

613のCI17 JVM再実行での観測値。skip/混在の列は各720試合の計算と最終codec往復を含む当該test内の時間で、起動・ビルド全体やAndroid性能ではない。

| master | 全skip | 混在 | 全受理球 | 終端サヨナラ例 | 完了season codec |
|---:|---:|---:|---:|---:|---:|
| 0 | 5.250秒 | 2.246秒 | 186230 | 37 | 1585586 bytes |
| 42 | 2.096秒 | 2.055秒 | 186304 | 24 | 1585652 bytes |
| 2026 | 1.413秒 | 1.243秒 | 186477 | 29 | 1585607 bytes |

warmup/GCを含む一回の環境測定で、速度の保証やmaster間の因果差とは扱わない。ゲーム後にsampleしたJVM heap最大値は313102872/316816792/346213560 bytesで、真のpeak RSSとは異なる。codecの容量もSQLite/手動slot/backupの保存容量ではない。

初期b683のGNU time観測ではGradle起動コマンドのelapsedは60.69/45.09秒、報告RSSは113920/113984 KiBだった。これはworkerのsampled heapより小さく、別に起動するJVM全体の最大常駐量とは呼べない。正式613ではportable bundleを`java -Xmx512m ... JUnitCore`で直接実行し、22 method成功31.04秒、GNU elapsed31.63秒・最大RSS442552 KiB（約432 MiB）を実測した。`pennant-junit-time.txt`と`pennant-junit.txt`を取得確認した。直接テストJVMにはJIT/GC/旧core/新seasonの全検査を含む。Gradle launcherの測定、直接JVMの測定、sampled heapを混同しない。Room保存/復旧時間・DB容量・端末性能は後続PRで測定する。

## 成果物と保護範囲

- [613正式portable/audit/測定 artifact](https://github.com/thrsth45h/baseball-sim/actions/runs/41316249615/artifacts/13087107566)：ZIP5300833 bytes、SHA256 `da703d2a5cb4ad2ad3a33d05cdca62cc87169d9d4c243189019422d79faaebf1`。first/repeat JSON・比較結果・XML・直接JVM/GNU time・source headとCRCを確認した。旧138/b683の取得ZIP・訂正前ドラフトはignored履歴へ保持し、現在の正式結果とは分ける。
- `audit-bundle/audit/pennant-core-audit.json`は22732153 bytes、SHA256 `0e12fe82d8a15c513ed07d66dee86694dd5eced1c4a37ff8132fb9243a5cbaf3`。大きな生成JSONはGitへ入れない。portable bundleはZIP内`audit-bundle/`を使用する。

正式artifactは14日保持、2026-11-15 14:30:47 UTC（23:30:47 JST）に失効予定。別headで再取得/再ビルドしたものを同じ実hashと扱わない。後続report commitのCIは[専用workflow](https://github.com/thrsth45h/baseball-sim/actions/workflows/pennant-core-tests.yml)の対象headと結び付けて確認し、固定613の本書の数値を自己SHAのため再生成し続けない。実物から集計した機械可読記録は[CI_EVIDENCE.json](CI_EVIDENCE.json)。

承認baseline追跡288パス中、既存変更はcore/build.gradle.ktsのtest出力/export案内だけ、287パスを保持。旧core4source・旧7tests/fixture・旧CI・Python/研究/公開結果・台帳/設計は変更していない。新workflowと新season/test/resource/説明を追加した。root README/HANDOFFは本PRで書き換えていない。

これは純Kotlinの第1PRの結果である。Room transaction・プロセス強制終了復旧・Androidペナント画面/新APKは未実装で、後続2PRまでが承認済みPhase5aの完了条件。Activity再生成やDB close/openをプロセス強制終了試験の代わりにはしない。製品日程/長期DB/年次移行/能力/経営/公式記録の採用も含めない。

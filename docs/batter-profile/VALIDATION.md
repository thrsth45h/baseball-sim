# 打者の試作用タイプの検証記録

2026-11-22 JST。設計承認時のmainは `d69f74409ffa5df658b5c34d02ae58b55694a7b9`。以下は最終コード `98988d1` の検証記録。統合状態は実装PRを参照。[承認設計PR #52](https://github.com/thrsth45h/baseball-sim/pull/52)・[実装PR #53](https://github.com/thrsth45h/baseball-sim/pull/53)・[CI証拠](CI_EVIDENCE.json)

## 実装と意味

新規v3は標準/ミート型/長打型の未較正表を新しい`PitchSource`で使う。各人物の固定タイプ、世界の表/source版、試合開始時の両軍18人の入力を保存する。開始後に現在の球団オーダーから入力を作り直さず、同じ入力で復旧・直近試合再生を行う。タイプの効果は逐球生成へ一度だけ適用する。

旧v1/v2の明示factoryと旧source/codec bytesを保持する。純coreのGameSession/Model/Rules/RandomSourceとSQL schema関係2ファイルは変更しない。実Storeの期待token/CAS、確定後公開、receipt、APPLYのstrict readと失敗時の暗黙進行禁止を維持する。通常commitでは世界の形式・タイプ・表/source版を変えられない。

## 受入条件と証拠

| 承認設計の条件 | 主な検査 |
|---|---|
| すべての抽選区間と消費 | 3タイプ×整数0〜99の境界、追加fair抽選、raw棄却、全18slotの固定タイプ参照 |
| 人物対応と診断分離 | 同タイプ交換のcore/RNG不変・記録帰属だけ変更、異タイプの固定入力差、全員standard診断と旧sourceの逐球一致、v3 productionへの診断engine混入拒否 |
| 理論値 | 実際の表/Rulesから得た全12カウントの終端確率と平均球数を観測。単一seedの成績順位を合格条件にしない |
| 独立参照 | master0/42/2026×default/order_bench/selection_persistsの9ケース、各720試合と全144人、ordinal1/7/13/19の全advance trace |
| 保存/旧互換 | 旧v1固定frame、旧v2 seed42三操作列の全frameを全byte比較。独立構成旧v1/v2 DBで読取・次球・再保存、全3形式のslot相互load、CAS競合とre-hash破損拒否 |
| UIと実進行 | 新規UIからv3作成、実編成変更、Roomへ保存しながら720試合、実Textノードから全144人のタイプ/現在配置/5件数を観測 |
| 操作安全性 | 狭幅320dp/文字1.5倍、未適用編集・回転・戻る、非standard先頭打者の固定表示、連打の二重進行防止、既存STOP/エラー回帰 |
| 配布APK | 同install/DBのv1/v2/v3を各prepare→host force-stop→新PID→同checkpoint/次1球で検証。prepareの意図killを成功JUnit methodに数えない |

## 独立性と来歴

責任者は2026-11-17に、新しいD062実装を使わず旧Python逐球ルールと承認表から9ケース6,480試合・36試合の全advance traceを固定した。workerとcore子は共有された操作入力だけを読み、期待出力を参照しない。CIでは実state/checkpoint/summary/cursorから観測JSONを出し、責任者が別に照合する。

全12カウントの独立有理数補助参照は責任者が2026-11-21、worker開始後に追加したもので、11/17の事前固定9ケースとは時点を分ける。どちらもMLBデータの妥当性検証ではない。

旧v2のDB fixtureも11/21にworker開始後、責任者が旧JVM source976のv2全720frameと旧SQL/schemaだけから独立構成した。全frameの機械的decode/encode全byte一致と集計再計算後、先頭1試合・day2・144人のschema1 DBに組み立てた。D062コード/出力をimportしていない。実旧APKの出力やAPK更新試験とは区別する。

このv2 fixtureのgzipは44,266byte / SHA256 `6f50ba22f38304a15dcd5925665355d2ced1915dd223fbcd5781a5869c4e377a`、DBは475,136byte / SHA256 `85e6d5316017ecccbd05ee2b8c6773e5337c78abdb42ed9ca66bcb21549481a9`、読取後frameは215,199byte / SHA256 `df5bf36fbe28cd27c8008ee8a02f682e42db04d3e80846105e2ebe4f3663ff61`。元MANIFESTを保持し、同じgzipをneutral `.fixture.bin` 名・Git binary指定で梱包する。既存v1とともに組立済みtest APKのentry・長さ・gzip/DB hashをCIで検査する。

## 検証中の修正

source `973c6978b2a3cbf154abb8b9defab2224cd85bd1`のcore artifactを責任者が別回収し、archive SHA/CRC/path/sourceを検査した。新9ケース6,480試合・1,296最終人行について、game ID/seed/出場者/タイプ/得点/5件数/全core状態/cursorが11/17の固定参照と一致した。36試合の9,754 advanceも全snapshot/cursorが一致し、値の補正はしていない。全300整数境界と36カウントの理論観測は別のExactFraction参照と一致し、最大浮動小数差は `4.984901380566953e-14`。旧v2の9ケース6,480試合も旧固定値と一致し、seed42の旧3frameは全byte一致した。これはcoreの独立照合であり、Android端末テストや工程全体の完了とは区別する。

初期source `bf3508c8e15384cc00f0541bc8ab7bc15af48663`の純coreは旧28method中2件失敗した。未知headerの拒否で例外型がIllegalStateExceptionへ変わったため、既存契約のIllegalArgumentExceptionへ戻した。既存assertを弱めていない。

責任者が新JVMテストのGameSnapshot参照比較を指摘し、SnapshotDtoによる深い全値比較へ修正した。v3に診断用のallstandard engineを注入できないよう、source種別・seed・両軍タイプが開始入力と一致するguardと回帰も追加した。

初回のAPI35端末試験では、試合を開いた直後の1回表でホーム側の先頭打者P11を検査していた。開始時の確定入力でホーム側のP11とミート型を検査したうえで、画面の「1打席」操作で1回裏の先頭へ進めるように修正した。回転の前後で打者名とタイプを確認し、8回の連続要求が1球だけ進めるという検査を保持した。[初回の通常端末試験](https://github.com/thrsth45h/baseball-sim/actions/runs/44799486352/job/136507597243)

同じ実行の別ジョブでは、シーズン進行の待機20分でタイムアウトした。終了前の進捗CSVが成果物に含まれず、到達試合数は不明。再検証では待機を32分、CIジョブ全体を50分とし、途中経過をCSVとlogcatに記録する。失敗時も観測資料を回収する。全720試合をRoomへ保存する条件と完走判定は共通。修正はテストとCIの4ファイルで、アプリ本体とコアは同じGit blobである。[初回のv3完走試験](https://github.com/thrsth45h/baseball-sim/actions/runs/44799486352/job/136507597587)

`ffbbccf`のv3再試験は残り707試合の進行中、696件の確定済み試合を表示した時点で停止した。開始から911,288msで停止した進捗行と、`working_state`の読取で`Row too big to fit into CursorWindow`が発生したlogcatを回収した。旧v1の通常端末試験と修正した打者表示・回転試験は同じsourceで成功した。[v3失敗時の証拠](https://github.com/thrsth45h/baseball-sim/actions/runs/44950582731/artifacts/14725304483)・[通常端末の証拠](https://github.com/thrsth45h/baseball-sim/actions/runs/44950582731/artifacts/14726461053)

`98988d1`では、新規v3の各checkpointとsummaryに既に保存済みの開始時打順を使い、両軍18人のタイプIDを打順に対応する18byteで保存する。標準は0、ミート型は1、長打型は2に固定し、表と生成器の版も保持する。読み出すIDはそのレコードに保存されている開始時打順のIDで、世界の現在の打順やタイプから再構成しない。不明コードと人物に合わないコードを拒否する。途中の未配布v3試作はこの保存形式へ移行する対象に含めない。旧v1/v2のbyte列、SQL schema1、DAO・CAS・保存ハッシュ検査は保持する。

回帰試験は最大64bit seedの719試合＋進行中checkpointおよび720試合の両方でcanonical frameの1,950,000byte上限と全byte往復を確認する。端末側は全720試合を実進行で確定した後、同じシーズンを再読込して手動保存し、別generationへロードしても結果と保存frameが一致することを確認する。[修正source](https://github.com/thrsth45h/baseball-sim/commit/98988d139cc021e2164580733f8d0c9dd5647096)

現在の固定仕様では、20桁seedの予定試合1件を最大287byte、全イニング記録と最長の結果・終了理由を含む完了試合1件を最大2,252byte、その他の固定数の編成・人物・集計などを最大15,884byteと見積もれる。720件ずつの合計上限は1,843,964byte。両snapshotを含む進行中checkpoint用にさらに8KiBを加えても1,852,156byteとなる。この見積りの対象は今の1シーズン・144人・固定バージョンの保存形式である。

## 途中のビルドと実プロセス復旧

検証sourceは `ffbbccfba4a10916d8b221a07103f1cd21c681c0`、treeは `de2d7db2c70217b8df905a83fff114ea431cf64b`。app 42件とcore 35件は失敗・エラー・スキップ0件で、従来の67件を保持した。Lintはエラー0件・警告8件。既存v1と独立構成v2の両fixtureについて、組立済みtest APK内の固定byteと展開後のDBを検査した。[JUnit・lint・fixture証拠](https://github.com/thrsth45h/baseball-sim/actions/runs/44950582731/artifacts/14725328915)

責任者は修正後のcore成果物を取得し、前回独立照合した973c697の成果物と、旧・新のシーズンJSON18件、逐球trace9件、frame6件、数表観測1件の全byte一致を確認した。[core成果物](https://github.com/thrsth45h/baseball-sim/actions/runs/44950582731/artifacts/14725631729)

`ffbbccf`のAPKを隔離API35でインストールし、同じインストール先とDBで、v1はPID 4163→4258、v2は4327→4400、v3は4484→4559。各形式でhostから強制終了して旧PIDが消えたことを確認し、新プロセスで同じチェックポイントと次の1球を照合した。v3にはミート型のP11が含まれる。保存時と次の1球のハッシュは、v1・v2が既存の検証値、v3が973c697の検証値と一致した。責任者はAPK/証拠ZIPのSHA-256、展開時のCRC、APK自体のSHA-256、source/tree、INTERNET権限がないことを確認した。[途中の実プロセス復旧の証拠](https://github.com/thrsth45h/baseball-sim/actions/runs/44950582646/artifacts/14725488293)

## 最終コードの検証

検証sourceは `98988d139cc021e2164580733f8d0c9dd5647096`、treeは `129ff1754e7e9e7e87bd74c44dbe32050e28afcc`。app 42件とcore 36件をJUnit XMLで確認し、失敗・エラー・スキップは0件。前回の77件は保持され、今回の上限seedの往復回帰が1件増えた。Lintはエラー0件・警告8件。組立済みtest APKのv1/v2固定fixtureと展開DBの検査記録は前回と一致した。[JUnit・lint・fixture証拠](https://github.com/thrsth45h/baseball-sim/actions/runs/44952560094/artifacts/14726267524)

責任者は独立参照に照合済みの前回成果物と、全31ファイルの同一性を確認した。対象は9ケースの6,480試合・1,296最終人行・36試合9,754 advanceの新v3観測、3タイプ300整数境界と36カウント、旧v2の9ケース6,480試合とseed42の旧3frame。新v3の残る3frameは、責任者が別処理で圧縮前のv3形式から各720試合分の12,960組の人物IDとタイプを検査して変換し、全byteが今回の出力と一致した。API35エミュレータの完走試験と同じseed42の`order_bench`は1,838,493byte、SHA-256 `d255bd19f2a9de71439b82334968d96723bccd79cfe222afc512f09c67e3fc86`。[core成果物](https://github.com/thrsth45h/baseball-sim/actions/runs/44952560094/artifacts/14725868746)

配布するAPKと同一のバイナリで、API35の同じインストール先とDBを使い、v1はPID 4229→4316、v2は4390→4463、v3は4538→4613。hostからの強制終了で旧PIDが消え、新プロセスで同じチェックポイントを復旧し、次の1球を照合した。v1/v2のハッシュは以前の固定値を保持し、v3は今回の変換後のハッシュで照合した。責任者はsource/tree、配布ZIPのSHA-256/CRC、APK SHA-256、生成されたmanifestにINTERNET権限がないことも確認した。[実プロセス復旧の証拠](https://github.com/thrsth45h/baseball-sim/actions/runs/44952560002/artifacts/14725998727)・[検証APKのZIP](https://github.com/thrsth45h/baseball-sim/actions/runs/44952560002/artifacts/14726113339)

API35の通常端末試験は60件すべて成功し、従来の53件を保持した。旧v1の実進行720試合は648,478ms。最終frameは1,585,652byteで、既存の固定参照と全byte一致した。旧v1/v2の独立構成fixtureの読取と再保存、回転後の打者とタイプ、連打による二重進行防止も確認した。[通常端末の証拠](https://github.com/thrsth45h/baseball-sim/actions/runs/44952560094/artifacts/14726772831)

別のAPI35試験でv3の720試合をすべて実操作経由でRoomへ確定した。seed42で編成を変更した先頭13試合も実トランザクションであり、838,240msは残る707試合の一括進行だけを計測した値である。得点、両軍の打順、タイプ、個人記録は720試合すべて独立参照に一致。全144人のID・球団・役割・タイプ・5件数も画面から取得した実際の文字列で照合した。最終frameは同条件のJVM観測と全byte一致し、再読込後の手動セーブ・ロードで別の保存世代へ復元しても同一だった。[v3完走と保存の証拠](https://github.com/thrsth45h/baseball-sim/actions/runs/44952560094/artifacts/14726288321)

責任者は完走画面を目視し、編成・選手詳細・打者表示の3枚は以前に目視した画像と最終sourceの画像が全byte一致することを確認した。横幅320dp・文字1.5倍の編成画面で「控えと交換」は折り返して表示される。完走画面ではスクロールに応じて上部が画面外に切れ、進行ボタンは無効だった。計11ワークフローが成功し、21ジョブは20成功・旧性能比較の適用外による意図したスキップ1件だった。[Android CI](https://github.com/thrsth45h/baseball-sim/actions/runs/44952560094)・[CI証拠](CI_EVIDENCE.json)

## 保存検査と性能比較の限界

`checkedState`は構造・canonical入力・台帳との整合と進行中checkpointの再生を検査する。完了履歴を毎回全試合シミュレーションし直す保証や、合法な別履歴と台帳を共同で改変した場合の暗号的な検知を提供するものではない。

旧ABBAは共有依存変更により適用外。旧generator/hash/rawを保持し、N/A・比較未実行・performance gate nullを出す。今回の動作検証や局所profileを新しい速度改善の実証に使わない。

## 未検証

正式能力係数、MLB妥当性、タイプ間の戦力均衡、最適打順、投球/知覚/接触/打球物理/守備の正式生成過程は未検証。API26実行・実機・電源断・実旧APK更新・製品長期保存も未検証である。Android/JDK/Gradle/SDKはローカル実行せず、ユーザーPCの画面・入力・既存環境を操作していない。runtime/test source、後続の報告だけのhead、main統合SHAを区別する。

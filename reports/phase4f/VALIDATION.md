# Phase 4f worker検証

[設計PR #34](https://github.com/thrsth45h/baseball-sim/pull/34)とphase4f-design-v0.1に基づく限定統合試作。設計main`409bbf6d40ee04a52b6e91546892a532d7c3d2d2`、最終固定core`a5fda901bc0a8cca9ae0eefc1d076fd8171c73a6`。2026-10-31日本時間、Python3.12.14/Windowsのworker自己検証であり、責任者の独立レビューを代行しない。

## 実際に動かしたこと

seed42のCLIは仮Away4/Home2、259球、完了打席Away44/Home38で終了。主要5成果物677,945bytes、CLI起動込み0.1903秒。0/1/2/2026も正常完了し、[6回分の実測](run-summary.json)に受理球/打席/終了理由/サイズ/所要時間を保存した。seed42の公開game-42-01とignored game-42-repeat-02は5成果物バイト一致。これらは仮設定内の合成出力で、MLB適合や能力係数を検証した値ではない。

新4fは**23 method /成功subTest13（別集計）**、1.006秒、failure/error/skip0。[旧4e](phase4e-regression.json)24/18112、[4d](phase4d-regression.json)26/320、[4c](phase4c-regression.json)28/3020、[4a](phase4a-regression.json)33/326、[4b](phase4b-regression.json)32/515を5別コマンド/5別記録で回帰し全failure/error/skip0。各suiteで実Windows junction4ケースを実施した。研究575や組合せをmethodへ足していない。Python3.11/3.12の専用CIは6suiteを別stepで実行し、対象PR headの結果を別途確認する。

## 検査した振舞い

- 固定2チーム各9打者。完了打席だけ打順を進め、一巡と半回持越しを確認。54球全打者アウトの固定列は9回引分・全line0・各チーム27完了打席になる。四球/三振と2ストライク後の普通ファウル継続は旧カウント処理へ委任する。
- 6つの固定終端例を手で指定した期待得点/理由へ照合。9表ホーム先行で裏を省略し追加入力0、9裏ビジター勝利/引分、サヨナラ四球、safe候補3得点から必要1点だけ、HR4点全員を確認。境界例の開始は明示した合成line履歴であり、生成した試合prefixと偽らない。通常CLIは1回0点から全履歴を生成する。
- サヨナラはその球の旧Pitchを受理した後、旧Resolve/ResolveSafeHit/Advance呼出し0で専用終端へ移る。非終端は旧stepを通す。終端旧stateが結果待ちのまま、最終scoreは投影＋専用増分一度だけになる。完了後追加one-pitchを拒否し、sourceを再呼出ししない。
- ログからproductionの集計関数を使わず、line score・打順・打者別件数・完了打席・投球数を別台帳で再集計し、通常試合と固定終端例のresultへ照合。公式AB/RBI/2B/3B/打率/投手成績は出さず、safe候補長打価値を公式記録として表示しない。
- 専用Random(seed)の整数drawを別Randomで再計算し、固定半開閾値とfair時だけ追加drawすることを確認。全100区間と24種類の占有/候補値の一定進塁を確認した。グローバルRNG関数を禁止しても生成でき、seedを逆順に複数実行しても各結果が一致した。
- recorded input replayはRandom生成を禁止しても一致。1球反復は自動進行と同じ結果/ログになり、40球時の明示snapshotをJSON往復して残りの記録入力を供給しても一致した。これは検算用で、製品セーブ/復帰互換ではない。外部snapshotのline履歴省略やscore不一致を拒否し、過去のイニングを0で補わない。
- 入力不足、source例外、任意の不正オブジェクト、fair不足/不明型/占有・順序不正は、その球のカウント/球数/完了打席を増やさずabortedにする。すでに出したBeginは保存する。不正オブジェクトのreprや私的例外文を公開JSONへ入れず、sanitized markerで同理由/部分stateを再生する。
- 上限は次球前に検査し、4球四球直後の上限でも強制アウト・引分・次Beginを作らない。半回が閉じた上限でも不要なAdvanceを行わない。上限ちょうどの球で正常終了する固定6例はcompletedが優先する。
- strict seed/max/入力値、bool/小数/文字列/範囲、list/tuple、JSONの余分不足/重複キー/非有限、未知event/fair、凍結値/flagsと独立JSONを確認。Python -Oでvalidationが消えない。ログ改ざんを記録再生が拒否する。
- 実namespace全体/子alias→旧領域/外部のjunction4ケースを新一時領域に作って拒否。旧namespace/外部/既存先/namespace自体/入力重複も拒否。権限不足の通常sandbox実行は成功とせず、必要な権限で4実ケースを完了した。過去の一時領域を削除しない。
- 新runのread/importを監視し、許可旧11sourceと新runtime/profile/fixtures以外の入力・研究/旧I/O/旧fixture/旧結果利用、global RNG、time/time_ns/monotonic/perf_counterを禁止して完走。旧11sourceのhash読取りは確認し、旧データ非利用と区別した。LF/CRLF/CRのコピーでidentityが一致する。

## 独立レビュー指摘への修正と保存

初版core05fe7bfでは、sourceがRecordedAbort('pitch_limit')を返す、またはInputError('pitch_limit')を投げると、上限未達0球でもpitch_limitを受け入れ、replayがそのmarkerをcontroller上限と見なして捨てるため不一致になった。責任者の独立レビューで見つかったP2として修正した。旧正式公開途中資料はignored `data/prototypes/phase4f/history-core-05fe7bf-01`へ保護し、旧run/テスト記録も保持した。

最終coreではresult用ABORT_REASONSにはpitch_limitを残し、source用SOURCE_ABORT_REASONSからは除外。RecordedAbort/InputErrorがcontroller専用値を拒否する。source内でそれぞれを作ろうとする2経路はsource_errorへ正規化され、0受理球/初期count/PA0を保ち同じ部分state/理由でreplayできる。mutable InputError.codeを後からpitch_limitへ変える経路もengine境界でsource_errorへ正規化した。正規controller上限・上限球での正常終了は既存検査で維持。23 methodのうち追加1 method/3 subTestがこの回帰で、すべての正式run/6suiteは最終coreで新規に実行し直した。

## 根拠の結合と保護の時点

公開helperは旧ルール関数をimportせず、旧静的fixture/研究モデルも読まない。固定coreと現在のsource identity、toy profile Git blob、2runの8 hash項目、連続したログの投影/delta、別集計のline/打順/打者件数、別Randomのdraw/閾値を検査する。完全な野球遷移の独立oracleとは称さない。helper出力の検算内容と時点は[verification.json](verification.json)に記録し、主要5成果物には時刻/絶対パスを入れない。

旧185trackedパス中、README案内以外184パスの保護はbaseline409bbf6→固定corea5fda90の歴史的Git差分で検査する。現在の全prototypes/依存/tests/CIは別に厳密照合し、後続旧文書の差分は参考欄へ分離する。現在も全旧文書が不変とは主張しない。旧4a〜4eのcode/型/契約/fixture/tests/CI/結果/flagsは変更しない。研究repoはworkerから開かず編集しない。研究Git/main/tree/作業状態の最終照合は責任者が担当する。公開hashとcommit後のGit blobを別に確認する。

## 未検証・未採用

toy-v1の35/15/15/15/20とfair65/22/7/1/5は共有仮重みで、MLB fit・選手能力や知覚/接触の推定ではない。ToyAdvancePolicyも一定進塁のprovider仮設定で、旧4eの一般契約を狭めない。投球真値/知覚/接触/打球物理・研究接続・能力1〜20/積極性・実データ較正・一般ルール/走塁/公式記録・Android/経営/UI/DB/製品セーブは未実装/未採用。D029/D030/D031/D032はOPEN。別言語/将来Python版の乱数・保存互換、永続再送の重複排除は保証しない。

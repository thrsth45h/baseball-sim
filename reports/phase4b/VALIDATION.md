# Phase 4b worker検証記録

正本は[承認PR #22](https://github.com/thrsth45h/baseball-sim/pull/22)、`phase4b-design-v0.1`、承認main `59ae0e2f726b88d5729d026a0e293916c33a73e6`。source/tests/fixtures/CIは`7f98536c8659d6fa6bd0bbe94ab6e48e8ee4fb3b`へ固定した。本書はworkerの自己検証であり、責任者の独立レビュー合格を代行しない。

## 実際の実行と件数

Python3.12.14、Windowsで新4b **32 test method成功**、failure/error/skip0、約0.485秒。成功subTest **515は別集計**。旧4aも別途33 method/326 subTest成功、failure/error/skip0、約0.404秒。32+515や新旧合算を新4bのmethod数にしない。旧研究575テストは実行せず、今回の検証件数へ数えない。新専用CIはPython3.11/3.12で新4bと旧4aを別コマンドで実行し、対象headの結果はPR上で確認する。

- 静的72表は設計§4のW8占有表とH人数表から記載した。生産関数から期待値を生成せず、最初の空き強制塁を予約する別式でtable自体とstepを照合。全72組で開始runs0/7/10^100の同じ増分を確認した。
- 全72組で人数保存、アウト/得点の単調性、W/Hでアウトなし・Xで得点なしを確認。人数保存だけに頼らず、Wの三塁だけ/二三塁/一三塁等の具体的反例も検査した。
- 16列/30イベント、空と両端を含む全46分割位置で、最終状態と番号なしtraceのprefix+tail連結が完全一致。逆順のケース実行とtuple入力でも一致した。
- 満塁W×1,000で得点+1,000、無死空塁W×1,000で(0,111,997)。便宜的な上限や強制アウトなし。巨大得点への厳密+1/+4も確認した。
- 三アウト×8占有×3イベントの24拒否。第三アウト時も残塁/既存得点を保持し、空replayは停止状態に許す。任意の外部開始局面を受け、4aの到達可能性制約を流用していない。
- bool/小数/文字列/非有限等の整数欄、basesのlist/0/1/余分要素、余分/不足JSONキー、重複JSONキー・NaN/Infinity、曖昧/未知/旧4a status入力を拒否。コンストラクタtupleとfrom_mappingのJSON listを区別した。
- state/result/traceとflagsの不変性、JSON返値の独立性、不正tailで入力不変・部分結果なし、厳密list/tuple以外拒否、Python -Oでvalidation維持を確認した。
- 固定16列のID/開始/正確な列/最終/行順をpin。mixed_halfのWWXHWXX→WWHXWXXは同じ最終状態になるが、loaderと実JSON置換のrunner検査で出力作成前に拒否した。一般の合法な列まで禁止するものではない。
- 出力namespace/既存先/旧4a/外部/入力重複を拒否。namespace全体/子aliasから旧4a/checkout外へ向かう**実Windows junction4ケース**を作成して拒否した。作成失敗をskipで成功扱いしていない。テスト領域は保持した。
- 新runを許可source/fixture/新出力以外の読取り、旧import、RNG、現在時刻/monotonic/perf_counter読取りの監視下で完走。無許可読取りは0。旧4aや研究を計算へimportせず、source/fixture同一性変化やtable不一致は出力作成前に拒否した。source identityはLF/CRLF/CRを実際に作ったコピーでも一致した。

## 正式2runと記録の時点

CLIで公開synthetic-01とignoredのsynthetic-repeat-02を新規作成。plan.json/traces.json/REPORT.md/SHA256SUMS.jsonの4成果物全てがバイト一致。主要成果物に時刻/絶対パスなし、Python版は記録する。同じ版の一致であり、異なるPython版間のmetadata一致は主張しない。

helperは固定sourceのGit fixtureと現在source identityの双方へ結びつけ、2runの6hash登録と60traceを静的72表から再照合する。テスト記録は実行結果をそのまま保存し、合成結果を得点率や能力係数へ換算しない。

旧ファイル保護は承認baseline `59ae0e2`→固定source `7f98536`のGit履歴を検査し、baseline65パスのうちREADME案内以外64パスがその実装時点で不変だったことを記録する。現在も全旧文書不変という意味ではない。固定source以後の旧ゲームファイル変更は参考情報欄へ分離し、現在の全prototypes/shared initializerと新旧CIの固定source照合は厳密に維持する。研究repoはworkerから開かず編集しない。研究mainの同一性は責任者が最終確認する。既存4aコード/fixture/テスト/CI/公開結果・旧worktree/ignored履歴を変更・削除しない。

公開JSON・hash索引と、Gitへcommit後の公開ファイル/Git blob一致を別途確認する。既存出力を上書きせず、訂正が必要な場合は旧公開記録をignored履歴へ保持し、新run番号で分離する。

## 限界

確定済み合成3プレーの限定計算であり、入力の前提を現実の観測から検証していない。四球・三振・捕球飛球一般をW/Xへ自動変換せず、旧4aやStatcast/3dと未接続。走者ID・打順・進塁判断・時系列の第三アウト得点判定・公式個人成績、完全な半回/試合生成、確率/能力較正・MLBの得点分布・Android/UI/セーブ・製品採用は未検証/未実施。D029/D031/D032はOPENのまま。

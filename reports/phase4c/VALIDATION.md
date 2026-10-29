# Phase 4c worker検証記録

正本は[承認PR #25](https://github.com/thrsth45h/baseball-sim/pull/25)・phase4c-design-v0.1、承認main `06256f8a4f0f8795709f56365f1ea212e2090092`。source/tests/fixtures/CI固定は`a1d2cbc4ae9f949babb2f350be9ed41193f477b5`。worker自己検証であり、責任者の独立レビュー合格を代行しない。

## 実行と検査単位

Python3.12.14/Windowsで新4c **28 test method成功、成功subTest3020は別集計**、failure/error/skip0、約0.443秒。旧4aは別に33 method/326 subTest、約0.432秒、旧4bは別に32 method/515 subTest、約0.445秒、全てfailure/error/skip0。各suiteで実Windows junction4ケース成功。研究575テストを実行・加算しない。新CIはPython3.11/3.12で3suiteを別コマンド実行し、対象headの結果をPR上で確認する。

- 制御12セルと互換12セルを設計の別記載matrixへ照合。24 open half（outs0/1/2×8占有、runs7）の全互換288組が120受理/168拒否。受理後は旧4b結果とcount=None、拒否時は状態不変を確認した。
- 12 active count（b0…3/s0…2、n=b+s）×8投球×24 halfの2,304組で旧4a結果と一致しhalf不変。24開始組ではcountだけゼロへ。これは有限の代表値であり任意のn/runsを網羅した主張ではない。half完了8占有×全12具体actionの96拒否と、half.outs3/countありの矛盾構成を検査した。
- 適用したPitch/Resolveについて旧対応stepを1回ずつ呼ぶspy検査を実施。Beginで両stepを呼ばず、不適合Resolveでも旧half stepを呼ばない。新接続層へ旧ルール式をコピーしていない。
- 終端前Resolve・終端後Pitch/Begin・解決後Resolve・Begin前Pitchを拒否。Pは未確定snapshotのまま停止し、明示X/Hだけで更新する。第三アウト待ちのhalf未反映と、Resolve後の残塁/得点保持・次打者の明示Gを確認した。
- 静的18列/82action、空/両端を含む全100分割位置で最終状態とtrace連結が完全一致。ケース順を逆にしても一致。Begin/Pitchではhalfのouts+runs+占有数が不変、成功Resolveだけで1増える。traceは正確なaction/before/after/runs_addedだけで番号なし。
- G,C,M,F×1,000後はcount=(0,2,1002,active)、half不変。巨大得点10^100への+1/+4と、ループ後の巨大nの厳密+1を確認。Pitchだけが既存pitches_seenを増やし、Beginはゼロ開始、Resolveはcount=Noneへ消費する。
- 厳密な旧型/新action、subclass/曖昧payload/余分不足キー/重複JSON/NaN/Infinity・旧型の数値/bases違反、段階違反を拒否。不変state/action/result/trace/flags、独立JSON返値、厳密list/tuple、空列全stage、無効tailで入力不変・部分結果なし、Python -Oでも残るvalidationを確認した。
- fixtureのID/開始/正確なactionとpayload/最終/stage/行順をpin。bunt_strikeoutのU×3をC×3へ変えて最終状態が同じでも、loaderと実JSON置換のrunnerは出力前に拒否した。制御/互換表drift、期待final不一致、source同一性変化も出力前に拒否した。
- 出力namespace/既存先/旧4a/b/外部/入力重複を拒否。namespace全体/子alias→旧領域/checkout外の実junction4ケースを検査。権限不足をskip成功にせず、テスト/既存実行の資料は保持した。
- 新runで許可した旧5ソースと全新source/fixture以外の入力読取り、研究/未許可旧import、RNG、time/time_ns/monotonic/perf_counter読取りを拒否する監視下で完走。旧5ソースの同一性hash読取りは実際に存在し、それを旧データ/研究モデル非利用と区別した。LF/CRLF/CRで作ったsourceコピーの同一性も一致した。

## 正式2run・helper・保護時点

CLIで公開synthetic-01とignored synthetic-repeat-02を新規作成した。plan/traces/REPORT/SHA索引の4成果物は全てバイト一致。主要4成果物に時刻/絶対パスなし、Python版は記録する。同一環境の一致であり異なるPython版間のmetadata一致は主張しない。

helperは新runと別の検証処理として、固定sourceの制御/互換表・18列に加え、旧4a96/4b72静的遷移fixtureをGit blobから読む。旧ルール関数/研究モデルはimportしない。これらの静的表で2runの164trace/6hash登録を再検算し、現在の新旧source/依存sourceと固定Git blobの同一性も検査する。新run自体は旧fixture/結果を入力せず、旧5ソースの型/step利用とhashだけを許可する。旧suiteによる従来fixtureの回帰も別処理・別集計である。

旧保護は承認baseline `06256f8`→固定source `a1d2cbc`の履歴差分。baseline93パス中README案内以外92パスがその実装時点で不変。現在の全prototypes/依存/新旧CIは別照合し、固定source後の旧文書変更は参考欄へ分離する。後続文書を保護例外へ追加せず、現在も全旧文書不変とは記録しない。旧4a/bコード・fixture・テスト・CI・公開結果・flags、既存worktree/ignored履歴は変更・削除しない。研究repoはworkerから開かず編集しない。同repoのmain同一性は責任者が最終確認する。

新旧テスト記録は実行結果をそのままJSON保存し、公開hashとcommit後のGit blob一致を別に検査する。訂正が必要なら旧公開/検証をignored履歴へ保持し、新source/実行番号に分離する。固定source後に新しいcore変更がなければ、レポート編集だけでruntimeの2runを再生成しない。

## 限界

halfは最後に確定反映したsnapshotであり、一般の野球時刻状態ではない。合成列の「盗塁/暴投等なし」という入力前提や正規触塁を観測から判定していない。古いsnapshot再投入・通信再送等の永続重複排除やexactly-onceは保証しない。未確定fairへ安打/アウトを補完しない。一般走塁/単打/併殺/第三アウト得点、公式個人成績、完全な半回/試合生成、Statcast/3d・確率/能力、Android/UI/DB/セーブと製品採用は未接続/未実施。D029/D031/D032はOPEN。

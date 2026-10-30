# Phase 4d worker検証記録

正本は[承認PR #28](https://github.com/thrsth45h/baseball-sim/pull/28)・phase4d-design-v0.1、承認main `b9f9b5830f9247691b4d05b0f81ad8fe84c12f13`。source/tests/fixtures/CI固定は`2fadced5b5da7b8387466641fd063180c5628e17`。本書はworker自己検証で、責任者の独立レビュー合格を代行しない。

## 実行と検査単位

Python3.12.14/Windowsで新4d **26 test method成功、成功subTest320は別集計**、failure/error/skip0、約0.458秒。旧4cは別に28 method/3020 subTest、約0.483秒、旧4aは33/326、約0.407秒、旧4bは32/515、約0.397秒、全failure/error/skip0。各suiteで実Windows junction4ケースを成功させた。研究575テストは実行/加算しない。新CIはPython3.11/3.12で4suiteを別コマンド実行し、対象headの結果はPR上で確認する。

- 制御16セルを別の対角受理matrixへ照合。旧3action型が旧4cと同一、旧kind parserへ1回委任、stepも旧3actionで1回/Advanceで0回をspyで確認。不適合旧actionは旧4cが拒否し、互換5許可/7拒否を両sideで保持する。
- 代表96Advance（inning1/2×side2×完了half占有8×runs0/7/11、closed11/13）を検査。表示score不変、正しいclosed加算先、top→bottom同回/bottom→top回+1、新current空/Begin必要、旧snapshot残塁/得点不変を確認。有限代表値であり全回数/得点の網羅ではない。
- 旧3actionでcurrentは旧4c結果と一致し、closed/回/側は保持。受理Resolveの得点差は攻撃側だけ、他方不変。Begin/Pitch/Advance差分は0。三アウト後の全12旧具体actionを両sideで拒否する。
- between/pitching/awaitingでのAdvance、第三アウトのcount終端だけでのAdvanceを拒否し、明示Resolve後に切替可能。連続Advance拒否と、同じ古い完了snapshotへの純粋関数再適用が同じ成功値になることを区別した。
- 固定16列/100action/全116分割位置で最終state/score/stageとtrace連結が一括処理に一致。逆順のケース実行でも一致。新traceは正確な5キーで連番なし、旧4c traceは変更しない。
- 別に積み上げたチーム別台帳と128半回の列が一致し、最終65回表、away128/home192。全Advanceの得点差は0で、二重加算/減算なし。巨大回・closed/half得点10^100でも厳密整数で表示score不変/得点加算を確認した。
- 9回表でホームリードでもAdvanceを受け、9回裏から10回表/10回裏から11回表へ移る。空塁へ切替え、自動終了・延長走者・勝者を作らない。実試合の終了規則への適合検証ではない。
- strict5保存field、bool/float/string/subclass/範囲違反、旧current型/矛盾state、導出score/stage余分キー、不明/重複JSON/非有限値/action/subclassを拒否。state/Advance/result/trace/flags不変、独立score/JSON返値、空replay全stage、有限list/tupleと不正tail入力不変/部分結果なし、Python -O validationを確認した。
- fixtureのID/明示開始の側/正確actionとpayload/最終/score/stage/行順をpin。top_switchのC×3→M×3が同じ最終状態でも、loaderと実JSON置換のrunnerは出力前に拒否した。制御表drift/期待score不一致/source同一性変化も出力作成前に拒否する。
- 出力namespace/既存/旧領域/外部/入力重複を拒否し、namespace全体/子alias→旧領域/外部の実junction4ケースを確認。権限不足をskip成功にせず、一時領域・旧履歴を保持した。
- 新runを許可7旧sourceと全新runtime/fixture以外の入力読取り、未許可旧/研究import、RNG・time/time_ns/monotonic/perf_counter拒否の監視下で完走。7旧sourceのhash読取りは実際に観測し、旧データ/研究モデル非利用と区別する。LF/CRLF/CRで作ったsourceコピーの同一性も一致した。

## 正式2run・helper・旧保護の時点

CLIで公開synthetic-01とignored synthetic-repeat-02を新規作成。plan/traces/REPORT/SHA索引の4成果物は全てバイト一致。時刻/絶対パスは主要4成果物に入れず、Python版は保持する。同一Python環境の一致であり異なる版のmetadata一致は主張しない。

helperは新runと別の検算処理として、固定SOURCE_COMMITの旧4a96/旧4b72静的遷移と旧4c互換表Git blobを読み、200trace/6run hash登録を再照合する。旧ルール関数/研究モデルはimportしない。現在sourceと固定Git sourceの同一性、許可7旧sourceと新runtime集合の完全性も検査する。新run自体は旧source7パスの型/step/import/hashだけを利用し、旧I/O/runner/fixture/結果を使わない。旧回帰suiteは従来fixtureを使う別処理/別集計である。

歴史的旧保護は承認baseline `b9f9b58`→固定source `2fadced`の差分で、旧123パス中README案内以外122パスが実装時点で不変。現在の全prototypes/依存/tests/新旧CIは別途厳密照合し、後続旧文書変更は参考欄へ分離する。後続文書を例外へ追加せず、現在も全旧文書不変とは記録しない。旧4a/b/cの全コード・契約・fixture・テスト・CI・公開結果・flags、旧worktree/ignored履歴を変更/削除しない。研究repoはworkerから開かず編集しない。同repoのmain同一性は責任者が最終確認する。

テスト結果は実行したJSONをそのまま保存。公開hashとcommit後のGit blobを別途検査する。訂正が必要なら旧公開/検証資料をignored履歴へ保持し、新source/実行番号へ分離する。core不変のレポート編集だけで正式2runを再生成しない。

## 限界

終了判定のない有限合成半回列で、最終得点/勝者や正規試合の終了を判定しない。9回/延長/サヨナラ・勝敗・架空リーグ規約、一般走塁/公式記録・完全試合・研究/能力・Android/UI/DB/セーブ採用は別設計。halfは最後に確定反映したsnapshotで、入力から省略された実世界事実の検出や古いsnapshot/通信再送の永続重複排除を保証しない。D029/D031/D032はOPEN。

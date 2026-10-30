# Phase 4dの設計点検・独立実装レビュー

冒頭から本人承認の直前までは承認前の履歴。現在はD045限定CONFIRMED/ADR Acceptedで、実装もレビュー後マージ済み。

2026-10-30 日本時間。[設計案](PHASE4D_DESIGN.md)・[ADR-0011](adr/0011-phase4d-half-sequence.md)は**未承認**。D045 OPEN、ADR Proposedを維持する。新4dの生産実装/新テスト成功はまだない。

## 現状と代替案

両repoのbranch/HEAD/未保存変更、最新main、未マージPR、引き継ぎを確認した。ゲームのcleanなdocs/phase4c-reference-completeはmain `91d5e1f40730e09d4970e0960a10b3c658fc65b8`と同じtree、研究もcleanでmain `cd8217a44788cf830c5554b71b04a27a98ce620c`と同じtree。未マージPRなし。既存branch/作業領域/ignored資料を保持し、最新ゲームmainからdesign/phase4d-half-sequenceへ分岐した。

旧4cは三アウト停止まで。次は表裏/両チーム得点の保持、単打等のプレー拡張、完全試合の終了判定、Android縦切りを比較した。半回列のcontrollerは新しい確率係数や走者裁定を増やさず、得点の二重加算/側の逆転/リセット漏れを検査できる。ただしプレー種類や野球としての再現性は増えない。

## 反例と点検

読取り専用担当は次工程として妥当とし、表示合計を別保存しないこと、Advance前後でscore不変、三アウト後だけ切替、直前snapshotの残塁保持と新半回でのリセット、過去履歴/永続重複排除の非保証を明示するよう助言した。設計へ反映した。

表得点→切替→裏得点→切替、無得点、満塁残塁、第三アウトのcount終端とResolveの間、連続Advance、全分割位置を主要反例にした。9回表でホームがリードしていてもcontrollerだけでは終了を検出しない例、10回以降を空塁にすることがMLB延長再現ではない点も明示した。

MLB公式2026 PDFの5.01(a)/5.09(e)と7.01の境界を確認し、通常開始/三アウト交代だけを根拠にした。明示Advance・保存形式・終了判定なしは本試験の契約であり、完全な試合規則の採用とは扱わない。

責任者はrepo外のメモリ上の設計検算で、旧4c.stepを明示利用し、固定16列/100action/116分割・全最終state/scoreを照合した。制御16セルは4制御許可/12拒否、96代表Advanceはscore不変・表裏/回数と空current・連続Advance拒否が整合。巨大値2例、9回/10回の終了判定を行わない境界も確認した。8文書の相対リンク107件に欠落なし。この検算は新4dの生産実装/新テスト成功や独立した製品妥当性検証ではない。

別の読取り専用担当の最終8文書点検でも必須指摘なし。旧runtimeをimportしないメモリ検算で16列の最終state/score、100action/116分割、96Advanceの組合せ数が整合した。依存7source・旧step委任、型/trace/replay/出力契約、9回/10回の非公式境界も点検し、107相対リンクに欠落なし。[設計PR #28](https://github.com/thrsth45h/baseball-sim/pull/28)は未承認Draftのまま提示する。旧CI成功・設計点検は新4dの実装成功や本人の仕様承認ではない。

## 本人の承認（2026-10-30）

上段は承認前の点検履歴。2026-10-30、本人が本PRを示す確認に「設計PR #28の範囲で進める（推奨）」と回答。限定半回列制御・得点表現・明示Advance・固定表/列・ゲーム側Python参照/CI・旧型/step利用とworker開始を承認した。 D045を限定CONFIRMED、ADR Acceptedとして設計をマージ後にworkerへ委任する。設計点検/CIを本人承認へ置き換えず、試合終了/延長/サヨナラ・勝敗・一般走塁・研究/能力・Android採用へ承認を拡張しない。

## 独立実装レビューと統合（2026-10-30 日本時間）

worker実装・責任者の独立レビュー後、[実装PR #29](https://github.com/thrsth45h/baseball-sim/pull/29)をmain `27c1c9df0d6286faf77334157eec2b236f94e68d`へマージ済み。旧4c.stepを再利用し、三アウト後の明示Advanceで表裏を切り替え、次半回だけを空の状態へ初期化する。切替済み累計と現在halfからscoreを導出し、Advance前後で両チームの合計を保持する。

最終実装head `898de25e62a3e81cf1ba2c0661a13e8fa4d5e0d1`、source/tests/fixtures/CI固定 `2fadced5b5da7b8387466641fd063180c5628e17`。実装時点で旧123パス中README案内以外122不変。公開15ファイル216,421bytes・17hashをworking/Git blob双方で照合。後続完了文書7件の変更は歴史的旧保護と区別する。研究main `cd8217a44788cf830c5554b71b04a27a98ce620c`/tree/作業状態は不変。研究575テストを新4dへ加算しない。

新4d26 test method/成功subTest320、旧4c28/3020・旧4a33/326・旧4b32/515を別集計し、責任者も別worktreeで全て成功、failure/error/skip0。対象headのPython3.11/3.12全CI成功。実装未参照の独立した得点ledgerと21,612比較項目/709期待拒否が一致。16列/100action/116分割、96Advance、両side合計4,608投球・576互換試行（240受理/336拒否）、完了後192拒否を確認。追加1,000半回/14,500actionの最終state/score・全trace hashも一致した。主要4成果物は責任者の監視下2runとworker公開版でバイト一致し、許可外入力/RNG・時計利用0。公開helperも200trace/6run hashを独立再検算した。

独立期待値SHA256 `334c735236a6e9acf3e7fbde64ad8cfa0f0657b1d10538761591922079911b11`。別担当は承認design/旧設計契約と自分が凍結した4c私的参照だけを用い、新旧prototype/worker/管理者私的チェックは未参照。カウントをprefix分類、走者を識別子集合で追跡し、半回segmentごとの得点entryから全scoreを集計した。Advanceは空segmentを付けるだけで、current得点をclosedスカラーへ移す生産方式を複写していない。比較前に10ファイルのhashを凍結・再照合し、worker実装開始前の凍結とは主張しない。独立比較/期待拒否/追加長列を生産test methodへ混ぜない。

責任者のPython3.12.14別実行は新4d26/320約0.531秒、旧4c28/3020約0.525秒、旧4a33/326約0.469秒、旧4b32/515約0.456秒で全fail/error/skip0。各suiteの実Windows junction検査も成功。旧action/parserの明示委任と新strict契約、fixture置換拒否、-O、監視下runを点検した。別の読取り専用担当も固定coreと公開helper/説明に必須指摘なし。同じGitHub利用者なのでCOMMENTで点検を記録し、人間の第三者APPROVEとは称していない。本人の継続マージ許可に基づき統合した。

新runは旧7sourceをimport/hashで利用し、旧I/O/fixture/結果・研究データ/モデルは計算へ入力しない。公開helperは別処理として固定Gitの旧4a96/4b72静的遷移と旧4c互換表を読む。型/step利用と、検証目的の旧表読取りを区別した。旧snapshotの再適用は純粋に同じ成功値を返す一方、返却された新半回への連続Advanceは拒否する。

対象headの新4d CI run40990660029（3.11 job123851977452 / 3.12 job123851977661）は新旧4suiteを別コマンドで成功。旧4c40990659993・旧4a40990660071・旧4b40990659994も各3.11/3.12全job/test stepが成功した。実装PRの統合前COMMENTレビューは `5707346355`。

完了文書7件が未commitの実checkoutでもCLIを2runし、helper200trace/6hashが成功した。現在の7文書変更は参考欄へ記録し、現在core/依存/tests/CI一致と実装時点の旧122保護を維持した。同版・固定sourceで責任者が実行した4suite記録を再利用しており、この補助検証を新しいtest method実行とは呼ばない。ignored領域へ資料を保存し、公開結果を上書きしていない。

終了判定のない有限合成列のcontrollerであり、9回/延長/サヨナラ・勝敗・最終得点、一般走塁/公式記録・完全試合、研究モデル/能力、Android/UI/DB/セーブ・製品採用は未実施。強い合成入力条件とhalf snapshotの意味を維持し、省略された現実イベントの検出や古いsnapshot/通信再送の永続重複排除は保証しない。D029/D031/D032はOPEN。次の重要な実装仕様は未承認で、設計相談から決める。

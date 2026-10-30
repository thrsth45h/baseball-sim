# Phase 4e worker検証記録

正本は[承認PR #31](https://github.com/thrsth45h/baseball-sim/pull/31)・phase4e-design-v0.1、承認main `e1af536153c36ab34be8199a3707ff0c1c9ba0fb`。本人承認/設計マージは2026-10-30、本書の実装検証は2026-10-31日本時間。source/tests/fixtures/CI固定は`88d239ce33c8330402fe17d8a9f3ee4dab9c34e8`。worker自己検証であり、責任者の独立レビュー合格を代行しない。

## 実行と検査単位

Python3.12.14/Windowsで新4e **24 test method成功、成功subTest18,112は別集計**、failure/error/skip0、約0.916秒。旧4d26/320、旧4c28/3020、旧4a33/326、旧4b32/515を別suite/別記録で実行し全failure/error/skip0。各suiteの実Windows junction4ケースも成功。研究575テストは実行/加算しない。専用CIはPython3.11/3.12で5suiteを別コマンド実行し、対象headの結果をPR上で確認する。

- 制御20セル・48静的受理パターンとmask別1/2/3安打受理数を確認。48表は設計の明示行先候補と識別した打者/走者から記載し、生産stepを呼んで期待値を生成していない。テストでは個人を当初スロットで識別し、全組合せの順序を調べる別式で照合した。生産の隣接順序比較だけを複製した期待式ではない。
- 型正payload375（hit3×5^3行先）×占有8×side2×outs3の18,000試行を検査し、288受理/17,712拒否。基礎3,000組は48受理/2,952拒否、受理キー集合は静的48表と完全一致。回2/closed11,13/runs7/代表fair count(0,0,1)での有限域であり、全歴史/全得点の網羅や実際の頻度ではない。
- 受理後のcount消費、outs/回/side/closed保持、正確な占有と既存走者本塁数だけの加点、攻撃側帰属、人数収支を全受理組で確認。拒否では元state不変。据置き、満塁単打1/2/3得点、複数本塁到達、None不一致・逆走・非本塁衝突/順序逆転の具体例も検査した。
- 新hitはfair待ちだけで受理し、between/pitching/closedとwalk/K/caught-foul待ちを拒否。旧state/4actionが同一型で、旧parser/step各1回、新hitで旧step0回をspy検査。旧Resolveの5許可/7拒否も保持した。
- 固定18列/60action/78分割位置で、最終state/score/stageとprefix+suffix traceが一括処理に一致し、逆順ケース実行でも一致。新traceの正確な5キーとderived score差分、newhit0…3得点/outs不変、非加点action差0を確認した。
- 解決後の連続hit/Pitch拒否と次打者の明示Begin、同じ古いfair snapshotへの純粋再適用を区別。10^100の得点と、異なるfair count（標準n1、b3/s2/n10^100）でも正しく処理する。旧W/H/アウト/Advanceと新hitを混在させた128半回の列は最終65回表、away320/home320、空currentで別台帳と一致した。
- constructorは形だけを検査し、占有/順序はstepへ分離。bool/float/subclass/範囲違反、list/tuple区別、余分不足/重複JSON/非有限値、不明payload、厳密state/action/有限list/tupleを検査。不変新action/result/trace/flagsと独立JSON、空replay全stage、不正tailで入力不変/部分結果なし、Python -Oでも残るvalidationを確認した。
- fixtureのID/開始側/正確action全payload/最終/score/stage/行順を別pin。hit_retire_switchのO→P・RX据置きは同じ最終になるが、loader/実JSON置換のrunnerは出力前に拒否する。payload変更、48表の欠落/重複/期待after不一致、制御表drift/source同一性変化も拒否した。
- 新namespace/既存先/旧領域/外部/入力重複を拒否。namespace全体/子alias→旧領域/外部の実junction4ケースを作成して拒否し、権限不足をskip成功にしない。旧worktree/ignored資料と過去のテスト領域は保持した。
- 許可9旧sourceと新runtime/fixture以外の入力読取り、未許可旧/研究import、RNG、time/time_ns/monotonic/perf_counter読取りを拒否する監視下で新runを完走。旧9sourceのhash読取りを実際に確認し、旧データ/研究モデル非利用と区別する。LF/CRLF/CRのsourceコピーでも同一性が一致した。

## 正式2runと保護の時点

CLIで公開synthetic-01とignored synthetic-repeat-02を新規作成。plan/traces/REPORT/SHA索引の4成果物は全てバイト一致。時刻/絶対パスを主要4成果物に入れず、Python版を記録。同じ版の一致であり別Python版のmetadata一致は主張しない。検証日10/31は補助根拠JSONへ記録した。

helperは別の検算として固定coreの新48表と旧4a96/4b72遷移・旧4c互換表のGit blobを読み、120trace/6run hashを再照合する。旧ルール関数/研究モデルはhelperへimportしない。新run自体は許可旧9sourceの型/parser/step/import/hashだけを利用し、旧I/O/runner/fixture/結果を入力しない。旧suiteは従来fixtureを使う別処理/別集計。

歴史的旧保護は承認baseline `e1af536`→固定source `88d239c`の差分で、旧153パス中README案内以外152パスが実装時点で不変。現在の全prototypes/依存/tests/新旧CIは別途厳密照合し、後続旧文書変更は参考欄へ分離する。後続文書を例外へ追加せず、現在も全旧文書不変とは記録しない。旧4a–4dの全code/型/契約/fixture/tests/CI/結果/flagsを変更/削除しない。研究repoはworkerから開かず編集しない。同repoのmain/tree/作業状態の最終確認は責任者が担当する。

5suite記録は実行結果をそのままJSON保存。公開hashとcommit後のGit blobは別検査する。訂正が必要なら旧公開/検証をignored履歴へ保持し、新source/実行番号に分離する。core不変のレポート編集だけで正式2runを再生成しない。

## 限界

外部裁定済みの全員safe安打で、打者が安打価値と等しい塁へ留まる場合に限る。最終位置の矛盾を拒否するだけで、途中触塁/追越し/失策/妨害等の履歴合法性や省略された実世界事実を証明/検出しない。永続重複排除・exactly-onceは保証しない。旧4dの9回以降の空塁切替/終了判定なしを継承し、サヨナラ/勝敗/公式個人成績・一般走塁裁定、研究/確率/能力・Android/UI/DB/セーブ採用は扱わない。D029/D031/D032はOPEN。

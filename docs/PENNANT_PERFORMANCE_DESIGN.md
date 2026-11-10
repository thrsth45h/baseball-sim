# Phase 5a保存・日程スキップの性能改善案

2026-11-10。**設計提案・未実装**。基準mainは `7ebb4a46a319ffde5e390a86b8f0a3a0352e4866`（[PR #44](https://github.com/thrsth45h/baseball-sim/pull/44)統合済み）。D057/D058の試作を維持する内部改善案で、新しいゲーム仕様・製品DB・次年度移行の採用ではない。[ADR-0016](adr/0016-pennant-store-performance.md)も参照。

## 問題と確認できた事実

[検証記録](pennant-android/VALIDATION.md)の実Activity/Roomによるseed42・720試合は1,110,228ms（18分30.228秒）。最終frameは1,585,652byte、SHA256 `0bde5d0de901771b13e75adc64aab9d21a5854c8f7aed9e34aafb72a3881bbde`。API35のdebugエミュレータの単一試行で、実機一般性能ではない。

prefix300の既存局所profileではmarker約645–661ms、result約658–672ms。局所profileはfixtureから始めた保存操作で、実720試合の時間と別物である。前回のhex変換・新規row限定変換では高速化を確認できなかった。旧失敗を含む数値・報告は書き換えない。

コードでは `RoomSeasonStore.commit` が候補をencode/checkedStateで検証し、更新前のread、transaction後のafterCommit→boundRead→readでも全frameとledgerを検証する。`checkedState` はhash・decode・canonical再encode・active再生を行う。繰り返しがあることは確認できるが、どの部分が時間を支配するか、提案で何倍速くなるかは未計測である。

## 推奨する限定案

最初に現行処理を区分計測し、その上で**通常commitの成功応答時だけ、同じ操作で既に検証した値の重複decode/replayを減らす**。保存後のDB確認は残す。create/saveSlot/loadSlot、公開read、重複要求、no-op、例外後の復旧は従来の厳密経路を初期実装では維持する。

1. 候補の確定byte列からcheckedStateが返す検証済み状態を保持する。呼出元のcandidateをそのまま返さない。byte列・状態・期待ledgerは外部から書換えられない操作内の所有物とし、操作をまたぐキャッシュを設けない。
2. 既存の更新前検査、状態遷移条件、prefix照合、start marker、CAS、receipt、原子的結果追加を保持する。検証やsimulationをSQLite書込transactionへ移さない。
3. transaction成功後、AFTER_COMMIT faultを通過してから、**新しい一つの読取transaction**でhead・そのoperationのreceipt・当該世代のledger全件を取得する。
4. headの全永続列（id、world、generation、revision、operation、hash、payload全byte）、receiptの全永続列（fingerprintを含む）、ledgerの件数・順序・全永続列・payload全byteを今回の確定内容と照合する。ByteArrayの参照同一性やhashだけで照合しない。ledgerは今回追加した末尾だけでなく既存prefixも対象とする。
5. 全一致した場合に限り、同じbyte列から検証済みの状態を、今回のtoken/operationに結び付けて返す。これは新鮮なDB確認の省略ではなく、同一byteの再解釈だけを省く案である。
6. 世代・revision・operationの相違、欠落、破損は従来の型と意味に沿って失敗させる。既存の厳密readへフォールバックする場合も、別操作の最新状態を今回の成功にすり替えない。CoordinatorのStoreConflict即時再送出・例外時の限定的成功判定を弱めない。

性能上の期待は「重複していた意味検証の回数を減らす」だけ。全ledgerのDB読取り・byte照合・更新前検査・全frame書込みは残るため、シーズン長に伴う費用増加や長期DB問題を解決したとは扱わない。期待ledger生成自体が高ければ効果が出ない可能性がある。

## 変えない契約

- 純Kotlin試合エンジン、toy確率、sm64-v1、日程/seed/打順/記録/順位、進行単位間の同一結果。
- Room/schema1、codecのbyte形式と受理/拒否条件、既存DBの読込、手動slotとBLOCKED診断保持。
- 試合開始前のdurable marker、結果/集計/進行位置のatomic性、保存成功後の公開、停止予約・連打拒否・回転。
- 公開read/起動/再起動時の厳密hash・canonical・ledger・active再生検証。失敗後に未確定計算を採用しない。
- world/generation/revision/operation/fingerprintの照合、競合・重複・取消・障害の意味。
- インターネット権限なし、端末単体動作、手動save/loadは試合外。ユーザーPCの画面/入力/既存環境は操作しない。

## 反例と代替案

| 案 | 利点 | 反例・費用／今回の扱い |
|---|---|---|
| 現行維持＋計測だけ | 安全性の実装変更なし | 改善はない。推奨案の安全性または性能を立証できなければここへ戻す |
| 操作内の検証済み値＋保存後の全byte照合 | 重複意味検証を減らせる可能性 | 外部可変参照や未照合列が一つでもあれば誤承認。限定案として推奨 |
| tokenだけ合えばメモリ値を採用 | 低費用 | 同tokenのpayload/ledger改変・receipt不一致を見逃すため不採用 |
| 複数試合を一括保存／保存頻度削減 | 書込回数を削減 | 強制終了後の確定結果/停止位置の契約を変えるため今回対象外 |
| 全frame保存を廃止し差分DB化 | 長期的な計算量改善の候補 | schema・移行・障害復旧の新設計が必要。別提案で判断 |
| UTF-8等のcodec内部割当削減 | 保存形式を維持し得る | 不正UTF-8受理や並行decoder共有の危険。別小変更として計測後に検討し、同時に混ぜない |

## 正しさの受入れ

既存app/core/device/host回帰を保持する。workerの自己点検に加え責任者が差分・実XML・成果物を独立確認する。

- 実Roomで正常marker/途中checkpoint/結果追加を従来経路と比較し、返却全値・保存frame全byte・ledger全列が一致する。0/1件追加、終端、BLOCKED、no-op、duplicateを別々に確認する。
- BEFORE_TRANSACTION/AFTER_RESULTS/AFTER_HEAD/AFTER_COMMITの障害、応答喪失、取消、重複operationの同内容/異内容を検査する。commit前失敗は成功扱いせず、commit後の回復は該当する確定内容だけ採用する。
- commit後〜応答前に別coordinatorがload/create/通常commitする競合をlatchで再現。旧操作が新世代や新revisionを成功応答として採用しない。
- 同tokenのままheadのpayload/hash、ledgerの既存prefix/末尾/順序/件数、receipt/fingerprintを個別改変。hashだけ更新した意味的不正も含め、従来が拒否するものを新経路が受理しない。未照合列を残さない。
- byte配列・リストの外部可変参照から検証済み値を書換えられないことを確認。証拠用オブジェクトは公開しない。
- seed0/42/2026の純Kotlin720試合について全skip/混在の結果・乱数を回帰確認。Androidはseed42を新規から実720transactionで完走し、最終frameを上記既存期待値と全byte比較する。fixture投入を実完走と呼ばない。
- 配布APKそのもののforce-stop復旧で、同GameId/確定checkpointから次1球だけ進む。再導入・データ消去で復旧試験を置換しない。

## 性能比較と採否

固定baselineはmain7ebb4a4（runtime/tests/CIの基準source7ec9d0e）。候補実装・設定・APK/source・CI機器情報を固定し、比較後に有利なseed・prefix・条件へ変更しない。

局所比較はprefix0/100/300/600/719の同一frameから別DBを準備する。準備時間を除外し、cold read、warm read、候補検証、更新前read、transaction、応答後検証、core進行を分離する。**書込を伴う標本ごとに、同一の開始frame/token/receipt/ledgerを持つ独立DBを準備し直す。** markerはactiveなし、resultは同じ未進行のdurable markerから、同一seedで作成した同じ完了候補を入力する。候補作成の計算時間はresult commitと別計数にする。operation等の不透明IDも両実装で同じ固定値を使える独立DBとし、初期byte/全値の一致を確認する。同じDBを連続して進めて標本を集めない。prefix719のresultも毎回719から720への同じ遷移とする。

終端720はreadのみ。各prefixでbaseline/candidateをABBA順に実行し、別計数のwarm-upを除いた最低5標本/実装/対象操作を保存する（例：ABBAを3組で各6標本）。warm-upも別DBとし、その更新を計測用DBへ持ち越さない。coldはwarmと混ぜず、新プロセス等の定義と実施回数を記録する。計測器を有効にしたbaseline/candidate同士で比較する。

別ビルドを同一CI emulator・同一構成・独立DBで比較できる検証用harnessを優先する。署名やpackage変更等の検証専用差分は配布版と区別する。実行順による温度/JIT/GC/キャッシュの影響を記載し、実現できない場合は比較可能性の制約として報告する。中央値・範囲・全標本を公開し、異なるrunner単発値から改善率を出さない。

工学上の暫定目標はprefix300/600のmarker/result中央値がそれぞれbaselineの80%以下、prefix0/100/719は10%超の悪化なし。これは未実測の目標で製品の速度保証ではない。全標本とばらつきも確認し、ノイズで判定不能なら追加の事前固定試行か計測のみの結論とする。効果が立証できなければ高速化と呼ばず、危険な検査省略へ切り替えない。

実UIの720試合時間も新規DBから計測し、DB/WAL・標本heap・完了frameを回収する。旧18分30秒は歴史的参考とし、異なるrunner間の因果的改善率の分母にはしない。timeout延長だけを成果にせず、必要な上限変更は理由と未完走を明記する。実機の一般速度や電源断耐性は別の未検証項目として残す。

## 作業分担と完了条件

この設計PRは文書のみで、実装承認・性能改善済みの主張ではない。採用判断後、専用worktreeのworkerへ「計測＋通常commit応答の限定改善」を委任し、責任者が独立レビュー・修正指示・CIと正しさ/性能のゲート確認を行う。問題がなければ継続的マージ許可の範囲で統合し、検証したAPKと実測/限界を報告する。安全性が立証できない/効果なしの場合は計測結果と見送り理由を成果として残す。

製品DB、次年度/世代交代、能力/経営/スタッフ/研究モデル、UI機能拡張へ進まない。D029/D031/D056等の未決定事項を確定に変更しない。

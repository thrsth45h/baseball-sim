# Phase 4fの設計点検・独立実装レビュー

冒頭から最新の進行指示記録までには提案時の履歴を含む。現在は提示済み具体案を本人の進行/確認省略指示に基づきD047限定CONFIRMED/Acceptedへ正式化している。

2026-10-31 日本時間。[設計案](PHASE4F_DESIGN.md)・[ADR-0013](adr/0013-phase4f-single-game-prototype.md)・[設計PR #34](https://github.com/thrsth45h/baseball-sim/pull/34)は**具体仕様未承認**、D047 OPEN/Proposed。本人は1試合統合試作を次の目標とする方向を承認した。新4fコード・テスト・結果はまだ作成していない。

## 現状と方向変更

ゲームmain `00b31a313bdc141ed7184965fd921adec219ed24`、研究main `cd8217a44788cf830c5554b71b04a27a98ce620c`をfetchし、cleanな現在branch/HEAD/両main・未マージPRなしを確認した。旧docs/phase4e-reference-completeはゲームmainと同tree。既存worker/review/ignored資料を保持し、最新mainからdesign/phase4f-single-game-prototypeへ分岐した。

本人から「序盤も序盤なきがする」「完成するのかな」と指摘を受け、研究/検証工程の完了と遊べるゲームの完成度を分けて説明した。次の目標を、個別研究追加より仮の2チーム/一球入力/試合終了/簡単な結果表示をつなぐことへ置く案に「じゃあ、それで次に進んで」と回答を受領。具体的な仮重み・進塁・終了規約は未提示だったため、方向への賛成だけで正式採用しない。

## 代替案と批判的点検

18半回固定なら既存前提を維持しやすいが、ホーム先行でも裏が行われサヨナラもない。readonly担当は9回打切り/裏省略/サヨナラを推奨し、旧4e/W/Hへ先に適用して得点をclipする契約違反、終端後stateの捏造、打順の半回リセット、上限を引分へ変える誤りを指摘した。専用終端event/GameResult、チーム別打順持越し、上限未完了を案へ反映した。

MLB2026公式の5.04(a)、7.01(a)/(e)、9.06(f)/(g)の指定部分を確認。9回同点打切りは独自の試作用設定として分離。終端の公式安打価値を候補塁数と同一視しない。公式記録裁定は今回の簡単なイベント件数へ混ぜない。

ToyPitchSourceは1球の判定済みeventを供給するstubで、D022上流の未実装を明示する。最終成績を先に抽選せず、EV/LA/Savant値も後付けしない。一定進塁も旧4eの一般契約を狭める規則ではなく、この仮policyの入力生成だけ。仮数値や結果を現実の能力/MLB成績へ解釈しない。

## 今後の確認

読取り専用担当の8文書点検では重大な構造矛盾なし。実装前の明確化として、fair結果不足/不正時の球の未受理とabort marker/replay保持、専用終端の「旧処理0回」はResolve系/Advanceだけでその球のBegin/Pitchを除くことの2点が指摘され、設計へ反映した。上限球で終了が成立した場合はcompleted優先という境界も明記。これらを新4f生産テスト成功や本人の仕様承認とは称さない。具体案を本人が承認後、設計マージ→専用worker→責任者レビューの順で進める。

## 責任者の設計検算

8文書以外の変更なし、新prototypes/phase4f不在を確認。相対リンク133件は欠落なし。新仮policyの通常進塁を全8占有mask×候補1/2/3の24ケースで既存4e.stepへ渡し、受理・count消費・人数収支が整合した。旧stepは非サヨナラ条件での検算にだけ使った。終了条件の8例は得点の算術として検算し、2重み表の100個の整数draw区間も重みと一致した。これは新4f controller/乱数source/サヨナラ実装や新テストの成功ではない。

指摘反映後の読取り専用再点検では必須指摘なし。fair入力不正時の球の未受理・abort/replay保持、終端解決で省く旧処理の範囲、Begin/Pitchの受理、上限境界とD047 OPENが整合した。最終提案は[設計PR #34](https://github.com/thrsth45h/baseball-sim/pull/34)のDraftに保持し、具体仕様の本人承認後だけ実装へ進む。

## 本人の進行・確認省略指示（2026-10-31）

上段は具体仕様の提示前から点検までの履歴。2026-10-31、本人は方向に「じゃあ、それで次に進んで」、提示済み設計PR #34の具体案を再確認した質問には「いちいちきくな」と回答した。提示済み仕様による進行・責任者判断・都度確認の省略を指示したものとして記録し、この限定試作を実装へ進める。全細目を本人が個別精査したという記録にはしない。 D047限定CONFIRMED/ADR Acceptedへ正式化し、継続的マージ許可に基づき設計統合後workerへ進める。技術点検/CIを本人指示の代用にはせず、指示の文言と責任者の範囲解釈を区別した。全体の方針や既存研究/能力/Android採用へ無制限には拡大しない。

## 独立実装レビューと統合（2026-10-31 日本時間）

worker実装・責任者の独立レビュー後、[実装PR #35](https://github.com/thrsth45h/baseball-sim/pull/35)をmain `73a5ca3fc3de942b5e3a9c7b455cdc250889a835`へマージ済み。仮の2チーム各9打順を一球ずつ進め、9回同点引分・ホーム先行の裏省略・サヨナラを扱うPython CLIが完成した。結果表示は得点表・終了理由・簡単なイベント件数で、未較正のtoy確率/一定進塁を使う限定統合試作。

責任者のseed42実行はaway 4 / home 2、終了理由`away_lead_after_bottom9`、受理投球数259。 新4f23/13、旧4e24/18112、旧4d26/320、旧4c28/3020、旧4a33/326、旧4b32/515（method/成功subTestは別集計）を責任者も別worktreeで実行し、全failure/error/skip0。対象headのPython3.11/3.12 CIも成功。seed0/1/2/42/2026のCLI実行が正常完了。実装未参照の凍結参照と276,674項目を独立照合し、逐球の入力・得点・打順・終了・再集計が一致した。同seed42の主要5成果物は責任者2runとworker公開版でバイト一致。記録入力replay、試合間非干渉、打順持越し、サヨナラ専用終端、上限/不足/不正の未完了を確認した。

最終実装head `29e8ab3cb31e583627b7180a56367aff08809ce5`、source/tests/profile/fixtures/CI固定 `a5fda901bc0a8cca9ae0eefc1d076fd8171c73a6`。実装時点で旧185パス中README案内以外184不変。公開20ファイル726,500bytes・23hashをworking/Git blob双方で照合。後続完了文書7件を歴史的旧保護と区別する。研究main `cd8217a44788cf830c5554b71b04a27a98ce620c`/tree/作業状態は不変。研究575テストを今回へ加算しない。

独立期待値SHA256 `d8e708ef5af0a599d64b1d83ee950922c7c2cfcae65a5bd590d0198b82fde2e7`。別担当は採用設計/旧設計契約と自分の凍結私的参照を使い、新旧prototype/worker/親privatechecks未参照で逐球・識別走者・打席別/半回別ledgerを作成した。比較前にhashを固定・再照合し、生産実装開始前の凍結と同一視しない。独立比較やscripted境界の件数を生産methodへ加算しない。

比較adapterは参照凍結後に実装APIを読んで接続し、親が点検・実行した。新半回の得点表を開く時点だけ、参照は最初のBegin、実装は直前のAdvanceと異なる。厳しい条件を満たす未投球の新半回1セルだけの一時的None/0を差分として記録し、他セルと最終得点表は厳密一致で確認した。5seedと14試験ケース、59期待拒否を含む比較であり、仮確率の現実妥当性の検証ではない。

初版core `05fe7bf`では入力sourceが予約済みpitch_limitを停止理由として返すと再生がsource_exhaustedへ変わるP2を親が再現した。workerがsourceからその理由を使用できなくし、変造された例外の理由もsource_errorへ正規化した。固定core `a5fda90`で3経路の同じ理由・部分状態・再生を再確認し、旧証拠は別ignored領域へ保存した。最終公開helper点検では、run_tests.pyが出力namespace自身を通常ファイルにできるP2も修正し、未作成の隔離namespaceで拒否を再確認した。

公開helperは独立ルールoracleではなく、記録ledger・打順・得点表・仮乱数の再集計を担当する。親の2runで各523event/259受理球/57fair draw/82完了打席、8run hash、専用Randomの316drawを確認した。主要5成果物には時計・絶対パスを含まず、監視実行の許可外入力・global RNG・時計利用は0。完了文書7件の編集中も同coreで2runと公開helperを実行し、各523event/259球/57fair draw/82完了打席・8run hashが一致した。既存6suiteの固定coreでの成功証拠は再利用と明示し、文書だけの変更を新規テスト件数に加算しない。

責任者は固定coreの別worktreeでCLI・6suite・公開helperを実行し、コードと公開説明をレビューした。終端解決を旧Resolve系へ渡してからclipする処理がないこと、Begin/Pitchは受理されること、fair不正球を受理せずabort理由をreplay保持することを確認。旧snapshotを終端後の塁配置と称さず、最終得点を二重加算しない。追加readonlyレビューも実施。対象headのCI成功と最新の進行/確認省略・継続マージ許可に基づき統合し、同一アカウントCOMMENTを人間第三者APPROVEとは称さない。

投球真値/知覚/接触/打球物理の上流はstub内で未実装、EV/LA/Savant値を後付けしない。安打/進塁の仮設定をMLB成績・能力1〜20の較正と解釈せず、公式AB/RBI/2B3B等も裁定しない。一般走塁・失策/併殺・投手交代・延長・Android/12球団ペナント/経営・製品セーブは今回含めない。D029/D030/D031/D032はOPEN、正式製品採用ではない。同じ範囲の確認を繰り返さず、最新WORKFLOWに基づき責任者が進める。

最終head `29e8ab3`の[新4f CI](https://github.com/thrsth45h/baseball-sim/actions/runs/41194155900)と旧a/b/c/d/eの6workflow・計12jobs、全test stepを責任者も確認した。[対象headの独立レビュー記録](https://github.com/thrsth45h/baseball-sim/pull/35#pullrequestreview-5722412604)後に統合した。完了記録は7文書だけを別PRで更新し、固定core・公開レポート・DECISIONS/AGENTS/WORKFLOWは変更しない。

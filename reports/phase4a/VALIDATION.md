# Phase 4a worker検証記録

承認設計は[ゲームPR #19](https://github.com/thrsth45h/baseball-sim/pull/19)、main `1c5b738671a452305a4be874c57f77cd8ad5db10`、`phase4a-design-v0.1`。source/tests/fixtures/CIは`4d3aab629d06bf4e079ca6da09dd6970b5c4e592`へ固定した。本書はworkerの自己検証で、責任者の独立レビュー・外部査読・製品採用を代行しない。

## テストと検査単位

Python3.12.14で31 test method成功、failure/error/skip0、正式の構造化記録で約0.382秒。subTest316は別集計で、method数へ加算しない。専用GitHub ActionsはPython3.11/3.12で同じunittestを実行する。PR対象headのCI結果はPR上で別に確認する。

- 静的96遷移、固定16列を実装と照合。静的表自体も生産関数を呼ばない設計列の別式で検査した。言語非依存fixtureを生産実行から生成して正解扱いしていない。
- 16列の全95分割位置で、prefix/残りの最終状態と絶対n+1のtrace連結が一括処理に完全一致。逆順のケース実行でも一致。
- 二ストライク後に通常ファウル1,000回を加えても(0,2,1002,active)を保持。チップ/未捕球バントと捕球ファウル飛球を区別し、球数による強制終端なし。
- 全statusのn/カウント整合、bool/小数/文字列/非有限等の不正型、未知項目、到達不能stateを拒否。16イベントまでの後続列挙でもコンストラクタの整合条件と到達集合が一致。
- 終端4種×全8イベントの拒否、不明/未捕球ストライク・死球/自動判定等の拒否、list/tuple以外の列の拒否を確認。無効なtailで元状態/入力を変えず、部分成功traceを返さない。空列はactive/停止状態をそのまま返す。
- Pythonの-Oでも明示的validationが残ることを別processで確認。不変state/result/trace、合成flagsの外部変更拒否を検査。
- 新出力先/上書き/旧namespace/外部/入力重複の拒否、namespace全体/子alias→旧領域/外部の実Windows junction4ケースを確認。Linuxでは実symlinkを使い、作成失敗をskipにしない。
- 新runを読取り/RNG/現在時刻読取り(time.time)の監視下で完走。許可した新source/fixtureと新出力以外の読取りなし。旧研究/モデルやStatcast/3dをimportしない。処理前後のsource/fixture同一性比較が異なる場合や期待表不一致では出力作成前に拒否。

## 正式2runと旧ファイル保護

公開synthetic-01とignoredのsynthetic-repeat-02はplan.json/traces.json/REPORT.md/SHA256SUMS.jsonの4ファイル全てがバイト一致。主要成果物には時刻/絶対パスなし。source identityはUTF-8テキストをLFへ正規化し、固定Git sourceとcheckoutをhelperで照合する。Python版を記録するため、異なるPython版同士のplanバイト一致を主張しない。

ゲームbaseline37パスのうち、README案内と.gitignoreのdata/prototypes追記以外の35パスは維持。既存の設計docs/AGENTS/テンプレートを変更せず、既存worktree/ignored資料も削除しない。今回のhelperは研究repoや原データ/旧モデルを開かず、既存ゲームGitメタデータだけを保護確認に使う。研究repo不変は責任者が最終確認し、未接続の研究575テストを今回の成功件数に混ぜない。

構造化テスト記録・再現性・保護記録と全公開hashは本folderに保存する。Gitへcommit後の公開ファイルとGit blobの一致は別に確認する。正式runのtraceは、静的96表からの各前後状態・イベント・順番号・最終状態もhelperで再検査する。

## 範囲の限界

8イベントは既に裁定された合成入力で、裁定/確率/物理の生成器ではない。fair_play_pendingから安打/アウト等を決めず、捕球されたバントファウルを三振に変換しない。nは今回受理したイベント数であり、公式投球数でもない。メモリ内の途中処理一致は製品セーブ形式の採用ではない。

MLB2026規則の指定部分を参照した限定ルールで、完全な野球・架空リーグ全規約の採用ではない。確率・Statcast/旧研究・3d・能力・走者/得点/イニング・公式成績・UI/セーブは未接続。Pythonは検算用でAndroid言語/DB/アーキテクチャを選ばず、D029/D031/D032はOPEN。全capability flagsをplan/tracesに記録した。

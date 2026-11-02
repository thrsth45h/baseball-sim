# Phase 5a Android接続の検証記録

2026-11-02 日本時間。**最終CI検証中**。[PR #44](https://github.com/thrsth45h/baseball-sim/pull/44)。数値・APK・sourceは検証完了後の値を固定し、未検証を成功と称さない。

## 実装と検査対象

- 新PennantViewModelは同期busy claimで連打をqueueせず、各操作の世代を確認してから公開する。確定済みStoredSeasonだけを表示し、書込失敗時の計算済みSessionを使い回さない。
- 旧単独1試合モードを初期画面として保ち、上部のペナント切替からRoomの保存/復旧へ接続する。
- VMのJVM検査は8 method。初回head5512be67の7 methodを含む既存CIは成功したが、追加した回帰と最終headはこれから確認する。
- 通常のAPI35 instrumented検査は新Activity 5 method、狭幅/診断/読込失敗表示3 method。通常runnerはhost専用processクラスを除外する。
- host process probeは、保存済みcheckpointを画面へ公開したprepare processを生存待機させ、hostが同じPIDを確認してforce-stopする。prepareは意図的に終了するsetupであり、成功JUnit件数へ数えない。recover 1 methodは別プロセスで同じworld/generation/revision・GameId・checkpoint hashを照合し、次の1球だけを進める。

## 機械可読の証拠

`pennant-real-season-42.bin`は実Activity/Roomを通して720試合を進めた最終SeasonStateの全値codec。対応JSONへmaster seed、12球団の順位/成績、108人のイベント件数、frame SHA256・保存容量・経過時間を出す。合成の最大payload試験やメモリ内完走とは区別する。

process成果物は`expected.json`、`before-pid.txt`、`after-force-stop-pid.txt`、`recovery.json`、正常recoverログと`verification.json`。force-stop前のPID生存と後の不在をhostが検査する。JUnit終了後にプロセスが自然終了し得るため、recover後もPIDが生存し続けることを要件にはしない。回復テスト内のProcess.myPidと新しい証拠ファイルで別プロセスを確認する。

PNGは新規/進行中/順位/記録/シーズン完了/狭幅/未完了診断/process前後をCI追加出力へ保存する。実際に取得/目視した範囲を完了時に記録する。

## 現時点のCI事実

head5512be67の全9workflowは成功。これは最終受入れ一式ではない。head261aa22のdeviceはテストへ到達する前、SDK emulator ZIP取得のエラーで停止した。アプリのテスト失敗や動作成功とは扱わない。新しいheadで結果を確認し、取得だけの一時障害なら最終対象の限定再実行で確認する。

## 未検証・範囲外

最終APK・host復旧・720実transaction・追加8表示/操作methodの成功はまだ未確定。物理端末、API26での実行、電源断/媒体故障、長期年度移行/保存互換、能力/CPU采配/経営/研究モデル採用は対象外または未検証。API35の1台のエミュレータの時間やメモリを端末一般性能へ外挿しない。

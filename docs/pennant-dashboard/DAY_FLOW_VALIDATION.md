# 当日の進行と自球団カードの検証

[設計PR #59](https://github.com/thrsth45h/baseball-sim/pull/59)・[実装PR #60](https://github.com/thrsth45h/baseball-sim/pull/60)

## 配布したビルド

- `0.8-day-flow-tech`（versionCode 8）、11,043,072バイト。ビルド元 `6669da57d6ae86169fa8926909dad9615fb37029`、ツリー `f013cda56466c52e2173ce964b27b1ce17548ff6`。[GitHub Release](https://github.com/thrsth45h/baseball-sim/releases/tag/android-v0.8-day-flow-tech)
- APKのSHA-256は `1770e8f489070f22b58416605cec169a55b1d4df53e5a423b308d536fe5e57d0`。配布ジョブはエミュレータへインストールしたAPKと成果物に入れたAPKのSHA-256を照合した。統合したマニフェストとパッケージ用マニフェストに `INTERNET` 権限はなかった。[配布元とメタデータ](https://github.com/thrsth45h/baseball-sim/actions/runs/45246566389/artifacts/14854149177)

## 画面と保存の確認

- API35の隔離エミュレータで先行端末試験11件が成功した。seed `42` の第1週火曜で右上の「進行」を実タップすると火曜のまま `3–5`、`2–5`、`4–3`、`3–2`、`2–3`、`5–4` の6試合を表示した。「翌日へ」を押すと水曜の試合前に切り替わり、その前後で保存トークンと保存内容は一致した。休養日、画面再作成、タブ切替、幅320dp・文字サイズ1.5倍での上部バーと下部タブも確認した。[実タップのJSON・画面・ログ](https://github.com/thrsth45h/baseball-sim/actions/runs/45246566389/artifacts/14854189260)
- 当日の仮球団1の終了カードは `S1-D002-L1-R01-M1-G1` の `3−5` を表示し、保存内容は同じだった。仮球団10のカードは同日の先行5試合だけを確定し、対象 `S1-D002-L2-R01-M3-G1` を0球で開いた。再度同じ試合を開いても保存内容は同じだった。ほかの球団のカードには操作を割り当てていない。[自球団カードの記録](https://github.com/thrsth45h/baseball-sim/actions/runs/45246566389/artifacts/14854189260)
- v1・v2・v3・年度移行済みv3で、アプリの実プロセスを強制終了してから同じチェックポイントへ復帰し、次の1球が一致した。確定スコアを表示する段階は画面回転では保持し、アプリを新しいプロセスで開くと保存済みの日程位置から再開する。[プロセス復旧の証拠](https://github.com/thrsth45h/baseball-sim/actions/runs/45246566389/artifacts/14854388542)
- 単体テストはapp46件・core38件が成功し、失敗・エラー・スキップはいずれも0件。Lintはエラー0件、既存警告8件。シーズン最終日の表示、保存を伴わない翌日切替、古いトークンの拒否、停止した日付からの再開を単体試験でも確認した。[テストとlintの成果物](https://github.com/thrsth45h/baseball-sim/actions/runs/45248163148/artifacts/14854996585)
- API35でv3の720試合完走と全144人の画面データを確認した。最終保存と選手の画面データは0.7とバイト単位で一致した。[v3の通年試験](https://github.com/thrsth45h/baseball-sim/actions/runs/45248163148/artifacts/14855108333)
- 2年目の720試合と3年目への移行をエミュレータで完了した。1〜3年目の試合seed 2,160件、年度別の球団24行と選手288行を独立参照と照合した。年度履歴の閲覧、手動セーブ・ロードも確認した。[年度移行の試験](https://github.com/thrsth45h/baseball-sim/actions/runs/45248158565/artifacts/14855531819)
- 通常の端末試験66件は成功した。旧v1の720試合完走と全108人の画面照合、単独1試合、AndroidのBackキー操作も成功した。固定バーと選択メニューに同じ球団名が現れるため、選択メニュー内の項目を指定するようテストを更新した。配布APKをビルドした後の `bb88606` はBackキーをAndroidから送る端末テストだけを変更した。[通常の端末試験と保存出力](https://github.com/thrsth45h/baseball-sim/actions/runs/45248163148/artifacts/14855422833)

## 検証環境と署名

物理端末、最低対応API26、電源断での復旧は未検証。配布APKの証明書SHA-256は `f4b0e9bacefe349e55431a5c6eb74acfa04c94e08579b45638205a163525632f`。0.7と証明書が異なり、既存インストールに上書きできない。旧版の端末内セーブを保つ場合は旧版を残す。[0.7の証明書](VALIDATION.md)・[今回の証明書](https://github.com/thrsth45h/baseball-sim/actions/runs/45246566389/artifacts/14854149177)

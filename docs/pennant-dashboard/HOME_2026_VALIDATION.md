# ペナントホームと2026年の日付の検証

[設計PR #61](https://github.com/thrsth45h/baseball-sim/pull/61)・[実装PR #62](https://github.com/thrsth45h/baseball-sim/pull/62)

## 配布APK

- `0.9-home-2026-tech`（versionCode 9）、11,043,080バイト。ビルド元 `d8a4feee1593b27e6afd9b147f2967fc6fa18e10`、ツリー `254318c0a10242aaf1d482e0c7df92ec154d3d9d`。[GitHub Release](https://github.com/thrsth45h/baseball-sim/releases/tag/android-v0.9-home-2026-tech)
- APKのSHA-256は `30e35930493d728d7eba6a2d9e9987c12f72618c89d38d5cee54490e05c26fbf`。端末試験でインストールしたAPKのチェックサムと一致した。統合マニフェストとパッケージ用マニフェストに `INTERNET` 権限がないことを確認した。[配布元のAPKとメタデータ](https://github.com/thrsth45h/baseball-sim/actions/runs/45397324650/artifacts/14921438070)

## 操作と回帰

- API35エミュレータの同じ配布APKで、11件の画面・操作試験が成功した。seed `42` の3月27日（金）で「進行」を実タップすると、同日の日付に6試合の結果 `3–5`、`2–5`、`4–3`、`3–2`、`2–3`、`5–4` を表示した。6カードの試合前と試合後の寸法が一致した。「翌日へ」を押すと3月28日（土）に切り替わり、保存トークンと保存内容は変化しなかった。日次処理中の停止予約と不要になったホームの項目は表示されなかった。[画面・実タップ観測](https://github.com/thrsth45h/baseball-sim/actions/runs/45397324650/artifacts/14922550673)
- セ・パ両方の球団を操作して、自リーグの順位表が左、他リーグが右にあることを確認した。パ・リーグで通常の確定順が6番目の楽天を選んだ際も表示は最上段だった。同じカードを押すと先行5試合を確定し、対象試合を0球で開いた。標準幅と幅320dp・文字1.5倍で勝率 `1.000` の全桁をComposeのテキスト配置で検査した。[画面・球団カードの観測](https://github.com/thrsth45h/baseball-sim/actions/runs/45397324650/artifacts/14922550673)
- 単体テストはapp49件・core38件が成功した。失敗・エラー・スキップは0件。Lintはエラー0件、既存警告8件。日次処理の途中確定を表示へ出さず、完了時に成績と一緒に反映することを、途中結果を注入するテストで確認した。[単体テストとlint](https://github.com/thrsth45h/baseball-sim/actions/runs/45397310607/artifacts/14921957173)
- 保存形式v1・v2・v3と年度移行済みv3で、強制終了後に同じチェックポイントを復旧し、次の1球が一致した。新しいアプリプロセスは保存済み日程位置から表示する。[プロセス復旧](https://github.com/thrsth45h/baseball-sim/actions/runs/45397324650/artifacts/14922420939)
- 2年目の720試合を完走し、3年目へ移行した。初日表示は2年目が2027年3月26日（金）、3年目が2028年3月26日（日）だった。年度別の球団24行・選手288行・3年分の各試合seed 2,160件・2年目の保存フレームが独立参照と一致した。[年度移行の回帰](https://github.com/thrsth45h/baseball-sim/actions/runs/45397324669/artifacts/14922962326)
- 通常の端末試験66件が失敗・エラー・スキップ0件で成功した。v1の720試合完走フレームは0.8と全バイトが一致した。全108人の画面観測は球団名以外の全項目が一致した。[通常の端末試験](https://github.com/thrsth45h/baseball-sim/actions/runs/45397310607/artifacts/14924215530)
- 打者タイプ付きv3で720試合を完走した。完走後の保存フレームは0.8の出力とバイト単位で一致し、選手144人の画面観測は球団名以外の全項目が従来と一致した。[v3の通年回帰](https://github.com/thrsth45h/baseball-sim/actions/runs/45397324669/artifacts/14924050007)

## 環境と署名

物理端末、最低対応API26、電源断は未検証。配布APKの証明書SHA-256は `c088147a6108cf146f737656713dab67a3515a65145eb031f2092c680f40a929`。0.8とは署名が異なるため上書きできない。端末内の旧セーブを残す場合は旧版を残す。[0.8の検証記録](DAY_FLOW_VALIDATION.md)・[今回の署名](https://github.com/thrsth45h/baseball-sim/actions/runs/45397324650/artifacts/14921438070)

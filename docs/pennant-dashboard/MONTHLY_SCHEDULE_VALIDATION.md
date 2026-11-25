# 月間日程表の検証

[設計PR #65](https://github.com/thrsth45h/baseball-sim/pull/65)・[実装PR #66](https://github.com/thrsth45h/baseball-sim/pull/66)

- 配布版 `0.11-monthly-schedule-tech`（versionCode 11）は11,075,848バイト。ビルド元 `5ae62c5af77af5e7d82d686be4c075b1caf10f11`、ソースツリー `7cfb0d413490335ad8c5679317588f3c8b98710e`、SHA-256 `6e8d430e84827393ce158bb80e7d0e42cb4f42d7ac9dec2fd6879e9d1e15c13c`。画面検査にインストールしたAPKと一致し、マニフェストにINTERNET権限はない。[ビルド証拠](https://github.com/thrsth45h/baseball-sim/actions/runs/45638208108/artifacts/15033532512)・[APK配布](https://github.com/thrsth45h/baseball-sim/releases/tag/android-v0.11-monthly-schedule-tech)
- 同じAPKでAPI35の画面試験13件が成功した。seed 42で3月27日のマスに「中日○ 5–3」、押して開いた最初のカードに「中日ドラゴンズ − 読売ジャイアンツ」「3 − 5」とイニング別得点を確認した。日付選択で6カード、3月26日の休養日、12月の日程外表示を確認した。月移動とタブ往復、画面再作成で4月1日の選択を維持し、保存リビジョンは13のままだった。[操作ログ、画面、観測記録](https://github.com/thrsth45h/baseball-sim/actions/runs/45638208108/artifacts/15034931320)
- 幅320dp・文字1.5倍で横スクロールなしの7列と実タップを確認した。日付の「27」、球団と勝敗の「中○」、得点「5–3」が欠けずに表示された。[画面試験](https://github.com/thrsth45h/baseball-sim/actions/runs/45638208108/artifacts/15034931320)
- 単体試験はapp 52件、core 38件が成功し、失敗・エラー・スキップはすべて0件。日曜始まり、月末の扱い、うるう年と翌年、勝ち・負け・引き分け、ホームとビジターでの得点順を確認した。Lintはエラー0件、既存警告8件。[単体試験とlint](https://github.com/thrsth45h/baseball-sim/actions/runs/45638208122/artifacts/15034397564)
- 年度試験では2年目の720試合と3年目への移行が成功した。試合seed 2160件、球団24行、選手288行、2年目の保存が独立参照と一致した。[年度移行](https://github.com/thrsth45h/baseball-sim/actions/runs/45638186073/artifacts/15035731543)
- 通常端末試験68件とv3通年試験が成功した。v1・v3の720試合後の保存と108人・144人分の選手表示は0.10と一致した。[通常端末試験](https://github.com/thrsth45h/baseball-sim/actions/runs/45638186073/artifacts/15035697521)・[v3通年試験](https://github.com/thrsth45h/baseball-sim/actions/runs/45638208122/artifacts/15036080720)
- 強制終了後の復旧はv1・v2・v3・年度移行済みv3の4ケースが成功した。保存時と次球のチェックポイントは0.10と一致した。物理端末は未検証。証明書SHA-256は `ef4306f2973898dd60cdbd21565296156204a237590763420e84c73810aa639d`。0.10とは署名が異なるため上書きインストールはできない。[復旧記録](https://github.com/thrsth45h/baseball-sim/actions/runs/45638208108/artifacts/15035001686)

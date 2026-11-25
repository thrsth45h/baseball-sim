# 休養日のカードと一体型順位表の検証

[設計PR #63](https://github.com/thrsth45h/baseball-sim/pull/63)・[実装PR #64](https://github.com/thrsth45h/baseball-sim/pull/64)

- 配布版 0.10-fixed-home-tech（versionCode 10）は11,043,080バイト。ビルド元 b464817b09d242e7e13b799585ef5e6a7b1ae675、SHA-256 208dbfa7ce728728321adc2a60bbcf18084fb8471ea4f5173297d33e05b55b7f。試験でインストールしたAPKと一致し、マニフェストにINTERNET権限はない。[配布元](https://github.com/thrsth45h/baseball-sim/actions/runs/45407947679/artifacts/14926574466)・[リリース](https://github.com/thrsth45h/baseball-sim/releases/tag/android-v0.10-fixed-home-tech)
- 配布APKの画面操作11件が成功。実タップで休養日・試合前・試合後の6枠がそれぞれ996×131px、順位表の開始位置が1119pxで一致した。休養日の表示は1件で、試合のタップ操作はなかった。[画面・観測記録](https://github.com/thrsth45h/baseball-sim/actions/runs/45407947679/artifacts/14927332471)
- 両リーグの順位が同じ表に並び、バーの表示は「順位表」「差」の2項目。通常幅と320dp・文字1.5倍で横スクロールがなく、左右の球団名と勝率1.000の全桁が収まったことを確認した。[画面・試験ログ](https://github.com/thrsth45h/baseball-sim/actions/runs/45407947679/artifacts/14927332471)
- 通常端末試験66件と単体試験87件が成功した。v1・v3の通年保存と108人・144人分の表示データは0.9と一致した。年次試験では2年目の720試合、3年目への移行、独立参照の試合seed 2160件と成績が一致した。PR側v3は試験成功後にエミュレータが切断され、同じコミットのpush側で成果物を回収した。[端末試験](https://github.com/thrsth45h/baseball-sim/actions/runs/45407947611/artifacts/14927409958)・[単体試験](https://github.com/thrsth45h/baseball-sim/actions/runs/45407947611/artifacts/14926453936)・[v3](https://github.com/thrsth45h/baseball-sim/actions/runs/45407935435/artifacts/14928556354)・[年度移行](https://github.com/thrsth45h/baseball-sim/actions/runs/45407947611/artifacts/14929176873)
- 強制終了後の復旧4ケースでチェックポイントと次の投球が0.9と一致した。物理端末は未検証。証明書SHA-256は541feca68df8840cd4a9283dee7a5e4955e12fe004213e78e06cea0f555a2ce2。0.9と署名が異なるため、上書きインストールはできない。[復旧記録](https://github.com/thrsth45h/baseball-sim/actions/runs/45407947679/artifacts/14927387275)

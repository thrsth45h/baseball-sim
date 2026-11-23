# ペナントのホームと下部ナビゲーションの検証

- 実装は [PR #58](https://github.com/thrsth45h/baseball-sim/pull/58)。APKとCIのソースは `699a838ccd0d491bb5d3c28c1f2864ac928ca701`、ツリーは `c7b05af9ef1d3556f90a8bbc7ef63fb5f6be8a54`。
- 配布APKは `0.7-dashboard-tech`（versionCode 7）、11,026,696バイト。SHA-256 `e876d21fe8a698cd1d57381760a8dbada9db2d1d1eb2aa485271f0f0a70984fe`。下記のエミュレータで復旧を確認したAPKを配布する。[CI配布元](https://github.com/thrsth45h/baseball-sim/actions/runs/45199315901/artifacts/14831066793)

## 検証結果

- 単体テストはapp43件・core38件が成功。失敗・エラー・スキップは0件。Lintはエラー0件・既存警告8件。旧保存用fixtureがテストAPKに保持されることも確認した。[試験XMLとlint](https://github.com/thrsth45h/baseball-sim/actions/runs/45199315886/artifacts/14831226910)
- 全11ワークフローが成功した。API35のタブ操作の先行試験5件と通常の端末試験64件に失敗・エラー・スキップはない。幅320dp・文字サイズ1.5倍で、試合前・進行中・終了、確定スコア、自球団の強調、固定タブ、選手詳細への遷移と復元を確認した。この表示条件では一部の長い球団名は2行になる。[通常の端末試験・画面](https://github.com/thrsth45h/baseball-sim/actions/runs/45199315886/artifacts/14832569163)
- v1・v3の各720試合と年度移行後の720試合の最終保存は、旧版と全バイト一致した。2年目完走後は「成績」から2年分の履歴を開き、手動セーブ・ロードと3年目開始を確認できた。独立参照と照合した1〜3年目の試合seed 2,160件、2年目720試合の得点、履歴の球団24行と選手288行も一致した。[v3の完走試験](https://github.com/thrsth45h/baseball-sim/actions/runs/45199315886/artifacts/14832488745)・[年度移行の試験](https://github.com/thrsth45h/baseball-sim/actions/runs/45199315886/artifacts/14833041186)
- 試合エンジン、Roomスキーマ、セーブ処理は前版と同じ。コアの検証資料35ファイルを前版と比較し、唯一の差分は計測時間とヒープ使用量だった。[コア監査](https://github.com/thrsth45h/baseball-sim/actions/runs/45199315886/artifacts/14831216542)
- API35の隔離エミュレータでv1・v2・v3・年度移行済みv3を実プロセス強制終了した。各ケースで同じ保存から再開し、次の1球が一致した。PIDは順に4168→4342、4423→4500、4588→4660、4731→4819。配布元のメタデータでRoom schema 1と2の検証用ハッシュを照合した。[プロセス復旧記録](https://github.com/thrsth45h/baseball-sim/actions/runs/45199315901/artifacts/14830942028)

## タブ切替の修正

最初の端末試験で、別のセーブを開いた直後などにタブが切り替わらないケースがあった。原因調査ではComposeのタップと別経路のAndroidタップの両方でクリック処理まで到達した。ホームを表示し続けた状態では、クリック処理の参照先が以前のセーブ世代に留まることを確認した。クリック時点の最新状態に結び付けるよう修正し、通常のタップでタブ選択を照合する試験を追加した。[修正前の調査ログ](https://github.com/thrsth45h/baseball-sim/actions/runs/45199082096/artifacts/14829933306)

## 検証環境と署名

画面・復旧の実行環境はAPI35の隔離エミュレータ。物理端末と最低対応API26では未検証。配布APKの証明書SHA-256は `851141549cb4fa9e22b2563f9be2fc803429f290ff41bac22859aa23a33de823`。0.6配布APKと署名が異なるため、実機の既存インストールには上書きできない。

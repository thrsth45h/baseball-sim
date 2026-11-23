# 年度別成績の検証

- 承認範囲は [設計 PR #55](https://github.com/thrsth45h/baseball-sim/pull/55) のD063。実装は [PR #56](https://github.com/thrsth45h/baseball-sim/pull/56)。
- 配布APKとその復旧試験のソースは `82057445b9ccbb9cb0095bbc39041ff4832effa2`、ツリーは `c6c336cbf78882d449a28f82967720d198c98e5b`。その後、旧保存を開いた後のRoomバージョンを照合する端末テスト2件の期待値を修正した。`9e62d0eb9453b6315cd071aa507a770113d168f3` の差分はAndroid端末テストの2ファイルのみで、配布APKのソースとビルド設定は同じ。
- APKは `0.6-season-history-tech`（versionCode 6）、10,990,713バイト。SHA-256は `a46a936bc3eb2ae2ab343c8d8e2c1d804333840b33e1e8ef8fa79d7f4fafa6f3`。同じAPKで実プロセスの復旧に成功した。manifestにINTERNET権限はない。[CI配布元](https://github.com/thrsth45h/baseball-sim/actions/runs/45010531552/artifacts/14749069035)

## 検証結果

- Kotlin単体試験はcore 38件、app 43件で失敗・エラー・スキップは0件。Lintはエラー0件、警告8件。独立に組み立てた旧v1/v2/v3の保存fixtureがテストAPKにそのまま入ることをバイト照合した。v3の720試合・head・手動スロットには旧版で実際に完走したフレームを使用した。[試験XMLとlint](https://github.com/thrsth45h/baseball-sim/actions/runs/45010531591/artifacts/14749961748)
- 期待値修正後の `9e62d0e` で全11ワークフローが成功した。通常のAPI35端末試験63件に失敗・エラー・スキップはなく、修正した2件を含む。2年目720試合の保存フレームは修正前と全バイト一致し、2年分の履歴集計を独立参照と再照合した。[通常の端末試験](https://github.com/thrsth45h/baseball-sim/actions/runs/45012287190/artifacts/14751380670)・[年度移行の再試験](https://github.com/thrsth45h/baseball-sim/actions/runs/45012287190/artifacts/14751231707)
- CIが出力したRoom schema 2を追跡ファイルと照合し、schema 1の原本を維持した。別のSQLite確認では移行後の全9テーブルの列・外部キーを現行compiler定義と照合し、旧v3の720試合行・head・手動スロットに移行前後の差分がなかった。
- core監査出力の旧版との比較では、35ファイル中34ファイルが全バイト一致した。残る1ファイルの差分は実行時間・ヒープ値の9項目で、試合・シーズンの値は一致した。2回の監査出力もCIの反復ジョブで照合した。[core監査](https://github.com/thrsth45h/baseball-sim/actions/runs/45010531591/artifacts/14749359059)・[反復](https://github.com/thrsth45h/baseball-sim/actions/runs/45010531654/artifacts/14749981679)
- API35で旧v3の完走済みfixtureから年度移行し、確認での取り消しと同じトークンへの連打を検証した。実際に2年目720試合を進めて保存し、3年目を開始して2年分の年度別成績を閲覧、手動セーブ・ロードを通した。2年目の進行は734,730ms、フレームは1,838,838バイト、SHA-256は `dcdf95966e74efa8a440b6de55911ca98c7fb058474ed3ee3a32aca5492dac7c`。1年目の実進行は旧版で検証済みで、この試験ではその保存済み結果を使用した。[年度移行の端末試験](https://github.com/thrsth45h/baseball-sim/actions/runs/45010531591/artifacts/14749349299)
- 責任者が実装前に固定した別実装で、1〜3年目の試合seed 2,160個、2年目の720試合の得点とフレームの集計、2年分の球団24行と選手288行、3年目初戦のseedと出場者を照合した。年度一覧と年度詳細の画面を確認した。履歴画面からの閲覧で保存トークンが変わらないことも確認した。
- Roomの端末試験で、途中失敗と応答消失、同じ年度の再送、打順を変えた分岐、別世界からのロード、改ざんされた途中の履歴参照・集計・手動スロットを確認した。100年分の合成履歴を保存し、年度指定の取得と当年の保存サイズを確認した。
- 既存v1/v3の720試合の完走フレームも旧版と全バイト一致した。v1は1,585,652バイトでSHA-256 `0bde5d0de901771b13e75adc64aab9d21a5854c8f7aed9e34aafb72a3881bbde`、v3は1,838,493バイトで `d255bd19f2a9de71439b82334968d96723bccd79cfe222afc512f09c67e3fc86`。[v3回帰の端末試験](https://github.com/thrsth45h/baseball-sim/actions/runs/45010531591/artifacts/14749729958)
- 4ケースでホストから同一インストール・DBのプロセスを強制終了し、新しいプロセスで同じ中断位置と次の1球を照合した。PIDはv1が4321→4416、v2が4489→4569、v3が4639→4720、年度移行後のv3が4790→4879。年度移行ケースでは2年目の試合と前年への参照も照合した。[プロセス復旧記録](https://github.com/thrsth45h/baseball-sim/actions/runs/45010531552/artifacts/14749208620)
- 過去の性能比較はD061の依存条件とD063のRoom変更で適用外とした。比較は未実行で、速度の合否は付けていない。[適用判定](https://github.com/thrsth45h/baseball-sim/actions/runs/45010531580/artifacts/14749816642)

## 範囲と限界

実行環境はAPI35の隔離エミュレータ。物理端末、API26での実行、端末の電源断、100年分の実プレイは未検証。既存データ形式のRoom移行と、過去に配布したAPKへの上書きは別である。今回のdebug証明書SHA-256は `7a9d10564bfa9a7f0f33bc1f8b3e42715e56e255e4c8b3adfd8f73b80196c20d`。前回v0.5の証明書と異なるため、過去のインストールへの上書き更新には使えない。署名を継続する方法と機種変更時のデータ移行は今後の判断とする。

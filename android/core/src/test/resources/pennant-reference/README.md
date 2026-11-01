# Phase5a 実装前の独立参照

2026-11-01。PR41の本人承認を親から受領し、具体的な日程/主催/ID契約は親の確認後に生成した。技術本文2186928、承認記録反映8b2f23fを区別して出典にした。新season実装、worker実装、親のprivate検算は読んでいない。既存coreは4ファイルのGit blob hashを読むだけで実行していない。GitHub投稿・コミット・既存worktreeへの書込みはない。

## 契約と正式ファイル

規約は[CONTRACT.md](CONTRACT.md)。生成物の正本は`frozen/`だけで、`regenerated/`は別生成による比較用。

| ファイル | 内容 |
|---|---|
| frozen/schedule.json | 720試合、ID・正規ordinal・主催・week/day/series |
| frozen/calendar.json | 140日、試合120日/Mon休養20日 |
| frozen/roster.json | 12球団・固定108選手ID |
| frozen/seeds_master_0.json | master0の720raw割当 |
| frozen/seeds_master_42.json | master42の720raw割当 |
| frozen/seeds_master_2026.json | master2026の720raw割当 |
| frozen/small_ledger.json | 手入力12結果、6件/12件時点の手計算順位、重複/衝突例 |
| frozen/player_attribution.json | 3例54打順入力→全108人、手計算合計 |
| frozen/provenance.json | 設計2時点と旧core4sourceのSHA、generator/context/契約SHA |
| frozen/checks.json | 件数・独立経路照合・不変条件の確認 |
| frozen/SHA256_MANIFEST.json | 上記10JSONのbytes/SHA256 |

10JSONの合計は498,605bytes。unsigned64はすべて十進文字列で、JSONのsigned Longや浮動小数へ暗黙変換しない。初版S1の明示形式であり、将来の年数上限を決めない。

## 独立性と検算

日程は、circleの回転配列によるseries-first計算と、非固定5要素の剰余式によるcalendar-first計算の全720行を照合した。各球団120/home60/away60、相手ごと24/home12/away12、同日重複なし、同日6試合、ID一意を検査した。

seedは任意長整数と64bit maskのmix計算、別の32bit limb乗算のmix計算を全2,160値で比較。1試合1nextRawであり、draw100や棄却を使わない。各masterの最終stateもmaster+720*gamma mod2^64と照合した。親のJDK検算値は事前に読んでいない。

小台帳のscoreは手入力で、**master42の実GameSessionがこの12結果になるという期待値ではない**。seed欄は該当日程の属性対応を確認するためのもの。入力は裁定済み合成要約として扱い、公式記録や合法な全投球履歴の証明をしない。各選手の5countsには既存EventCountsの包含・非負条件を満たす値を置いたが、core実行済みsnapshotとは称さない。

順位の6件/12件時点は別の手入力表と一致。12結果後は8決着/4引分、全チーム合計24試合・8勝/8敗/8分・得失点各36。同score/同チーム合計でも四球帰属の変化は衝突。重複時は集計だけでなくcursor/activeも不変という期待を置いた。

帰属の疎な3例は第1試合、第91試合の主催反転、第4試合の別league。これは90試合を飛ばした有効なシーズン進行列ではなく、18ローカル打順を恒久IDへ射影する計算単位の参照。別の代数式による108期待行と集計ループが一致し、手入力した4球団合計とも一致。全体5counts合計は順に`3600,945,216,270,92`、未出場72選手はゼロ行で保持する。

## 再生成と凍結

このtaskディレクトリをcwdとして、Python標準libraryのみで実行する。

```powershell
$env:PYTHONDONTWRITEBYTECODE = '1'
python .\generate_reference.py --repo ..\..\work\pennant-basic-design --output .\another-regeneration
```

旧source/設計は指定commitのGit blobを読む。将来の新seasonコードはimportも読取りもしない。`--output`はこのtask内の未作成子dirだけを許可し、既存のfrozen/既存資料を上書きしない。provenanceにPython版を含めるので、全bytes一致には同版を使う（今回CPython3.12.14）。

`REGENERATION_CHECK.json`に別dir再生成11ファイルのbytes比較を記録し、`FREEZE_MANIFEST.json`にgenerator/契約/context/この説明/比較記録/正式11ファイルを封印する。凍結時刻はclock toolの観測値を別記するもので、暗号学的な時刻証明ではない。親が受領検査してからworkerへ配布する。期待値修正が必要なら別版とし、実装に合わせてfrozenを書き換えない。

Roomの保存/復旧・Android画面・720試合実core完走・実機性能・年次移行・公式成績の妥当性はこの参照では検証していない。

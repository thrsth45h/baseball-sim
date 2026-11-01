# Phase5a独立参照の固定契約

2026-11-01。PR41の承認済み範囲を具体化し、親が実装前に確認した規約。新season実装を一切読まずに作成する。NPB/MLB公式規約や製品の永久仕様を採用するものではない。

## ID・クラブ

seasonIdは`S1`。leagueは`L1`/`L2`、各番号1〜6。クラブIDは`L1-T01`等。表示名はL1の1〜6を仮球団1〜6、L2の1〜6を仮球団7〜12。固定打順slotは1〜9、選手IDは`{clubId}-P{slot:02}`（例`L2-T06-P09`）。IDは表示名や画面の配列indexから再作成しない。この初版のS1表記は将来の年数上限を定めない。

## circle法と主催

各leagueで配列`[1,2,3,4,5,6]`から開始。0番を固定し、残り5個を各round後に右へ1回転する（次配列は`[a0,a5,a1,a2,a3,a4]`）。base round r=0..4、pair i=0..2は`(a[i],a[5-i])`。r+iが偶数なら左がhome、奇数なら右がhome。matchupIndex=i+1であり、club番号による再sortをしない。

第1roundのaway→homeは、matchup1=`06→01`、2=`02→05`、3=`04→03`。両leagueで同じ番号配置を使用する。

round1〜5と、そのhome/awayを反転したround6〜10を1cycleにする。この10roundを4回繰り返し、全体seriesRoundは1〜40。各roundを同一主催の3連戦とする。主催反転のround6先頭は`01→06`。各クラブ120試合、home60/away60、相手ごと24試合、相手別home12/away12、全球団合計720試合。

## 20週の暦・順序

dayIndexは1始まり。第1週Monがday1、以後140日（20週）。各Monは休養日で試合なし。seriesRoundが奇数なら、その週のTue開始、偶数ならFri開始。week=`floor((seriesRound−1)/2)+1`、開始day=`7*(week−1)+(奇数なら2、偶数なら5)`、seriesGameは1〜3。第1シリーズday2/3/4、第2シリーズday5/6/7、最後はday138/139/140。

各試合日の正規順は**数値dayIndex→league L1/L2→matchupIndex1/2/3**。この順でscheduleOrdinalを1〜720とする。日付文字列やUIの並び替えを順序に使わない。

GameIdは`S1-D{dayIndex:03}-L{leagueNumber}-R{seriesRound:02}-M{matchupIndex}-G{seriesGame}`。第1試合は`S1-D002-L1-R01-M1-G1`、第2日の同カードは`S1-D003-L1-R01-M1-G2`。ID文字列のpaddingはこの初版の表示形式であり、数値比較の代わりではない。

## 試合seed

master seedは正規unsigned64十進文字列。master0/42/2026それぞれについて、state=masterから独立sm64-v1を作り、正規ordinal順に**nextRawを1回だけ**呼んで、その64bit出力を試合seedのunsigned十進文字列とする。1masterで720raw、draw100/上位32bit/棄却は使わない。永続stateへgammaを加算し、コピーzだけをmixする。mix出力をstateへ戻さない。

gamma=`9e3779b97f4a7c15`、xor logical shift30後multiply=`bf58476d1ce4e5b9`、xor shift27後multiply=`94d049bb133111eb`、xor shift31。加算・乗算はmod2^64。初期masterを先にmixしない。独立したPythonのmask計算と32bit limb乗算を全2,160出力で照合する。

試合seedは開幕前の割当であり、未来の試合結果を先に抽選するものではない。現在の注目クラブ・観戦/skip・復旧は割当器を進めない。sm64の製品永久固定や次年度の導出は決めない。

## 小さな手計算参照

`small_ledger.json`は最初の12試合（2試合日）の裁定済み合成要約。スコアは手入力、順位の6件/12件時点は別の手入力表と照合する。同率はcompetition rank、勝率未定義はrank/ratio=nullで定義済みの後にclub ID順。得失点差や勝数だけで同率を崩さない。

同じ結果の再受理は集計/cursor/activeを含め無変化。同score・同team totalsでも四球の選手帰属が違う要約は衝突。この例に新たなGameSessionの合法全投球履歴があると称さない。保存する全要約値の比較例である。

`player_attribution.json`は別の疎な集計参照。scheduleOrdinal1、91（同カードの主催反転）、4（別league）の各18打順を108人へ対応付ける。54入力行から108人の期待値を作り、未出場72人もゼロ行で残す。これを途中の90試合を飛ばした正当なシーズン進行列とは扱わない。

## 独立性と凍結

純Python標準libraryのみ。新season/worker実装・親private検算は未参照。既存Kotlin core4ファイルは出典のhash読取りだけで、import/実行しない。旧Python・研究/raw/model・既存fixture/resultsを変更しない。Room/Android UI/永続性の実装成功を示す資料でもない。

生成後に別の新規dirへ再生成し、全JSON/manifestをbytes比較する。正式な期待値は`frozen/`のみ。変更が必要な場合は別版を作り、新実装に合わせて凍結済み期待値を上書きしない。

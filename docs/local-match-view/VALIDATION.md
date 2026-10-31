# ローカル操作画面のworker検証

2026-10-31日本時間。基準main`527266dbb2826ce6c8d00c647c21e0049e2a8ed3`、[設計PR #37](https://github.com/thrsth45h/baseball-sim/pull/37)の範囲。workerはブラウザ/ネイティブUI・マウス/キーを操作せず、Python単体検査と実loopback HTTPを検証した。責任者の独立レビュー/画面検証とは区別する。

## 実行結果

Windows/Python3.12.14。すべてfailure/error/skip0。新suiteと旧6suiteは別コマンド・別記録で実行し、subTestや研究575を新methodへ足していない。

| suite | test method | 成功subTest（別集計） | 秒 |
|---|---:|---:|---:|
| match_view | 14 | 16 | 7.727 |
| phase4f | 23 | 13 | 1.394 |
| phase4e | 24 | 18,112 | 1.031 |
| phase4d | 26 | 320 | 0.521 |
| phase4c | 28 | 3,020 | 0.619 |
| phase4a | 33 | 326 | 0.486 |
| phase4b | 32 | 515 | 0.553 |

新suiteは実127.0.0.1 server/client接続を含み、旧6suiteは各実Windows junction4ケースを含む。通常sandboxのHTTP接続はWinError10013で制限されたため、必要な実行権限で検査を完了した。権限不足をskip成功にはしていない。旧suiteの過去出力・ignored検証領域も保持する。初版7suite記録はignored `data/prototypes/match-view/worker-check-01`、ヘッダ拒否試験の安定化後の新suite記録は`worker-check-02`へ保存した。旧コードは不変なので旧6suite記録は同じ実行結果を保つ。専用CIはPython3.11/3.12で7suiteを別step実行する。

## 検査した内容

- seed0/42/2026/2^64-1それぞれで、1球/1打席/最後まで/混在の4方式、計16ケースのGameResultとevents_documentが、既存GameSessionの直接実行に完全一致した。逐次呼出し以外の野球遷移・乱数・重みを新設していない。
- 初期Away1、打席完了直後の次打者、1回裏のHome1、2回表のAway4を確認。1打席は途中countからも次の完了までで止まる。state読取りを繰り返してもsource/RNGを消費しない。
- サヨナラ四球では確定scoreとlineを表示し、旧stateがawaiting_resolutionでもAPIのcurrentはnull、旧snapshot自体を現在状態として返さない。終端後の3操作は409で拒否し、状態/ログを保つ。投球上限のabortedは未完了で、引分/勝敗に変換しない。
- seedはJSON stringのASCII十進表現を厳密検査。2^64-1を丸めず新規開始でき、bool/数値/負数/空白/全角/指数/範囲外や余分不足のkeysを拒否。不正command/payloadは状態と所有RNGを変えない。返したstateの配列や辞書は独立している。
- 127.0.0.1とport0、固定4GETの許可、GETの状態/RNG不変を実HTTPで確認。任意file/path・dot/encoded traversal・二重スラッシュ・query付き操作pathは配信しない。
- Host/Origin不一致や欠落、token不一致/非ASCII・重複header、Content-Type違反・不正/重複/非有限/非UTF8 JSON・サイズ超過・不正Content-Length・Transfer-Encodingを拒否し、状態不変を確認。tokenは起動ごとに異なり、URLへ出さず、CORS許可headerも返さない。
- 2つの同時HTTP要求が1球・2球と逐次処理され、既存エンジンの2球結果に一致する。クライアント側は通信中にnewを含むbutton/inputを無効化する実装を静的確認した。
- HTMLの意味あるbutton/table/label・状態通知、focus-visible/狭幅用CSS、外部リソース不要、textContentによる描画、seed文字列送信、終端の現在欄hiddenを静的に検査した。

## 責任者のヘッドレス確認と未検証

責任者から、専用Chromeヘッドレス/new contextでの確認成功を受領した。可視ウィンドウ・物理入力・ユーザープロファイルを使わず、1280/390/320px幅で横はみ出しなし、1球→仮想Enterによる1打席→最後まででseed42の4:2/259球を確認。終了時は進行3ボタンが無効/currentが非表示、最大64bit seed文字列で新規試合2:6、不正seedの状態維持、seed0の9回裏`—`、JS pageerror0とPNG目視の正常を確認した。これは責任者の隔離ヘッドレス検証であり、workerの静的検査だけを表示合格としたものではない。

親の再実行ではヘッダ拒否caseに一度WinError10053が発生したが、8条件の分離実行は403/415で成功。HTTPクライアントがheaders/bodyを別送信し、即時拒否/closeとの競合で送信が中断する場合があるため、ヘッダだけの拒否試験を空本文POSTへ変更した。正規の不正JSON/payload/サイズ検査は別の試験で保持し、新14/16を全成功で再実行した。サーバの拒否方針や野球処理は変更していない。

**ネイティブPCでの可視ブラウザ・物理キー操作、スマートフォン実機、読み上げ挙動は未検証。** ユーザーからPCを操作しない指示があり、担当エージェントはユーザーの既存画面/マウス/キー/プロファイルを操作しない。ヘッドレスの仮想入力や画面幅検査を、スマホ実機やネイティブ入力の検証と同一視しない。

製品用HTTPサーバ・Android技術選定・公式成績/全野球ルール・能力較正・研究モデル接続・製品セーブは未採用。旧prototypes/phase4a–f・旧reports/CI・研究repoは変更しない。

## 再実行

```powershell
python -m unittest discover -s prototypes/match_view/tests -v
python -m unittest discover -s prototypes/phase4f/tests -v
python -m unittest discover -s prototypes/phase4e/tests -v
python -m unittest discover -s prototypes/phase4d/tests -v
python -m unittest discover -s prototypes/phase4c/tests -v
python -m unittest discover -s prototypes/phase4a/tests -v
python -m unittest discover -s prototypes/phase4b/tests -v
```

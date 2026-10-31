# Androidで動く1試合の技術検証版

Kotlin＋Jetpack Composeで、仮の2チーム各9人が1球ずつ進行するオフラインアプリ。PCサーバ・Python・通信を使わない。[設計PR #39](https://github.com/thrsth45h/baseball-sim/pull/39)の承認範囲で、現在は[実装Draft PR #40](https://github.com/thrsth45h/baseball-sim/pull/40)のCI/独立レビュー中。APKビルド・エミュレーターの結果は確定後にここへ記録する。

画面は「1球」「1打席」「最後まで」、seedを指定した新規試合、現在打者/B/S/O/塁、得点表と直近ログを表示する。停止後は旧snapshotの塁/カウントを隠し、確定scoreと終了理由を使う。9回同点引分は試作用の独自条件で、一般の全野球ルールや公式個人成績の裁定は行わない。

seedはASCII十進文字列0〜18446744073709551615。描画/画面回転で投球し直さず、処理中の連打は受理しない。ViewModelは画面再生成の間だけ試合と乱数を保持し、プロセス終了後はseed42の新規試合になる。今回のプロセス終了制約を将来の製品セーブ仕様として採用しない。

## APKの取得

1. 実装PRの対象headに対応するGitHub Actionsの`android-first-match`を開く。
2. `android-debug-対象headのSHA` artifactをダウンロードしZIPを展開する。
3. 同梱の`source-info.json`でsource head/tree・実JDK/SDK・APK SHA256を確認する。`app-debug.apk.sha256`にも同じAPKのhashを保存する。
4. インストールはユーザーの任意確認。こちらから接続済み端末へインストール/操作はしない。最低対応はAPI26（Android8.0）。実機の対応/表示/性能は未検証。

artifactの保持期間は14日。失効したものを永続ダウンロードリンクと扱わず、再ビルドが必要なら別runと実APKhashを記録する。debug署名だけの技術検証APKで、Play公開/製品配布/署名鍵管理は含めない。

## 検証の分け方

- **遷移互換**：Kotlin実装前に別担当が凍結した49ケース・2,600 advanceへ同じ入力を与え、各snapshotと最終結果を比較する。[凍結の受領](FIXTURE_RECEIPT.md)とtest resourceの説明を参照。
- **native生成**：sm64-v1を実際のToyPitchSourceに接続し、4seedの各入力/各snapshot/論理draw数・raw数・内部stateを凍結参照と比較する。別にraw/draw vectorと強制棄却境界を調べる。
- **UI/ライフサイクル**：ViewModel unit testsと、CI内の画面なしAPI35エミュレーターによるCompose操作/再生成/不正seed/狭幅/PNGを確認する。APKビルドの成功だけをこの成功と呼ばない。

sm64-v1は今回用で製品まで永久固定しない。nativeのseed42は旧Python Randomのseed42と同じ試合にならない。新seed42の期待例は仮Away7–3仮Home・239球で、旧Python例の4–2・259球とは乱数契約が異なる。重み35/15/15/15/20、fair65/22/7/1/5と一定進塁は未較正の仮設定で、MLBや能力1〜20の効果を測定したものではない。

## 開発環境を変更しない検証

ユーザーPCの可視画面/入力/既存プロファイル・環境を操作しない。SDK/IDE/Gradle/JDKをローカルへ導入せず、ビルドとエミュレーターは使い捨てCI runner内で行う。[固定toolchainと一次資料](TOOLCHAIN.md)を参照。

CIは純core検算用`android-core-audit-対象headのSHA`も14日保存する。展開したbundleに`classes/`と`lib/*.jar`があれば、**既存JDKだけ**で次のようにunit testsを再実行できる。SDK/Gradle不要の監査用で、製品の実行方法ではない。

```powershell
java -cp 'bundle/classes;bundle/lib/*' org.junit.runner.JUnitCore com.example.baseballsim.core.CoreTest
```

この初版へ12球団日程/ペナント・経営・交代・一般走塁・能力較正/研究採用・DB/製品セーブを追加しない。長期方針の詳細実装や次のペナント基本機能は別設計で扱う。

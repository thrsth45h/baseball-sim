# Android検証APKの配布

次回のAndroid検証版から、[GitHub Releases](https://github.com/thrsth45h/baseball-sim/releases) に `app-debug.apk` を添付する。利用者は各リリースの **Assets** にあるAPKを直接取得できる。ActionsのZIPは検証記録と配布元として使う。

## 公開手順

1. 実装PRの独立レビュー・CI検証・統合を終える。動作検証に使ったビルドのAPKとソースSHAを既存のGitHub Actions成果物から特定し、APKのSHA-256とオフライン構成を証拠に照合する。文書だけが後から変わった場合も、APKの出所には実際のビルドSHAを記載する。配布のための再ビルドで検証済みAPKを差し替えない。
2. バージョンが分かる固有のタグで、統合コミットを対象にプレリリースの下書きを作る。リリースノートに実装PR、APKのビルドSHA、検証記録、実機や上書き更新などの未検証事項を記載する。既存セーブの読取試験と、既にインストールされた旧APKへの上書き試験は別に記載する。試験していない場合は、セーブを保持したまま上書きできると案内しない。
3. 同じ `app-debug.apk` と `app-debug.apk.sha256` を添付してから公開する。公開後、APKの直接URLで取得でき、取得したファイルのSHA-256が配布元と一致することを確認する。署名鍵やCIの作業ファイルは添付しない。
4. 利用者へ対象バージョンとAPKの直接リンクを案内する。公開または取得の確認に失敗した場合は、完了扱いにせず状況を報告する。

リリースの作成と添付は [GitHub CLI](https://cli.github.com/manual/gh_release_create) で実施できる。次回配布までは [PR #53の取得手順](batter-profile/README.md) を参照する。

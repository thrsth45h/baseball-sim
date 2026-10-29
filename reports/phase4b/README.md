# Phase 4b — 塁・アウト・得点の限定参照

[承認設計PR #22](https://github.com/thrsth45h/baseball-sim/pull/22)、承認main `59ae0e2f726b88d5729d026a0e293916c33a73e6`の[設計v0.1](../../docs/PHASE4B_DESIGN.md)に基づく、Python標準ライブラリだけの検算用参照。2026-10-29の本人回答「設計PR #22の範囲で進める（推奨）」による限定承認で、D043のみ当該範囲をCONFIRMED、D029/D031/D032はOPEN。source/tests/fixtures/CI固定commitは`7f98536c8659d6fa6bd0bbe94ab6e48e8ee4fb3b`。

## 受ける事実と状態

入力は**プレーが完了し必要な裁定・正規触塁・全得点が確定した後**の合成3種だけ。追加進塁・失策・他アウト・妨害・アピール・サヨナラ条件がないことは、呼出し側が確定した前提であり、この試作が観測から推定するものではない。

| 略号 | 正確な入力 | 意味 |
|---|---|---|
| W | `walk_forced_only` | 追加移動なしの通常四球。打者一塁、押される走者だけ進む |
| X | `batter_out_runners_hold` | 打者のみアウト、他走者の最終塁据置き、得点なしと確定 |
| H | `legal_over_fence_home_run` | 合法なフェンス越えフェア本塁打と全正規触塁・得点が確定 |

Wは途中の四球情報だけでは足りず、Xを三振/飛球一般と同一視せず、Hへバウンド場外打球を補完しない。曖昧な`walk/out/strikeout/flyout/home_run/single`等、単打/走塁判断・併殺・同一プレーの第三アウトと得点の可否は拒否する。

不変な`HalfInningState(outs,bases,runs)`は、outsが厳密なintの0〜3、basesが一塁/二塁/三塁の厳密bool3要素tuple、runsが半回の既存得点を表す非負の厳密int。boolを数値として受けず、浮動小数へ変換しない。任意の外部開始局面を受け、過去履歴の合法性や3種だけでの到達可能性を証明しない。三アウト後は残塁と得点を保持して停止し、次半回へ自動リセットしない。

JSONはちょうどouts/bases/runs、basesはJSON boolean配列。`from_mapping`が配列からtupleへ明示変換する。`fixtures.parse_json`は重複キーとNaN/Infinityを拒否する。APIコンストラクタへ可変listを直接渡すこととは区別する。

`step(state,event)`は新状態を返す純粋関数。`replay(start_state,events)`は厳密なlist/tupleだけを受け、空列は停止状態にも許す。不正tailでは例外となり、元入力を変えず部分成功を返さない。result/traceも不変で、traceのevent/before/after/runs_addedには振り直す番号を付けない。全分割位置でtrace連結と最終状態が一括処理に一致する。

## Fixtureと検証

[静的72遷移表](../../prototypes/phase4b/fixtures/transitions.json)と[固定16列](../../prototypes/phase4b/fixtures/scenarios.json)は言語非依存JSON。設計のW8占有表とHの人数表から記載し、生産step/replayの出力を正解にしていない。別の「強制される最初の空き塁を埋める」期待式でも照合した。固定列はID/明示開始/正確なイベント順/最終状態を設計から別にpinし、mixed_halfでX/Hを入れ替えて同じ最終状態になる置換も拒否する。一般の合法なイベント順の拒否ではなく、固定試験入力のdrift防護である。

新4bは**32 test method成功**、failure/error/skip0。成功subTest515は別集計で32へ足さない。72遷移・16列/30イベント/46分割位置・三アウト後24拒否・実junction4ケースは検査範囲であり、method件数ではない。旧4aは変更せず、別集計で**33 method/326 subTest成功**。[新テスト記録](test-results.json)、[旧4a回帰記録](phase4a-regression.json)、[worker検証](VALIDATION.md)を参照。研究575テストを今回の件数へ加算していない。

正式[synthetic-01](synthetic-01/REPORT.md)とignoredのsynthetic-repeat-02は、plan.json/traces.json/REPORT.md/SHA256SUMS.jsonの4成果物すべてがバイト一致。[計画とsource identity](synthetic-01/plan.json)、[全trace](synthetic-01/traces.json)、[再現性](reproducibility.json)、[時点を明記した保護記録](verification.json)を保存する。合成例の得点や割合をリーグ得点率・能力効果・公式個人成績と解釈しない。worker自己検証と責任者の独立レビューは別工程である。

## 再実行

Python3.11または3.12、標準ライブラリだけ。ゲームrepo rootで、新しい出力先を選ぶ。

```powershell
python -m unittest discover -s prototypes/phase4b/tests -v
python -m unittest discover -s prototypes/phase4a/tests -v
python -m prototypes.phase4b run --output data/prototypes/phase4b/my-run-01
python -m prototypes.phase4b run --output data/prototypes/phase4b/my-repeat-02
```

Windowsの実junction試験は作成を許可する権限で実行する。Linux CIでは実directory symlinkを使う。作成失敗をskip成功にしない。テストの専用一時領域と過去の実行資料を削除しない。

CLIは固定fixtureのrunのみ。外部入力/raw-root/model/seed/resume/4a接続オプションはない。新出力は、このcheckoutのdata/prototypes/phase4bまたはreports/phase4bの**新しい子**だけ。既存先・namespace自体・旧領域・外部・入力重複を拒否し、許可namespaceの宣言位置をjunctionの転送先で再定義させない。出力作成前にsource/fixtureの同一性と全表/列の照合を終える。

source identityは必要shared package initializerと全新runtime/fixtureのUTF-8テキストをCRLF/CR→LFへ正規化してSHA256を記録し、Python版も保持する。主要4成果物に時刻/絶対パスを入れない。同じPython環境の2runの一致を検査し、Python3.11/3.12間で版metadataまで同一とは主張しない。公開の正式run/テスト記録はPython3.12.14である。

`verify.py`は新runを実行せず、2run、静的表に沿うtrace、固定sourceと現在checkoutの同一性、新旧テスト記録を照合する。旧ファイル保護は承認baseline `59ae0e2`→固定source `7f98536`の履歴差分で、README案内以外の64パスがその実装時点で不変だったことを検査する。固定source以後の旧ゲームファイル変更は`current_checkout_changes_since_source_commit`へ参考情報として別記し、後続文書更新を保護例外へ追加しない。現在も旧文書全て不変という主張ではない。現在の全prototypes/shared initializerと新旧CIは別途厳密に固定source照合する。研究repo/raw/モデルを開かず、既存の検証JSONを上書きしない。

次はこのcheckoutに保持した正式2runの検査例。別のPython版で作った新runを検査する場合は、その同じ版・固定sourceでのテスト記録を別ファイルへ用意する。

```powershell
New-Item -ItemType Directory -Path reports/phase4b/new-verification -ErrorAction Stop | Out-Null
python reports/phase4b/verify.py --first reports/phase4b/synthetic-01 --second data/prototypes/phase4b/synthetic-repeat-02 --tests reports/phase4b/test-results.json --regression reports/phase4b/phase4a-regression.json --output reports/phase4b/new-verification
```

## 未接続・未採用

`source_kind=synthetic_fixture`、`scope=partial_base_out_rules`、`probability_model=false`、`connected_to_phase4a=false`、`connected_to_statcast=false`、`connected_to_phase3d=false`、`official_stats_generated=false`、`ability_calibrated=false`、`android_integrated=false`、`product_adoption=false`。

4aのwalk_awarded等だけではW/X/Hの追加事実が足りないため、既存statusからの自動adapterは作っていない。旧4aの契約・コード・結果・CIと研究repoを変更しない。走者ID/打順・触塁履歴・アピール・自責点、完全な半回/試合生成、確率・能力・Android/DB/UI/セーブと製品採用は別設計とする。

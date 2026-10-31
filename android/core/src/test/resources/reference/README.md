# Android移植用の凍結参照

2026-11-01。承認済みAndroid技術検証版の固定入力互換・sm64-v1検証用。書込みはこの専用ディレクトリだけで行った。Kotlin/worker実装、親のprivate期待値、研究/raw/modelは未参照。workerへは直接送っていない。

これは**既存Pythonの情報契約を移植する参照**であり、野球ルールの別実装による数学的oracleではない。元のtoy確率・一定進塁・限定ルールの妥当性や製品採用を証明しない。Androidコード、APK、UIの成功も主張しない。

## 正式な参照ファイル

- `frozen/transition_cases.json.gz`：workerへ渡す候補の決定的gzip。展開内容は同名の元JSONと完全一致。gzipはmtime=0、filename空、圧縮level9。pure JVMのGZIPInputStreamで読める。元の約7.2MB JSONはprivate参照として保持し、両方をテストresourceへ複製する必要はない。
- `frozen/transition_cases.json`：49ケース・2,600 advanceの無損失元JSON。
- `frozen/sm64_vectors.json`：seed0/42/最大unsigned64の各20 raw出力と別途20 draw100、上位32bitの棄却境界・強制棄却例。
- `frozen/case_summary.json`：各ケースの件数・終了理由・得点・RNG最終カウンター。
- `frozen/provenance.json`：旧source固定commitと、実際にimportした旧source/package markerのLF正規化SHA256、生成Python版、generator hash。
- `frozen/SHA256_MANIFEST.json`：上記5ファイル（元/圧縮の両方を含む）のbytes/hash。
- `REGENERATION_CHECK.json`：別ディレクトリへ同じgeneratorで再生成し、6ファイルを直接bytes比較した記録。
- `FREEZE_MANIFEST.json`：generator・この説明・再生成記録・正式参照6ファイルの封印manifest。自己hashは含めない。

`regenerated/`は独立した2回目の生成物であり、正式テストresourceの追加候補ではない。旧リポジトリのsource/fixture/resultsは変更していない。

## fixture形式と再生

`cases`の各項目は`id`、文字列`seed`、`max_pitches`、`rng_kind`、`initial_snapshot`、`inputs`、`expected_after_each_advance`、`expected_result`を持つ。snapshot/resultは旧`GameSnapshot.as_dict`/`GameResult.as_dict`の情報で、旧capability flagsや実装identityは含まない。ネイティブRNGのケースには各advance後/最終のRNGカウンターも付く。

`inputs`の各値は旧`PitchInput.as_dict`または厳密に`{"abort_reason":"source_exhausted等"}`。`pitch_limit`はcontroller自身の停止なので入力markerを作らない。不正な元オブジェクトは保存せず、旧controllerが作るsanitized理由を保存する。したがって不正ケースはcanonical abortの状態保持を照合できるが、marker再生だけでAndroid自身の不正payload検出まで検証したとは扱わない。元の不正fair構築はgenerator内で確認でき、Androidの型/入力検査は別にテストする。

初期snapshotから開始し、停止まで**実際のadvance入口**を1回ずつ呼ぶ。各呼出し後を同じ添字の`expected_after_each_advance`と比較し、最後に`expected_result`を比較する。sourceを要求されたときだけ次の入力を消費する。advanceは第三アウト後の自動表裏切替を含み得る。入力不足・既使用capの場合には、球を受理しないadvanceもあるため、inputs数とadvance数を常に同じと仮定しない。capはsuffix球数ではなく`initial_snapshot.pitches_accepted`を含む総受理球数へ適用する。

provided初期snapshotは、旧controllerへ合法な合成prefixを実際に適用して作り、`history_origin=provided_snapshot`と明示した。過去の投球列や未観測の成績を捏造して補完していない。snapshotは製品セーブ形式ではない。

## ケースの範囲

- 旧Python Randomでseed0/1/2/42/2026の5完走試合を取得し、記録入力で全snapshot/結果を再生照合。
- 独立整数計算のsm64-v1を旧ToyPitchSourceの`randrange(100)`adapterとして接続したseed0/42/2026/最大値の4完走試合。旧sourceの重み・fair時追加抽選・進塁も通る。逐球入力/状態/各advance後RNGカウンターを凍結し、記録入力でも再生照合。
- 54 first-pitch outsの引分、打順持越し、全8投球イベント、2strike後の100 ordinary fouls。
- 通常四球の全8塁占有mask。1塁が空いている2/3塁走者の据置き、満塁だけの得点を明示検算。
- safehitの走者据置き、単打/二塁打の2得点、三塁打の3得点。
- サヨナラ四球、候補1/2/3塁打で最少1得点、必要2/3得点、空塁HRと満塁HR全4得点。old projectionを未解決のまま保持する終端差分を含む。
- 9回表での裏省略、9回裏のaway勝利、まだ勝越さない安打の通常解決。
- cap後のcount保持、cap球第三アウトでAdvance前停止、同cap球で通常/サヨナラ終了ならcompleted優先、既使用capではsource不要。
- 空sourceでBegin保持、有効prefix後の不足、missingfair/占有矛盾/塁衝突の未受理、unknown pitch、sanitized source error。

9完走試合の内訳は旧Python1,291 advance、sm64 1,033 advance。合成40ケース276 advanceと合わせ2,600。各ケースは旧JSONのround-tripと記録入力再生を検査済み。

## 乱数の別契約

sm64は永続stateへgammaを加算し、コピーzのみをmixする。mixed outputをstateへ戻さない。unsigned64はJSON十進文字列、upper32/draw100は非負整数。Pythonの任意長整数+mask計算と、32bit limbのlow64乗算計算を全raw出力で照合した。seed0の最初のrawは`e220a8397b1dcdaf`。

`u<4294967200`だけ採用し`u%100`を返す。u=4294967199は99、4294967200と4294967295は棄却。棄却96値と再試行時のraw消費数を固定例で確認する。実際の4完走sm64試合には棄却は発生していないため、その試合だけで棄却経路を検証したとは扱わない。

| RNG | seed | away:home | pitches | draw100/raw |
|---|---:|---:|---:|---:|
| sm64-v1 | 0 | 3:3 | 276 | 330/330 |
| sm64-v1 | 42 | 7:3 | 239 | 300/300 |
| sm64-v1 | 2026 | 0:5 | 243 | 293/293 |
| sm64-v1 | 18446744073709551615 | 2:9 | 275 | 326/326 |

旧Python Randomのseed42は4:2/259球/316 logical draws。新sm64の同seedと結果一致を要求しない。今回版の算術契約であり、製品RNGの永久固定ではない。

## 再生成

Python標準libraryのみ。taskディレクトリをcwdにし、旧sourceが保存されたgame checkoutを指定する。generatorは旧固定core`a5fda901bc0a8cca9ae0eefc1d076fd8171c73a6`のGit blobとの一致を検査し、出力先はこのtask配下の未作成ディレクトリだけ受け付ける。既存freezeを上書きしない。`PYTHONDONTWRITEBYTECODE`と`sys.dont_write_bytecode`で旧repoのpycache生成を抑止する。

```powershell
$env:PYTHONDONTWRITEBYTECODE = '1'
python .\generate_reference.py --repo ..\..\work\android-first-match-design --output .\another-regeneration
```

`provenance.json`にはPython版を含めるため、完全byte一致を再確認する際は同じPython版を使用する。別版での差を数学的結果差と混同しない。元JSON SHA256は`10a3e8fa701c53b34c7de0a4fc1e2d4b8dd22a97acbcad760cc469ec0b7c63f8`、gzip SHA256は`09ceb0d6253dd22344e681a30c1b61888ca5e8054a2c80f84533b151b35d071b`。freeze内容を変える場合は別版として記録し、期待値へ新実装を追随させない。

#!/usr/bin/env bash
set -euo pipefail

# Disposable GitHub emulator only. This is not a production speed setting.
report="app/build/pennant-store-benchmark"
mkdir -p "$report"
collect_on_exit() {
    local status=$?
    adb logcat -d > "$report/logcat.txt" || true
    # Preserve an interrupted run's already-published observations. On success the
    # normal pull below owns this directory; never nest another duplicate copy.
    if [[ ! -d "$report/observations" ]]; then
        adb pull /sdcard/Android/media/com.example.baseballsim/additional_test_output "$report/observations" || true
    fi
    exit "$status"
}
trap collect_on_exit EXIT
adb wait-for-device
adb shell getprop ro.build.version.sdk | tr -d '\r' > "$report/device-api.txt"
adb shell getprop ro.build.fingerprint | tr -d '\r' > "$report/device-fingerprint.txt"
adb shell df -k /data > "$report/device-data-filesystem.txt"
test "$(cat "$report/device-api.txt")" = 35
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r \
  -e class 'com.example.baseballsim.data.RoomSeasonBenchmarkTest#pairedOperationsUseTheSameInputAndPublishEveryObservation' \
  -e additionalTestOutputDir /sdcard/Android/media/com.example.baseballsim/additional_test_output \
  com.example.baseballsim.test/androidx.test.runner.AndroidJUnitRunner | tee "$report/instrumentation.txt"
grep -F 'OK (1 test)' "$report/instrumentation.txt"
if grep -E 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed' "$report/instrumentation.txt"; then exit 1; fi
adb pull /sdcard/Android/media/com.example.baseballsim/additional_test_output "$report/observations"
python3 - <<'PY'
from pathlib import Path
import json
from statistics import median
p=Path('app/build/pennant-store-benchmark')
raw=list((p/'observations').rglob('pennant-store-abba.json'))
summaries=list((p/'observations').rglob('pennant-store-abba-summary.json'))
assert len(raw)==len(summaries)==1
record=json.loads(raw[0].read_text()); summary=json.loads(summaries[0].read_text())
samples=record['samples']
assert len(samples)==22*14, len(samples)
assert len([r for r in samples if not r['warmup']])==22*12
for prefix in (0,100,300,600,719,720):
    operations=('cold_db_read','warm_read') if prefix==720 else ('cold_db_read','warm_read','marker','result')
    for operation in operations:
        rows=[r for r in samples if r['prefix']==prefix and r['operation']==operation]
        assert len(rows)==14
        assert len({r['input_sha256'] for r in rows})==1
        assert len({r['input_frame_sha256'] for r in rows})==1
        assert len({r['candidate_frame_sha256'] for r in rows})==1
        measured=[r for r in rows if not r['warmup']]
        assert [r['variant'] for r in measured]==list('ABBA')*3
        assert all(r['phase_sum_ns']<=r['elapsed_ns'] for r in measured)
        for row in rows:
            assert row['phase_sum_ns']==sum(row['phases_ns'].values())
            expected_phases={'candidate_encode','candidate_check','before_read','transaction','after_commit'} if operation in ('marker','result') else set()
            assert set(row['phases_ns'])==expected_phases
            assert all(v>=0 for v in row['phases_ns'].values())
assert len(summary['comparisons'])==10
recomputed=[]
for prefix in (0,100,300,600,719):
    for operation in ('marker','result'):
        def values(variant):
            return [r['elapsed_ns'] for r in samples if r['prefix']==prefix and r['operation']==operation and r['variant']==variant and not r['warmup']]
        a=values('A');b=values('B');baseline=float(median(a));candidate=float(median(b))
        limit=0.8 if prefix in (300,600) else 1.1
        recomputed.append({'prefix':prefix,'operation':operation,
            'baseline_median_ns':baseline,'candidate_median_ns':candidate,
            'baseline_min_ns':min(a),'baseline_max_ns':max(a),
            'candidate_min_ns':min(b),'candidate_max_ns':max(b),
            'candidate_over_baseline':candidate/baseline,'gate_limit':limit,
            'passes_engineering_gate':candidate/baseline<=limit})
assert summary['comparisons']==recomputed
assert summary['all_engineering_gates_pass']==all(r['passes_engineering_gate'] for r in recomputed)
manifest={'schema':'pennant-store-benchmark-host-verification-v1',
          'same_emulator_same_process':True,'same_prepared_inputs_per_operation':True,
          'independent_database_per_sample':True,'all_samples':len(samples),
          'measured_samples':264,'warmup_samples':44,
          'abba_three_blocks_per_operation':True,
          'summary_recomputed_from_all_raw_observations':True,
          'generation_evidence':'generation.json',
          'performance_gate_passed':summary['all_engineering_gates_pass'],
          'gate_failure_does_not_invalidate_raw_measurement':True,
          'distribution_apk_timing_instrumented':False,
          'actual_ui_720_transaction_run':False}
(p/'verification.json').write_text(json.dumps(manifest,indent=2)+'\n')
print(json.dumps(manifest,indent=2))
PY

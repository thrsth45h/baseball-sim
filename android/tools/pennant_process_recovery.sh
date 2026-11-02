#!/usr/bin/env bash
set -euo pipefail

# Runs only inside the disposable CI emulator. Do not clear app data or reinstall
# between preparation and recovery: those would invalidate the process-death test.
report="app/build/pennant-process-proof"
mkdir -p "$report"
trap 'adb logcat -d > "$report/logcat.txt" || true' EXIT
adb wait-for-device
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

run_case() {
    local method="$1"
    local logfile="$2"
    adb shell am instrument -w -r \
      -e class "com.example.baseballsim.PennantProcessRecoveryTest#$method" \
      -e additionalTestOutputDir /sdcard/Android/media/com.example.baseballsim/additional_test_output \
      com.example.baseballsim.test/androidx.test.runner.AndroidJUnitRunner | tee "$logfile"
    grep -F 'OK (1 test)' "$logfile"
    if grep -E 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed' "$logfile"; then return 1; fi
}

run_case prepareDurableCheckpointBeforeHostKill "$report/prepare.txt"
adb shell pidof com.example.baseballsim | tr -d '\r' > "$report/before-pid.txt"
test -s "$report/before-pid.txt"
adb exec-out run-as com.example.baseballsim cat files/pennant-process-expected.json > "$report/expected.json"

adb shell am force-stop com.example.baseballsim
sleep 1
adb shell pidof com.example.baseballsim | tr -d '\r' > "$report/after-force-stop-pid.txt" || true
test ! -s "$report/after-force-stop-pid.txt"

run_case recoverTheSamePublishedCheckpointAndAdvanceExactlyOnce "$report/recover.txt"
adb shell pidof com.example.baseballsim | tr -d '\r' > "$report/recovered-pid.txt"
test -s "$report/recovered-pid.txt"
adb exec-out run-as com.example.baseballsim cat files/pennant-process-proof.json > "$report/recovery.json"
python3 - <<'PY'
from pathlib import Path
import json
p=Path('app/build/pennant-process-proof')
before=(p/'before-pid.txt').read_text().strip()
after=(p/'recovered-pid.txt').read_text().strip()
expected=json.loads((p/'expected.json').read_text())
proof=json.loads((p/'recovery.json').read_text())
assert before and after and before!=after
assert (p/'after-force-stop-pid.txt').read_text().strip()==''
assert str(expected['pid'])==before
assert proof['previous_pid']==expected['pid'] and str(proof['new_pid'])==after
assert proof['same_world_generation_and_checkpoint'] is True
assert proof['one_additional_pitch_only'] is True
assert proof['saved_checkpoint_sha256']==expected['checkpoint_sha256']
assert proof['next_checkpoint_sha256']!=expected['checkpoint_sha256']
record={'schema':'pennant-process-recovery-v1','same_install_and_database':True,
        'force_stop_observed_process_absent':True,'new_process_id':after,'old_process_id':before,
        'same_committed_checkpoint_recovered':True,'exactly_one_next_pitch':True,
        'power_loss_or_physical_device_tested':False}
with (p/'verification.json').open('x') as f:json.dump(record,f,indent=2);f.write('\n')
PY
adb pull /sdcard/Android/media/com.example.baseballsim/additional_test_output "$report/screenshots"

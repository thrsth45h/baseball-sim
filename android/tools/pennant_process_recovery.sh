#!/usr/bin/env bash
set -euo pipefail

# Runs only inside the disposable CI emulator. Do not clear app data or reinstall
# between preparation and recovery: those would invalidate the process-death test.
report="app/build/pennant-process-proof"
mkdir -p "$report"
trap 'adb logcat -d > "$report/logcat.txt" || true' EXIT
adb wait-for-device
adb shell getprop ro.build.version.sdk | tr -d '\r' > "$report/device-api.txt"
adb shell df -k /data > "$report/device-data-filesystem.txt"
test "$(cat "$report/device-api.txt")" = 35
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

run_case() {
    local method="$1"
    local logfile="$2"
    local world_format="${3:-v1}"
    adb shell am instrument -w -r \
      -e world_format "$world_format" \
      -e class "com.example.baseballsim.PennantProcessRecoveryTest#$method" \
      -e additionalTestOutputDir /sdcard/Android/media/com.example.baseballsim/additional_test_output \
      com.example.baseballsim.test/androidx.test.runner.AndroidJUnitRunner | tee "$logfile"
    grep -F 'OK (1 test)' "$logfile"
    if grep -E 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed' "$logfile"; then return 1; fi
}

# Separate short diagnostic. Prefix setup is explicitly a canonical fixture,
# never reported as the full Activity/Room 720-transaction acceptance result.
run_case profileRealStoreCostsAtCanonicalPrefixBoundaries "$report/store-profile.txt"
adb pull /sdcard/Android/media/com.example.baseballsim/additional_test_output "$report/profile-output"

# Keep one installation and database throughout the four host-death experiments.
base_report="$report"
for format in v1 v2 v3 annual; do
    report="$base_report/$format"
    mkdir -p "$report"
    world_format="$format"
    adb shell getprop ro.build.version.sdk | tr -d '\r' > "$report/device-api.txt"
# Remove only this test's own stale probe files. App/database state is untouched.
adb shell run-as com.example.baseballsim rm -f files/pennant-process-expected.json files/pennant-process-proof.json
adb shell am instrument -w -r \
  -e class 'com.example.baseballsim.PennantProcessRecoveryTest#prepareDurableCheckpointBeforeHostKill' \
  -e world_format "$world_format" \
  -e additionalTestOutputDir /sdcard/Android/media/com.example.baseballsim/additional_test_output \
  com.example.baseballsim.test/androidx.test.runner.AndroidJUnitRunner > "$report/prepare.txt" 2>&1 &
prepare_adb_pid=$!
ready=false
for attempt in $(seq 1 180); do
    if adb exec-out run-as com.example.baseballsim cat files/pennant-process-expected.json > "$report/expected-candidate.json" 2>/dev/null; then
        if python3 -c 'import json,sys; assert json.load(open(sys.argv[1]))["prepare_probe_waiting_for_host"] is True' "$report/expected-candidate.json"; then
            mv "$report/expected-candidate.json" "$report/expected.json"
            ready=true
            break
        fi
    fi
    sleep 1
done
test "$ready" = true
kill -0 "$prepare_adb_pid"
adb shell pidof com.example.baseballsim | tr -d '\r' > "$report/before-pid.txt"
test -s "$report/before-pid.txt"
python3 -c 'import json,sys; from pathlib import Path; p=Path(sys.argv[1]); assert str(json.loads((p/"expected.json").read_text())["pid"])==(p/"before-pid.txt").read_text().strip()' "$report"

adb shell am force-stop com.example.baseballsim
wait "$prepare_adb_pid" || true
sleep 1
adb shell pidof com.example.baseballsim | tr -d '\r' > "$report/after-force-stop-pid.txt" || true
test ! -s "$report/after-force-stop-pid.txt"

run_case recoverTheSamePublishedCheckpointAndAdvanceExactlyOnce "$report/recover.txt" "$world_format"
adb shell pidof com.example.baseballsim | tr -d '\r' > "$report/recovered-pid.txt" || true
adb exec-out run-as com.example.baseballsim cat files/pennant-process-proof.json > "$report/recovery.json"
RECOVERY_REPORT="$report" python3 - <<'PY'
from pathlib import Path
import json
p=Path(__import__('os').environ['RECOVERY_REPORT'])
before=(p/'before-pid.txt').read_text().strip()
expected=json.loads((p/'expected.json').read_text())
proof=json.loads((p/'recovery.json').read_text())
after=str(proof['new_pid'])
surviving=(p/'recovered-pid.txt').read_text().strip()
assert before and after and before!=after
assert (p/'after-force-stop-pid.txt').read_text().strip()==''
assert str(expected['pid'])==before
assert proof['previous_pid']==expected['pid'] and str(proof['new_pid'])==after
assert not surviving or after in surviving.split()
assert proof['same_world_generation_and_checkpoint'] is True
assert proof['one_additional_pitch_only'] is True
assert proof['saved_checkpoint_sha256']==expected['checkpoint_sha256']
assert proof['next_checkpoint_sha256']!=expected['checkpoint_sha256']
record={'schema':'pennant-process-recovery-v1','same_install_and_database':True,'lineup_world':expected['lineup_world'],'world_format':expected['world_format'],'profiles_world':expected['profiles_world'],
        'device_api':int((p/'device-api.txt').read_text().strip()),
        'requested_ci_data_partition':'2048M',
        'prepare_instrumentation_intentionally_terminated':True,
        'force_stop_observed_process_absent':True,'new_process_id':after,'old_process_id':before,
        'recovery_process_observed_alive_after_junit':bool(surviving),
        'same_committed_checkpoint_recovered':True,'exactly_one_next_pitch':True,
        'power_loss_or_physical_device_tested':False}
if expected['world_format']=='annual':
    assert proof['annual_year']==expected['annual_year']==2
    assert proof['previous_archive']==expected['previous_archive']
    record.update(annual_year=proof['annual_year'], root_seed=expected['root_seed'], previous_archive=proof['previous_archive'])
with (p/'verification.json').open('x') as f:json.dump(record,f,indent=2);f.write('\n')
PY
adb pull /sdcard/Android/media/com.example.baseballsim/additional_test_output "$report/screenshots"

done
report="$base_report"
# Legacy flat files retain their original interpretation; v2 evidence is separately named.
cp "$report/v1/expected.json" "$report/v1/recovery.json" "$report/v1/before-pid.txt" "$report/v1/after-force-stop-pid.txt" "$report/v1/recovered-pid.txt" "$report/"
python3 - <<'PY'
from pathlib import Path
import json
p=Path('app/build/pennant-process-proof')
old=json.loads((p/'v1/verification.json').read_text())
modern=json.loads((p/'v2/verification.json').read_text())
profile=json.loads((p/'v3/verification.json').read_text())
annual=json.loads((p/'annual/verification.json').read_text())
assert old['lineup_world'] is False and modern['lineup_world'] is True
assert all(v['same_install_and_database'] and v['same_committed_checkpoint_recovered'] and v['exactly_one_next_pitch'] for v in (old,modern,profile,annual))
assert profile['world_format']=='v3' and profile['profiles_world'] is True
old['formats']={'v1':old.copy(),'v2':modern,'v3':profile}
old['all_three_formats_verified']=True
old['both_formats_verified']=True
old['annual_v3_after_rollover']=annual
(p/'verification.json').write_text(json.dumps(old,indent=2)+'\n')
PY

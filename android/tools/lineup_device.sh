#!/usr/bin/env bash
set -euo pipefail
# This script runs only inside a disposable CI emulator, never on a user's device.
report=app/build/lineup-device
mkdir -p "$report"
trap 'adb logcat -d > "$report/logcat.txt" || true' EXIT
git rev-parse HEAD > "$report/SOURCE_HEAD.txt"
adb shell getprop ro.build.version.sdk | tr -d '\r' > "$report/device-api.txt"
test "$(cat "$report/device-api.txt")" = 35
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r \
  -e class com.example.baseballsim.PennantLineupFullSeasonTest \
  -e additionalTestOutputDir /sdcard/Android/media/com.example.baseballsim/additional_test_output \
  com.example.baseballsim.test/androidx.test.runner.AndroidJUnitRunner | tee "$report/instrumentation.txt"
grep -F 'OK (1 test)' "$report/instrumentation.txt"
if grep -E 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed' "$report/instrumentation.txt"; then exit 1; fi
adb pull /sdcard/Android/media/com.example.baseballsim/additional_test_output "$report/observations"

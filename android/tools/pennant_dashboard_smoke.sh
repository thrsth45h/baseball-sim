#!/usr/bin/env bash
set -euo pipefail

# This job runs in an isolated GitHub emulator. Fail early on a tap or route
# regression before the ordinary 720-game suite, preserving the full suite below.
report=app/build/dashboard-navigation-smoke
device_output=/sdcard/Android/media/com.example.baseballsim/dashboard_navigation_smoke
mkdir -p "$report"
trap 'adb pull "$device_output" "$report/screens" || true' EXIT

(cd app/build/outputs/apk/debug && sha256sum app-debug.apk) > "$report/installed-apk.sha256"
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

prefix=com.example.baseballsim
classes=(
    "$prefix.MatchActivityTest"
    "$prefix.PennantActivityTest#androidBackReturnsDetailToListAndDismissesTheConfirmationBeforeLeavingSettings"
    "$prefix.PennantActivityTest#calendarStandingsAndAllClubsRecordsAreReachable"
    "$prefix.PennantActivityTest#manualSaveLoadIsOutsideMatchesAndChangesTheDurableGeneration"
    "$prefix.PennantRestorationTest"
    "$prefix.PennantDashboardTest"
    "$prefix.PennantDayFlowActivityTest"
    "$prefix.PennantBrowseStoreTest"
)
class_filter=$(IFS=,; echo "${classes[*]}")
adb shell am instrument -w -r -e class "$class_filter" \
    -e additionalTestOutputDir "$device_output" \
    com.example.baseballsim.test/androidx.test.runner.AndroidJUnitRunner | tee "$report/instrumentation.txt"
grep -F 'OK (11 tests)' "$report/instrumentation.txt"

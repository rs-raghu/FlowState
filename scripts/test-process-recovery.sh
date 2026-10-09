#!/usr/bin/env bash
set -euo pipefail

# Gradle can uninstall the APKs during connected-test teardown. Reinstall both for these phases.
report_dir="app/build/reports/process-recovery"
mkdir -p "$report_dir"
runner="dev.flowstate.test/androidx.test.runner.AndroidJUnitRunner"
test_class="dev.flowstate.ProcessRecoveryTest"

assert_pass() {
  if command -v rg >/dev/null 2>&1; then
    rg -q '^OK \(1 test\)' "$1"
  else
    grep -qE '^OK \(1 test\)' "$1"
  fi
}

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

adb shell am instrument -w -r -e class "$test_class" -e recoveryPhase prepare "$runner" |
  tee "$report_dir/prepare.txt"
assert_pass "$report_dir/prepare.txt"

adb shell am force-stop dev.flowstate
adb shell am start -W -n dev.flowstate/dev.flowstate.MainActivity |
  tee "$report_dir/reopen.txt"

adb shell am instrument -w -r -e class "$test_class" -e recoveryPhase verify "$runner" |
  tee "$report_dir/verify.txt"
assert_pass "$report_dir/verify.txt"

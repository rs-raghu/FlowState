#!/usr/bin/env bash
set -euo pipefail

# connectedDebugAndroidTest installs the application and instrumentation APK first.
report_dir="app/build/reports/process-recovery"
mkdir -p "$report_dir"
runner="dev.flowstate.test/androidx.test.runner.AndroidJUnitRunner"
test_class="dev.flowstate.ProcessRecoveryTest"

adb shell am instrument -w -r -e class "$test_class" -e recoveryPhase prepare "$runner" |
  tee "$report_dir/prepare.txt"
rg -q '^OK \(1 test\)' "$report_dir/prepare.txt"

adb shell am force-stop dev.flowstate
adb shell am start -W -n dev.flowstate/dev.flowstate.MainActivity |
  tee "$report_dir/reopen.txt"

adb shell am instrument -w -r -e class "$test_class" -e recoveryPhase verify "$runner" |
  tee "$report_dir/verify.txt"
rg -q '^OK \(1 test\)' "$report_dir/verify.txt"

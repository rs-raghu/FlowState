#!/usr/bin/env bash
set -euo pipefail
mkdir -p app/build/reports/device
trap 'adb logcat -d > app/build/reports/device/logcat.txt || true' EXIT
api=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
bash ./gradlew :app:connectedDebugAndroidTest --max-workers=2 --console=plain
bash ./scripts/test-process-recovery.sh
# Revocation can kill the application, so apply it outside instrumentation.
if [ "$api" -ge 33 ]; then
  adb shell pm revoke dev.flowstate android.permission.POST_NOTIFICATIONS
else
  adb shell cmd appops set --uid dev.flowstate POST_NOTIFICATION ignore
  adb shell cmd appops set dev.flowstate POST_NOTIFICATION ignore
fi
adb shell am instrument -w -r -e class dev.flowstate.PermissionRecoveryTest -e permissionPhase verify dev.flowstate.test/androidx.test.runner.AndroidJUnitRunner | tee app/build/reports/device/permission-recovery.txt
grep -qE '^OK \(1 test\)' app/build/reports/device/permission-recovery.txt
# Install the optimized application and prove that Hilt/Room/serialization survive R8.
bash ./gradlew :app:assembleRelease -Pflowstate.releaseSmoke=true --max-workers=2 --console=plain
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am force-stop dev.flowstate
adb logcat -c
adb shell am start -W -n dev.flowstate/.MainActivity > app/build/reports/device/release-launch.txt
sleep 3
adb shell dumpsys activity activities > app/build/reports/device/release-activity.txt
if ! grep -q 'dev.flowstate/.MainActivity' app/build/reports/device/release-activity.txt; then
  echo 'Optimized application did not remain running'; exit 1
fi
if adb logcat -d -s AndroidRuntime | grep -q 'FATAL EXCEPTION'; then
  echo 'Optimized application crashed'; exit 1
fi

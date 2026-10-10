#!/usr/bin/env bash
set -euo pipefail
mkdir -p app/build/reports/device
trap 'adb logcat -d > app/build/reports/device/logcat.txt || true' EXIT
bash ./gradlew :app:connectedDebugAndroidTest --max-workers=2 --console=plain
bash ./scripts/test-process-recovery.sh
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

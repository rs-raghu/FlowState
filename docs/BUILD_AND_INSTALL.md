# Build and installation

Build debug with `gradlew.bat :app:assembleDebug` on Windows or `./gradlew :app:assembleDebug` elsewhere. Expected output: `app/build/outputs/apk/debug/app-debug.apk`. The file's existence and final verification results are recorded in TESTING.md; do not infer installation from a build.

Install on Android 10 or later with `adb install -r app/build/outputs/apk/debug/app-debug.apk`, or copy the APK to the phone, open it and allow installation from that source. Launch FlowState. Configure notifications and optional precise/background location/exact alarm access in Settings. Google Play Services is required for automatic geofences, but manual/time execution works without it.

`gradlew.bat :app:assembleRelease` produces an unsigned release APK with R8 enabled when no signing key is configured. A release signing key was not supplied. To build a signed release, create a private signing key outside source control and set `FLOWSTATE_STORE_FILE`, `FLOWSTATE_STORE_PASSWORD`, `FLOWSTATE_KEY_ALIAS`, and `FLOWSTATE_KEY_PASSWORD` in the build environment before running the task. Verify the resulting APK using `apksigner verify`. Never commit credentials. Keep the same signing key for future updates. Debug and release signatures differ; exporting a backup before changing signatures avoids losing personal configuration during reinstall.

No local Android device/emulator is connected. GitHub-hosted API 29/36 emulators install/launch the app and pass nine native database/UI/interaction tests. Physical geofence movement, reboot delivery, power restrictions and signed-release launch remain release gates.

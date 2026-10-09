# Build and installation

Build debug with `gradlew.bat :app:assembleDebug` on Windows or `./gradlew :app:assembleDebug` elsewhere. Expected output: `app/build/outputs/apk/debug/app-debug.apk`. The file's existence and final verification results are recorded in TESTING.md; do not infer installation from a build.

Install on Android 10 or later with `adb install -r app/build/outputs/apk/debug/app-debug.apk`, or copy the APK to the phone, open it and allow installation from that source. Launch FlowState. Configure notifications and optional precise/background location/exact alarm access in Settings. Google Play Services is required for automatic geofences, but manual/time execution works without it.

`gradlew.bat :app:assembleRelease` produces an unsigned release APK with R8 enabled. A release signing key was not supplied; do not install or claim an unsigned artifact as a signed release. Create a private signing key outside source control and configure a local signing configuration through environment variables/untracked properties, then sign with Android build tools and verify using `apksigner verify`. Keep the same signing key for future updates. Debug and release signatures differ; exporting a backup before changing signatures avoids losing personal configuration during reinstall.

No Android device/emulator is connected in the current environment. Installation, launch, notification taps, geofence movement, reboot and power-restriction procedures have not been executed. They remain release gates.

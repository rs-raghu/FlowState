# Build and installation

Use JDK 23, Node 24, SDK platform `platforms;android-37.0` and build tools 37.0.0. Set `sdk.dir` in untracked local.properties or ANDROID_HOME. The checked-in wrapper provisions Gradle 9.6.0 with an official checksum. `scripts/verify.ps1` rebuilds/tests the editor, core, debug APK, lint, native compilation and unsigned optimized release.

Build debug using `gradlew.bat :app:assembleDebug` (Windows) or `./gradlew :app:assembleDebug` (Unix). Install with `adb install -r app/build/outputs/apk/debug/app-debug.apk`, or copy/open the APK on Android 10+ and allow installation from that source. Launch FlowState. Grant only the permissions used by your workflows. Automatic geofences need Google Play Services, precise/background access and device location; manual/time workflows do not need geofencing services.

`gradlew.bat :app:assembleRelease` enables R8 and produces `app/build/outputs/apk/release/app-release-unsigned.apk` without supplied credentials. For a personal signed release, keep a private key outside source control and supply FLOWSTATE_STORE_FILE, FLOWSTATE_STORE_PASSWORD, FLOWSTATE_KEY_ALIAS and FLOWSTATE_KEY_PASSWORD to the build environment. Never commit these values. Keep the same key for updates and verify with apksigner.

The PowerShell argument `'-Pflowstate.releaseSmoke=true'` signs an optimized build with the existing debug test key for CI/local install-and-launch verification only, producing `app-release.apk`. Quote it in PowerShell to preserve the dotted property name. It can update a debug APK signed by that same key; hosted and local debug keys may differ. APKs signed by a different key require a reinstall, so export a backup first. BUILD_ARTIFACTS.md identifies the exact local files/hashes/certificate.

CI installs/tests APIs 29/36/37.0, executes separate external process/permission phases and installs/launches the optimized test build. API 37 emulator graphics workarounds belong to the test harness. Real movement/OEM/reboot/battery checks use PHONE_ACCEPTANCE.md.

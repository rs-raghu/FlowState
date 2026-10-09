# Verified build artifacts

Verified locally on 2026-10-09. Executable source in the built working tree is committed as `6adfdbf5e1fad350857e3b82bc17478bbeabb2f0`; assembly occurred before that commit, so embedded release VCS metadata can refer to its predecessor. Documentation changes after assembly do not change executable code. APK byte hashes below identify these exact local artifacts; a CI build uses its own debug key and may have a different byte hash.

## Debug APK

Exact path:

`D:\Hello World\2. Automations\FlowState\app\build\outputs\apk\debug\app-debug.apk`

Size: **36,151,567 bytes**.

SHA-256:

`DF9A0FC4CDFDA711FDD66968BB1C17AAB8C44C01D56BE845E94B8784BD03F99D`

`apksigner verify --verbose --print-certs` succeeded: one RSA-2048 Android Debug signer, APK Signature Scheme v2 valid. Certificate SHA-256: `cce89bfb98d8c5b6b35274514a9d49c1b7e6fcbd61a96aae2ddabaf04341e398`. This verifies the package signature; it is not an installation/launch result.

`aapt dump badging` confirms package `dev.flowstate`, versionCode 1/versionName 0.1.0, label FlowState, minSdk 29, target/compile SDK 36, launchable `dev.flowstate.MainActivity`. Permissions include location, notifications, exact scheduling, boot and library WorkManager permissions; **INTERNET is absent**. App operational receivers are non-exported, automatic backup and cleartext are disabled.

ZIP inspection confirms local editor HTML (1,446 bytes), bundle (731,383 bytes), Blockly license (11,358 bytes) and country map (838,726 bytes). The packaged bundle matches the generated source SHA-256 `99037F0298ECA9173F7833C07BCEF54E499B957C3875144BFD42F71FB2C18ABE`. Blockly media is copied locally and uses a relative media URL.

## Unsigned release APK

Exact path:

`D:\Hello World\2. Automations\FlowState\app\build\outputs\apk\release\app-release-unsigned.apk`

Size: **4,442,495 bytes**.

SHA-256:

`018C744D73EC51DA9B6146D189CE510FEF93E2982B3CCDC2046218AFF156A732`

R8 release assembly succeeded. No signing credentials were supplied; this artifact is **unsigned** and requires signing before installation. Optional environment signing is documented in BUILD_AND_INSTALL.md. Release merged manifest also has no Internet permission and excludes the debug Compose preview activity. Release launch/minification behavior has not been tested on Android.

## Build/test evidence

Last full local Gradle invocation succeeded in 2m 8s: `:engine:test :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin :app:assembleRelease`, 118 actionable tasks, with two workers. 36 JVM tests pass, four frontend tests pass, native database tests compile without being executed, and lint has zero errors/25 warnings. Kotlin formatting dry-run/fail-if-changed passes with ktfmt 0.64. See TESTING.md for exact coverage and acceptance procedures.

Local reports:

- `D:\Hello World\2. Automations\FlowState\engine\build\reports\tests\test\index.html`
- `D:\Hello World\2. Automations\FlowState\app\build\reports\lint-results-debug.html`
- `D:\Hello World\2. Automations\FlowState\engine\build\test-results\test\`

GitHub Actions run [37961304993](https://github.com/rs-raghu/FlowState/actions/runs/37961304993) for commit `00a9f31` completed **successfully**, including editor tests, native tests/debug/lint/instrumentation compilation/release and debug APK upload. Earlier runs failed at SDK setup (exit 127); explicit SDK provisioning fixed that run. Last observed code run [37962090927](https://github.com/rs-raghu/FlowState/actions/runs/37962090927) for `864a860` completed **successfully**, with one debug APK artifact (observed on the GitHub run page). The later suspended-caller correction (6adfdbf) is verified locally with 36 tests; its hosted rerun is not yet reported as passed.

No connected device/AVD is available. Install, launch, instrumentation/UI, process death/reboot, notification taps, geofence movement and battery/permission acceptance are **not executed**. Full specification acceptance remains open. APKs and local SDK/cache files are ignored by Git; source, tests, documentation and CI are pushed to the requested repository.

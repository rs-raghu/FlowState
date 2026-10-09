# Verified build artifacts

Verified locally on 2026-10-09. Executable source includes the bundled native examples stage built on `dd18944`; assembly preceded its commit, so embedded release VCS metadata may refer to that predecessor. Documentation changes after assembly do not change executable code. APK byte hashes below identify these exact local artifacts; a CI build uses its own debug key and may have a different byte hash.

## Debug APK

Exact path:

`D:\Hello World\2. Automations\FlowState\app\build\outputs\apk\debug\app-debug.apk`

Size: **36,151,645 bytes**.

SHA-256:

`CAA753323DA091688C23865A82A471C07543D1D53FD091BEE2A150D436943686`

`apksigner verify --verbose --print-certs` succeeded: one RSA-2048 Android Debug signer, APK Signature Scheme v2 valid. Certificate SHA-256: `cce89bfb98d8c5b6b35274514a9d49c1b7e6fcbd61a96aae2ddabaf04341e398`. This verifies the package signature; it is not an installation/launch result.

`aapt dump badging` confirms package `dev.flowstate`, versionCode 1/versionName 0.1.0, label FlowState, minSdk 29, target/compile SDK 36, launchable `dev.flowstate.MainActivity`. Permissions include location, notifications, exact scheduling, boot and library WorkManager permissions; **INTERNET is absent**. App operational receivers are non-exported, automatic backup and cleartext are disabled.

ZIP inspection confirms local editor HTML (1,446 bytes), bundle (731,388 bytes), Blockly license (11,358 bytes) and country map (838,726 bytes). The packaged bundle matches the generated source SHA-256 `5D3A67FF054570B34470C3397233581E7FCF743C1E9BB8774B305AA30D3DEB18`. Blockly media is copied locally and uses a relative media URL.

## Unsigned release APK

Exact path:

`D:\Hello World\2. Automations\FlowState\app\build\outputs\apk\release\app-release-unsigned.apk`

Size: **4,462,845 bytes**.

SHA-256:

`CBE6ACF53EEA26ECF0D3266E6D52FDA54C5CBD49D030A4F6604E90B6BEB3E3C8`

R8 release assembly succeeded. No signing credentials were supplied; this artifact is **unsigned** and requires signing before installation. Optional environment signing is documented in BUILD_AND_INSTALL.md. Release merged manifest also has no Internet permission and excludes the debug Compose preview activity. Release launch/minification behavior has not been tested on Android.

## Build/test evidence

Last full local Gradle invocation succeeded in 1m 51s: `:engine:test :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin :app:assembleRelease`, 118 actionable tasks, with two workers. 49 JVM tests pass, four frontend tests pass, ten native tests compile; nine then-current tests passed on API 29/36 in run 37967271781, and lint has zero errors/25 warnings. Kotlin formatting dry-run/fail-if-changed passes with ktfmt 0.64. See TESTING.md for exact coverage and acceptance procedures.

Local reports:

- `D:\Hello World\2. Automations\FlowState\engine\build\reports\tests\test\index.html`
- `D:\Hello World\2. Automations\FlowState\app\build\reports\lint-results-debug.html`
- `D:\Hello World\2. Automations\FlowState\engine\build\test-results\test\`

GitHub Actions run [37967271781](https://github.com/rs-raghu/FlowState/actions/runs/37967271781) for `dd18944` executes the native build and API 29/36 emulator jobs. Both device jobs passed all nine then-current tests. Initial run 37965241810 exposed two defects now repaired and verified. Earlier build-only runs (including c201487) completed successfully after SDK provisioning was corrected. Each emulator job retains test reports, and the build job uploads the debug APK.

No local device/AVD is available. Remote API 29/36 instrumentation installs and launches the debug app; the repaired nine-test run 37967271781 passed on both images; the tenth bundled-example case awaits execution. Physical process-death/reboot, geofence movement and battery/permission acceptance remain open. Full specification acceptance remains open. APKs and local SDK/cache files are ignored by Git; source, tests, documentation and CI are pushed to the requested repository.

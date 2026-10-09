# Verified build artifacts

Verified locally on 2026-10-09. Executable source includes the parallel/JOIN and explicit-null recovery stage built on `f09a6b1`; assembly preceded its commit, so embedded release VCS metadata may refer to that predecessor. Documentation changes after assembly do not change executable code. APK byte hashes below identify these exact local artifacts; a CI build uses its own debug key and may have a different byte hash.

## Debug APK

Exact path:

`D:\Hello World\2. Automations\FlowState\app\build\outputs\apk\debug\app-debug.apk`

Size: **36,151,579 bytes**.

SHA-256:

`9A69D24E8C39257C3FBBB51757EE282D5212543C673DC870A4BCFD8B6918E7AB`

`apksigner verify --verbose --print-certs` succeeded: one RSA-2048 Android Debug signer, APK Signature Scheme v2 valid. Certificate SHA-256: `cce89bfb98d8c5b6b35274514a9d49c1b7e6fcbd61a96aae2ddabaf04341e398`. This verifies the package signature; it is not an installation/launch result.

`aapt dump badging` confirms package `dev.flowstate`, versionCode 1/versionName 0.1.0, label FlowState, minSdk 29, target/compile SDK 36, launchable `dev.flowstate.MainActivity`. Permissions include location, notifications, exact scheduling, boot and library WorkManager permissions; **INTERNET is absent**. App operational receivers are non-exported, automatic backup and cleartext are disabled.

ZIP inspection confirms local editor HTML (1,446 bytes), bundle (731,388 bytes), Blockly license (11,358 bytes) and country map (838,726 bytes). The packaged bundle matches the generated source SHA-256 `5D3A67FF054570B34470C3397233581E7FCF743C1E9BB8774B305AA30D3DEB18`. Blockly media is copied locally and uses a relative media URL.

## Unsigned release APK

Exact path:

`D:\Hello World\2. Automations\FlowState\app\build\outputs\apk\release\app-release-unsigned.apk`

Size: **4,458,887 bytes**.

SHA-256:

`35AEE77DCD24B8D2B676D26C480FDD3C75B7C6570BA7D84266031197684A3D4E`

R8 release assembly succeeded. No signing credentials were supplied; this artifact is **unsigned** and requires signing before installation. Optional environment signing is documented in BUILD_AND_INSTALL.md. Release merged manifest also has no Internet permission and excludes the debug Compose preview activity. Release launch/minification behavior has not been tested on Android.

## Build/test evidence

Last full local Gradle invocation succeeded in 1m 51s: `:engine:test :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin :app:assembleRelease`, 118 actionable tasks, with two workers. 49 JVM tests pass, four frontend tests pass, nine native tests compile; initial remote instrumentation exposed two repaired defects, and lint has zero errors/25 warnings. Kotlin formatting dry-run/fail-if-changed passes with ktfmt 0.64. See TESTING.md for exact coverage and acceptance procedures.

Local reports:

- `D:\Hello World\2. Automations\FlowState\engine\build\reports\tests\test\index.html`
- `D:\Hello World\2. Automations\FlowState\app\build\reports\lint-results-debug.html`
- `D:\Hello World\2. Automations\FlowState\engine\build\test-results\test\`

GitHub Actions run [37961304993](https://github.com/rs-raghu/FlowState/actions/runs/37961304993) for commit `00a9f31` completed **successfully**, including editor tests, native tests/debug/lint/instrumentation compilation/release and debug APK upload. Earlier runs failed at SDK setup (exit 127); explicit SDK provisioning fixed that run. Last observed code run [37962090927](https://github.com/rs-raghu/FlowState/actions/runs/37962090927) for `864a860` completed **successfully**, with one debug APK artifact (observed on the GitHub run page). The later suspended-caller correction (6adfdbf) is verified locally with 36 tests; its hosted rerun is not yet reported as passed.

No local device/AVD is available. Remote API 29/36 instrumentation installs and launches the debug app; the initial eight-test run exposed two repaired defects and the expanded nine-test run awaits execution. Physical process-death/reboot, geofence movement and battery/permission acceptance remain open. Full specification acceptance remains open. APKs and local SDK/cache files are ignored by Git; source, tests, documentation and CI are pushed to the requested repository.

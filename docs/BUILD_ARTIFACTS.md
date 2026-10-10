# Verified build artifacts

Verified locally on 2026-10-10 from application source committed as `09decf5`. Assembly preceded that commit, so embedded release VCS metadata can identify its predecessor. Documentation updates do not change executable code. These hashes identify the local APKs exactly; CI builds use a different debug key and can have different bytes.

| Artifact | Size in bytes | SHA-256 |
|---|---:|---|
| Debug `app/build/outputs/apk/debug/app-debug.apk` | 49,872,259 | `4C42B90212F8A4AE6693979AF2526E6B8D18A20AADF8479634D2A907F0264E1B` |
| Optimized test build `app/build/outputs/apk/release/app-release.apk` | 4,745,735 | `B37E334EBA15A8F276CB6A168F831FB1D61D1A4BC255B106AFAD29A03C92AC18` |

Both are installable and signed with the same local Android Debug RSA-2048 key. `apksigner verify --verbose --print-certs` succeeds with valid APK Signature Scheme v2. Certificate SHA-256: `cce89bfb98d8c5b6b35274514a9d49c1b7e6fcbd61a96aae2ddabaf04341e398`. Signature verification alone does not prove installation or execution.

Absolute optimized APK path: `D:\Hello World\2. Automations\FlowState\app\build\outputs\apk\release\app-release.apk`. This is the recommended local artifact for final phone checks because R8 optimization matches normal release behavior. It uses a test key, not a personal production signing key. The debug APK is in the corresponding `debug` directory. These APKs share an application ID/key and replace each other when installed with `adb install -r`; make a backup first if preserving phone data matters.

The optimized artifact was built using the quoted PowerShell argument `'-Pflowstate.releaseSmoke=true'`. Normal release builds require private environment signing credentials or produce `app-release-unsigned.apk`; see [build and install](BUILD_AND_INSTALL.md). Do not install a stale unsigned output left by an earlier build.

`aapt dump badging` confirms package `dev.flowstate`, versionCode 1/versionName 0.1.0, minSdk 29, target/compile SDK 37 and launchable `dev.flowstate.MainActivity`. INTERNET is absent. Automatic backup and cleartext are disabled; operational receivers are non-exported.

ZIP inspection verifies local Blockly license (11,358 bytes), country map (838,726 bytes) and editor bundle (734,965 bytes). The bundled editor matches source SHA-256 `0E1FEB9C1AD245C0A7B92E595279E26E46A74B0DDF0A80F9D7B52184933B3E2B`. Editor HTML, compatibility code and media are packaged locally.

## Verification evidence

The full local core/build command succeeded in 1m 33s. After renderer recovery, debug/lint/native compilation and optimized assembly succeeded in 1m 56s, with 118 actionable tasks:

```powershell
.\gradlew.bat :engine:test :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin :app:assembleRelease '-Pflowstate.releaseSmoke=true' --max-workers=2
```

74 JVM tests and six editor tests pass. Lint has zero errors and 33 warnings. Debug/native compilation and R8 optimized assembly succeed. Local reports are `engine/build/reports/tests/test/index.html` and `app/build/reports/lint-results-debug.html`.

Hosted run [38041344419](https://github.com/rs-raghu/FlowState/actions/runs/38041344419) passes build and the complete API 29 sequence, including normal instrumentation, external process and permission recovery, and R8 installation/launch. Earlier run [38035623654](https://github.com/rs-raghu/FlowState/actions/runs/38035623654) passes the complete API 36 sequence. Final run [38042188535](https://github.com/rs-raghu/FlowState/actions/runs/38042188535), source 09decf5, checks renderer recovery and the corrected emulator setup across API 29/36/37.0; its result is pending. See [testing](TESTING.md) for the final executed results.

There is no local connected device or AVD. Physical movement, real reboot/OEM scheduling and prolonged battery measurements remain the user's final checks in [phone acceptance](PHONE_ACCEPTANCE.md). APKs, SDKs, caches and signing keys stay out of Git; source, tests, documentation and CI are pushed to the requested repository.

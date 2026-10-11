# Verified build artifacts

Verified locally on 2026-10-11 from application source `a6e0cf4`, version 0.2.0. This includes the browsable block library, nested Settings pages and full-screen location form with clipped map drawing and stable gestures. Documentation updates do not change executable code. These hashes identify the local APKs exactly; CI builds use a different debug key and can have different bytes.

| Artifact | Size in bytes | SHA-256 |
|---|---:|---|
| Debug `app/build/outputs/apk/debug/app-debug.apk` | 39,393,175 | `353434767F4F687D1331A598FF8715EFB195045DDD0586677C8058E73E720D4C` |
| Optimized test build `app/build/outputs/apk/release/app-release.apk` | 4,716,039 | `A88E44FCFDB2955D4D57158696D45FE2D1C2145B76D6B33EEAEF37B7B16EAB88` |

Both are installable and signed with the same local Android Debug RSA-2048 key. `apksigner verify --verbose --print-certs` succeeds with valid APK Signature Scheme v2. Certificate SHA-256: `cce89bfb98d8c5b6b35274514a9d49c1b7e6fcbd61a96aae2ddabaf04341e398`. Signature verification alone does not prove installation or execution.

Absolute optimized APK path: `D:\Hello World\2. Automations\FlowState\app\build\outputs\apk\release\app-release.apk`. A byte-identical copy named `FlowState-0.2.0.apk` is alongside it for transfer to the phone. This is the recommended local artifact for final phone checks because R8 optimization matches normal release behavior. It uses a test key, not a personal production signing key. The debug APK is in the corresponding `debug` directory. These APKs share an application ID/key and replace each other when installed with `adb install -r`; make a backup first if preserving phone data matters.

The optimized artifact was built using the quoted PowerShell argument `'-Pflowstate.releaseSmoke=true'`. Normal release builds require private environment signing credentials or produce `app-release-unsigned.apk`; see [build and install](BUILD_AND_INSTALL.md). Do not install a stale unsigned output left by an earlier build.

`aapt dump badging` confirms package `dev.flowstate`, versionCode 2/versionName 0.2.0, minSdk 29, target/compile SDK 37 and launchable `dev.flowstate.MainActivity`. INTERNET is absent. Automatic backup and cleartext are disabled; operational receivers are non-exported.

ZIP inspection verifies local Blockly license (11,358 bytes), country map (838,726 bytes) and editor bundle (740,850 bytes). The bundled editor matches source SHA-256 `66EC29D57D02B5A6C51E9AF97F03BD4BEC57845CADF8CB6CE5021F88C76A13BB`. Editor HTML, compatibility code and media are packaged locally.

## Verification evidence

The final debug/lint/native compilation and optimized APK build succeeded in 3m 11s with 118 actionable tasks:

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin :app:assembleRelease '-Pflowstate.releaseSmoke=true' --max-workers=2
```

74 JVM tests and six editor serialization tests pass. Real browser UI checks pass at 360/412/800 px. Lint has zero errors, 32 warnings and one hint. Debug/native compilation and R8 optimized assembly succeed. Local reports are `engine/build/reports/tests/test/index.html`, `blockly-editor/browser-reports/` and `app/build/reports/lint-results-debug.html`.

Hosted UI verification run [38112343024](https://github.com/rs-raghu/FlowState/actions/runs/38112343024), source a6e0cf4, passes build and every API 29/36/37.0 job; downloaded reports and UI screenshots were inspected. It includes 26 standard native cases, real editor creation/save/run, map pin/pan/zoom/save/recreation, nested Settings at enlarged text, renderer termination/reload, external process/permission recovery and R8 installation/launch. See [testing](TESTING.md) for results and report interpretation.

There is no local connected device or AVD. Physical movement, real reboot/OEM scheduling and prolonged battery measurements remain the user's final checks in [phone acceptance](PHONE_ACCEPTANCE.md). APKs, SDKs, caches and signing keys stay out of Git; source, tests, documentation and CI are pushed to the requested repository.

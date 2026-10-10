# Development

Use JDK 23 (JVM 17 output), Node 24, SDK platform `platforms;android-37.0` and build tools 37.0.0. Compile SDK uses the AGP minor-level DSL `release(37) { minorApiLevel = 0 }`; target is 37, minimum 29. Gradle wrapper 9.6.0 is SHA-256 pinned. AGP 9.4.1 uses built-in Kotlin 2.4.21; KSP 2.3.12 and Hilt 2.60.1 replace kapt/manual injection. Compose BOM is 2026.09.00, Room 2.8.5, Blockly 13.3.0 and esbuild 0.28.2. Pins are in the build files/lockfile.

Set untracked `local.properties` sdk.dir or ANDROID_HOME. Run `scripts/verify.ps1` for npm ci/tests/bundle, core tests, debug/lint/native compilation and unsigned R8 assembly. Run one Gradle build at a time and avoid changing source while compile/lint reads it. Host-specific ignored `.tools` is not a runtime dependency.

Generated editor assets are committed. Regenerate after changing editor/catalogue/compat using `npm run build` inside blockly-editor. Blockly produces data only. Native Compiler, Runtime and Expressions define executable semantics; add blocks with validation, resource bounds and meaningful roundtrip/runtime coverage. Simulator never invokes DAO/platform operations.

Hilt AppModule owns singleton database/coordinator; MainActivity is an entry point and FlowViewModel is injected. Coordinator serializes mutations with a Mutex and Room transactions. Preserve exported schemas 1–5; add a new version and explicit migration for schema changes. Pending/old execution definitions remain immutable.

`connectedDebugAndroidTest` requires a device. `scripts/test-device.sh` runs hosted normal/native external process and permission phases, then R8 smoke installation. External fixtures require their phase argument and are excluded/skipped from ordinary discovery. The test dependency explicitly selects Espresso 3.7.0 rather than Compose's older transitive version for SDK 37 compatibility.

Signing credentials are environment-only; see BUILD_AND_INSTALL.md. APK integrity/metadata/results belong in BUILD_ARTIFACTS.md. Commit and push each completed stage/improvement as the owner requested.

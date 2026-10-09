# Development

Requirements: JDK 23 for the configured Gradle toolchain (emits JVM 17 bytecode), Android SDK 36, build tools, Node 24 (verified) and npm. The Gradle wrapper is pinned to 8.13; AGP 8.13.2, Kotlin/Compose compiler 2.2.21 and Compose BOM 2025.10.00 form the verified SDK-36 compatibility set. Newer stable dependency releases exist; a current SDK-37/AGP-9 migration has not been completed.

Set `sdk.dir` in untracked local.properties or ANDROID_HOME. On this machine the SDK is in `.tools/android-sdk`. Do not share or commit signing credentials. Run one build at a time to avoid competing writes to task output.

```
cd blockly-editor
npm ci
npm test
npm run build
cd ..
./gradlew :engine:test :app:assembleDebug :app:lintDebug
./gradlew :app:compileDebugAndroidTestKotlin
./gradlew connectedDebugAndroidTest
```

The final command needs a connected Android device/emulator. Generated editor assets are committed for offline/reproducible APK builds; regenerate them after changing catalogue/editor code. `npm ci` uses the lockfile. Build outputs are ignored. Room exports schema JSON; preserve schema history and add explicit migrations on version increments. Core tests are in engine/src/test; native database tests are in app/src/androidTest.

Add blocks by updating catalogue, compiler validation, native runtime/expression semantics, security bounds, roundtrip/runtime tests and documentation together. Never put unsupported operations in the toolbox. Dependencies are explicit via the application container rather than Hilt; see DECISIONS.md. Production configuration, snapshots and simulation state must remain separate.

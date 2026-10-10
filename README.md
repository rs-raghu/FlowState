# FlowState

Offline-first visual personal automation for Android 10+. The app uses native Kotlin/Compose screens, a locally bundled Blockly editor, and a validated native workflow interpreter. No login, remote backend, analytics or Internet permission.

**Implementation complete; final verification in progress:** 74 JVM and six editor tests pass locally. Debug/optimized release assembly, native compilation and lint pass. Hosted Android 16 completes native tests, external process/permission recovery and optimized APK launch. Current-source API 29/36/37.0 verification is running; final phone geofence/reboot/OEM and battery checks are in [phone acceptance](docs/PHONE_ACCEPTANCE.md). Read [status](docs/IMPLEMENTATION_STATUS.md), [limitations](docs/KNOWN_LIMITATIONS.md) and [tests](docs/TESTING.md).

## What works in code

- Native Dashboard, Automations, Locations, Activity and Settings.
- Editable Blockly branches, choices/checklists, typed variables/expressions, typed lists, configurable budgets, multi-parameter calls, error handling and durable waits/deadlines.
- Version-isolated execution snapshots, transactional variables/outbox, response tokens and recovery/deduplication.
- Time recurrences, DST and weekly-window catch-up; geofence adapters with permission/status checks and day/overnight eligibility.
- Isolated simulator with fake clock/variables/occupancy, steps, breakpoints, responses and effect traces.
- Offline geographic map/pins/radius, permission health, diagnostics, personal defaults, editable presets, reusable checklist templates, offline help and full configuration/value/history backups.
- [Eight editable examples](sample-workflows/README.md), built from ordinary blocks. Imported disabled; replace demo coordinates before enabling.

## Build and install

Use JDK 23, Android SDK 37.0 (target 37) and Node 24. Set untracked local.properties sdk.dir or ANDROID_HOME.

```powershell
cd blockly-editor
npm ci
npm test
npm run build
cd ..
.\gradlew.bat :engine:test :app:assembleDebug :app:lintDebug --max-workers=2
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Debug output is app/build/outputs/apk/debug/app-debug.apk. `scripts/verify.ps1` also compiles instrumentation tests and builds the R8 release. A device is required for installation and `:app:connectedDebugAndroidTest`. [Build/install guide](docs/BUILD_AND_INSTALL.md) covers signing through environment variables. [Artifact report](docs/BUILD_ARTIFACTS.md) records the exact verified local files and hashes. GitHub Actions uploads a debug APK after a successful verification run.

## Guides and audits

[User guide](docs/USER_GUIDE.md) · [Block reference](docs/BLOCK_REFERENCE.md) · [Workflow language](docs/WORKFLOW_LANGUAGE.md) · [Architecture](docs/ARCHITECTURE.md) · [Developer guide](docs/DEVELOPER_GUIDE.md) · [Scheduling](docs/TRIGGER_SCHEDULING.md) · [Permissions](docs/ANDROID_PERMISSIONS.md) · [Security](docs/SECURITY.md) · [Repository explanation — Report A](docs/REPOSITORY_EXPLANATION.md) · [Defect/risk audit — Report B](docs/FINAL_AUDIT.md) · [Original brief](docs/PROJECT_BRIEF.md).

PARALLEL provides durable independent branches and JOIN. Geofences require Google Play Services and precise/background location, and Android/OEM background restrictions can delay execution. These limits are documented alongside the remaining implementation requirements.

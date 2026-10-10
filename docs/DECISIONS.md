# Engineering decisions

- Use two modules (`engine`, `app`) with package boundaries rather than twenty mostly empty modules.
- Room transactions are the single writer boundary for state and scoped variables; no destructive migrations.
- Persist full definition snapshots for each run, plus versioned source/IR on automations.
- AlarmManager handles next occurrence; WorkManager handles recoverable execution/reconciliation. Inexact fallback is visible when exact access is absent.
- Geofence occupancy starts UNKNOWN, is timestamped, becomes stale, and never fabricates an exit. No continuous GPS polling.
- Loop, node, call-depth, value-size and step budgets apply to untrusted user input.
- Android toolchain is local under ignored `.tools`; credentials and APK outputs stay out of Git.
- Commit and push every completed stage or corrective improvement as requested.

The current stack uses AGP built-in Kotlin with KSP/Hilt, Room schema migrations 1 through 5, and explicit Espresso 3.7.0 for current Android input APIs. Older WebViews use a bounded foreground queue with one JSON decoding boundary. Full backup imports validate before the Room transaction; preferences use DataStore separately, so a storage failure is not an atomic transaction across both stores.

The emulator runner prepends its `cmdline-tools/latest` directory. CI points that path at current command-line tools 23.0, validates the created AVD target and enables GLDirectMem; disabling it causes the newer system images to restart SurfaceFlinger. Emulator configuration is separate from production application behavior.

Official references consulted 2026-10-10:

- https://developer.android.com/build/releases
- https://developer.android.com/build/kotlin-support
- https://developer.android.com/develop/ui/compose/bom
- https://developer.android.com/develop/background-work/services/alarms
- https://developer.android.com/develop/sensors-and-location/location/geofencing
- https://developer.android.com/develop/ui/views/layout/webapps/load-local-content
- https://developers.google.com/blockly/guides/configure/web/serialization
- https://developer.android.com/build/releases/gradle-plugin
- https://developer.android.com/build/migrate-to-built-in-kotlin
- https://github.com/google/dagger/releases
- https://github.com/google/ksp/releases
- https://developer.android.com/jetpack/androidx/releases/test
- https://developer.android.com/studio/run/emulator-acceleration
- https://github.com/ReactiveCircus/android-emulator-runner/blob/main/src/sdk-installer.ts

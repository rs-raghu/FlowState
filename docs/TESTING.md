# Verification and acceptance

Updated 2026-10-10. A compiled test is not an executed device test. Local verification uses JDK 23, Gradle 9.6.0, AGP 9.4.1, Kotlin 2.4.21, SDK 37.0/target 37 and Node 24.

```powershell
cd blockly-editor
npm ci
npm test
npm run build
cd ..
.\gradlew.bat :engine:test :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin :app:assembleRelease --max-workers=2
```

74 JVM tests pass: Engine 23, Control 12, Parallel 12, Sample 2, Window 4, Reminder 3, Binding 4, Simulation 3, Resource 11. Six Node tests pass for all custom blocks/examples, fourth-choice branches, typed lists and multiple CALL parameters. Debug assembly, lint (zero errors, 33 warnings), native compilation and optimized release assembly pass. Warnings include synchronous durable preference writes, optional KTX/resource suggestions, intentional restricted editor JavaScript and a renderer-handler detection warning despite the explicit override. Errors are not blanket-suppressed.

The standard native suite has 24 cases: database 7, Activity 4, policy/migrations 2, reminder/template ownership 2, backup 5, editor 1, defaults/geofence filtering 2 and real alarm/worker continuation 1. PermissionRecoveryTest is reserved for its external phase and skipped if a runner unexpectedly discovers it in the ordinary suite. ProcessRecoveryTest runs separately around force-stop/reopen. CI provisions Google APIs x86_64 KVM emulators for API 29, 36 and 37.0, with 8 GB data storage and direct SwiftShader rendering. It retains XML/HTML/logcat and external-phase reports on failure.

Final run [38042626097](https://github.com/rs-raghu/FlowState/actions/runs/38042626097), source c20794d, completes successfully: build and all API 29/36/37.0 jobs. Downloaded reports verify 24 standard native cases per version with no failures, process prepare/verify each reporting `OK (1 test)`, external notification recovery reporting `OK (1 test)`, and optimized APK launch reporting `Status: ok` with the Activity remaining running. The editor fixture terminates its actual renderer, reloads after Compose applies the tap and verifies the preserved draft before saving.

AGP's XML exporter records the deliberately ignored PermissionRecoveryTest assumption as a `<failure>` in the ordinary 25-entry report. The runner treats that fixture as skipped and the Gradle job succeeds; the other 24 cases have zero failures/errors. Its actual permission-denied scenario executes separately and must report `OK (1 test)`. Do not mistake that exported assumption for a failed application assertion or count it as additional executed coverage.

## Acceptance mapping

“Core/native coverage” identifies executed behavior within its test scope. Phone procedures remain required where physical Android delivery, OEM restrictions or actual accessibility use cannot be established by an emulator.

| Gate | Automated coverage / remaining boundary |
|---|---|
| AT-001 create/save | Native real editor validation/save/draft recreation; defaults and every preset compile |
| AT-002 manual execution | Engine/sample completion, native manual checklist/questions/timer |
| AT-003 permissions | Native notification denial/recovery; precise/background/location-service changes on phone |
| AT-004 time trigger | Scheduling boundaries/DST/occurrence identities; native alarm/worker continuation; long clock schedule on phone |
| AT-005 geofence EXIT | Native injected filtering/history/ledger; actual movement in PHONE_ACCEPTANCE |
| AT-006 geofence ENTER | Same injected event coverage; actual movement on phone |
| AT-007 geofence DWELL | Core history/duration and native minimum-dwell/cooldown logic; real dwell timing on phone |
| AT-008 branch responses | Core independent choices, typed answers and native separate question notifications/responses |
| AT-009 checklist | Native persisted progress/recreation/duplicate response; captured templates and protected deletion |
| AT-010 wait/resume | Core serialized continuations; native timer; external new-process token/wake/version restoration |
| AT-011 bounded loops | Core loop limits, budgets, variable types, restart and notification/shared-branch limits |
| AT-012 malformed import | Core malformed/deep/random input rejection; native rollback and invalid persistent-value rejection |
| AT-013 full backup | Native templates/values/history archival/ledger/settings, large envelope, merge/overwrite and retained-template precedence |
| AT-014 Friday→Sunday | Core weekly window recovery and eligibility |
| AT-015 window dedup | Core stable identity/catch-up; native unique event ledger and duplicate geofence filtering |
| AT-016 overnight | Core eligibility boundary and starting-day semantics |
| AT-017 DST | Core gap/overlap/local clock and next-day semantics |
| AT-018 exact access | Checked fallback and revocation handling; owner checks access changes with final phone run |
| AT-019 disabled triggers | Core queued delivery/version guards; native queue/replace; owner confirms disabled movement does not start runs |
| AT-020 simulation isolation | Core memory-only writes, fake event history/coordinates, stepping/replay; simulator has no DAO/platform effects |
| AT-021 expired response | Core duplicate/late token and overall deadline rejection |
| AT-022 concurrent values | Core deterministic parallel writes/JOIN conflicts; serialized coordinator transactions, native concurrent questions |
| AT-023 duplicate geofence | Native duplicate timestamp and cooldown filtering preserve occupancy/history/ledger |
| AT-024 invalid Blockly | Core type/resource/block validation and Node serialization; native real editor validation |
| AT-025 offline | No INTERNET permission, local editor/media/map/examples, native WebView and execution; optional full guide opens external browser only on request |
| AT-026 install/launch | Hosted debug install/native suite and optimized debug-signed R8 install/launch |
| AT-027 re-edit/run | Native save/draft recreation; captured version/process isolation, source roundtrip tests |
| AT-028 notification denial | External native denied permission → durable question → grant/reconcile → dismissal stays dismissed |
| AT-029 deletion cleanup | Native outbox cascade, template dependency protection, cancellation and owned notification tests; stale deliveries use version/token guards |
| AT-030 recovery | External force-stop/reopen with new PID, captured old version/token/wake and duplicate rejection; reboot, OEM behavior and real geofence re-registration on phone |

External process tests deliberately reinstall APKs after Gradle teardown. Permission revocation is applied outside instrumentation because revoking a runtime permission terminates the tested process. Each external invocation must explicitly report `OK (1 test)`; launch success alone is not sufficient. Final phone movement/reboot/battery and TalkBack inspection are not reported as passed by automation.

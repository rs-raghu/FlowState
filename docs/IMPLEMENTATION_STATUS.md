# Implementation status

Latest milestone: queued schedule delivery is version checked; valid already queued alarms survive reconciliation. Cancellation removes pending worker delivery and stale notification outbox entries. Catch-up ASK uses a persisted interaction. Editor blocks carry v1 metadata and local media paths. Integer arithmetic preserves INTEGER types and exact Long precision, rejects overflow, and comparisons avoid Double rounding. CI and a local verification script are included. Historical notes below describe the state at each commit, not the current gap inventory.

Advanced controls added: WAIT CLOCK and SET TIMEOUT with persisted deadlines and DST handling; simulator breakpoints, pause/continue retaining the blocked state, mock occupancy, scoped variable injection, notification-permission display and next-trigger inspection. Compiler rejects loop control outside loops and incompatible global variable declarations; saving rejects a type change over an existing non-null persistent value. Eight example scenarios are editable and pass Blockly/native validation. Sequential BRANCHES and incomplete device verification still prevent full release acceptance.

## Stage 1 — architecture

The remote repository was empty and has been cloned without overwriting existing files. Architecture, decisions, requirements traceability and original brief are recorded. Java 23 and Node are present. Android SDK and Gradle are being provisioned in ignored `.tools`.

Tests executed: repository inspection and remote reachability; no application tests yet.

Open work: stages 2–12, all app functionality, build and device validation. No APK exists yet. Do not treat this milestone as an application delivery.

## Stage 2 — native foundation
Gradle configuration, manifest, initial Compose host and normalized Room schema implemented. Android SDK 36 has been installed locally. Compilation is running; this milestone is not yet build-verified. Dependency resolution is still in progress. Hilt is deferred in favor of an explicit application-owned dependency container; full screen wiring follows integration.


## Stage 3 — compiler and interpreter
Implemented versioned IR, Blockly parsing, strict native validation, scoped values, expression evaluation, snapshots, persisted frames, wait/response deadlines, bounded loops, calls and error handlers. JVM test run succeeded: 16 tests, no failures. Includes branches, process-independent serialized waits, duplicate/expired responses, loops, weekly recovery, DST, malformed input and stale occupancy. Android integration is not yet verified.


## Stage 4 — offline Blockly editor
Blockly 13.3.0 is bundled with local media and license. Custom blocks, JSON serialization, undo/redo, zoom, search and cleanup implemented. Frontend tests passed (2 suites covering every block and template IDs). Browser bundle generated. Native origin-restricted bridge and simulator sheet added; Android compilation still pending.
Build correction: Compose 2026.08 requires SDK 37/AGP 9.1+, so the SDK 36 toolchain uses the compatible stable 2025.10 BOM. Full current-stack migration is deferred, not silently claimed.


## Stages 5–8 — platform execution integration
Native AlarmManager adapters, inexact fallback, durable WorkManager delivery, boot/clock recovery, Play Services geofencing, registration diagnostics, interactive notification actions, checklist/text/number response UI, scoped Room variables and transactional coordinator implemented. Trigger deduplication and response tokens are persisted. Device-dependent behavior has not been tested; adb reports no connected devices.
## Stage 9 — simulator
In-memory runtime with ISO clock, timezone, step/run/pause/restart, wait-time advance, response injection, trace and editor block highlighting. Simulation never invokes production adapters. Rich occupancy/permission input controls and breakpoint stopping remain open.
## Stage 10 — native workflows and backup
Five sections, creation templates, search/filter/sort, rename/copy/delete protection, themes, onboarding, permission health, diagnostics and bounded transactional workspace/location backup implemented. Map pin selection and richer notification configuration remain open. Android compilation pending.


## Audit improvements
Added bounded JSON preflight, stricter expression checks, serialized coordinator operations, snooze wake correction, separate informational notification identity, consistent catch-up IDs, per-choice dynamic Blockly branches, saved-resource dropdowns, reusable input/return values and transitive definition snapshots. JVM tests now include JSON bounds, recovery identity, reusable snapshots and fourth-choice routing. An offline Natural Earth geographic map with tap placement and radius rendering is implemented; no street tiles or search provider. Latest Android build is still running after repairing copied dependency artifacts inside the workspace cache.


## Verified build milestone
2026-10-09: native Kotlin compilation, 20 JVM tests, debug APK assembly, Android lint, instrumentation-test compilation and unsigned R8 release assembly all succeeded. Blockly frontend: 3 tests passed and assets rebuilt. Android instrumentation tests were compiled, not executed; no device/emulator is connected. Permission revocation, Android-10 import compatibility and reactive editor state lint defects were corrected without suppressing checks. Kotlin sources formatted with ktfmt 0.64. Debug APK exists at app/build/outputs/apk/debug/app-debug.apk. The app is still not accepted as production-complete: remaining specification gaps and device release gates follow in the status inventory.


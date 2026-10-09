# Implementation status

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


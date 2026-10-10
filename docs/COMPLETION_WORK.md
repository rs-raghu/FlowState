# Implementation completion work

The user has requested that all software implementation and automated verification finish before the physical geofence and battery checks. This file tracks the work without treating those physical checks as a coding blocker.

## Durable policies and recovery

Implemented configurable parallel/ignore/queue/replace policies, one to four active instances, bounded global queue, priority ordering, daily/weekly/entry-session/completion frequency limits, daily/monthly windows, structured missed/started/ignored event ledger, location transition history and location/time/coordinate expressions. Weekly event keys remain backward compatible. Room schema 2 upgrades schema 1 without deleting workflows or runs and seeds deduplication records before history can be pruned.

Completed geofence registration fingerprinting keyed by configuration, permission/service state and boot generation. Boot, package replacement and API errors invalidate the fingerprint. Platform calls have a 15-second deadline and preserve coroutine cancellation. History retention keeps active executions and the latest location transition of each kind, with a 400-day event ledger.

Verification: four new core boundary regressions; native upgrade/queue/replace tests added. Automated build and emulator results will be recorded after execution.

## Rich reminders and reusable checklists

Implemented three priority channels, categories, ongoing/group settings, named owned update/cancel targets and notification expiry. Interactions capture configurable snooze choices/limits and one optional follow-up. Pending questions can be restored after reopening; dismissed questions stay in Activity without being reposted unless an explicit snooze/follow-up requests it. Dismissal is persisted before asynchronous processing.

Checklist templates support native create/edit/delete/duplicate, item order, required/optional items, groups and notes. Only referenced templates are captured in each run; later edits cannot change pending items. References protect template deletion. Room schema 3 has an explicit 2-to-3 migration.

Verification: 56 core tests and four editor tests pass; debug assembly, lint and native test compilation pass. Native ownership/template regressions are added for the emulator run.

## Workflow bindings and diagnostics

CALL supports up to 20 named expression parameters, named callee-to-caller local output bindings, and returned execution status. Legacy single-input/result calls continue to work. Type and dependency checks run before saving/importing; immutable bindings survive durable waits. Typed lists have constructors, optional declared element types and checked updates. Native field failures include a block ID, code and correction; the editor shows severity/code/remediation and legal indefinite-wait/high-priority warnings.

Verification: 60 core tests pass, including multi-value wait/restart, incompatible output bindings, typed-list updates and malformed-field attribution. Debug/native test compilation pass. Two editor roundtrip regressions cover dynamic list and parameter inputs.

## Debugger, editor recovery and complete backup

Implemented step into/over/out, configurable node breakpoints, chronological fake event streams, permission/trigger eligibility explanations, captured-run debugging, branch inspectors and replay of up to 200 recorded simulation states. Replay restores values, effects, occupancy, permissions and deterministic interaction token generation. Core simulation tests pass.

The editor persists its foreground draft on changes and restores after recreation, and native bridge parsing is depth bounded. Backups schema 2 include templates, persistent values, recent execution snapshots, event ledger and preferences. Merge-by-name keeps existing definitions; overwrite updates matching IDs while disabling automatic triggers. Imported active snapshots become cancelled historical records. Native backup and actual WebView tests are included.

Verification: 63 core and six editor tests pass; debug assembly, lint and native test compilation pass. API 36 passed the previous 14 native cases plus process recovery. The API 29 named-notification test observed asynchronous platform posting; a bounded state wait repairs its assertion. Current native additions await CI.

## Personal defaults, presets and current Android stack

Implemented validated personal defaults, Material You colors and time formatting, confirmed data controls, private diagnostic reports/test actions, meaningful editable time/location/choice presets, enlarged-text wrapping and accessibility labels. Location dwell and cooldown are configurable; nested expression dependencies protect deletion. Room schema 4 migrates 3 without loss. Location history and all defaults roundtrip with backups; automation deletion removes its ledger and draft.

Migrated to SDK 37.0/target 37, AGP 9.4.1, Gradle 9.6.0, built-in Kotlin 2.4.21, KSP 2.3.12 and Hilt 2.60.1, with verified current AndroidX pins. The editor targets older Chromium and uses a bounded foreground message queue when the origin-scoped WebMessage listener is unavailable. No JavaScript interface is exposed to frames. Added actual startup diagnostics, notification permission/recovery, platform timer, preset/default, geofence-filter and enlarged-text native tests. CI now also targets API 37.0 and installs/launches an R8 build signed only for smoke testing with its debug key.

Verification: 65 core tests and six editor tests pass. Debug assembly, lint (zero errors), native test compilation and optimized release assembly pass. The 23-case native suite and R8 launch await the new hosted run; no device result is inferred from compilation.

## Remaining software stages

- Final resource-budget and backup-boundary audit, remaining native/platform verification.
- Final requirement audit and APK/handoff documentation.

## Final user checks

Actual phone geofence entry/exit/dwell, permission and location-service changes, reboot delivery, OEM power restrictions and prolonged battery behavior. Signing with a personal private release key remains the owner's credential-dependent step.

## Emulator startup repair

Hosted Android 10 and 16 each passed 21 of 23 native tests. The editor initialized its JavaScript object but the native workspace handshake did not complete during the test; startup now has an idempotent trusted-page callback and immediate main dispatcher. Notification denial tests revoke the runtime permission on modern Android and set both package/UID app ops on Android 10. Android 17 requires a larger emulator data partition; CI allocates 8 GB. Native test compilation passes; execution awaits the next hosted run.

## Resource and backup boundaries

Implemented editor-configurable step/loop/burst and per-minute notification budgets, persisted across waits and shared across parallel branches/calls. Captured libraries are limited to 256 KB, snapshots to 2 MB and aggregate persistent values to 1 MB/10,000 entries. Overflow records a compact FAILED snapshot, drops new effects/value writes and cancels pending questions. Platform notification capacity keeps pending questions accessible in Activity and reports a truthful diagnostic. Clearing terminal history also removes its owned message notifications.

Fixed 16 MB backup envelope handling while retaining each workspace/snapshot’s 2 MB UTF-8/depth boundary, template precedence during merge, and export/import count compatibility. Added large-backup and retained-template native regressions. Updated verified Play Services Location 21.4.0 and esbuild 0.28.2; 72 core and six editor tests pass. Debug/lint/native compilation and optimized unsigned assembly passed before the final harness changes. Android 17 exposed Compose’s old transitive Espresso 3.5.0; explicitly select stable 3.7.0 for its supported input manager. Permission revocation runs outside instrumentation because it terminates the tested process.

## Final native controls and compatibility

Added optional saved-location icons with Room schema 5 and a non-destructive 4-to-5 migration, backup roundtrip support, a bundled native help guide, configurable CALL depth and specific compiler remediation. Core coverage is now 73 tests; six editor tests pass. Schema migration tests validate every preserved schema version.

Run 38035623654 passed build and the complete API 36 job, including normal native tests, external process recovery, notification denial/recovery and R8 launch. Android 10 exposed double serialization in the older-WebView message queue; return its JSON message string directly for the single evaluateJavascript decoding boundary. Special permission tests also explicitly require their external phase. Android 17’s SurfaceFlinger repeatedly crashed in the deprecated indirect GPU mode; use the current direct SwiftShader renderer. These corrections await the next hosted run.

## Required-input attribution

Missing expression inputs now carry their owning block ID and a concrete connection correction, including nested list/expression inputs. Remaining root-level issues point to the trigger when there is no more specific block. A disconnected IF regression passes; core total is 74 tests.

# Verification and acceptance gates

Verification date: 2026-10-09. A compiled test is not an executed test. JVM and Node tests run on this Windows host; Android database, notification, lifecycle, map and accessibility behavior require Android execution.

## Executed checks

The local SDK-36 build uses JDK 23, Gradle 8.13, Kotlin 2.2.21 and AGP 8.13.2. Commands:

```powershell
cd blockly-editor
npm ci
npm test
npm run build
cd ..
gradlew.bat :engine:test :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin :app:assembleRelease --max-workers=2
```

On this host Gradle is invoked from `.tools/gradle-8.13/bin/gradle.bat` with `-g .tools/gradle-home` to isolate a repaired dependency cache. `scripts/verify.ps1` and GitHub Actions reproduce the normal workflow. Remote CI execution is a separate result; it must not be inferred from the workflow's presence.

Latest engine suite: 49 JVM tests, zero failures/errors (23 EngineTest, twelve ControlTest, twelve ParallelTest, two SampleTest). Four Node tests pass. Coverage includes strict expressions/integer overflow, branching/responses/checklists/loops, captured calls, bounded JSON/graphs, recurrence/DST/recovery, deadlines/clock waits, variable validation, overnight eligibility, all examples, explicit-null/legacy cursors and durable parallel waits/questions/JOIN/conflicts/TRY/cancellation/budgets. Sample completion exercises the first response path of each example; it does not exhaust every branch or verify platform delivery.

Seven native database tests in `DatabaseTest` cover transaction rollback, event identity, malformed import, backup roundtrip, closing/reopening a pending snapshot, and pending-effect foreign-key cleanup. Three `AppSmokeTest` cases launch/navigate/recreate the Activity, persist/complete a checklist and reject duplicate responses, and independently answer concurrent branch questions (including distinct notification tags when permission is available). All ten compile locally, including repeat installation of bundled examples with fresh references. Initial run 37965241810 exposed final-question cursor recovery and event conflict defects. After fixing explicit-null cursors and new-run INSERT, run 37967271781 passed all nine then-current native tests on BOTH API 29 and 36; the tenth bundled-example test awaits the next run. No local Android device/AVD is available.

Run [37968384343](https://github.com/rs-raghu/FlowState/actions/runs/37968384343), commit 0d53310, completed successfully: native build plus all ten standard instrumentation tests on BOTH API 29 and 36, including bundled example repeat import. This supersedes the pending tenth-test status above.

The CI device jobs use hardware-accelerated Linux/KVM, Google APIs x86_64 system images, disabled animations and a pinned emulator runner. `:app:connectedDebugAndroidTest` installs both APKs and executes instrumentation. Device XML/HTML reports are uploaded even after a failed test. Emulator coverage does not certify physical geofence movement, reboot delivery, OEM battery behavior or accessibility acceptance gates.

`scripts/test-process-recovery.sh` reinstalls both APKs after Gradle's connected-test teardown, then runs ProcessRecoveryTest in prepare/verify phases around an external `am force-stop` and Activity reopen. It requires a different process PID, preserves a pending parallel question's token/title/version and a sibling's wake, edits the current workflow while the captured run stays on its old version, rejects a repeated response, and cancels cleanly. This test is excluded from the single-process Gradle suite using `notClass`; both phases must report OK separately. It compiles locally; remote execution is pending. Initial run 37969410758 exposed an asynchronous Compose assertion (fixed by waiting for saved state plus UI) and the teardown reinstall requirement; it did not certify process recovery. Logs are retained with device artifacts. Device reboot/alarm delivery remains a separate physical gate.

Build outputs and integrity checks are recorded in BUILD_ARTIFACTS.md. Lint uses abortOnError, with no blanket error suppression: zero errors, 25 retained warnings. Warnings include dependency freshness, kapt/KSP migration, intentional editor JavaScript and stylistic API recommendations. Incremental kapt may report unused processor options when there are no changed Room declarations. Native shared libraries may be packaged without stripped debug symbols when the SDK tool cannot strip them; that build warning is not a test failure. No prior public database schema exists; exported schema 1 is checked in, and future upgrades need explicit migrations and migration tests.

## AT-001–AT-030 mapping

Run 37970443385 passed the standard ten-test suite and recovery preparation on both API levels, then stopped because the runner lacks ripgrep. The shell helper now falls back to grep; the force-stop verification phase still awaits the next run. Failed harness runs are retained as evidence rather than reported as application acceptance.

“Core passed” below means the named JVM behavior passed, with platform steps still pending. “Compiled” means a native test has not run. **None of the end-to-end device gates is certified passed.**

| Gate | Automated evidence | Required Android/manual procedure and current status |
|---|---|---|
| AT-001 persistence | backupWorkspaceRoundtrip (API 29/36 passed) | Save a weekday time workflow; dismiss editor, terminate process with `adb shell am kill dev.flowstate`, reopen; compare Blockly IDs, fields, enabled state and next schedule. Pending. |
| AT-002 registration | No live geofence test | Save a real pin/radius, grant precise/background access, enable referencing workflow; wait for API result and check Registered only after success; move across boundary. Pending. |
| AT-003 background denied | Permission checks compiled/linted | Grant foreground location only, deny background, enable location workflow; verify Settings and location registration show access required and never active monitoring. Pending. |
| AT-004 nested IF | nestedBranches; sample completion (core passed) | Edit nested conditions and run both outer/inner outcomes in simulator and live manual execution; inspect trace. Device pending. |
| AT-005 choices | arbitraryChoiceBranchRouting; fourth-choice Blockly roundtrip (core passed) | Create four choices with distinct messages; tap each of first two notification actions and open Activity for third/fourth; inspect selected branch. Pending. |
| AT-006 duplicate action | duplicateAndExpiredResponsesIgnored (core passed) | Rapidly tap same action twice; verify one response trace and one continuation/message. Repeat after worker retry/reopen. Pending. |
| AT-007 suspended recovery | Serialized execution snapshots/response tests (core passed) | Wait on a question, terminate process, reopen; verify same token/options/deadline and exactly one valid continuation. Pending. |
| AT-008 future wake | waitPersistsAndResumesWithSnapshot; execution deadline serialization (core passed) | Wait five minutes, terminate/reopen and reboot before wake; inspect one rescheduled wake and no early execution. Pending. |
| AT-009 version isolation | waitPersistsAndResumesWithSnapshot; reusableInputReturnAndDependencySnapshot (core passed) | Suspend parent and child, edit both definitions, resume old run; compare old text/branches with newly started run's new versions. Pending. |
| AT-010 dependency delete | Coordinator dependency checks compiled | Import examples, attempt deleting Essential Items while Going Out calls it; expect visible refusal. Remove CALL, save, delete again; expect success. Pending. |
| AT-011 bounded loops | loopBudget; repeatAndBreak; loopControlAndGlobalTypesAreValidated (core passed) | Connect WHILE true with finite limit; run and verify visible failure at limit. Attempt out-of-loop BREAK and invalid limits; save must fail with a block issue. Device pending. |
| AT-012 malformed import | safeJsonBoundsAndQuotedBraces; randomMalformedGraphsFailSafely (core passed); malformedImportHasNoWrites (API 29/36 passed) | Import invalid/truncated/deep/oversized JSON, duplicate IDs and recursive dependencies; compare before/after database/workflow counts. Native execution pending. |
| AT-013 backup equivalence | backupWorkspaceRoundtrip (API 29/36 passed); all example compile/roundtrips (core passed) | Export workflows/locations, clear app data, import; compare names, IDs, fields/branches and locations. Imported automations remain disabled intentionally; history/persistent values are not included. Pending. |
| AT-014 Friday→Sunday | weeklyWindowSundayRecovery (core passed) | Simulator or pure schedule test with Friday window/Sunday clock; inspect same eligible window key. Then miss an actual enabled alarm until Sunday and reopen; expect one catch-up. Device pending. |
| AT-015 window dedup | catchupUsesScheduledOccurrenceIdentity (core passed); eventIdentityIsUnique (API 29/36 passed) | Execute weekly window once, reopen repeatedly and deliver duplicate worker/alarm; history must retain one automatic event key. Device pending. |
| AT-016 overnight | overnight (core passed) | Evaluate 22:00–02:00 at 23:00/01:00 true, 02:00/12:00 false. Device UI pending. |
| AT-017 DST | dstGapAndOverlap; waitClockResolvesDstGapAndChoosesNextDayAfterTime (core passed) | Select America/New_York, spring gap and fall overlap clocks; verify forward gap and earlier overlap, explicit zone stable under device zone change. Device pending. |
| AT-018 exact access | Platform fallback compiled/linted | Enable short time schedule, revoke Alarms & reminders access, reopen; expect Inexact scheduling and eventual single execution without crash. Test revocation during scheduling. Pending. |
| AT-019 disabled triggers | Version/enabled guards compiled; queuedAlarmSurvivesReconciliationButRejectsEditedVersions (core passed) | Disable just before queued alarm/geofence; confirm no new automatic run. Manual Run remains intentionally available. Pending. |
| AT-020 simulator isolation | simulatedVariableWritesDoNotMutateTheirInput (core passed); architecture has no Platform/DAO in Simulator | Record production values/history/registrations, inject simulated values/occupancy and run fake effects; close and compare production state. Device UI pending. |
| AT-021 expired response | duplicateAndExpiredResponsesIgnored; exampleTimeoutCannotBeResurrected; executionDeadlineRejectsOtherwiseValidUserResponse (core passed) | Leave notification visible beyond deadline, tap old action; verify EXPIRED and no continuation. Pending. |
| AT-022 concurrent writes | Coordinator Mutex/Room transactions compiled; no live concurrency test | Run two workflows incrementing same GLOBAL INTEGER many times; compare final count with total serialized increments, including simultaneous responses and process termination before commit. Pending. |
| AT-023 duplicate geofence | Stable event key, persisted eventAt/cooldown checks compiled | Inject/reproduce same geofence timestamp twice; expect one run. Deliver a newer event during cooldown, then after cooldown; expect refusal then eligible run. Pending. |
| AT-024 invalid Blockly | compilerRoundtripAndMalformed; block roundtrip tests (core passed) | Disconnect IF Boolean, SET wrong type, use unknown resource, unsupported block version and disconnected stack; Save/Validate should show issue and highlight applicable block. Device pending. |
| AT-025 offline | Local assets bundled, no merged Internet permission, native engine tests | Airplane mode: create/edit/save/manual run/time wait/backup/checklist; verify no network dependency. Automatic geofencing still depends on Play Services/device location availability. Pending. |
| AT-026 install/launch | Debug APK build/signature checks | Connect Android API 29+, `adb install -r app/build/outputs/apk/debug/app-debug.apk`, launch MAIN; inspect crash log and all five screens. Not executed. |
| AT-027 re-edit/run | Every block/example JSON roundtrip and compiler tests (core passed) | Save, close, reopen editor, alter connected branch, save, run; compare IDs for untouched blocks and new execution version. Pending. |
| AT-028 notifications denied | Platform permission check/diagnostic path compiled/linted | Deny POST_NOTIFICATIONS (API 33+), run message and question; expect diagnostic, Activity pending interaction, valid Activity response and no crash. Pending. |
| AT-029 deletion cleanup | Cancellation/alarm tags/outbox cascade code compiled | Enable scheduled automation, delete after confirmation; inspect canceled alarms/work tags, removed run notifications, location Not monitored if last reference; stale delivery must not restart it. Pending. |
| AT-030 recovery | Schedule/window/snapshot core tests | Reboot, package update preserving data, change clock/zone, toggle location/permissions and reopen; verify registration results, wake deadlines and no duplicate occurrences. Force-stop is outside delivery guarantees until reopening. Pending. |

## Device test execution record

Record OS/API, model, Play Services version, app commit/APK SHA-256, battery optimization state, permission settings, steps, expected/actual result and relevant private trace/logcat excerpts for every gate. Run `gradlew.bat :app:connectedDebugAndroidTest` with a connected device for the database suite. Repeat important lifecycle/permission gates on API 29, 31, 33 and a current target-compatible Android version where environments exist. Test TalkBack, large text, contrast, touch targets, rotation/backgrounding and editor restoration. Run actual geofence movement on a physical phone; emulated locations alone cannot establish battery/OEM reliability.

The brief's full acceptance criteria remain open. Passing JVM, packaging and lint checks establishes buildable code and specific engine behavior, not production acceptance.

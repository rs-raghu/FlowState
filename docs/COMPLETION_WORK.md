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

## Remaining software stages

- Multi-value calls, typed lists and complete validation diagnostics.
- Simulator, native UI and backup/personal settings completion.
- Current Android stack migration and release runtime/platform verification.
- Final requirement audit and APK/handoff documentation.

## Final user checks

Actual phone geofence entry/exit/dwell, permission and location-service changes, reboot delivery, OEM power restrictions and prolonged battery behavior. Signing with a personal private release key remains the owner's credential-dependent step.

# Known limitations and unmet requirements

FlowState is a buildable development implementation, not a fully accepted production release of PROJECT_BRIEF.md. The following remain open as of 2026-10-09. The corresponding acceptance procedures are in TESTING.md; concrete risks are in FINAL_AUDIT.md.

## Release verification

No Android phone/emulator is connected; native instrumentation tests compile but have not run. Installation, launch, visual editing on-device, notification taps, process death/reboot, exact-access revocation, geofence movement, OEM battery restrictions, accessibility and long-running reliability remain unverified. R8 release assembly is not proof of release runtime correctness. No signing credentials were supplied; the debug APK is signed with the debug key and the release APK is unsigned. A personal release can be signed through documented environment variables.

The SDK-36 dependency set is a verified compatible pin, rather than the brief's fully current stable stack. A newer Compose BOM tried during development required SDK 37/AGP 9.1+, so it was not left in a broken build. The current-stack migration remains required. Explicit application-owned dependencies replace Hilt. Room schema 1 is exported without destructive migration; no released earlier schema or executed upgrade test exists.

## Workflow and interaction scope

- BRANCHES executes A fully then B with shared local variables; it does not implement the requested deterministic interleaving/parallel JOIN. Waiting in A delays B. This is labeled in the editor and language guide.
- CALL has one named input and one named local result, with immutable transitive definition snapshots and error propagation. Arbitrary parameter/output lists, explicit returned execution-status values and a separate reusable checklist-template manager are absent.
- Notifications use one channel, a common group and fixed display behavior. There are no user-configurable priority/channel/category/ongoing settings, arbitrary named update targets, custom snooze choices or escalation policies. Owned informational messages share one tag per run and replace earlier messages; interactions use a separate tag. At most two choice actions fit directly, with remaining options in Activity.
- Snooze is currently five or fifteen minutes with at most three snoozes. Checklist progress is durable, but reusable checklist-template CRUD, per-item notes and rich grouping/order controls are absent.
- Fixed concurrency limits are four active runs per automation and 32 globally. Custom queue/replace/single-instance and entry-session/once-until-completion policies are absent; cooldown plus persisted event identities are implemented.
- Weekly-window catch-up, grace, skip/record diagnostics and durable ASK recovery are implemented. General daily/monthly execution windows and a separate durable missed-occurrence ledger are absent. Diagnostics are not a long-term structured missed-event record.
- Location expression support is freshness-sensitive INSIDE/OUTSIDE/UNKNOWN. Last-entry/last-exit time, dwell duration, location coordinates/radius as expressions and complete location transition history are absent. Location triggers do enforce selected days, dates and local overnight time eligibility.
- Some requested conveniences are composed from existing blocks: increment/decrement through SET(GET ± value), Boolean toggle through SET(NOT GET), delayed follow-up through WAIT and ASK. Lists are unparameterized; literal list items are strings and there is no typed-list constructor toolbox block.
- Compiler Issue includes code/message/block ID, severity and a default correction. The editor bridge sends block/message/correction; code/severity presentation, specific remediation and suspicious-but-legal warning reporting are incomplete. Some malformed field parse errors surface as a general error without identifying a block. Optional missing branches continue by defined semantics.

## Editor, simulator and native UI

- The bundled Blockly editor has native compilation, local serialization, stable IDs/version metadata, categories, branching, undo/redo, search/cleanup and zoom. Actual Android WebView touch behavior, rotation/process restoration, TalkBack and large-text layout remain unverified.
- Simulator uses the real engine with fake clock/timezone/responses/effects, mock occupancy/scoped variables, permission display, breakpoint pause and next-trigger inspection. It has no automatic fake event stream, step-over/step-out, arbitrary node breakpoint list, historical-state replay, or comprehensive trigger/permission configuration simulator. Debugging a saved run starts a fresh memory execution of its captured definition/library.
- Native creation presets are simple manual message/checklist starters. The eight richer requested examples are delivered as an importable backup, rather than deeply integrated first-run templates. They contain demo coordinates and import disabled.
- The offline Natural Earth map supports geographic context, tap placement, pan/zoom and radius. It has no street-level tiles, place search/geocoding or address names. Use precise editable coordinates/current-position selection for real geofences.
- Backup includes workspaces and locations, imports disabled and rejects duplicate IDs; it does not back up persistent variable values, execution/history, preferences or signing keys. There is no merge-by-name or overwrite import mode.
- History/diagnostic display limits are 200/100 rows, but database retention is not bounded. A long-lived installation can accumulate data. Automatic pruning must preserve trigger deduplication/window records and active snapshots; a retention policy has not been implemented.

## Platform constraints

Geofencing requires usable Google Play Services, precise/background access and device location. It may be delayed by minutes and is not continuous GPS tracking. Known occupancy expires after one hour. Exact alarm access can be denied/revoked; even exact alarms dispatch work whose execution may be delayed. WorkManager recovery is deferrable. Force-stop prevents background delivery until reopening. The code reports health and diagnostics; no OEM timing/battery guarantee is made.

Reconciliation currently removes/rebuilds relevant geofences on every recovery/foreground pass. This maintains truthful status after the API response but can introduce registration gaps and extra work; a registration generation/fingerprint policy and physical-phone battery measurements remain open. Runtime effects are limited to local notifications and variable changes; there is no email, app control, remote API, account/backend, analytics or always-running service.

# Known limitations and unmet requirements

FlowState is a buildable development implementation, not a fully accepted production release of PROJECT_BRIEF.md. The following remain open as of 2026-10-09. The corresponding acceptance procedures are in TESTING.md; concrete risks are in FINAL_AUDIT.md.

## Release verification

API 29 and 36 GitHub-hosted emulators install and launch the app and pass nine native tests in run 37967271781. All ten standard cases now pass in run 37968384343. The separate force-stop/reopen phases also pass on both images in run 37971159000. Physical geofence movement, process death/reboot delivery, exact-access revocation, OEM battery restrictions, accessibility and long-running reliability remain unverified. R8 release assembly is not proof of release runtime correctness. No signing credentials were supplied; the debug APK is signed with the debug key and the release APK is unsigned. A personal release can be signed through documented environment variables.

The SDK-36 dependency set is a verified compatible pin, rather than the brief's fully current stable stack. A newer Compose BOM tried during development required SDK 37/AGP 9.1+, so it was not left in a broken build. The current-stack migration remains required. Explicit application-owned dependencies replace Hilt. Room schema 2 exports both versions and includes a non-destructive 1-to-2 migration that seeds the event ledger from existing runs. An emulator upgrade test is included; execution is pending.

## Workflow and interaction scope

- PARALLEL now provides durable deterministic interleaving, independent waits/questions and JOIN with explicit variable-conflict/failure semantics. Broader physical lifecycle and long-running parallel stress checks remain release gates; supported semantics and limits are in WORKFLOW_LANGUAGE.md.
- CALL has one named input and one named local result, with immutable transitive definition snapshots and error propagation. Arbitrary parameter/output lists, explicit returned execution-status values and a separate reusable checklist-template manager are absent.
- Notifications use one channel, a common group and fixed display behavior. There are no user-configurable priority/channel/category/ongoing settings, arbitrary named update targets, custom snooze choices or escalation policies. Owned informational messages share one tag per run and replace earlier messages; interactions use a separate tag. At most two choice actions fit directly, with remaining options in Activity.
- Snooze is currently five or fifteen minutes with at most three snoozes. Checklist progress is durable, but reusable checklist-template CRUD, per-item notes and rich grouping/order controls are absent.
- Configurable parallel/ignore/queue/replace policies support one to four active instances and a bounded global total of 32 active/queued runs. Priority ordering and daily/weekly/entry-session/completion frequency policies are implemented. Native queue and schema-upgrade verification is in progress.
- Daily, weekly and monthly windows, grace, skip/record and ASK recovery use stable identities and a durable event ledger. Core window boundaries pass; native scheduling and recovery checks remain to be executed.
- Location expressions include freshness-sensitive occupancy, last entry/exit/dwell, current dwell duration and saved coordinates/radius. Deduplicated transition history is persisted. Physical movement verification remains deferred to the final phone check.
- Some requested conveniences are composed from existing blocks: increment/decrement through SET(GET ± value), Boolean toggle through SET(NOT GET), delayed follow-up through WAIT and ASK. Lists are unparameterized; literal list items are strings and there is no typed-list constructor toolbox block.
- Compiler Issue includes code/message/block ID, severity and a default correction. The editor bridge sends block/message/correction; code/severity presentation, specific remediation and suspicious-but-legal warning reporting are incomplete. Some malformed field parse errors surface as a general error without identifying a block. Optional missing branches continue by defined semantics.

## Editor, simulator and native UI

- The bundled Blockly editor has native compilation, local serialization, stable IDs/version metadata, categories, branching, undo/redo, search/cleanup and zoom. Actual Android WebView touch behavior, rotation/process restoration, TalkBack and large-text layout remain unverified.
- Simulator uses the real engine with fake clock/timezone/responses/effects, mock occupancy/scoped variables, permission display, breakpoint pause and next-trigger inspection. It has no automatic fake event stream, step-over/step-out, arbitrary node breakpoint list, historical-state replay, or comprehensive trigger/permission configuration simulator. Debugging a saved run starts a fresh memory execution of its captured definition/library.
- Native creation presets are simple manual message/checklist starters. The eight richer scenarios are bundled offline and can be added directly from Dashboard/Settings as ten editable workflows with fresh workflow/location references. Per-template creation and a separate reusable checklist-template manager remain absent. Demo coordinates must be replaced before enabling location workflows.
- The offline Natural Earth map supports geographic context, tap placement, pan/zoom and radius. It has no street-level tiles, place search/geocoding or address names. Use precise editable coordinates/current-position selection for real geofences.
- Backup includes workspaces and locations, imports disabled and rejects duplicate IDs; it does not back up persistent variable values, execution/history, preferences or signing keys. There is no merge-by-name or overwrite import mode.
- Automatic retention preserves active runs, a 400-day event ledger, entry-session suppression records and the latest transition of each kind. Completed executions retain 30 days, transition details 90 days, and diagnostics 1,000 rows. Native retention verification remains pending.

## Platform constraints

Geofencing requires usable Google Play Services, precise/background access and device location. It may be delayed by minutes and is not continuous GPS tracking. Known occupancy expires after one hour. Exact alarm access can be denied/revoked; even exact alarms dispatch work whose execution may be delayed. WorkManager recovery is deferrable. Force-stop prevents background delivery until reopening. The code reports health and diagnostics; no OEM timing/battery guarantee is made.

Geofence reconciliation reuses a persisted fingerprint for unchanged configurations and invalidates on boot, package replacement, errors and permission/service changes. Play Services operations time out after 15 seconds. Physical-phone battery measurements remain open. Runtime effects are limited to local notifications and variable changes; there is no email, app control, remote API, account/backend, analytics or always-running service.

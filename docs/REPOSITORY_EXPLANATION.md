# Repository explanation — Report A

Updated 2026-10-10. This describes implemented source; Report B records defects, fixes and remaining evidence boundaries.

| Source | Responsibility |
|---|---|
| engine/Model.kt | Versioned typed definitions/values/nodes/frames, independent branch snapshots, interactions, effects and budgets |
| engine/Compiler.kt | Bounded Blockly JSON → allowlisted IR; types/scopes/resources/calls/graphs/fields and structured block remediation |
| engine/Expressions.kt | Strict typed evaluation, exact checked integers, lists, date/time/duration and freshness-sensitive location history/coordinates |
| engine/Runtime.kt | Immutable interpreter, captured transitive calls/checklists, durable waits/questions/deadlines, deterministic PARALLEL/JOIN, TRY, response and rate budgets |
| engine/Scheduling.kt | Recurrence/DST/eligibility, stable daily/weekly/monthly windows and missed-occurrence recovery |
| engine/Simulation.kt | Fake clock/events/values/permissions/locations, steps/breakpoints/replay and branch inspection; no Android side effects |
| engine/SafeInput.kt, ResourceLimits.kt, Dependencies.kt | UTF-8/depth preflight, bounded snapshot/value failure and recursive resource dependency discovery |
| app/FlowStateApp.kt, MainActivity.kt, data/AppModule.kt | Hilt application/activity, singleton Room/coordinator, lifecycle reconciliation and native presentation setup |
| data/Database.kt | Room schema 5, preserved schemas 1–5, explicit migrations, unique events/FKs, typed values/outbox/history/ledger/templates |
| data/Coordinator.kt | Serialized user/platform mutations, compile/version/dependency checks, transactional transitions, policies/frequency, outbox drain and reconciliation |
| data/Backups.kt, BuiltInExamples.kt | Validated schema-2 full backup/import and independent disabled offline sample copies |
| data/Preferences.kt, WorkflowPresets.kt, EditorDrafts.kt | Validated personal settings, ordinary editable presets and synchronous bounded foreground drafts |
| platform/Platform.kt | Permission/app-op health, alarms/work scheduling, fingerprinted geofences, owned reminders/dismissals, capacity and cancellation |
| platform/Receivers.kt | Short explicit broadcast handling and bounded worker retries; no human-response coroutine or perpetual service |
| ui/Screens.kt, FlowViewModel.kt | Five native sections, CRUD/filter/enable/run/history/health/onboarding, observed state and error presentation |
| ui/Editor.kt | Trusted local WebView, blocked remote navigation/resources, origin/main-frame listener or bounded foreground queue, native compile/save/simulate and draft recovery |
| ui/Simulator.kt | Native isolated debugger controls and trace/variable/branch/resource inspectors |
| ui/LocationMap.kt | Bundled geographic map, pan/zoom/pin and geofence circle |
| ui/ChecklistManager.kt, PersonalSettings.kt | Ordered reusable checklist CRUD, defaults/data controls/private diagnostic/help/about actions |
| blockly-editor/* | Blockly 13.3.0 catalogue/editor/compat/bundling and six serialization tests; data output, no workflow JavaScript execution |
| app/src/main/assets/* | Offline editor/media/license/map notices/examples |
| app/src/androidTest/* | Database/UI/policy/template/backup/editor/default/event/timer regressions and externally phased permission/process fixtures |
| engine/src/test/* | 74 pure regression tests; every example compiled/exercised with fake state |
| scripts/*, .github/workflows/android.yml | Reproducible local verification and hosted device/external phase/R8 checks, report upload |
| Gradle files, wrapper, npm lockfile | Verified pinned toolchain/dependencies, environment signing, aborting lint and optimized release |

IR/source/configuration are distinct from captured execution/simulation state. One app process and coordinator Mutex serialize transitions; Room atomically commits snapshot/variable/outbox writes. Platform effects occur after commit using stable owned identities. Simulations never access the production coordinator. Pending human state is durable, not a blocked worker.

The manifest has no Internet permission, account/backend/telemetry dependency, excludes automatic backup/transfer and keeps operational receivers non-exported. Only geofencing uses a mutable explicit app PendingIntent; other actions use immutable app-owned intents and validated tokens.

PROJECT_BRIEF.md preserves the user's original engineering specification. IMPLEMENTATION_STATUS.md is current inventory; KNOWN_LIMITATIONS.md distinguishes Android/design constraints from unfinished verification. TESTING.md maps automated coverage to 30 gates; PHONE_ACCEPTANCE.md contains final movement/recovery/battery checks. BUILD_ARTIFACTS.md identifies exact APKs. Remaining guides describe actual language, controls, permissions, schema, build/signing and security.

Ignored SDKs/caches/build outputs/keys are development artifacts, not shipped source requirements. APKs remain in standard output folders and are not committed. Every completed stage is pushed to the owner's repository.

# Report A — Repository explanation

This report describes the implemented code. Requirements and unimplemented behavior are tracked separately in KNOWN_LIMITATIONS.md and Report B. Paths below are repository relative. Production Android code is under `app/src/main/java/dev/flowstate`; the platform-independent engine is under `engine/src/main/kotlin/dev/flowstate/engine`.

## Module and runtime relationships

`app` owns Compose screens, Android adapters, Room and WorkManager. It depends on `engine`. `engine` has Kotlin/JVM plus kotlinx.serialization and java.time; it cannot access Android services, UI or the database. `blockly-editor` is a Node build project whose browser output is copied into APK assets. It produces Blockly workspace JSON, never executable workflow JavaScript. `sample-workflows` contains normal editable JSON backups. `docs` records contracts, verification and gaps. `scripts` and `.github` run build checks; neither participates in runtime execution.

The main data path is: Blockly workspace → Compiler → Definition → Room automation → Coordinator.start → Runtime snapshot → Runtime.tick → transactional state/variable/outbox writes → Platform notifications and wakes. Recovery reads the same snapshots. Simulator directly invokes Runtime with memory-owned state/effects, bypassing Room and Platform.

## Engine sources

| File | Responsibilities and important functions | Inputs, outputs, dependencies and runtime significance |
|---|---|---|
| Model.kt | Type, Scope, Value, Expr, Variable, Trigger, Node, Definition, State, Frame, Interaction, Trace, Execution, Effect, Tick, EvaluationContext, Issue/ValidationException; strict `codec`, `Value.parse` and `Execution.capturedDefinitions` | Defines serialization contracts used by every module. Values contain typed text/list data; definitions contain validated graphs; executions contain version snapshots, library, frames, interaction and deadlines. Default fields preserve older v1 JSON. No Android dependency. |
| SafeInput.kt | Quote/escape-aware JSON structural preflight | Bounded source text in, checked source text out or rejection. Prevents excessive parser nesting before serialization parsing in Compiler/Backups. |
| Compiler.kt | `compile`, typed expression inference, `validate`, `validateSharedVariables`; converts trigger/statements/expressions and declarations | Workspace JSON + IDs/resource sets in, Definition or block-associated issues out. Enforces sizes, arities, references, graph reachability/cycles, typed conditions/assignments, loop context, schedules and block versions. Imports and editor saves use the same authority. |
| Expressions.kt | `evaluate`, scoped `key`, numeric comparison, `inTimeRange` | Expr plus clock/zone/local/persistent/occupancy context in, Value out. Strict Booleans with short-circuiting; checked Long arithmetic and precise numeric comparisons; bounded lists/strings, dates and freshness-sensitive location occupancy. Exceptions become runtime failures/handlers. |
| Scheduling.kt | `validate`, `zone`, `resolve`, `next`, `recovery`, occurrence/window keys, `acceptsDelivery`, `locationEligible` | Trigger + timestamps/device zone in, next/recovery occurrence, stable key or eligibility out. Encodes recurrence, month clamp, DST, Friday→Sunday weekly windows, edited-version rejection and overnight location day/date semantics. Android adapter schedules its output. |
| Runtime.kt | `start`, immutable dependency capture, `tick`, `respond`, terminal-state set | Definition/snapshot + clock/context in, updated execution, values and Effect descriptions out. Durable loops/call/handler frames; waits and user tokens; bounded slices/budgets; deadline expiration; production trace markers and optional simulator breakpoint pauses. It never posts a notification or registers a trigger. |

## Android application sources

| File | Responsibilities and important classes/functions | Inputs, outputs, dependencies and runtime significance |
|---|---|---|
| FlowStateApp.kt | Application-owned lazy `database` and `coordinator` | Creates one Room database and one Coordinator per process. Explicit container replaces Hilt; workers and ViewModel share the same transaction/Mutex boundary. |
| MainActivity.kt | Compose host, FlowViewModel creation, edge-to-edge, resume reconciliation | Android lifecycle in, FlowState UI and recovered scheduling out. Foreground entry rechecks permission/registration state. No workflow execution in WebView/Activity callbacks. |
| data/Database.kt | AutomationEntity, LocationEntity, ExecutionEntity, VariableEntity, OutboxEntity, DiagnosticEntity, FlowDao, FlowDatabase | Room tables, indexed unique event keys, foreign keys, transactional CRUD and observed Flows. Snapshot/IR are serialized strings; queryable state/wake columns support recovery. Partial rename query preserves unrelated fields. Schema version 1; no destructive fallback. |
| data/Coordinator.kt | Mutex-protected save/enable/delete/start/trigger/drive/respond/cancel/snooze/checklist/geofence/reconcile/import/location entry points; private internal operations | DAO data/user intent/platform events in, durable executions/variables/outbox/status and Android effects out. Compiles changes, protects dependencies, snapshots calls, enforces enabled/cooldown/concurrency, checks schedule versions, commits atomically, drains outbox after commit, rebuilds wakes and geofences. ASK recovery creates a durable prelude. |
| data/Backups.kt | Backup/BackupAutomation/BackupLocation, `Backups.export/import` | Room configuration → JSON backup, or bounded JSON → fully compiled disabled automations and locations. Validates IDs/coordinates/resources/recursive dependencies/shared types before writes; import transaction rejects duplicates without partial writes. Execution history and persistent values are excluded. |
| data/Preferences.kt | Preferences DataStore and theme/onboarding state | Stored local settings → observed theme/onboarding; UI selections → persisted settings. Independent of workflow variables and execution. |
| platform/Platform.kt | Permission/service health; AlarmManager `schedule/cancelAlarm`; WorkManager `enqueue/periodicRecovery`; Play Services `register`; notification/cancellation functions | Validated times/location records/Effect descriptions → explicit PendingIntents, alarms/jobs/geofences/notifications. Exact access checked with inexact fallback; status returned only after registration API result. Mutable PI only for geofencing; response/alarm/open PIs immutable. Notification denial returns failure for diagnostics. |
| platform/Receivers.kt | AlarmReceiver, ResponseReceiver, RecoveryReceiver, GeofenceReceiver, EngineWorker | Short broadcast payloads → durable work requests; worker invokes Coordinator. Recovery action allowlist; geofence transitions/timestamp extracted from platform event; errors diagnostic. Worker cancellation rethrows, transient failures retry at most three times. Receivers do not run long engine loops. |
| ui/FlowViewModel.kt | Observed StateFlows, user actions/error snackbar, creation templates, rename/copy, locations, backup support | DAO/coordinator/preferences in, Compose-observed lists/state and user action commands out. Templates generate normal editable workspaces. Renames update only name/timestamp. All execution/location mutations route through Coordinator. |
| ui/Screens.kt | FlowState and Dashboard/Automations/Locations/Activity/Settings; interaction forms; permission/import/export launchers | StateFlows → five native sections. Lists show counts, schedule/monitoring/pending states; CRUD confirmations; editable location map/coordinates/current-position; checklist/text/number/choice responses; themes/diagnostics and SAF backup. Native permission sequence distinguishes foreground/background/exact/notifications. Activity displays snapshots/traces. |
| ui/Editor.kt | Origin-restricted WebViewAssetLoader, WebMessage listener, dirty/back protection, compile/save/copy/validate/simulate callbacks | Saved workspace/resource JSON → local Blockly page; bounded main-frame messages → native compilation/coordinator or isolated simulator. Denies remote requests/navigation/file/content access; destroys WebView with composition. Issues are sent back for block highlighting. |
| ui/Simulator.kt | Memory-only Runtime controls, fake clock/zone/effects, pause-state preservation, breakpoint support, responses, occupancy/variable injection and schedule inspection | Definition library and user-entered mock values → simulated state/trace/effects. No Context, DAO or Platform reference. Step/Run stop at blocked states; Run pauses at breakpoint; Restart resets variables/effects. Notification permission is a fake display input. |
| ui/LocationMap.kt | Native Canvas offline geographic map, coordinate projection/pin, gestures and radius circle | Bundled country GeoJSON + selected coordinates/radius → offline map. Tap/drag/zoom produce user-selected coordinates. This is geographic context, not a street map or geocoder; coordinates remain editable. |

## Editor sources and packaged assets

| Path | Purpose, inputs/outputs and dependencies |
|---|---|
| blockly-editor/catalogue.js | Custom JSON block definitions, toolbox, typed connections, dynamic saved-resource dropdowns, choice mutation, version metadata, initial workspace. Depends on Blockly API; consumed by browser editor and headless tests. Native compiler remains type/security authority. |
| blockly-editor/editor.js | Injects workspace with local media; wires toolbar, JSON load/save, undo/redo/cleanup/search/zoom, dirty messages, native bridge and issue/highlight hooks. Browser interactions yield declarative JSON. |
| blockly-editor/index.html | Local toolbar/layout and restrictive CSP. Provides script and DOM host for Blockly; no CDN or external connection. |
| blockly-editor/build.mjs | Uses esbuild and locked dependencies to bundle browser JavaScript and copy HTML/media/license to app assets. Build-time only. |
| blockly-editor/package.json / package-lock.json | Exact Blockly/esbuild versions and transitive integrity hashes; `npm ci`, test/build commands. No production Node server. |
| blockly-editor/test.mjs | Headless Blockly roundtrips for every block, templates, fourth choice and every sample. Native Android/WebView rendering is outside its coverage. |
| blockly-editor/samples.mjs | Stable-ID fixture generator for eight requested scenarios with ten ordinary workflows and three demo locations. Writes sample-workflows/examples.json; exported data is used by frontend tests. |
| app/src/main/assets/editor/index.html / editor.bundle.js | Generated APK-local editor host/bundle. Commit alongside source so Android builds remain offline at runtime. JavaScript executes editor UI only. |
| app/src/main/assets/editor/media/* / BLOCKLY_LICENSE | Blockly icons, cursor files, sprites, quotes, audio and Apache license from pinned package. Relative media path prevents blocked remote fallback. No custom workflow logic. |
| app/src/main/assets/maps/countries.geojson / NOTICE.txt | Natural Earth public-domain country boundaries and source attribution. Read by LocationMap; no network API/key or remote tiles. |
| sample-workflows/examples.json / README.md | Settings-importable backup and usage/semantics. Imported disabled; demo coordinates must be replaced before enabling. Compiler/Runtime tests validate examples and timeout behavior. |

## Tests, Android resources and build files

| Path | Responsibilities and significance |
|---|---|
| engine/src/test/.../EngineTest.kt | Main pure compiler/interpreter/scheduling regression tests: branching, guards, snapshots, malformed inputs, choices, checklist, time windows and queued delivery. Fake clocks/definitions in, asserted states/effects/keys out. |
| engine/src/test/.../ControlTest.kt | Deadlines, local clock/DST waits, simulator-only breakpoints, loop/global validation, isolated variable writes, location eligibility and timestamp overflow. Regression checks for advanced control boundary cases. |
| engine/src/test/.../SampleTest.kt | Loads actual checked-in backup, compiles every workspace with saved-resource IDs, exercises first-response completion and late timeout rejection. Includes reusable resolver and scoped memory values. |
| app/src/androidTest/.../DatabaseTest.kt | Android in-memory Room transaction rollback, unique event identity, safe malformed import and semantic backup roundtrip. Needs instrumentation runner/device; compilation does not execute it. |
| app/src/main/AndroidManifest.xml | Required location/notification/alarm/boot access, application class, MAIN activity and non-exported app receivers, backup/cleartext restrictions. Libraries add permission-protected WorkManager/profile components; merged manifest is audited. |
| app/src/main/res/drawable/ic_flowstate.xml | Vector launcher identity; used by Android launcher. Notification icon currently uses Android's reminder drawable. |
| app/src/main/res/values/styles.xml | Native host theme before Compose renders. Compose controls light/dark/system application theme. |
| app/src/main/res/xml/data_extraction_rules.xml | Excludes cloud/device-transfer data; explicit user SAF export is the configuration backup channel. |
| app/schemas/dev.flowstate.data.FlowDatabase/1.json | Room-exported table/index/foreign-key identity contract. Preserve on schema increments for non-destructive migrations. Initial schema only; migration execution not tested. |
| settings.gradle.kts | Repository/dependency resolution and module inclusion (`app`, `engine`). |
| build.gradle.kts | Shared pinned Android/Kotlin/Compose/serialization/kapt plugin versions. |
| gradle.properties | Gradle/JVM/Android settings; no credentials. |
| engine/build.gradle.kts | JVM toolchain/output target and serialization/JUnit dependencies. Produces engine JAR and pure test reports. |
| app/build.gradle.kts | SDK/version/application ID, Compose, Room schema export, Android dependencies, aborting lint and R8 release configuration; optional signing via four FLOWSTATE_* environment variables. API below 29 rejected. |
| app/proguard-rules.pro | Rules accompanying default optimized Android shrinker configuration. Reviewed alongside successful R8 assembly; real release launch still needs testing. |
| gradlew / gradlew.bat / gradle/wrapper/* | Cross-platform Gradle bootstrap, version 8.13 URL/JAR/properties and official distribution SHA-256 pin. Unix executable bit set. |
| scripts/verify.ps1 | Fail-fast npm install/tests/bundle, native tests/debug/lint/instrumentation compilation/release and APK hash. Reports failure via exit status; does not claim device execution. |
| .github/workflows/android.yml | Push/PR Ubuntu CI build with JDK 23/Node 24/SDK 36 and debug APK upload; API 29/36 KVM emulator jobs install and execute native tests and retain reports. No release signing credentials. |
| .gitignore | Excludes local SDK/caches, generated builds, keystores, node_modules and machine-specific settings. |

## Documentation inventory

`engine/src/test/kotlin/dev/flowstate/engine/ParallelTest.kt` exercises independent waits/questions, serialized joins, local conflict/merge, persistent write order, failure/TRY/cancellation, deadlines/snooze, breakpoints, nesting/budgets and loop-boundary validation. `app/src/androidTest/java/dev/flowstate/AppSmokeTest.kt` launches/navigates/recreates the real Activity and checks durable checklist and concurrent question responses/notification ownership.

README introduces installation, implemented features and development status. PROJECT_BRIEF preserves the complete user requirements. ARCHITECTURE describes module/data boundaries. DECISIONS records compatibility/container/map/recovery choices. REQUIREMENTS maps staged deliverables to verification. WORKFLOW_LANGUAGE defines IR/value/control contracts. BLOCK_REFERENCE covers actual toolbox semantics. WORKFLOW_ENGINE describes durable execution, transactions/outbox and recovery. DATABASE_SCHEMA describes Room's persisted contracts. TRIGGER_SCHEDULING defines recurrence, window identity, DST and location eligibility. ANDROID_PERMISSIONS defines the grant/revocation flow. SECURITY explains local data, WebView/PI/import defenses and remaining security verification. USER_GUIDE explains native screens and workflow usage. DEVELOPER_GUIDE and BUILD_AND_INSTALL explain reproducible builds, signing and deployment. TESTING maps all AT-001–AT-030 gates to evidence/procedures. BUILD_ARTIFACTS records verified file paths/hashes/signatures. IMPLEMENTATION_STATUS is the current feature/build inventory. KNOWN_LIMITATIONS contains unmet specification/platform requirements. FINAL_AUDIT is independent Report B with concrete defects/risks and verification status. This file is Report A.

Ignored `.tools`, local.properties, caches and app/engine build outputs are local development artifacts, not source or runtime requirements. APKs remain in standard build output folders and are not committed.

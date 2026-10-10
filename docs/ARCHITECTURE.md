# FlowState architecture

Native Android application, API 29 minimum. Compose provides dashboard, automation management, saved locations, execution activity and settings. A restricted AndroidX WebViewAssetLoader hosts bundled Blockly assets only inside the editor. No backend, analytics or unrestricted script execution.

`engine` is a Kotlin/JVM module: versioned models, Blockly JSON compiler, strict expression evaluator, bounded interpreter, recurrence calculations and isolated simulator. It has no Android dependencies. `app` contains Room persistence, platform adapters, workers/receivers and Compose presentation. These two modules avoid artificial module proliferation while separating platform-independent logic.

The compiler accepts Blockly JSON, checks identifiers, input types, block allowlist and structural bounds, and emits a separately persisted immutable definition. Execution snapshots carry the full definition so editing cannot change suspended runs. Runtime steps produce effects; the Android coordinator commits state and an outbox in a Room transaction. Workers drain the outbox using stable notification identities. Notification delivery is effectively-once via replacement; arbitrary external side effects are not supported.

Executions suspend with durable continuation frames, local values, interaction tokens and deadlines. Workers never hold a coroutine for a human response. Every transition is serialized transactionally with persistent scoped variables. Receivers enqueue unique WorkManager jobs; alarm and geofence registrations are reconciled on boot, upgrade, clock/timezone changes and app foregrounding.

Security boundaries: import size/depth/node limits, native compiler validation, app-owned explicit PendingIntents, origin-restricted WebMessage listener, non-exported operational receivers and no WebView remote navigation. Simulation uses in-memory effects and cloned variables.

Hilt supplies application/activity/ViewModel injection and singleton Room/coordinator dependencies. Room schema 5 has explicit preserved migrations from version 1. DataStore holds validated defaults/theme/onboarding; synchronous bounded draft storage protects foreground editor work. Reusable checklist templates, location events and a separate durable trigger ledger support captured runs and reliable retention. Full backup/schema validation recompiles source before Room writes; preferences remain a separate storage boundary.

# Database schema v1

Room schema export: `app/schemas/dev.flowstate.data.FlowDatabase/1.json`.

| Table | Identity and contents | Integrity |
|---|---|---|
| automations | UUID, name, enabled, version, complete workspace, compiled definition, update time, monitoring status, next occurrence and last start | Primary ID |
| locations | UUID, name, coordinates, radius, description, enable/timestamps, occupancy/event timestamp and registration result | Coordinates/radius validated at write/import boundaries |
| executions | UUID, automation ID, unique event key, serialized snapshot, state, update and wake times | RESTRICT FK to automation; unique eventKey index |
| variables | owner + name, serialized typed value | Compound primary key; owner is automation UUID or global |
| outbox | Stable effect ID, execution ID, serialized effect | CASCADE FK to execution and execution index |
| diagnostics | Monotonic row ID, timestamp and message | Local-only data |

Pending interactions, checklist completion and continuation frames live inside the execution snapshot rather than duplicate normalized rows. Versioned source and IR live on the automation; each run retains an independent definition/dependency snapshot. Only the latest editable automation definition is retained outside execution history.

No destructive migration fallback is configured. This is the first schema; no previous public database exists. Every future schema version requires an explicit migration and migration test. API 29/36 emulator tests pass rollback, event uniqueness, malformed import, workspace backup, closing/reopening a pending snapshot and pending-effect foreign-key cleanup. Schema upgrades and physical lifecycle recovery remain separate gates.

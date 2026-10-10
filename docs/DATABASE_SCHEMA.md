# Database schema 5

Room exports schemas 1–5 under `app/schemas/dev.flowstate.data.FlowDatabase`. There is no destructive migration fallback.

| Table | Contents and integrity |
|---|---|
| automations | UUID, name/enabled/version, separate Blockly source and compiled IR, status/next occurrence/last start |
| locations | UUID, coordinates/radius/description/icon, enabled/timestamps, occupancy/event timestamp, registration result, dwell/cooldown |
| executions | UUID, automation RESTRICT FK, unique event key, serialized immutable continuation, state/wake/update |
| variables | Owner/name compound primary key, serialized typed value |
| outbox | Stable effect ID, execution CASCADE FK, serialized effect |
| diagnostics | Local timestamped messages, retained to 1,000 rows |
| event_ledger | Stable trigger identity/outcome, independent of pruned execution history |
| location_events | Deduplicated transition/timestamp, location index |
| checklist_templates | UUID, name, ordered required/optional items, notes/groups, update time |

Pending interactions, frames, values, captured transitive libraries, branches, deadlines and notification-budget timestamps are part of the execution snapshot. Editing configuration never mutates old runs.

Migrations: 1→2 adds ledger/location events and seeds deduplication from existing executions; 2→3 adds checklist templates; 3→4 adds dwell/cooldown with 120/30-second SQL defaults; 4→5 adds optional icon with an empty-string default. Native migration validation exercises every exported version and retains an existing run/event identity.

Terminal execution retention is configurable (30 days by default); active runs are preserved. Transition details retain 90 days plus the latest of each kind; ledger retains 400 days plus the latest entry-session suppression record per automation. Deleting a location removes its transition history; deleting an automation removes its runs/outbox/values/ledger/draft after dependency checks. Clear history retains active runs and deduplication while cancelling owned messages from removed terminal rows.

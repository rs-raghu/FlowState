# Engineering decisions

- Use two modules (`engine`, `app`) with package boundaries rather than twenty mostly empty modules.
- Room transactions are the single writer boundary for state and scoped variables; no destructive migrations.
- Persist full definition snapshots for each run, plus versioned source/IR on automations.
- AlarmManager handles next occurrence; WorkManager handles recoverable execution/reconciliation. Inexact fallback is visible when exact access is absent.
- Geofence occupancy starts UNKNOWN, is timestamped, becomes stale, and never fabricates an exit. No continuous GPS polling.
- Loop, node, call-depth, value-size and step budgets apply to untrusted user input.
- Android toolchain is local under ignored `.tools`; credentials and APK outputs stay out of Git.
- Commit and push every completed stage or corrective improvement as requested.

Official references consulted 2026-10-09:

- https://developer.android.com/build/releases
- https://developer.android.com/build/kotlin-support
- https://developer.android.com/develop/ui/compose/bom
- https://developer.android.com/develop/background-work/services/alarms
- https://developer.android.com/develop/sensors-and-location/location/geofencing
- https://developer.android.com/develop/ui/views/layout/webapps/load-local-content
- https://developers.google.com/blockly/guides/configure/web/serialization

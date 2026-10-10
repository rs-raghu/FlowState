# Known limitations and final checks

Updated 2026-10-10. The prior functional gap inventory has been implemented. The final hosted device run must pass before the handoff is marked verified; current evidence and any failures are in TESTING.md.

## Physical acceptance

Actual geofence entry/exit/dwell timing, permission/location-service changes, reboot delivery, OEM restrictions and prolonged battery use require the user's phone. No battery consumption or movement timing is inferred from emulator tests. Follow PHONE_ACCEPTANCE.md after installing the final APK. These checks are acceptance work, not missing software to implement manually.

## Android constraints

Geofencing requires working Google Play Services, precise/background location and device location services. Events can be delayed by minutes. Occupancy becomes UNKNOWN after one hour without a fresh event. There is no continuous GPS loop. Exact-alarm denial/revocation uses visible inexact/WorkManager fallback; worker execution and periodic recovery can be delayed. Force-stop blocks background delivery until reopening. No OEM delivery/battery guarantee is made.

Notifications fit two direct choices plus Open choices; all choices/checklists remain in Activity. Android channel/user settings control actual sound/importance. Dismissed questions stay available without immediate reposting; explicit snooze/follow-up can request another reminder. At notification capacity, older informational messages can be replaced; pending questions remain in Activity with a diagnostic if display cannot be posted.

## Deliberate scope and bounds

The map is an offline geographic map, with coordinates, pan/zoom, tap placement and radius circle. Street tiles, address search and geocoding are not included. Integer increment/toggle use ordinary SET and expression blocks. Workflows execute native allowlisted operations; arbitrary scripts, email, remote API/app control, accounts and telemetry are outside this project.

Limits include 10,000 steps, 1,000 loop iterations, 100 steps/burst, eight nested CALLs, 16 parallel nesting levels and 64 branch executions, four active runs per automation and 32 globally including queued runs. Most workflow limits can be lowered in the trigger. Notification frequency is configurable from 1–120/minute per run and shared across calls/branches. Captured definitions are limited to 256 KB, snapshots to 2 MB, persistent values to 1 MB/10,000 entries, UTF-8 backups to 16 MB and JSON depth to 128. Budget exhaustion fails visibly; it cannot create an unrestricted background loop.

Backups contain sensitive personal data in plain JSON. Imports archive active history rather than resuming it, start updated/new automations disabled and keep existing definitions in merge mode. Room configuration/history writes are transactional; preference storage is separate DataStore and cannot share a cross-store atomic commit during a storage failure. Keep the original backup until restore is checked. Very large exports reject explicitly rather than omit deduplication records.

No personal private release key was supplied. The debug APK is installable; the normal R8 release is unsigned until the documented environment signing credentials are provided. Debug-signed optimized builds are for local/CI testing. Preserve the same private key for personal release updates.

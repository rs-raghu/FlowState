# Android permissions

Android 10/API 29 is the default minimum; compile SDK is 37.0 and target SDK is 37. `flowstate.minSdk` is configurable but values below 29 are not supported by this implementation.

1. Notifications: runtime POST_NOTIFICATIONS on API 33+. Denial leaves pending interactions in Activity and records delivery diagnostics.
2. Foreground location: request coarse + fine together; precise access is required for geofencing.
3. Background location: request separately on Android 10. On Android 11+, explain the need and open app settings for 'Allow all the time'. Foreground grant is never interpreted as a background grant.
4. Exact alarm special access: optional SCHEDULE_EXACT_ALARM on API 31+. Automatic inexact fallback; USE_EXACT_ALARM is not requested.
5. RECEIVE_BOOT_COMPLETED: restores supported registrations after unlocking/reboot. No always-running service is used.

Settings shows permission health, location service state, Play Services availability, exact-access state and diagnostic history. Use the app/battery settings shortcut to review manufacturer restrictions. Permission changes are reconciled when the app resumes and during recovery; exceptions caused by revocation are handled explicitly. There is no Internet permission, so core execution and map/editor assets remain offline.

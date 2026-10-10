# Final phone acceptance: geofences and battery

Use the final APK identified in BUILD_ARTIFACTS.md. Save a backup first if updating an existing installation. Record phone/model, Android version, Play Services version, APK SHA-256, permissions and battery optimization settings with the result. The software and emulator checks are performed by Codex; these physical checks are the user's final acceptance step.

## Geofence and recovery

1. Install/open FlowState. Save your actual home/hostel/class coordinates. Begin with a 150–300 m radius, minimum dwell 120 seconds and event cooldown 30 seconds. Replace bundled demo coordinates.
2. Grant notifications, precise and background location when enabling a location automation. Settings must show location services/Play Services ready and a successful registration; fix any displayed permission issue first.
3. Enable a simple EXIT workflow with a message or question. Start well inside, lock/background the phone, walk clearly beyond the radius and allow several minutes. Confirm one run, correct title/action and matching Activity/transition history. Duplicate deliveries must not create another run for the same event.
4. Repeat with ENTER and DWELL. Remain inside for at least the configured dwell duration and allow delivery delay. Verify distinct transitions and no rapid reminder storm near the boundary. Repeat once after cooldown.
5. While a question/timer is pending, reopen, rotate and reboot the phone. Unlock/open after reboot and inspect the original pending state, registration health and absence of duplicate runs. Force-stop requires reopening before further delivery.
6. Disable location services or remove location access, reopen and confirm truthful status without a crash. Restore access/services and confirm reconciliation. Disable the automation; further movement must not create automatic runs.

## Battery

1. Record battery use for a comparable baseline period without enabled location workflows.
2. Enable the intended routines and use the phone normally for at least 24 hours, including screen-off periods and a few real transitions. Note total/app battery use, charging intervals, wake behavior and notification delivery. FlowState should not need a persistent foreground-service notification or continuous GPS session.
3. Inspect Android Settings → Battery → FlowState. Compare with the baseline; investigate unusual drain or repeated transitions using FlowState's private diagnostic report/history. Check the phone manufacturer's background restrictions if delivery is suppressed. Record any changed optimization setting.
4. Accept only after delivery and battery behavior are satisfactory for your phone. Report the reproduction steps and private diagnostic details for any failure; emulator success cannot certify those measurements.

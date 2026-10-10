# User guide

Complete the short introduction. Create an automation in Automations, give it a name and select one of eleven editable presets. Location presets require a saved location. All presets save disabled so you can inspect/simulate before enabling.

The full-screen offline editor has categorized blocks, search, Undo/Redo, zoom and Arrange. Connect actions beneath a single trigger. Long-press blocks for duplication/deletion/help. Validate and Save run the native compiler; errors highlight the owning block with a correction. Foreground changes are saved as a recovery draft, restored after recreation. Back asks before discarding unsaved changes.

Select manual, time or location on the trigger. Time fields accept comma-separated clocks, days Monday=1…Sunday=7, YYYY-MM-DD dates and `device` or an IANA timezone. Recurrences include daily/weekly/monthly windows. Catch-up can skip, record, use grace/window or ask. Set cooldown, frequency, concurrency (parallel/ignore/queue/replace), maximum active instances and priority. Trigger budgets configure steps, iterations, burst size, notification rate and CALL depth.

Locations support coordinates, current-position selection, offline geographic pin/pan/zoom/radius, description, optional icon, enabled state, minimum dwell and event cooldown. Radius presets are 100/150/200/300/500/1000 m; custom values are bounded. Street/address search is absent. Replace demo coordinates before enabling. Optional days/date/time eligibility applies to location triggers; overnight windows belong to their starting day. Settings explains precise/background location and shows registration health.

Use IF/SWITCH and typed expressions for decisions. Declare local/automation/global variables before GET/SET; explicit conversions are available. Typed lists enforce their element type. ASK supports independent choice branches or yes/no/text/number/confirmation; responses can fill declared local values. Reusable CALL blocks bind multiple named inputs/outputs and a STRING status result. Enable saved targets before running callers. Old runs keep their captured target version.

Messages/questions support low/normal/high channels, category/group/ongoing/expiry and named owned update/cancel targets. Configure bounded snooze choices and one follow-up. Notifications offer two direct choices plus Open choices; Activity has every choice. Dismissal keeps a question in Activity without immediate reposting. Late/duplicate responses are rejected. Checklist Manager creates/edits/duplicates ordered templates with required/optional items, notes and groups; running instances capture their items independently.

PARALLEL advances both branches before JOIN. Waits/questions do not block siblings. Use separate local names for different branch results; conflicting local writes fail visibly. Parent cancellation cancels both branches. WAIT and overall deadlines persist without holding a thread. TRY can handle a visible runtime error.

The simulator uses an isolated copy: clock/timezone, fake events, responses, scoped values, permissions and location coordinates/history. Step into/over/out, breakpoints, branch inspection and replay explain behavior without changing production data or posting notifications. Debug from Activity uses the captured run, including its old dependency library.

Settings includes theme/dynamic color/time format and validated personal defaults; those defaults affect new blocks/locations. Health/diagnostics/test actions and a private report explain current state. Help and documentation includes an offline guide. Confirmed data actions clear terminal history, reset persistent values after cancelling active runs, or delete all data. Export a backup before deletion.

Export/Import uses Android's document picker. Backup schema 2 includes workspaces/locations/templates/values/recent history/ledger/settings. New imports reject duplicate IDs, merge keeps existing resources by name, overwrite updates matching IDs. Imported active histories are archived CANCELLED rather than resumed, and changed/new automations start disabled. Keep the original file until checking restore.

Add example workflows on Dashboard/Settings installs eight ordinary editable scenarios (ten workflows) with fresh IDs and three demo locations. They include reusable references; enable the checklist target before its caller. See PHONE_ACCEPTANCE.md for final real movement/reboot/battery checks.

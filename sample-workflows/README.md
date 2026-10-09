# Editable examples

Import `examples.json` through Settings → Import backup into a clean installation. It contains the eight requested scenarios as ten workflows (persistent-variable and reusable examples each need two definitions), plus three clearly named demo locations. Imported workflows start disabled. Update the demonstration coordinates before enabling location workflows; enable Essential Items Checklist before running Going Out. All examples are ordinary Blockly workspaces and native IR, with no special runtime handlers.

1. Leaving Hostel: exit, weekday IF, four choices, nested Lecture/Lab and separate checklists.
2. Morning Routine: 07:30 weekdays, message, Yes/No, nested Study/Relax.
3. Weekly Task: Friday 09:00 window, once-per-window identity and current-window catch-up.
4. Delayed Follow-up: gym exit, 15-minute wait, question, No waits 10 minutes and asks once more. An unanswered final question expires after 10 minutes; an answered question completes.
5. Prepare Tomorrow / Preparation Check: evening Yes/No writes global Boolean `prepared`; morning reads it then resets false. Execution order is defined by their schedules; manual runs use the same data.
6. Notification Timeout: hostel exit, three choices, 15-minute deadline, default expiration with no timeout branch.
7. Nested Conditions: academic entry, weekday AND 08:00–17:00, question or alternate message.
8. Going Out / Essential Items Checklist: choice calls the reusable checklist and continues on completion.

Regenerate using `node blockly-editor/samples.mjs`. IDs are deterministic. JVM tests compile every workspace and exercise completion plus timeout paths; Blockly tests load, serialize, and reload every example. Device triggers and notification rendering remain separate device checks.

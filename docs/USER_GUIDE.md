# User guide

Open FlowState and complete the short introduction. Settings provides individual permission actions; configure only features you use.

Create an automation from Dashboard or Automations. Pick an editable template and name it. In the full-screen editor, expand categories, drag blocks, and snap them below the single trigger. Long-press for duplication, deletion/collapse or help. Use Undo/Redo, zoom, Arrange and Find block. Save validates natively and stores both workspace and executable definition. Invalid blocks get warning markers. Back asks before discarding unsaved changes.

For time automation select `time`, choose clock times separated by commas and the recurrence. Day values use Monday=1 through Sunday=7. Dates are YYYY-MM-DD. `device` follows the device zone; other values are IANA names. For location automation first add a saved location, then select it in the trigger dropdown. Manual coordinates, explicit current-position request and an offline country map pin are available. Show radius zooms around its boundary. Street search is unavailable.

Drag IF blocks and connect Boolean expressions. Nested decisions are ordinary blocks. ASK CHOICE accepts labels separated by `|` and adds one independently connected branch per choice. ASK YES/NO uses the first/second branches. Text/number answers may be stored in a declared local variable. Number answers are Decimal. For persistent memory declare an automation/global variable, then use SET. Checklist entries with `?` are optional; progress is saved as you tick items.

Manual Run starts a compatible saved workflow. Enable its switch for automatic starts. Activity contains pending questions, checklists, cancellation, bounded snooze, wait deadlines and private trace. Notifications show at most two direct choices plus Open choices. A late/duplicate tap cannot resume an invalid interaction.

Simulate from the editor to use a fake clock and inspect each block. Step/Run, pause, restart, wait-time advance and fake responses do not modify production state or schedule alarms. Debug from history uses that run's definition snapshot. Settings exports validated local workspace/location backups; imports start disabled and reject duplicate IDs. See IMPLEMENTATION_STATUS.md for advanced controls still absent.

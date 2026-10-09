# Block reference

All custom block identifiers have `fs_` prefixes. Every block has a stable Blockly instance ID, version-1 semantics, local serialization, category colour, tooltip and native validation. There is one trigger root; disconnected executable stacks are rejected.

| Block | Inputs and behavior | Error behavior |
|---|---|---|
| trigger | Manual/time/location, recurrence, timezone, dates, days, cooldown, missed policy; resource dropdown | Invalid schedules/resources reject saving |
| value | Type and literal text | Malformed or oversized values rejected |
| expr | Operation and up to three typed values | Missing/type-incompatible inputs rejected; division by zero fails visibly |
| variable/get/set/delete | Name, scope, type/default or expression; reset produces Null | Undeclared or incompatible assignment rejected |
| if/switch | Boolean or comparable value; nested statement stacks | Conditions must be Boolean; no coercion |
| message/notifyCancel | Title/body; owned notification cancellation | Permission denial is recorded; workflow continues |
| ask | Choice/yesno/text/number/confirm, title, choices, local response variable, bounds, timeout and branches | Invalid input remains pending; stale tokens ignored |
| checklist | Required/optional items, completion/cancel/timeout branches | Required items must be completed; individual progress is persisted |
| wait/waitUntil/waitClock/waitCondition | Duration, timestamp, next local clock time with timezone, or bounded condition polling | Clock waits select tomorrow after today's time; DST follows schedule rules; polling minimum 60 s |
| setTimeout | Persisted execution deadline from current time | Caps waits and expires the execution; late responses cannot bypass it |
| repeat/while | Body and maximum iterations | Loop/step budgets fail safely |
| break/continue | Exit/advance nearest loop frame | Native validation rejects use outside a loop |
| call/return | Saved workflow, named input/value, named local output; return expression | Missing/disabled target, type mismatch or recursion rejected/fails visibly |
| try | Body and error handler | Unhandled failures set FAILED with diagnostic trace |
| parallel | Independent A/B progress with deterministic round robin and JOIN | Isolated locals merge; conflicting writes fail; ordered shared persistent writes; independent questions; parent cancellation |
| stop/log/assert/breakpoint | End, private trace, Boolean assertion or simulator pause | Breakpoints pause simulation after the marker; production records and continues |

Expression operations: Boolean AND/OR/XOR/NOT; equality and ordered comparisons; range/empty/contains/prefix/suffix; arithmetic; concatenation/trim/case/length; list append/remove/item/join; explicit string/number conversion; current instant/date/time/weekday/month/weekend; overnight time windows, before/after; elapsed duration, timestamp addition/subtraction, formatting; fresh occupancy.

List literals use `|` separators. Choice/checklist labels use `|`; labels cannot contain this separator. ASK NUMBER produces Decimal. Current date/time are ISO strings. Occupancy is a string INSIDE/OUTSIDE/UNKNOWN; use equality to test it. A result older than one hour becomes UNKNOWN. Several richer block families from the brief remain absent; see IMPLEMENTATION_STATUS.md.

# MASTER ENGINEERING PROMPT
# PROJECT: FLOWSTATE — PERSONAL VISUAL AUTOMATION ENGINE FOR ANDROID

## 0. YOUR ROLE AND MISSION

Act as a coordinated team of senior software engineering specialists:

1. Principal Android Architect
2. Senior Kotlin and Jetpack Compose Developer
3. Visual Programming Language / Blockly Engineer
4. Workflow Engine and State Machine Architect
5. Android Background Execution Specialist
6. Database and Persistence Engineer
7. Android Security and Privacy Engineer
8. UI/UX Designer
9. Senior QA Automation and Reliability Engineer
10. Build, Release and DevOps Engineer

Your mission is to design, implement, test, audit, and package a complete, production-quality Android application called **FlowState**.

FlowState is a privately installed, offline-first, visual automation application for a single Android user.

The user must be able to construct arbitrarily complex, valid personal automation workflows using draggable, connectable programming blocks without writing code.

The application must support:

- Time-based triggers.
- Location-based triggers.
- A visual drag-and-drop block editor.
- Conditional logic.
- Nested decisions.
- User questions and responses.
- Interactive notifications.
- Checklists.
- Variables and persistent memory.
- Wait and resume operations.
- Loops with execution safeguards.
- Reusable sub-workflows.
- Workflow debugging and simulation.
- Trigger and execution history.
- Import, export and backup.
- Reliable execution under Android background restrictions.
- Comprehensive configuration and diagnostics.

The final deliverable must be a functional Android application, not a prototype, mockup, landing page, or proof of concept.

### Core instructions

- Do not stop after creating the initial project structure.
- Do not replace required functionality with placeholder screens.
- Do not silently omit difficult features.
- Do not claim that a feature works without implementing and testing it.
- Do not create fake implementations that always return successful results.
- Do not introduce unnecessary cloud infrastructure.
- Do not depend on Firebase or any remote backend for core functionality.
- Do not generate a web-only app.
- Do not use a WebView for the entire application; use it specifically for the Blockly editor.
- Do not implement workflow execution through unrestricted JavaScript evaluation.
- Do not rely on long-running background services for ordinary location and time monitoring.
- Do not hardcode the user's workflows, locations or decisions.
- Do not silently swallow exceptions.
- Do not suppress tests or compiler errors merely to obtain a successful build.
- Do not claim that Android guarantees exact background execution where the operating system does not provide such guarantees.
- Do not make destructive changes outside the project workspace.

You have authority to make reasonable technical decisions.

Whenever a requested feature is limited by Android, implement the best compliant solution and clearly document the limitation.

When a platform API, dependency or implementation detail is uncertain, verify it against its official documentation before coding.

Prioritize correctness, reliability, security, maintainability and usability.

---

# PART 1 — PRODUCT VISION

## 1.1 Application concept

FlowState is a personal automation engine that converts real-world events into customizable workflows.

The core conceptual model is:

TRIGGER → CONDITIONS → DECISIONS → ACTIONS → WORKFLOW CONTINUATION

Example:

The user exits their hostel's 150-metre geofence.

FlowState detects the exit and checks whether the automation is enabled and its conditions are satisfied.

It displays:

"Where are you going?"

Possible choices:

- Class
- Mess
- Gym
- Going out
- Other

If the user selects Class:

Ask:

"Lecture or Lab?"

If Lecture:

Display a configurable lecture checklist.

If Lab:

Display a configurable lab checklist.

Once the checklist is completed, the workflow may wait, display another question, update a variable, execute a reusable workflow, or terminate.

Everything must be editable through visual blocks.

## 1.2 Core philosophy

The user creates their own logic.

The developer creates the tools needed to construct and execute that logic.

There must be no unnecessary restriction to predefined activities such as Class, Gym or Mess.

Those are examples, not hardcoded application behaviors.

## 1.3 Distribution

The app is for personal use on Android.

Requirements:

- No Google Play Store publication needed.
- No user account.
- No mandatory login.
- No subscription.
- No mandatory server.
- No analytics SDK.
- No advertisements.
- No telemetry collection.
- No internet requirement for executing saved workflows.
- Support direct APK installation.

A network connection may be needed for map tiles, first-time dependency setup, or optional services, but the automation engine must remain functional without internet.

## 1.4 Definition of done

The project is complete only when:

1. The Android project builds successfully.
2. An installable debug APK is generated.
3. A release build configuration is available.
4. Saved automations can be created, edited, enabled and disabled.
5. Both time and geofence triggers are functional on supported devices.
6. Blockly workspaces persist correctly.
7. Saved blocks compile into validated executable workflows.
8. Workflows run correctly without the editor being open.
9. Notifications and their actions work.
10. Suspended workflows resume correctly.
11. Important execution state survives process death and device restart where supported.
12. The application handles missing permissions safely.
13. Automated tests pass.
14. Background execution limitations are documented and exposed appropriately in the UI.
15. No critical or high-severity defects remain unaddressed.
16. All deliverables and known limitations are documented accurately.

---

# PART 2 — TECHNOLOGY STACK

Use the following default technologies unless a clearly better supported alternative exists.

## 2.1 Android

- Kotlin.
- Jetpack Compose.
- Material Design 3.
- AndroidX lifecycle components.
- Kotlin Coroutines.
- StateFlow.
- Hilt for dependency injection.
- Room database.
- DataStore preferences.
- Navigation Compose.
- WorkManager.
- AlarmManager.
- Android Notification APIs.
- Google Play Services Geofencing API.

Use current stable, mutually compatible dependency versions.

Do not invent dependency versions.

Verify current Android Gradle Plugin, Kotlin, Compose, Blockly and library compatibility before configuring the build.

Select an appropriate supported compile SDK and target SDK. Make minSdk configurable, with Android 10 / API 29 as the preferred minimum if technically suitable.

Document any differences in behavior across supported Android versions.

## 2.2 Visual editor

Use Google Blockly.

Embed the editor in a locally bundled HTML/JavaScript application loaded into an Android WebView.

Bundle all runtime assets locally.

Do not depend on a remotely hosted Blockly editor.

Implement:

- Android-native wrapper.
- Secure JavaScript bridge.
- Custom automation blocks.
- Custom Blockly toolbox.
- Block serialization.
- Workspace restoration.
- Touch-friendly editing.
- Undo/redo.
- Zoom and pan.
- Block search.
- Validation feedback.
- Import and export.

Use Blockly's supported JSON workspace serialization.

Do not use deprecated APIs if maintained alternatives exist.

## 2.3 Workflow execution

Implement a native Kotlin execution engine.

The Blockly editor is responsible for constructing workflow definitions.

The native interpreter is responsible for executing them.

Do not generate unrestricted Kotlin, JavaScript or shell scripts from user blocks.

Use a versioned, validated, typed intermediate representation.

## 2.4 Architecture

Use Clean Architecture and MVVM.

Suggested modules:

- app
- core:model
- core:database
- core:common
- core:permissions
- core:logging
- core:security
- feature:dashboard
- feature:locations
- feature:triggers
- feature:editor
- feature:workflows
- feature:notifications
- feature:history
- feature:simulator
- feature:settings
- engine:compiler
- engine:runtime
- engine:scheduler
- engine:geofencing
- engine:expression

Adjust the module boundaries if necessary, but preserve separation of concerns.

Avoid circular dependencies and unnecessary abstraction.

---

# PART 3 — APPLICATION NAVIGATION AND UI

## 3.1 Main navigation

Create these primary sections:

1. Dashboard
2. Automations
3. Locations
4. Activity
5. Settings

The Workflow Editor should be accessible from automation creation and editing.

The Debugger should be accessible from an automation's details screen and execution history.

## 3.2 Dashboard

Display:

- Number of active automations.
- Number of disabled automations.
- Next scheduled time trigger.
- Current pending workflow interactions.
- Recent automation executions.
- Trigger failures.
- Permission warnings.
- Quick automation creation.
- Manual execution shortcuts.
- Recently edited workflows.

Provide a clear empty state when no workflows exist.

Never imply that geofence monitoring is active if registration failed.

## 3.3 Automations screen

Support:

- Search.
- Sorting.
- Filtering.
- Enable/disable.
- Duplicate.
- Rename.
- Delete with confirmation.
- Import/export.
- Open editor.
- View trigger configuration.
- View execution statistics.
- Test automation.
- Inspect errors.

Automation cards should display trigger type, name, current state and last execution.

## 3.4 Locations screen

Support saving locations using:

- Map pin placement.
- Search, if a map provider supports it.
- Latitude/longitude entry.
- Current device position with explicit permission.
- Manual editing.

Each location must have:

- Stable identifier.
- Name.
- Coordinates.
- Radius in metres.
- Optional description.
- Optional icon.
- Enabled state.
- Creation and modification timestamps.

Minimum allowed radius: 100 metres.

Provide presets such as:

100 m, 150 m, 200 m, 300 m, 500 m and 1000 m.

Allow larger custom values within reasonable validation limits.

Show an actual radius circle on the map.

Explain that the geofence radius is not a guarantee of metre-level detection precision or immediate event delivery.

## 3.5 Workflow Editor

This is the most important feature.

Design for portrait and landscape layouts.

Support:

- Draggable blocks.
- Snap connections.
- Nested blocks.
- Typed expression inputs.
- Expandable block categories.
- Pinch-to-zoom.
- Workspace panning.
- Undo and redo.
- Block duplication.
- Block deletion.
- Collapse/expand.
- Workspace cleanup.
- Auto-arrange when practical.
- Block search.
- Editable labels.
- Context-sensitive block help.
- Save.
- Save as copy.
- Validate.
- Run simulation.
- Execution highlighting.
- Unsaved changes warning.

The editor must remain usable on a normal Android phone.

Avoid tiny click targets.

Provide a fullscreen editor mode and an optional properties panel for complicated block configuration.

Saving must persist a complete, restorable Blockly workspace and a separately compiled executable definition.

A workflow should not be marked executable merely because Blockly produced syntactically valid JSON.

---

# PART 4 — BLOCK PROGRAMMING LANGUAGE

Implement a typed, extensible block system.

Every block must have:

- Unique type identifier.
- Stable instance identifier.
- Version.
- Display name.
- Category.
- Typed inputs.
- Outputs.
- Validation rules.
- Execution semantics where applicable.
- Description.
- Error behavior.
- Serialization support.

Organize the toolbox into logical categories.

## 4.1 Trigger blocks

Initial supported trigger types:

### Location

- Enter geofence.
- Exit geofence.
- Dwell in geofence, if reliably implementable using supported platform mechanisms.
- Selected saved location.
- Configurable radius.
- Trigger cooldown.
- Eligible days.
- Eligible time ranges.

Treat dwell as part of the location trigger family, not a separate sensor integration.

### Time

- At specific local time.
- Once at selected date/time.
- Every day.
- Selected weekdays.
- Weekly.
- Monthly.
- Selected calendar dates.
- Every N days.
- Multiple configured clock times.
- Date range.
- Start/end date.

Support timezone-aware scheduling.

Define behavior for:

- Timezone changes.
- Device clock changes.
- Daylight saving transitions.
- Missed occurrences.
- Device reboot.
- Scheduling permission revocation.

Use explicit policies for ambiguous and nonexistent local times.

Do not assume a periodic WorkManager job executes at an exact wall-clock time.

## 4.2 Logical blocks

Implement:

- IF.
- IF / ELSE.
- ELSE IF.
- AND.
- OR.
- NOT.
- XOR.
- Equal.
- Not equal.
- Greater than.
- Less than.
- Greater than or equal.
- Less than or equal.
- IN RANGE.
- IS EMPTY.
- IS NOT EMPTY.
- CONTAINS.
- STARTS WITH.
- ENDS WITH.
- SWITCH / CASE.
- Boolean constants.

Logical expressions must be type-checked.

No implicit string-to-number coercion.

Define short-circuit behavior for AND and OR.

## 4.3 Time and date blocks

- Current date.
- Current local time.
- Current weekday.
- Current month.
- Is weekend.
- Is weekday.
- Before time.
- After time.
- Between two times.
- Date comparison.
- Add duration.
- Subtract duration.
- Time elapsed since stored timestamp.
- Format date/time.

Time comparisons crossing midnight must be handled correctly.

Example:

22:00 to 02:00 is a valid overnight time range.

## 4.4 Location condition blocks

- Is currently inside saved geofence.
- Is currently outside saved geofence.
- Last known entry timestamp.
- Last known exit timestamp.
- Last detected location event.
- Time since last entry/exit.
- Last known geofence occupancy state.

Important:

Do not present stale or unknown occupancy as definite.

Model occupancy as:

INSIDE / OUTSIDE / UNKNOWN

Include timestamp and confidence/freshness metadata where applicable.

Do not continuously poll precise location for every condition.

Use existing geofence state and permitted location requests as appropriate.

## 4.5 User interaction blocks

Implement:

ASK CHOICE
- Question.
- Configurable choices.
- User-selectable options.
- Output for each option.
- Optional cancellation branch.
- Optional timeout branch.

ASK YES/NO
- Yes path.
- No path.
- Timeout path.

ASK TEXT
- User-entered text.
- Validation.
- Store response in variable.

ASK NUMBER
- Numeric input.
- Min/max validation.
- Store result.

SHOW CHECKLIST
- Configurable checklist items.
- Individual completion state.
- Required/optional items.
- Completion branch.
- Cancel branch.

SHOW MESSAGE
- Title.
- Body.
- Optional priority.
- Optional action.

CONFIRM ACTION
- Proceed/cancel branching.

User interaction nodes must suspend the workflow without holding a running thread, service or coroutine indefinitely.

Persist the continuation state.

## 4.6 Notification blocks

- Send notification.
- Send notification with choices.
- Send reminder.
- Update existing notification.
- Cancel owned notification.
- Notification priority/category where supported.
- Notification channel selection.
- Mark as ongoing when appropriate.
- Group notifications.
- Delay reminder.
- Expire notification.

Support:

- Open workflow.
- Mark complete.
- Snooze.
- Dismiss.
- Cancel.
- Custom choice buttons.

Comply with Android notification permission requirements.

Never assume unlimited notification buttons fit in a notification.

When options exceed platform limits, show a compact subset and provide an "Open choices" action that opens the app.

Notification actions must be safe, authenticated by app-owned pending intents and associated with the correct execution instance.

## 4.7 Control flow blocks

- WAIT duration.
- WAIT until time.
- WAIT until date/time.
- WAIT for user response.
- WAIT until condition, with bounded re-evaluation.
- REPEAT N times.
- WHILE condition.
- BREAK.
- CONTINUE.
- STOP workflow.
- RETURN from sub-workflow.
- CALL sub-workflow.
- TRY / HANDLE ERROR.
- SET TIMEOUT.
- DELAY next action.
- PARALLEL branches where explicitly supported.
- JOIN branches.

Every loop must have safeguards.

Do not permit uncontrolled background spinning.

Set configurable runtime budgets, maximum iterations and minimum scheduling intervals as appropriate.

If a loop or wait exceeds limits, suspend, fail safely, or terminate with a visible diagnostic.

Parallel branches must have defined variable and cancellation semantics.

If true parallelism is unnecessarily risky, implement deterministic interleaved execution while preserving documented branch behavior.

## 4.8 Variable blocks

Support:

- Create variable.
- Set variable.
- Get variable.
- Increment/decrement.
- Boolean toggle.
- String concatenation.
- Arithmetic.
- Reset variable.
- Delete variable value where permitted.

Variable types:

- Boolean.
- Integer.
- Decimal.
- String.
- Date/time.
- Duration.
- String list.
- Typed list where practical.
- Null/optional.

Scopes:

1. Workflow execution local.
2. Automation persistent.
3. Application global.

Persist appropriate variables.

Provide clear naming, type validation and scope display.

Prevent accidental collisions between scopes.

Support default values.

## 4.9 List and string operations

Add:

- Create list.
- Append.
- Remove.
- List length.
- Contains item.
- Get item.
- Join strings.
- String length.
- Trim.
- Convert supported types explicitly.
- Case conversion.
- Basic formatting.

Guard against extremely large lists or values.

## 4.10 Reusable workflow blocks

Implement CALL WORKFLOW.

Support:

- Selecting another workflow.
- Input parameters.
- Output values.
- Returning execution status.
- Passing values by defined semantics.
- Failure propagation.
- Cancel propagation.

Detect recursive call cycles.

Apply maximum call-depth protection.

Reject invalid references.

If a referenced workflow is deleted or disabled, define and test predictable behavior.

## 4.11 Debug blocks

- Log message.
- Inspect variable.
- Assert condition.
- Debug breakpoint, simulation only.
- Trace marker.

Debug operations must never leak sensitive personal data into public or remotely transmitted logs.

---

# PART 5 — WORKFLOW COMPILER AND VALIDATION

Implement a compiler that converts Blockly workspace data into a typed intermediate representation.

Pipeline:

Blockly Workspace JSON
→ Parse
→ Validate Structure
→ Validate Types
→ Validate References
→ Validate Control Flow
→ Generate Versioned Workflow IR
→ Persist
→ Execute

The editor state and executable workflow must be stored separately.

Use a deterministic compilation process.

## 5.1 Validation

Detect:

- Disconnected executable blocks.
- Missing required inputs.
- Invalid block combinations.
- Unsupported block types.
- Invalid variable references.
- Type mismatches.
- Missing branches.
- Invalid location IDs.
- Invalid workflow references.
- Recursive workflow cycles.
- Impossible or invalid schedule definitions.
- Zero-duration unbounded loops.
- Invalid numeric ranges.
- Unsupported expression operations.
- Malformed serialization.
- Duplicate identifiers where uniqueness is required.

Every validation error must include:

- Error code.
- Severity.
- Human-readable message.
- Relevant block identifier.
- Recommended correction.

The editor must visually highlight the corresponding block.

Provide warnings for suspicious but legal configurations.

## 5.2 Intermediate representation

Create a versioned executable model with:

- Workflow identifier.
- Definition version.
- Entry point.
- Node definitions.
- Typed parameters.
- Directed connections.
- Branch outcomes.
- Variable definitions.
- Execution policies.
- Dependency references.

Use stable schema versions and migrations.

Do not use arbitrary serialized object graphs that cannot be safely validated.

## 5.3 Execution snapshots

When a workflow starts, bind that execution to a specific workflow definition version.

Editing the source workflow must not silently change already-running executions.

Define policies for disabled or deleted workflows with suspended executions:

- Continue existing execution.
- Gracefully cancel.
- Ask the user.

Choose safe defaults and expose settings where appropriate.

---

# PART 6 — NATIVE WORKFLOW EXECUTION ENGINE

Implement an explicit state machine.

Execution states:

- CREATED
- QUEUED
- RUNNING
- WAITING_FOR_USER
- WAITING_FOR_TIME
- WAITING_FOR_CONDITION
- PAUSED
- COMPLETED
- CANCELLED
- FAILED
- EXPIRED

The runtime must be able to:

1. Load a compiled workflow.
2. Create an execution instance.
3. Evaluate conditions.
4. Execute actions.
5. Follow correct branches.
6. Persist execution state.
7. Suspend when required.
8. Resume after an external event.
9. Recover from interruption.
10. Finish cleanly.

## 6.1 Persistence

Persist:

- Execution ID.
- Workflow ID.
- Workflow version.
- Current node/continuation.
- Execution state.
- Local variables.
- User responses.
- Pending timers.
- Pending notification interaction IDs.
- Retry count.
- Error state.
- Start and update timestamps.
- Idempotency keys.

Use transactional database writes.

## 6.2 Idempotency

Android receivers and background workers may execute more than once.

Design the engine for at-least-once event delivery with idempotent processing.

A repeated trigger event must not accidentally create repeated identical workflow executions.

A repeated notification tap must not execute the same action twice.

A retried worker must not duplicate a completed side effect.

Use stable event IDs, unique constraints, atomic state transitions and action execution records.

Define at-most-once or effectively-once behavior per action wherever feasible.

Do not claim exactly-once delivery across arbitrary external side effects.

## 6.3 Concurrency

Support concurrent independent workflow executions.

Define:

- Per-automation concurrency policy.
- Queue behavior.
- Maximum active instances.
- Replace/ignore/queue choices.
- Global execution limits.
- Cancellation rules.
- Variable synchronization rules.

Avoid deadlocks and races.

## 6.4 Error handling

Classify errors:

- User configuration errors.
- Permission failures.
- Platform limitations.
- Temporary runtime failures.
- Missing dependencies.
- Corrupt data.
- Execution timeouts.
- Invalid state transitions.

Support bounded retry policies with exponential backoff when appropriate.

Do not retry permanent configuration errors indefinitely.

Expose actionable failure information in the UI.

---

# PART 7 — LOCATION TRIGGER ENGINE

Use Android's supported Geofencing API.

## 7.1 Required behavior

Support:

- Saved locations.
- Enter detection.
- Exit detection.
- Configurable radius.
- Geofence registration.
- Geofence removal.
- Registration synchronization.
- Enabled/disabled state.
- Monitoring diagnostics.
- Location event history.

Use a minimum configurable radius of 100 m.

Do not promise instantaneous or precise boundary-crossing detection.

## 7.2 Permissions

Handle:

- Fine location.
- Coarse location.
- Background location.
- Location services disabled.
- Google Play Services unavailable.
- Location permission revoked.
- Approximate location granted without precise location.
- Battery restrictions.
- Device-specific background restrictions.

Request permissions in the appropriate Android-supported sequence.

Explain why background location is required before requesting it.

Do not assume background access is available merely because foreground location was granted.

Provide degraded operation where possible.

## 7.3 Geofence lifecycle

Register geofences when relevant configuration changes.

Reconcile registrations:

- After app setup.
- After location modification.
- After permission changes.
- After relevant device restart events.
- After Google Play Services or registration failures where recovery is possible.

Account for geofence registration limits.

Do not assume all configured geofences can be monitored if the device or platform limit is exceeded.

Show monitoring failures visibly.

## 7.4 Debouncing

Support:

- Per-geofence cooldown.
- Per-automation cooldown.
- Duplicate-event suppression.
- Minimum dwell policy.
- Trigger frequency limits.
- Optional entry/exit confirmation strategy.

Do not implement endless high-frequency GPS polling to improve accuracy.

Preserve battery life.

## 7.5 Initial state

A newly registered geofence might have unknown or initial occupancy state.

Do not fabricate an exit event.

Clearly define how initial state transitions are handled.

---

# PART 8 — TIME TRIGGER ENGINE

Implement precise user-facing scheduling using appropriate AlarmManager APIs when allowed.

Use WorkManager for deferrable persistence and recovery tasks.

Do not use WorkManager as an exact clock alarm system.

## 8.1 Scheduling modes

Support:

- Exact when permission and platform policy allow.
- Inexact fallback.
- Repeating schedules through calculation of next occurrence.
- One-time events.
- Local timezone-based schedules.
- Absolute instant schedules.

Explain scheduling precision in the UI.

## 8.2 Exact alarm permissions

Check supported permission state at runtime.

Handle:

- Permission granted.
- Permission denied.
- Permission revoked after scheduling.
- Device version differences.
- Permission settings changes.

Do not misuse USE_EXACT_ALARM permission.

Use only permissions appropriate for the app's actual purpose and supported Android behavior.

## 8.3 Reliability

Handle:

- Device reboot.
- Timezone changes.
- Manual clock changes.
- Daylight saving changes.
- Missed alarms.
- App package replacement.
- Duplicate scheduling requests.
- Disabled automations.
- Deleted automations.

Schedule only the next required occurrence where practical.

Avoid generating excessive alarms.

Use stable scheduling identifiers.

## 8.4 Weekly-window semantics

This is a critical correctness requirement.

A recurring time window must be modeled independently from whether the app happened to execute on a specific weekday.

Example:

An automation is eligible once per weekly window.

Window starts Friday at 00:00 and ends at the following Friday at 00:00, end-exclusive.

If the execution is missed on Friday, and the configured catch-up policy permits it, the automation must still be eligible later in the same weekly window.

Do not implement logic equivalent to:

"Run only when today is Friday."

Instead use:

- Stable window identification.
- Execution history within the window.
- Explicit eligible period.
- Catch-up policy.
- Maximum executions per window.

Apply equivalent semantics to daily and monthly windows when configured.

Support policies:

- Skip missed event.
- Run once when recovered within grace period.
- Run once before the window ends.
- Ask user.
- Record as missed without executing.

Avoid accidental catch-up storms after the device has been offline.

---

# PART 9 — INTERACTIVE NOTIFICATIONS

Notifications are a central interaction surface.

## 9.1 Notification types

- Informational.
- Question.
- Checklist reminder.
- Pending workflow.
- High-priority user-configured reminder.
- Failure/diagnostic alert.
- Expiration notice.

## 9.2 Interactive actions

Examples:

A notification says:

"Where are you going?"

Actions:

- Class
- Mess
- Open choices

Tapping Class must resume the correct workflow execution at the correct branch.

Notification interactions must work without the user manually opening the application first, subject to platform constraints.

Use safe PendingIntent configurations and Android-compliant background execution behavior.

## 9.3 Expiration

Every interactive notification should support an explicit expiration policy.

Possible policies:

- Never expire until manually dismissed.
- Expire after N minutes.
- Expire at a time.
- Expire when a related workflow completes.
- Expire when replaced.
- Expire after a configured triggering window closes.

Expired responses must not resume invalid executions.

## 9.4 Snooze

Support:

- Snooze 5 minutes.
- Snooze 15 minutes.
- Snooze 30 minutes.
- Custom permitted duration.
- Maximum snooze count.
- Snooze cancellation.

## 9.5 Notification grouping

If multiple workflows generate compatible reminders, optionally group them.

Do not merge independent user decisions in ways that change workflow semantics.

Maintain a clear mapping between notification actions and execution instances.

---

# PART 10 — ADVANCED AUTOMATION FEATURES

## 10.1 Reusable checklists

Create checklist templates.

Support:

- Add/remove items.
- Reorder items.
- Required/optional.
- Completion tracking.
- Duplicate template.
- Reuse across workflows.

Checklist instances must have independent completion states.

## 10.2 Smart reminder bundles

Allow grouping of related reminders into a single interaction.

Example:

- Take ID card.
- Take wallet.
- Take smartwatch.

Group only when semantically safe.

## 10.3 Workflow priorities

Allow:

- Low.
- Normal.
- High.

Use priorities for display and queue ordering.

Do not attempt to bypass Android notification restrictions.

## 10.4 Cooldown policies

Support:

- Once per N minutes.
- Once per day.
- Once per configured weekly window.
- Once per location entry session.
- Once until completed.
- Unlimited, subject to safety limits.

## 10.5 Manual workflow execution

Allow users to manually start any compatible workflow.

Manual starts are an explicit user action, not a third automatic trigger category.

## 10.6 Automation templates

Provide editable sample templates:

- Leaving Hostel.
- Going to Class.
- Going to Mess.
- Gym Preparation.
- Returning Home.
- Morning Routine.
- Bedtime Routine.
- Daily Planning.
- Weekly Checklist.
- Location-based Safety Checklist.

Templates must be normal editable workflow definitions.

No hardcoded execution shortcuts.

## 10.7 Workflow dependency management

Show which workflows depend on:

- Saved locations.
- Variables.
- Templates.
- Reusable workflows.

Warn before deletion of referenced resources.

## 10.8 Optional notification escalation

Support user-defined escalation:

- One reminder.
- One follow-up.
- Show on dashboard.
- Expire.
- Mark missed.

Set safe defaults and maximum repetition limits.

---

# PART 11 — WORKFLOW SIMULATOR AND DEBUGGER

Implement an actual workflow debugger rather than a decorative preview.

## 11.1 Simulation inputs

Allow user configuration of:

- Simulated date.
- Simulated local time.
- Simulated timezone.
- Simulated location event.
- Simulated geofence occupancy.
- Simulated variable values.
- Simulated responses.
- Simulated permission state.
- Simulated trigger metadata.

## 11.2 Execution visualization

Highlight the currently executing Blockly block.

Display:

- Current node.
- Previous node.
- Chosen branch.
- Evaluated expression.
- Variable values.
- Pending wait.
- Notification that would appear.
- Workflow state.
- Execution timeline.

## 11.3 Debug controls

- Run.
- Pause.
- Continue.
- Step into.
- Step over where supported.
- Stop.
- Restart.
- Inspect variables.
- View execution trace.
- Add breakpoints in simulation.

## 11.4 Isolation

Simulation must never:

- Send real reminders unintentionally.
- Register real geofences.
- Modify production global variables.
- Schedule actual alarms.
- Perform irreversible side effects.

Use fake clocks and fake adapters.

Support deterministic replay.

## 11.5 Diagnostic explanations

Example:

"Workflow did not run because the current time was 11:30 AM, outside the configured 7:00–10:00 AM window."

Explanations must reference actual evaluated conditions rather than generic failure messages.

---

# PART 12 — DATABASE DESIGN

Use Room with well-designed entities and migrations.

Suggested entities:

1. AutomationEntity
2. TriggerEntity
3. SavedLocationEntity
4. BlocklyWorkspaceEntity
5. CompiledWorkflowEntity
6. WorkflowVersionEntity
7. WorkflowExecutionEntity
8. ExecutionEventEntity
9. VariableDefinitionEntity
10. VariableValueEntity
11. PendingInteractionEntity
12. ScheduledActionEntity
13. NotificationRecordEntity
14. ChecklistTemplateEntity
15. ChecklistInstanceEntity
16. TriggerHistoryEntity
17. GeofenceRegistrationEntity
18. WorkflowDependencyEntity
19. DiagnosticEventEntity
20. SchemaMetadataEntity

Modify this schema where normalization or performance justifies it.

Use:

- Stable UUIDs.
- Foreign keys.
- Appropriate indices.
- Explicit uniqueness constraints.
- Transactions.
- Schema migrations.
- Safe deletion behavior.

Avoid unnecessarily storing precise location history.

Persist only data needed for application behavior and user-selected history retention.

## 12.1 Database migrations

Every schema change must have a defined migration path.

Do not rely on destructive database recreation in production.

Test upgrades from previous schemas.

## 12.2 Data integrity

Test:

- Orphan prevention.
- Invalid references.
- Duplicate execution prevention.
- Concurrent variable updates.
- Failed transaction rollback.
- Corrupt import handling.
- Deletion of active workflow dependencies.

---

# PART 13 — SECURITY AND PRIVACY

The application is private and local-first.

## 13.1 Permissions

Request only what is necessary.

Potential permissions include:

- Location.
- Background location.
- Notifications.
- Exact alarm special access where appropriate.
- Boot event handling.

Do not request unrelated permissions.

Explain each permission in human-readable language.

Provide a permission health screen.

## 13.2 WebView security

Treat the Blockly WebView as a security boundary.

Requirements:

- Load bundled trusted assets.
- Prefer AndroidX WebViewAssetLoader.
- Restrict navigation.
- Disable unnecessary file access.
- Disable unnecessary remote content.
- Restrict JavaScript bridge exposure.
- Validate all bridge messages.
- Avoid passing arbitrary executable source.
- Use narrow, documented bridge APIs.
- Prevent untrusted URLs from gaining bridge access.

## 13.3 Data security

- No plaintext secrets in code.
- No remote telemetry.
- No hidden data collection.
- No unnecessary third-party trackers.
- Export only user-selected data.
- Validate imported files.
- Limit file sizes.
- Protect against path traversal and malformed JSON.
- Avoid logging sensitive location coordinates unnecessarily.

## 13.4 Backups

Support explicit local export/import.

Use Android's Storage Access Framework.

Consider optional user-supplied password-encrypted backup archives if implemented with maintained cryptographic APIs.

If encryption is unavailable, do not falsely label backups encrypted.

Provide backup format documentation and versioning.

## 13.5 Debug versus release

Disable development-only debugging surfaces in release builds unless explicitly part of the user-facing diagnostic feature.

Avoid debug WebView exposure in release builds.

---

# PART 14 — BATTERY AND PERFORMANCE

Avoid continuous GPS tracking.

Avoid busy waiting.

Avoid permanent foreground services unless a legitimate platform-compliant use case exists.

Design background execution around:

- Geofence event callbacks.
- Alarm callbacks.
- WorkManager.
- Persistent state.
- Short-lived processing.

## 14.1 Performance goals

Targets, not unverified claims:

- Smooth navigation.
- Responsive Blockly editing.
- Efficient workflow compilation.
- Minimal idle CPU usage.
- Reasonable memory consumption.
- No runaway notification loops.
- No obvious battery drain when automations are idle.

Measure performance on available devices or emulators.

If physical battery testing is unavailable, document that limitation.

## 14.2 Resource limits

Enforce reasonable configurable limits on:

- Workflow steps per execution burst.
- Loops.
- Recursive depth.
- Active workflow executions.
- Pending timers.
- Variable storage.
- Notification frequency.
- Database history.
- Imported data size.

Provide explicit errors when limits are reached.

---

# PART 15 — SETTINGS

Include:

## General

- App theme.
- Material You dynamic colors where supported.
- Dark mode.
- 12/24-hour time.
- Default timezone behavior.
- Notification preferences.

## Automations

- Default cooldown.
- Default notification expiration.
- Default workflow concurrency.
- Missed execution policies.
- Execution history retention.
- Default geofence radius.

## Permissions

- Notification permission.
- Foreground location.
- Background location.
- Exact alarm access.
- Location service state.
- Google Play Services availability.
- Background restriction guidance.

## Data

- Export all.
- Import.
- Backup.
- Restore.
- Clear history.
- Reset variables.
- Delete all app data, with confirmation.

## Diagnostics

- Trigger registration status.
- Last background event.
- Last scheduler reconciliation.
- Upcoming alarm information.
- Pending execution count.
- Recent errors.
- Test notification.
- Test workflow.
- Copy diagnostic report.

## About

- Version.
- Build number.
- Dependencies/licences.
- Privacy statement.
- Documentation.

---

# PART 16 — USER EXPERIENCE QUALITY

Design a polished application that feels native to Android.

Requirements:

- Material 3.
- Consistent spacing.
- Accessible typography.
- Responsive layouts.
- Dark/light themes.
- Proper loading states.
- Error states.
- Empty states.
- Useful confirmations.
- Non-destructive defaults.
- Accessibility semantics.
- Screen reader-friendly controls.
- Reasonable minimum touch sizes.

The editor may be visually complex, but normal application navigation should remain simple.

Design a first-launch onboarding flow explaining:

1. What FlowState does.
2. How time and location triggers work.
3. What Android permissions are needed.
4. Why geofence notifications may be delayed.
5. How to create a first automation.
6. How to run the simulator.

Do not bombard users with every permission at initial launch.

Request permissions when the associated functionality is configured or enabled.

---

# PART 17 — EXAMPLE WORKFLOWS TO IMPLEMENT AND TEST

Create editable examples.

## Example 1: Leaving Hostel

Trigger:

EXIT Hostel geofence.

Radius:

150 m.

Condition:

Monday through Friday.

Question:

"Where are you going?"

Options:

Class / Mess / Gym / Going Out.

Class branch:

Ask "Lecture or Lab?"

Lecture:

Show lecture checklist.

Lab:

Show lab checklist.

Mess:

Show protein-powder checklist.

Gym:

Show gym checklist.

Going Out:

Show wallet, ID card and keys checklist.

All branches eventually complete.

## Example 2: Morning Routine

Trigger:

7:30 AM every weekday.

Actions:

Send notification.

Ask:

"Do you have class today?"

Yes:

Show morning checklist.

No:

Ask:

"Do you want to study or relax?"

Follow corresponding branch.

## Example 3: Weekly Task

Trigger:

Weekly period beginning Friday.

Condition:

Only once in the current weekly window.

If the scheduled occurrence was missed, obey the configured catch-up policy.

Never rely solely on checking whether the current weekday equals Friday.

## Example 4: Delayed Follow-up

Trigger:

EXIT Gym geofence.

Action:

Wait 15 minutes.

Notification:

"Have you had your post-workout meal?"

If Yes:

Complete.

If No:

Wait 10 minutes and remind once.

Then expire.

## Example 5: Persistent Variable

Trigger:

Every evening at 9 PM.

Ask:

"Did you prepare tomorrow's essentials?"

Store answer in persistent variable.

At the next eligible morning trigger, inspect that variable and choose a branch.

Provide a defined reset/update policy.

## Example 6: Notification Timeout

Trigger:

Exit Hostel.

Question:

"Where are you going?"

Timeout:

15 minutes.

On timeout:

Mark workflow expired.

A late notification response must not resurrect it.

## Example 7: Nested Conditions

Trigger:

Enter Academic Building.

IF weekday AND between 8 AM and 5 PM:

Ask whether attending class.

ELSE:

Show alternate message.

## Example 8: Sub-workflow

Trigger:

Going Out selection.

Call:

Essential Items Checklist.

On completion:

Continue main workflow.

---

# PART 18 — TESTING STRATEGY

Implement tests throughout development, not only at the end.

## 18.1 Unit tests

Test:

- Block parsing.
- Type validation.
- Expression evaluation.
- Boolean logic.
- Branch selection.
- Variable scoping.
- Arithmetic.
- String operations.
- Time comparisons.
- Overnight ranges.
- Weekly windows.
- Recurrence calculation.
- Loop limits.
- Sub-workflow call semantics.
- Serialization.
- State transitions.

## 18.2 Integration tests

Test:

- Blockly JSON to compiled workflow.
- Workflow persistence and reload.
- Notification action to workflow continuation.
- Trigger event to workflow execution.
- Wait scheduling to resumption.
- Database transaction recovery.
- Permission-denied fallback.
- Import/export roundtrip.

## 18.3 UI tests

Test:

- First-launch onboarding.
- Automation creation.
- Location creation.
- Blockly editor launch.
- Workspace save/load.
- Validation errors.
- Automation enable/disable.
- Execution history.
- Checklist interaction.
- Theme switching.

## 18.4 Platform tests

Where test devices/emulators are available, test:

- Cold start.
- Background operation.
- Process death.
- Reboot.
- Timezone change.
- Permission revocation.
- Notification denial.
- Location disabled.
- Google Play Services unavailable.
- Offline operation.
- Battery-restricted states.

Tests requiring physical geofence movement should be documented separately if actual-device execution is unavailable.

## 18.5 Property-based and fuzz testing

Where practical, generate malformed and randomized inputs for:

- Workflow graphs.
- Expressions.
- Date calculations.
- Imported JSON.
- Control-flow loops.
- State transitions.

Verify the interpreter terminates safely or suspends appropriately.

## 18.6 Failure injection

Simulate:

- Database write failure.
- Duplicate receiver delivery.
- Repeated notification tap.
- Worker retry.
- Missing location.
- Corrupted workflow state.
- Deleted referenced workflow.
- Invalid pending notification.
- Device clock jump.
- App termination during WAIT.
- Process death between action execution and state persistence.

Document the recovery behavior.

---

# PART 19 — CRITICAL ACCEPTANCE TESTS

Treat the following as release gates.

### AT-001

Create a time automation, save it, terminate the app process, reopen it, and verify configuration persistence.

### AT-002

Create a location automation and verify correct registration status when permissions are available.

### AT-003

Deny background location access and confirm the UI does not report background geofencing as active.

### AT-004

Create a nested IF/ELSE workflow and verify branch correctness.

### AT-005

Create a multi-choice notification and verify response routing.

### AT-006

Tap a notification action twice and verify only one logical transition occurs.

### AT-007

Suspend a workflow for user input, terminate the process, and verify state recovery.

### AT-008

Suspend a workflow until a future time, restart the application, and verify valid rescheduling.

### AT-009

Edit a workflow while an older execution remains suspended; verify definition-version isolation.

### AT-010

Delete a referenced sub-workflow and verify dependency protection or safe failure.

### AT-011

Attempt to create an unbounded loop and verify validation/runtime safeguards.

### AT-012

Import malformed JSON and verify safe rejection without database corruption.

### AT-013

Export all workflows, import into a clean database, and verify semantic equivalence.

### AT-014

Simulate a weekly window that begins Friday, with a missed Friday execution and recovery on Sunday. Verify the catch-up policy evaluates window state rather than weekday equality.

### AT-015

Simulate a weekly window that has already executed. Verify no duplicate execution within the same window.

### AT-016

Verify overnight time conditions such as 22:00–02:00.

### AT-017

Verify correct handling of daylight saving transitions using simulated timezones.

### AT-018

Revoke exact alarm permission and verify graceful degradation.

### AT-019

Disable a workflow and confirm no new execution starts from its triggers.

### AT-020

Verify workflow simulation does not modify production variables or register real triggers.

### AT-021

Verify a late response to an expired notification does not resume execution.

### AT-022

Verify two concurrent executions do not corrupt persistent variables.

### AT-023

Verify a duplicate geofence event respects configured cooldown and idempotency policies.

### AT-024

Verify invalid Blockly block connections and missing inputs are reported clearly.

### AT-025

Verify offline workflow execution without a remote backend.

### AT-026

Verify an actual compiled APK can be installed and launched on an available Android environment.

### AT-027

Verify a saved workflow can be visually edited, recompiled and run again.

### AT-028

Verify notifications behave correctly when notification permission is denied, including appropriate diagnostic status.

### AT-029

Verify deleting an automation cancels or safely reconciles its scheduled alarms and geofence dependencies.

### AT-030

Verify the application can restore or rebuild required registrations after supported restart/recovery events.

All acceptance tests must be mapped to concrete automated tests or clearly documented manual device procedures.

A test that was not executed must not be reported as passed.

---

# PART 20 — IMPLEMENTATION WORKFLOW FOR CODEX

Work autonomously through the following engineering stages.

These stages are internal development milestones, not separate user-approved projects.

Do not stop at each stage to request confirmation unless absolutely necessary.

## Stage 1 — Research and architecture

1. Inspect the existing repository.
2. Determine whether an Android project already exists.
3. Preserve useful existing work.
4. Verify official Android and Blockly documentation.
5. Choose compatible dependencies.
6. Document architecture.
7. Define requirements traceability.
8. Define database schema.
9. Define workflow IR.
10. Define runtime state machine.
11. Define Android permission strategy.

Create:

docs/ARCHITECTURE.md
docs/REQUIREMENTS.md
docs/DECISIONS.md

## Stage 2 — Android foundation

Implement:

- Gradle build.
- App shell.
- Navigation.
- Compose theme.
- Database.
- Dependency injection.
- Preferences.
- Logging.
- Error handling.

Verify compilation.

## Stage 3 — Workflow compiler and interpreter

Implement the runtime before connecting real Android trigger events.

Use deterministic test fixtures.

Build:

- IR.
- Compiler.
- Validators.
- Expression evaluator.
- Execution state machine.
- Variable system.
- Continuations.
- Persistence.
- Recovery.
- Error handling.

Verify thoroughly with unit tests.

## Stage 4 — Blockly editor

Implement:

- Offline Blockly bundle.
- Custom blocks.
- Toolbox.
- Native bridge.
- Serialization.
- Save/load.
- Compilation integration.
- Error highlighting.
- Visual execution tracing.

Verify editor-runtime roundtrips.

## Stage 5 — Time triggers

Implement scheduling, permission handling, missed-event policies and reconciliation.

Test timezone and weekly-window edge cases.

## Stage 6 — Location triggers

Implement saved locations, geofence registration, background permissions, event dispatch and diagnostics.

Use mock geofence events in automated tests.

## Stage 7 — User interactions

Implement:

- Notifications.
- Choices.
- Checklists.
- Timeout.
- Snooze.
- Resume.
- Expiration.
- Error notifications.

Test stale, duplicate and concurrent responses.

## Stage 8 — Advanced blocks

Implement:

- Variables.
- Loops.
- Reusable workflows.
- Parallel execution where safe.
- Control-flow guards.
- Error handling blocks.

## Stage 9 — Simulator

Implement deterministic fake services, clock controls, workflow tracing and visual highlighting.

## Stage 10 — Polish

Implement:

- Templates.
- Imports/exports.
- History.
- Diagnostics.
- Accessibility.
- Settings.
- Permissions onboarding.

## Stage 11 — Full audit

Perform a file-by-file audit.

Check:

- Correctness.
- Logic.
- Thread safety.
- Lifecycle safety.
- State recovery.
- Security.
- Resource leaks.
- Battery usage.
- Error handling.
- Performance.
- Missing tests.
- Maintainability.
- Documentation consistency.

Fix defects and rerun affected tests.

## Stage 12 — Build and release

Generate:

- Installable debug APK.
- Release-ready Gradle configuration.
- Signed release APK only if signing credentials are securely supplied; otherwise document how to generate one.
- README.
- Installation guide.
- User guide.
- Testing report.
- Audit report.

Do not invent successful build results.

---

# PART 21 — REQUIRED DOCUMENTATION

Create:

README.md

docs/ARCHITECTURE.md

docs/REQUIREMENTS.md

docs/WORKFLOW_LANGUAGE.md

docs/BLOCK_REFERENCE.md

docs/WORKFLOW_ENGINE.md

docs/DATABASE_SCHEMA.md

docs/TRIGGER_SCHEDULING.md

docs/ANDROID_PERMISSIONS.md

docs/USER_GUIDE.md

docs/DEVELOPER_GUIDE.md

docs/TESTING.md

docs/KNOWN_LIMITATIONS.md

docs/SECURITY.md

docs/BUILD_AND_INSTALL.md

docs/FINAL_AUDIT.md

docs/IMPLEMENTATION_STATUS.md

Documentation must reflect actual code.

Do not describe a planned feature as already implemented.

---

# PART 22 — FILE AND FOLDER AUDIT

Produce two independent reports.

## Report A — Repository Explanation

For every meaningful source file and directory explain:

- Purpose.
- Responsibilities.
- Important classes/functions.
- Inputs and outputs.
- Dependencies.
- Relationships to other modules.
- Runtime significance.

Avoid generic explanations such as "contains utility functions."

## Report B — Defect and Risk Audit

Inspect:

- Logical defects.
- State-machine errors.
- Scheduling mistakes.
- Boundary cases.
- Invalid assumptions.
- Android lifecycle hazards.
- Background execution restrictions.
- Duplicate event delivery.
- Permission edge cases.
- Race conditions.
- Persistence failures.
- Database corruption risks.
- Security concerns.
- Test gaps.

For every issue provide:

- ID.
- Severity.
- File.
- Relevant function/class.
- Explanation.
- Reproduction scenario.
- Expected behavior.
- Actual behavior.
- Fix.
- Verification status.

Classify issues:

CRITICAL / HIGH / MEDIUM / LOW / INFORMATIONAL.

Do not classify an issue as fixed without verifying the actual correction.

---

# PART 23 — FINAL DELIVERABLE STRUCTURE

Use a clean repository layout such as:

FlowState/
- app/
- core/
- feature/
- engine/
- blockly-editor/
- build-logic/ (if necessary)
- gradle/
- docs/
- tests/ (if needed for shared fixtures)
- sample-workflows/
- scripts/
- README.md
- settings.gradle.kts
- build.gradle.kts
- gradle.properties
- gradlew
- gradlew.bat

Keep compiled artifacts outside version control where appropriate.

Place generated APKs in their standard build output folders.

At completion provide exact verified file paths.

Do not invent paths to artifacts that do not exist.

---

# PART 24 — FINAL VERIFICATION PROCEDURE

Before claiming completion:

1. Run formatting/static checks.
2. Run Kotlin compilation.
3. Run unit tests.
4. Run integration tests.
5. Run Android instrumentation/UI tests if an emulator or device is available.
6. Run Blockly frontend tests.
7. Validate sample workflows.
8. Build the APK.
9. Confirm the APK exists.
10. Inspect build errors and warnings.
11. Review the manifest.
12. Inspect dependencies and security configuration.
13. Verify important database migrations.
14. Verify trigger recovery mechanisms.
15. Verify documentation accuracy.
16. Execute the final audit.

If the environment lacks Android SDK, emulator, network access or other required infrastructure:

- Identify the exact blocker.
- Complete all work that remains possible.
- Do not fabricate successful tests.
- Provide concrete instructions to finish verification.
- Preserve a compilable, coherent project as far as the environment allows.

A successful unit test does not prove real-world geofencing reliability.

A successful APK build does not prove background execution correctness.

An emulator simulation does not prove operation under all manufacturers' battery restrictions.

Clearly distinguish verified behavior from unverified platform-dependent behavior.

---

# PART 25 — RELEASE ACCEPTANCE CRITERIA

Do not describe the app as complete unless these areas meet the required standard:

| Area | Requirement |
|---|---|
| Android app | Builds and launches |
| Time triggers | Functional with documented precision policies |
| Geofencing | Implemented with permission and registration handling |
| Blockly | Fully editable persistent workspace |
| Compiler | Validated typed executable representation |
| Interpreter | Executes branches and actions correctly |
| Notifications | Functional interactive actions |
| Persistence | Executions and configuration recover appropriately |
| Wait/resume | No indefinite in-memory dependency |
| Loops | Bounded and safe |
| Variables | Correct persistence and scoping |
| Sub-workflows | Validated references and bounded recursion |
| Debugger | Deterministic simulation and trace |
| Backup | Validated export/import |
| UI | Usable and accessible |
| Security | No critical vulnerabilities known |
| Tests | Executed tests documented truthfully |
| Documentation | Accurate and complete |
| APK | Generated and path verified |

Any unsupported, incomplete or untested requirement must be listed explicitly in IMPLEMENTATION_STATUS.md.

---

# PART 26 — NON-NEGOTIABLE ENGINEERING PRINCIPLES

1. Correctness is more important than shortcuts.
2. Background reliability requires persistence, not continuous execution.
3. Duplicate delivery must not imply duplicate behavior.
4. Trigger time and delivery time are not necessarily identical.
5. User-defined workflows are untrusted structured input.
6. Every executable block requires defined semantics.
7. Every asynchronous wait requires a persistable continuation.
8. Every scheduled event requires cancellation and reconciliation behavior.
9. Every permission-dependent feature requires a graceful failure state.
10. Every workflow version must be identifiable.
11. Every error must be observable and diagnosable.
12. Every important logical edge case must have a test.
13. Every platform limitation must be documented honestly.
14. Editor state must be separated from runtime state.
15. Debug simulation must be isolated from production.
16. App upgrades must not silently destroy user automations.
17. No cloud backend is required for basic functionality.
18. Avoid overengineering while preserving correct architecture.
19. Never replace a required implementation with a fake success path.
20. Never declare success based solely on the absence of compilation errors.

---

# PART 27 — HOW TO REPORT PROGRESS

Maintain docs/IMPLEMENTATION_STATUS.md.

After each major implementation stage update:

- Completed work.
- Files changed.
- Tests added.
- Tests executed.
- Results.
- Open issues.
- Blockers.
- Next engineering steps.

Do not repeatedly ask me for approval of routine technical decisions.

Do not stop at planning.

Do not finish after scaffolding.

Proceed through implementation, integration, testing, audit and packaging.

At the end, provide:

1. Concise architecture summary.
2. Implemented feature inventory.
3. Explicitly incomplete features.
4. Test results.
5. Build results.
6. Exact APK path if generated.
7. Android installation procedure.
8. Permission setup instructions.
9. Known limitations.
10. Remaining defects.
11. Suggested future improvements, clearly separated from original requirements.

---

# FINAL INSTRUCTION

You are building **FlowState**, a complete personal visual automation engine for Android.

The most important outcome is that I can open the app, create a location or time trigger, visually drag and connect Blockly-style blocks, define my own conditions and decisions, save the automation, and have Android execute it reliably within documented platform constraints.

I must be able to build complex logic without modifying application source code.

The application must be data-driven, extensible, offline-first, safe, observable and usable.

Begin by inspecting the repository and verifying your toolchain, then create the architecture and implementation plan and execute it.

Build the real application.

Do not stop at the design stage.

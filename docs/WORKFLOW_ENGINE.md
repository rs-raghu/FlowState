# Execution and recovery

`Runtime.start` initializes local defaults and captures referenced definitions. `tick` advances at most 100 nodes per slice and returns a new immutable execution, side effects and scoped values. `Coordinator.drive` runs at most five slices before enqueueing another worker. Terminal states are COMPLETED/CANCELLED/FAILED/EXPIRED. Human, time and condition waits contain durable tokens/deadlines/continuation frames and do not retain a thread.

Room stores the complete snapshot, current state, event identity and next wake time. One coordinator Mutex serializes platform operations within the app process, and Room transactions atomically commit execution, variable changes and outbox effects. No separate process is configured. Duplicate event keys have a unique database index. Duplicate/late responses cannot transition a consumed or expired interaction. Each notification action uses an explicit immutable PendingIntent with execution ID, unpredictable token and option identity.

Outbox draining occurs after commit. Interactive notifications replace the same owned tag; informational messages have a separate tag so workflow completion does not immediately remove them. A crash after posting but before deleting an outbox row may re-post the same tag. This is effectively-once display, not a universal exactly-once side-effect guarantee. Workflow effects are limited to local notifications and variables.

On boot, upgrade, clock/timezone changes and foreground entry, reconciliation calculates next alarms, restores execution wakes and registers geofences. A unique periodic WorkManager job performs deferrable recovery. Force-stop prevents delivery until the app is reopened. Permissions and platform failures are visible in Settings/diagnostics; denied notifications leave human interactions accessible in Activity.

Concurrency defaults: four active runs per automation, 32 globally, visible refusal when exceeded. Manual starts are explicit and permitted for disabled workflows. Automatic starts require enabled state and configured cooldown. Custom replace/queue policies are not implemented.

# Workflow language v1

Blockly JSON is editor state. `Compiler.compile` creates `Definition(schema=1, id, version, entry, nodes, variables, trigger, budgets)`. Nodes have stable block IDs, an allowlisted operation, string configuration fields, typed expression trees, named branches and an optional next node. Definitions are structured data; neither generated JavaScript nor Kotlin is executed.

Values are Boolean, Integer, Decimal, String, Instant (epoch milliseconds), Duration (milliseconds), List or Null. Literal timestamps use ISO 8601; duration fields use seconds. Strings are capped at 16,384 characters; list operations cap lists at 1,000 items. Explicit conversions are available. Numeric strings do not participate in arithmetic without conversion. AND/OR short-circuit; other binary expressions evaluate their required inputs.

Declare variables with a scope and type before reading or writing them. Blank defaults mean null. Response variables are local; use SET to persist them. Automation values belong to the currently executing definition ID; global values belong to `global`. Local values are isolated between calls; input and return values pass by value.

Statement stacks compile to acyclic next/branch edges. Guarded loop nodes introduce repetition through durable frames. Every loop has a limit of at most 1,000 iterations and the interpreter applies a 10,000-step maximum. Imported IR is never accepted as authoritative: backups import workspaces and recompile them natively.

IF branches are YES/NO. Choice blocks have independently persisted CHOICE0…CHOICEn branches, with legacy first/second/other fallback. Yes/no and text/number blocks use YES/NO and valid-input semantics. All interactions have CANCEL/TIMEOUT branches. Checklist entries beginning with `?` are optional. Missing action branches continue to the next statement. Timeout with no branch expires the execution.

CALL may pass one named local input and receive one named local output. Target definitions and their transitive dependencies are snapshotted when an execution starts. RETURN with no value returns Null. Reaching the end returns to the caller without overwriting its output variable. Calls are limited to depth eight; recursive dependencies are rejected. Disabling an automation stops new automatic starts; existing snapshots continue. Deleting an automation removes its own runs and is prevented when another saved automation calls it.

The BRANCHES block executes A to completion, then B, sharing the execution's local variables. It does not provide simultaneous execution. TRY handles errors with a visible trace and resumes the handler. STOP completes the workflow. Breakpoints record a trace marker in production and pause simulation after the marker. WAIT CLOCK chooses the next strictly future clock time in its selected zone. SET TIMEOUT sets a persisted overall deadline from the current time, caps subsequent wakes and rejects responses after expiration.

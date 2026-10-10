package dev.flowstate.engine

import kotlinx.serialization.encodeToString

object ResourceLimits {
    const val SNAPSHOT_BYTES = 2_000_000
    const val VARIABLE_BYTES = 1_000_000

    fun bound(tick: Tick, previous: Map<String, Value>): Tick {
        val failure =
            when {
                tick.persistent.size > 10000 -> "Persistent variable count exceeds 10,000"
                codec.encodeToString(tick.persistent).toByteArray(Charsets.UTF_8).size >
                    VARIABLE_BYTES -> "Persistent variable storage exceeds 1 MB"
                codec.encodeToString(tick.execution).toByteArray(Charsets.UTF_8).size >
                    SNAPSHOT_BYTES -> "Execution snapshot exceeds 2 MB"
                else -> return tick
            }
        val e = tick.execution
        val root = e.frames.firstOrNull { it.kind == "call" }?.definition ?: e.definition
        return Tick(
            e.copy(
                definition = root,
                state = State.FAILED,
                cursor = null,
                frames = emptyList(),
                locals = emptyMap(),
                interaction = null,
                wakeAt = null,
                branches = emptyList(),
                library = emptyMap(),
                trace = e.trace.takeLast(20),
                error = failure,
            ),
            listOf(Effect("${e.id}:resource-budget:${e.steps}", "cancel", e.id)),
            previous,
        )
    }
}

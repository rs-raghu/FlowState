package dev.flowstate.engine

import java.time.*

data class SimulationSnapshot(
    val execution: Execution,
    val now: Long,
    val zone: String,
    val values: Map<String, Value>,
    val effects: List<Effect>,
    val occupancy: Map<String, Pair<String, Long>>,
    val tokenCounter: Int,
    val permissions: Map<String, Boolean>,
)

/** All adapters and state belong to this instance. There is no Android/database dependency. */
class Simulation(
    val definition: Definition,
    definitions: List<Definition>,
    now: Long,
    zone: String,
    initial: Execution? = null,
) {
    private var tokenCounter = 0
    private val runtime = Runtime { id ->
        definitions.find { it.id == id }
    }
        .apply { interactionToken = { "simulation-token-${tokenCounter++}" } }
    var execution = initial ?: runtime.start("simulation", definition, now)
        private set

    var now = now
    var zone = zone
    var values: Map<String, Value> = emptyMap()
    var effects: List<Effect> = emptyList()
        private set

    var occupancy: Map<String, Pair<String, Long>> = emptyMap()
    var breakpoints: Set<String> = emptySet()
    var permissions =
        mapOf(
            "notifications" to true,
            "precise" to true,
            "background" to true,
            "location services" to true,
            "play services" to true,
            "exact alarms" to true,
        )
    var explanation = ""
        private set

    private var paused: State? = null
    val history = mutableListOf<SimulationSnapshot>()

    init {
        capture()
    }

    private fun capture() {
        history +=
            SimulationSnapshot(
                execution,
                now,
                zone,
                values.toMap(),
                effects.toList(),
                occupancy.toMap(),
                tokenCounter,
                permissions.toMap(),
            )
        if (history.size > 200) history.removeAt(0)
    }

    fun replay(index: Int) {
        val s = history[index]
        execution = s.execution
        now = s.now
        zone = s.zone
        values = s.values
        effects = s.effects
        occupancy = s.occupancy
        tokenCounter = s.tokenCounter
        permissions = s.permissions
        paused = null
        while (history.lastIndex > index) history.removeAt(history.lastIndex)
    }

    fun pause() {
        if (execution.state !in Runtime.terminal && execution.state != State.PAUSED) {
            paused = execution.state
            execution = execution.copy(state = State.PAUSED)
        }
    }

    fun resume() {
        if (execution.state == State.PAUSED) {
            execution = execution.copy(state = paused ?: State.RUNNING).resumePausedBranches()
            paused = null
        }
    }

    fun stop() {
        execution = execution.copy(state = State.CANCELLED, interaction = null, wakeAt = null)
        capture()
    }

    fun restart() {
        tokenCounter = 0
        execution = runtime.start("simulation", definition, now)
        effects = emptyList()
        history.clear()
        paused = null
        capture()
    }

    private fun atBreakpoint(e: Execution): Boolean =
        if (e.branches.isNotEmpty()) e.branches.any(::atBreakpoint)
        else e.state in setOf(State.CREATED, State.RUNNING) && e.cursor in breakpoints

    fun step(honorBreakpoints: Boolean = false): Boolean {
        if (honorBreakpoints && atBreakpoint(execution)) {
            pause()
            explanation = "Paused before configured node breakpoint"
            return false
        }
        val before = execution
        val result =
            runtime.tick(
                execution,
                now,
                zone,
                values,
                occupancy,
                singleStep = true,
                simulationBreakpoints = true,
            )
        execution = result.execution
        values = result.persistent
        effects = (effects + result.effects).takeLast(100)
        if (execution != before || result.effects.isNotEmpty()) capture()
        return execution != before &&
            execution.state !in Runtime.terminal &&
            execution.state != State.PAUSED
    }

    fun run() {
        resume()
        for (i in 0 until 10000) if (!step(honorBreakpoints = i > 0)) break
    }

    fun stepOver() {
        resume()
        val id = execution.definition.id
        val depth = execution.frames.count { it.kind == "call" }
        if (!step()) return
        for (i in 0 until 10000) {
            if (
                execution.definition.id == id &&
                    execution.frames.count { it.kind == "call" } <= depth
            )
                break
            if (!step(true)) break
        }
    }

    fun stepOut() {
        resume()
        val depth = execution.frames.count { it.kind == "call" }
        if (depth == 0) {
            stepOver()
            return
        }
        for (i in 0 until 10000) {
            if (!step(true) || execution.frames.count { it.kind == "call" } < depth) break
        }
    }

    fun respond(token: String, response: String) {
        execution = runtime.respond(execution, token, response, now)
        capture()
        run()
    }

    fun variable(name: String, scope: Scope, raw: String) {
        val v =
            execution.definition.variables.find { it.name == name && it.scope == scope }
                ?: error("Select a declared variable in this scope")
        val value =
            if (v.type == Type.LIST && v.elementType != null)
                Value(
                    Type.LIST,
                    items =
                        raw.split('|')
                            .filter { it.isNotEmpty() }
                            .map { Value.parse(v.elementType, it) },
                    elementType = v.elementType,
                )
            else Value.parse(v.type, raw)
        require(v.accepts(value))
        if (scope == Scope.LOCAL)
            execution = execution.copy(locals = execution.locals + (name to value))
        else values = values + (Expressions.key(scope, name, execution.definition.id) to value)
        capture()
    }

    fun event(at: Long, kind: String, payload: String) {
        require(at >= now) { "Events must be chronological; use replay to move back" }
        now = at
        when (kind) {
            "enter",
            "exit",
            "dwell" -> {
                occupancy =
                    occupancy + (payload to ((if (kind == "exit") "OUTSIDE" else "INSIDE") to at))
                val eligible =
                    definition.trigger.kind == "location" &&
                        definition.trigger.locationId == payload &&
                        definition.trigger.transition == kind &&
                        Scheduling.locationEligible(definition.trigger, now, ZoneId.of(zone))
                val allowed =
                    listOf("precise", "background", "location services", "play services").all {
                        permissions[it] == true
                    }
                explanation =
                    if (!eligible)
                        "Location event is outside the configured trigger or eligibility window"
                    else if (!allowed) "Location permissions/services would block this trigger"
                    else "Location trigger is eligible"
                if (eligible && allowed && execution.state in Runtime.terminal)
                    execution = runtime.start("simulation-event-$at", definition, at)
                if (eligible && allowed) run()
            }
            "response" ->
                execution.pendingInteractions().firstOrNull()?.let { respond(it.token, payload) }
            "clock" -> run()
            "notification" -> {
                permissions = permissions + ("notifications" to payload.toBooleanStrict())
                explanation = "Notification permission changed in simulation"
            }
            else -> error("Use enter, exit, dwell, response, clock or notification")
        }
        capture()
    }
}

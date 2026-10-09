package dev.flowstate.engine

import java.util.UUID

class Runtime(private val resolve: (String) -> Definition? = { null }) {
    fun start(id: String, d: Definition, now: Long): Execution {
        val library = mutableMapOf<String, Definition>()
        fun capture(definition: Definition, path: Set<String>) {
            require(path.size < 8 && definition.id !in path) {
                "Recursive or excessive workflow dependency"
            }
            definition.nodes
                .filter { it.op == "call" }
                .forEach { n ->
                    val child =
                        resolve(n.fields["WORKFLOW"] ?: "")
                            ?: error("Referenced workflow is missing or disabled")
                    if (child.id !in library) {
                        capture(child, path + definition.id)
                        library[child.id] = child
                    }
                }
        }
        capture(d, emptySet())
        return Execution(
            id,
            d,
            started = now,
            locals =
                d.variables.filter { it.scope == Scope.LOCAL }.associate { it.name to it.default },
            library = library,
        )
    }

    fun tick(
        original: Execution,
        now: Long,
        zone: String,
        persistent: Map<String, Value> = emptyMap(),
        occupancy: Map<String, Pair<String, Long>> = emptyMap(),
        singleStep: Boolean = false,
        simulationBreakpoints: Boolean = false,
    ): Tick {
        var e = original
        val values = persistent.toMutableMap()
        (listOf(e.definition) + e.library.values).forEach { d ->
            d.variables
                .filter { it.scope != Scope.LOCAL }
                .forEach { v ->
                    values.putIfAbsent(Expressions.key(v.scope, v.name, d.id), v.default)
                }
        }
        val effects = mutableListOf<Effect>()
        fun record(node: String, detail: String) {
            e = e.copy(trace = (e.trace + Trace(now, node, detail)).takeLast(1000))
        }
        fun branch(n: Node, key: String) {
            e =
                e.copy(
                    cursor = n.branches[key] ?: n.next,
                    frames =
                        if (n.branches[key] != null) e.frames + Frame("branch", n.next)
                        else e.frames,
                )
        }
        fun ctx() = EvaluationContext(now, zone, e.locals, values, e.definition.id, occupancy)
        fun evaluate(n: Node, key: String) =
            Expressions.evaluate(requireNotNull(n.expressions[key]) { "Missing $key" }, ctx())
        fun set(name: String, scope: Scope, value: Value) {
            val declared =
                e.definition.variables.find { it.name == name && it.scope == scope }
                    ?: error("Undeclared variable $name")
            require(value.type == declared.type || value.type == Type.NULL) {
                "Variable type mismatch"
            }
            if (scope == Scope.LOCAL) e = e.copy(locals = e.locals + (name to value))
            else values[Expressions.key(scope, name, e.definition.id)] = value
        }
        if (e.state in terminal || e.state == State.PAUSED) return Tick(e, effects, values)
        if (e.deadline?.let { now >= it } == true) {
            record(e.cursor ?: "", "Execution deadline expired")
            return Tick(
                e.copy(state = State.EXPIRED, interaction = null, wakeAt = null),
                listOf(Effect("${e.id}:deadline", "cancel", e.id)),
                values,
            )
        }
        try {
            if (e.state == State.WAITING_FOR_USER) {
                val interaction = requireNotNull(e.interaction)
                if (interaction.deadline == null || now < interaction.deadline)
                    return Tick(e, effects, values)
                val node = e.definition.nodes.first { it.id == interaction.node }
                if (node.branches["TIMEOUT"] == null) {
                    e = e.copy(state = State.EXPIRED, interaction = null, wakeAt = null)
                    record(node.id, "Response deadline expired")
                    effects += Effect("${e.id}:expire", "cancel", e.id)
                    return Tick(e, effects, values)
                }
                e = e.copy(state = State.RUNNING, interaction = null, wakeAt = null)
                branch(node, "TIMEOUT")
                effects += Effect("${e.id}:expire", "cancel", e.id)
            }
            if (e.state in setOf(State.WAITING_FOR_TIME, State.WAITING_FOR_CONDITION)) {
                if (now < (e.wakeAt ?: Long.MAX_VALUE)) return Tick(e, effects, values)
                e = e.copy(state = State.RUNNING, wakeAt = null)
            }
            e = e.copy(state = State.RUNNING)
            var slice = 0
            while (e.state == State.RUNNING && slice < if (singleStep) 1 else 100) {
                if (e.cursor == null) {
                    val frame = e.frames.lastOrNull()
                    if (frame == null) {
                        e = e.copy(state = State.COMPLETED)
                        break
                    }
                    e = e.copy(frames = e.frames.dropLast(1))
                    when (frame.kind) {
                        "repeat" ->
                            if (frame.remaining > 1)
                                e =
                                    e.copy(
                                        cursor = frame.body,
                                        frames =
                                            e.frames + frame.copy(remaining = frame.remaining - 1),
                                    )
                            else e = e.copy(cursor = frame.returnTo)
                        "while" ->
                            e =
                                e.copy(
                                    cursor = frame.node,
                                    frames = e.frames + frame.copy(kind = "whileCounter"),
                                )
                        "call" ->
                            e =
                                e.copy(
                                    definition = requireNotNull(frame.definition),
                                    locals = frame.locals,
                                    cursor = frame.returnTo,
                                )
                        else -> e = e.copy(cursor = frame.returnTo)
                    }
                    continue
                }
                require(e.steps < e.definition.maxSteps) { "Runtime step budget exhausted" }
                val n = e.definition.nodes.first { it.id == e.cursor }
                e = e.copy(steps = e.steps + 1)
                slice++
                record(n.id, "Execute ${n.op}")
                fun f(name: String, default: String = "") = n.fields[name] ?: default
                when (n.op) {
                    "message" -> {
                        effects +=
                            Effect(
                                "${e.id}:${e.steps}",
                                "message",
                                f("TITLE", "FlowState"),
                                f("BODY"),
                            )
                        e = e.copy(cursor = n.next)
                    }
                    "notifyCancel" -> {
                        effects += Effect("${e.id}:${e.steps}", "cancel", e.id)
                        e = e.copy(cursor = n.next)
                    }
                    "if" -> {
                        val result = evaluate(n, "TEST").boolean()
                        record(n.id, "Condition = $result")
                        branch(n, if (result) "YES" else "NO")
                    }
                    "switch" -> {
                        val result = evaluate(n, "VALUE").display()
                        branch(
                            n,
                            when (result) {
                                f("CASE1") -> "YES"
                                f("CASE2") -> "NO"
                                else -> "OTHER"
                            },
                        )
                    }
                    "ask",
                    "checklist" -> {
                        val kind = if (n.op == "checklist") "checklist" else f("KIND", "choice")
                        val options =
                            when (kind) {
                                "yesno",
                                "confirm" -> listOf("Yes", "No")
                                "text",
                                "number" -> emptyList()
                                else ->
                                    f("OPTIONS", "Option 1|Option 2").split('|').filter {
                                        it.isNotBlank()
                                    }
                            }
                        require(options.size <= 100)
                        val timeout = f("TIMEOUT", "900").toLong()
                        require(timeout in 0..31536000)
                        val interaction =
                            Interaction(
                                UUID.randomUUID().toString(),
                                n.id,
                                kind,
                                f("TITLE", "Question"),
                                options,
                                if (timeout == 0L) null else Math.addExact(now, timeout * 1000),
                                f("NAME"),
                                Scope.valueOf(f("SCOPE", "LOCAL")),
                                required =
                                    if (kind == "checklist")
                                        options.indices.filter { !options[it].startsWith("?") }
                                    else emptyList(),
                            )
                        e =
                            e.copy(
                                state = State.WAITING_FOR_USER,
                                interaction = interaction,
                                wakeAt = interaction.deadline,
                            )
                        effects +=
                            Effect(
                                "${e.id}:${interaction.token}",
                                "interaction",
                                interaction.title,
                                interaction = interaction,
                            )
                    }
                    "wait" -> {
                        val seconds = f("SECONDS", "60").toLong()
                        require(seconds in 1..31536000)
                        e =
                            e.copy(
                                state = State.WAITING_FOR_TIME,
                                cursor = n.next,
                                wakeAt = Math.addExact(now, seconds * 1000),
                            )
                    }
                    "waitUntil" -> {
                        val v = evaluate(n, "VALUE")
                        require(v.type == Type.INSTANT)
                        val at = v.text.toLong()
                        e =
                            e.copy(
                                cursor = n.next,
                                state = if (at > now) State.WAITING_FOR_TIME else State.RUNNING,
                                wakeAt = if (at > now) at else null,
                            )
                    }
                    "waitClock" -> {
                        val time = java.time.LocalTime.parse(f("TIME", "09:00"))
                        val selectedZone =
                            java.time.ZoneId.of(
                                f("ZONE", "device").let { if (it == "device") zone else it }
                            )
                        val date =
                            java.time.Instant.ofEpochMilli(now).atZone(selectedZone).toLocalDate()
                        val today = Scheduling.resolve(date, time, selectedZone)
                        val at =
                            if (today > now) today
                            else Scheduling.resolve(date.plusDays(1), time, selectedZone)
                        e = e.copy(cursor = n.next, state = State.WAITING_FOR_TIME, wakeAt = at)
                    }
                    "setTimeout" -> {
                        val seconds = f("SECONDS", "3600").toLong()
                        require(seconds in 1..31536000)
                        e = e.copy(cursor = n.next, deadline = Math.addExact(now, seconds * 1000))
                    }
                    "waitCondition" -> {
                        if (evaluate(n, "TEST").boolean()) branch(n, "DO")
                        else {
                            val max = f("LIMIT", "60").toLong()
                            val interval = f("SECONDS", "60").toLong()
                            require(interval >= 60 && max in 1..1000)
                            val marker = "_wait:${n.id}"
                            val count = (e.locals[marker]?.text?.toLongOrNull() ?: 0) + 1
                            require(count <= max) { "Condition wait expired" }
                            e =
                                e.copy(
                                    state = State.WAITING_FOR_CONDITION,
                                    wakeAt = now + interval * 1000,
                                    locals = e.locals + (marker to Value.integer(count)),
                                )
                        }
                    }
                    "repeat" -> {
                        val count = f("LIMIT", "1").toInt()
                        require(count in 1..e.definition.maxIterations)
                        val body = n.branches["DO"]
                        e =
                            e.copy(
                                cursor = body ?: n.next,
                                frames =
                                    if (body == null) e.frames
                                    else e.frames + Frame("repeat", n.next, body, count, n.id),
                            )
                    }
                    "while" -> {
                        val previous =
                            e.frames.lastOrNull()?.takeIf {
                                it.kind == "whileCounter" && it.node == n.id
                            }
                        if (previous != null) e = e.copy(frames = e.frames.dropLast(1))
                        val count = (previous?.remaining ?: 0) + 1
                        if (evaluate(n, "TEST").boolean()) {
                            require(
                                count <=
                                    f("LIMIT", "100")
                                        .toInt()
                                        .coerceAtMost(e.definition.maxIterations)
                            ) {
                                "While iteration limit exceeded"
                            }
                            require(n.branches["DO"] != null)
                            e =
                                e.copy(
                                    cursor = n.branches["DO"],
                                    frames =
                                        e.frames +
                                            Frame("while", n.next, n.branches["DO"], count, n.id),
                                )
                        } else e = e.copy(cursor = n.next)
                    }
                    "break",
                    "continue" -> {
                        val index = e.frames.indexOfLast { it.kind in setOf("repeat", "while") }
                        require(index >= 0) { "Loop control outside loop" }
                        val frame = e.frames[index]
                        e =
                            e.copy(
                                frames = e.frames.take(if (n.op == "break") index else index + 1),
                                cursor = if (n.op == "break") frame.returnTo else null,
                            )
                    }
                    "set" -> {
                        set(f("NAME"), Scope.valueOf(f("SCOPE", "LOCAL")), evaluate(n, "VALUE"))
                        e = e.copy(cursor = n.next)
                    }
                    "delete" -> {
                        set(f("NAME"), Scope.valueOf(f("SCOPE", "LOCAL")), Value.NULL)
                        e = e.copy(cursor = n.next)
                    }
                    "call" -> {
                        require(e.frames.count { it.kind == "call" } < 8) { "Call depth exceeded" }
                        val d = e.library[f("WORKFLOW")] ?: error("Workflow snapshot missing")
                        require(
                            d.id != e.definition.id && e.frames.none { it.definition?.id == d.id }
                        ) {
                            "Recursive workflow call"
                        }
                        var locals =
                            d.variables
                                .filter { it.scope == Scope.LOCAL }
                                .associate { it.name to it.default }
                        if (f("INPUTNAME").isNotEmpty()) {
                            val value = evaluate(n, "INPUT")
                            require(
                                d.variables.any {
                                    it.scope == Scope.LOCAL &&
                                        it.name == f("INPUTNAME") &&
                                        it.type == value.type
                                }
                            ) {
                                "Sub-workflow input type mismatch"
                            }
                            locals = locals + (f("INPUTNAME") to value)
                        }
                        e =
                            e.copy(
                                frames =
                                    e.frames +
                                        Frame(
                                            "call",
                                            n.next,
                                            definition = e.definition,
                                            locals = e.locals,
                                            outputName = f("OUTPUTNAME"),
                                        ),
                                definition = d,
                                cursor = d.entry,
                                locals = locals,
                            )
                    }
                    "return" -> {
                        val output =
                            n.expressions["VALUE"]?.let { Expressions.evaluate(it, ctx()) }
                                ?: Value.NULL
                        val index = e.frames.indexOfLast { it.kind == "call" }
                        if (index < 0) e = e.copy(state = State.COMPLETED)
                        else {
                            val frame = e.frames[index]
                            e =
                                e.copy(
                                    definition = requireNotNull(frame.definition),
                                    locals = frame.locals,
                                    cursor = frame.returnTo,
                                    frames = e.frames.take(index),
                                )
                            if (frame.outputName.isNotEmpty())
                                set(frame.outputName, Scope.LOCAL, output)
                        }
                    }
                    "try" -> {
                        e =
                            e.copy(
                                cursor = n.branches["DO"],
                                frames =
                                    e.frames + Frame("try", n.next, body = n.branches["ERROR"]),
                            )
                    }
                    "parallel" -> { // Deterministic sequential interleaving: shared locals, A
                        // completes before B.
                        e =
                            e.copy(
                                cursor = n.branches["A"],
                                frames =
                                    e.frames +
                                        Frame("branch", n.next) +
                                        Frame("branch", n.branches["B"]),
                            )
                    }
                    "assert" -> {
                        require(evaluate(n, "TEST").boolean()) { f("MESSAGE", "Assertion failed") }
                        e = e.copy(cursor = n.next)
                    }
                    "log" -> {
                        record(n.id, f("MESSAGE"))
                        e = e.copy(cursor = n.next)
                    }
                    "breakpoint" ->
                        e =
                            e.copy(
                                cursor = n.next,
                                state = if (simulationBreakpoints) State.PAUSED else State.RUNNING,
                            )
                    "stop" ->
                        e = e.copy(state = State.COMPLETED, cursor = null, frames = emptyList())
                    else -> error("Unsupported node ${n.op}")
                }
            }
            if (e.state == State.RUNNING && e.cursor == null && e.frames.isEmpty())
                e = e.copy(state = State.COMPLETED)
        } catch (failure: Exception) {
            val index = e.frames.indexOfLast { it.kind == "try" }
            record(e.cursor ?: "", failure.message ?: failure.javaClass.simpleName)
            if (index >= 0) {
                val frame = e.frames[index]
                val call = e.frames.drop(index + 1).firstOrNull { it.kind == "call" }
                e =
                    e.copy(
                        definition = call?.definition ?: e.definition,
                        locals = call?.locals ?: e.locals,
                        cursor = frame.body ?: frame.returnTo,
                        frames = e.frames.take(index) + Frame("branch", frame.returnTo),
                        state = State.RUNNING,
                        error = failure.message,
                    )
            } else
                e =
                    e.copy(
                        state = State.FAILED,
                        error = failure.message ?: "Runtime failure",
                        interaction = null,
                        wakeAt = null,
                    )
        }
        val deadline = e.deadline
        val wakeAt = e.wakeAt
        if (e.state !in terminal && deadline != null && (wakeAt == null || wakeAt > deadline))
            e = e.copy(wakeAt = deadline)
        return Tick(e, effects, values)
    }

    fun respond(e: Execution, token: String, response: String, now: Long): Execution {
        val i = e.interaction ?: return e
        if (
            e.state != State.WAITING_FOR_USER ||
                token != i.token ||
                i.deadline != null && now >= i.deadline ||
                e.deadline?.let { now >= it } == true
        )
            return e
        val n = e.definition.nodes.first { it.id == i.node }
        var locals = e.locals
        val branch =
            when {
                response == "__cancel" -> "CANCEL"
                i.kind == "checklist" -> {
                    val checked = response.split(',').mapNotNull(String::toIntOrNull)
                    require(i.required.all { it in checked }) { "Complete all required items" }
                    "DONE"
                }
                i.kind == "text" || i.kind == "number" -> {
                    val value =
                        if (i.kind == "number")
                            Value.number(response.toDouble()).also {
                                require(
                                    it.number() >=
                                        (n.fields["MIN"]?.toDoubleOrNull() ?: -Double.MAX_VALUE) &&
                                        it.number() <=
                                            (n.fields["MAX"]?.toDoubleOrNull() ?: Double.MAX_VALUE)
                                )
                            }
                        else
                            Value.string(response).also {
                                require(it.text.length >= (n.fields["MIN"]?.toIntOrNull() ?: 0))
                            }
                    require(i.scope == Scope.LOCAL) {
                        "Response variables must be local; use SET for persistent values"
                    }
                    if (i.variable.isNotBlank()) {
                        val declaration =
                            e.definition.variables.find {
                                it.name == i.variable && it.scope == Scope.LOCAL
                            }
                        require(declaration?.type == value.type)
                        locals = locals + (i.variable to value)
                    }
                    "YES"
                }
                else -> {
                    require(response in i.options)
                    if (i.variable.isNotBlank()) {
                        require(
                            e.definition.variables.any {
                                it.name == i.variable &&
                                    it.scope == Scope.LOCAL &&
                                    it.type == Type.STRING
                            }
                        )
                        locals = locals + (i.variable to Value.string(response))
                    }
                    val index = i.options.indexOf(response)
                    if (n.branches["CHOICE$index"] != null) "CHOICE$index"
                    else
                        when (index) {
                            0 -> "YES"
                            1 -> "NO"
                            else -> "OTHER"
                        }
                }
            }
        return e.copy(
            state = State.RUNNING,
            interaction = null,
            wakeAt = null,
            locals = locals,
            cursor = n.branches[branch] ?: n.next,
            frames =
                if (n.branches[branch] != null) e.frames + Frame("branch", n.next) else e.frames,
            trace = (e.trace + Trace(now, n.id, "Response routed to $branch")).takeLast(1000),
        )
    }

    companion object {
        val terminal = setOf(State.COMPLETED, State.CANCELLED, State.FAILED, State.EXPIRED)
    }
}

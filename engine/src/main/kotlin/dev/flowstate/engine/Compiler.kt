package dev.flowstate.engine

import java.time.*
import kotlinx.serialization.json.*

object Compiler {

    val actions =
        setOf(
            "message",
            "notifyUpdate",
            "ask",
            "checklist",
            "if",
            "switch",
            "wait",
            "waitUntil",
            "waitClock",
            "setTimeout",
            "waitCondition",
            "repeat",
            "while",
            "break",
            "continue",
            "stop",
            "return",
            "call",
            "set",
            "delete",
            "log",
            "assert",
            "try",
            "parallel",
            "notifyCancel",
            "breakpoint",
        )

    private val unary =
        setOf("not", "empty", "trim", "upper", "lower", "length", "toString", "toNumber", "elapsed")

    private val binary =
        setOf(
            "and",
            "or",
            "xor",
            "eq",
            "ne",
            "gt",
            "lt",
            "ge",
            "le",
            "add",
            "subtract",
            "multiply",
            "divide",
            "mod",
            "contains",
            "starts",
            "ends",
            "concat",
            "append",
            "remove",
            "item",
            "join",
            "betweenTime",
            "addDuration",
            "subtractDuration",
            "format",
        )

    private val zero =
        setOf(
            "now",
            "date",
            "time",
            "weekday",
            "month",
            "weekend",
            "occupancy",
            "latitude",
            "longitude",
            "radius",
            "lastEntry",
            "lastExit",
            "lastDwell",
            "dwellDuration",
        )

    fun compile(
        source: String,
        id: String,
        version: Int,
        locations: Set<String> = emptySet(),
        workflows: Set<String> = emptySet(),
        templates: Map<String, ChecklistTemplate> = emptyMap(),
    ): Definition {

        var currentBlock = ""

        try {

            require(source.length <= 2_000_000) { "Workspace exceeds 2 MB" }

            val root = codec.parseToJsonElement(SafeInput.json(source)).jsonObject

            val tops =
                root["blocks"]?.jsonObject?.get("blocks")?.jsonArray
                    ?: throw ValidationException(
                        listOf(Issue("EMPTY", "Add a trigger and connect actions"))
                    )

            val issues = mutableListOf<Issue>()

            val nodes = mutableListOf<Node>()

            val seen = mutableSetOf<String>()

            val vars = mutableListOf<Variable>()

            fun fields(b: JsonObject) =
                b["fields"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap()

            fun input(b: JsonObject, name: String): JsonObject? {

                val i = b["inputs"]?.jsonObject?.get(name)?.jsonObject

                return (i?.get("block") ?: i?.get("shadow"))?.jsonObject
            }

            fun identify(b: JsonObject, depth: Int): String {

                require(depth <= 64) { "Block nesting exceeds 64" }

                require(seen.size < 2000) { "Too many blocks" }

                require(b["disabled"]?.jsonPrimitive?.booleanOrNull != true) {
                    "Remove disabled blocks before compiling"
                }

                val key = b["id"]?.jsonPrimitive?.content ?: error("Missing block ID")

                currentBlock = key

                val blockVersion = b["extraState"]?.jsonObject?.get("version")

                if (blockVersion != null && blockVersion.jsonPrimitive.intOrNull != 1)
                    throw ValidationException(
                        listOf(Issue("BLOCK_VERSION", "Unsupported block version", key))
                    )

                require(seen.add(key)) { "Duplicate block ID $key" }

                return key
            }

            fun expression(b: JsonObject?, depth: Int): Expr {

                if (b == null) {

                    issues += Issue("INPUT", "Connect the required expression")

                    return Expr("literal", Value.NULL)
                }

                val bid = identify(b, depth)

                val type = b["type"]!!.jsonPrimitive.content

                val f = fields(b)

                return when (type) {
                    "fs_value" -> {

                        val t = Type.valueOf(f["TYPE"] ?: "STRING")

                        val text = f["VALUE"] ?: ""

                        val v =
                            when (t) {
                                Type.BOOLEAN -> Value.bool(text.toBooleanStrict())

                                Type.INTEGER -> Value.integer(text.toLong())

                                Type.DECIMAL -> Value.number(text.toDouble())

                                Type.INSTANT ->
                                    Value(t, Instant.parse(text).toEpochMilli().toString())

                                Type.DURATION ->
                                    Value(t, Math.multiplyExact(text.toLong(), 1000).toString())

                                Type.NULL -> Value.NULL

                                Type.LIST ->
                                    Value(
                                        Type.LIST,
                                        items =
                                            text
                                                .split('|')
                                                .filter { it.isNotEmpty() }
                                                .map(Value::string),
                                    )

                                else -> Value.string(text)
                            }

                        Expr("literal", v)
                    }

                    "fs_list" -> {

                        val count = (f["COUNT"] ?: "2").toInt()
                        require(count in 0..100)

                        Expr(
                            "list",
                            name = f["TYPE"] ?: "STRING",
                            args =
                                (0 until count).map { expression(input(b, "ITEM$it"), depth + 1) },
                        )
                    }

                    "fs_get" ->
                        Expr(
                            "get",
                            name = f["NAME"] ?: "",
                            scope = Scope.valueOf(f["SCOPE"] ?: "LOCAL"),
                        )

                    "fs_expr" -> {

                        val op = f["OP"] ?: "eq"

                        val n =
                            when (op) {
                                in unary,
                                "beforeTime",
                                "afterTime" -> 1

                                in binary -> 2

                                "range" -> 3

                                in zero -> 0

                                else -> {

                                    issues += Issue("EXPR", "Unsupported operation $op", bid)

                                    0
                                }
                            }

                        (n..2).forEach {
                            if (input(b, listOf("A", "B", "C")[it]) != null)
                                issues += Issue("UNUSED", "Remove unused expression input", bid)
                        }

                        Expr(
                            op,
                            name = f["NAME"] ?: "",
                            args =
                                (0 until n).map {
                                    expression(input(b, listOf("A", "B", "C")[it]), depth + 1)
                                },
                        )
                    }

                    else -> {

                        issues += Issue("TYPE", "Not an expression: $type", bid)

                        Expr("literal", Value.NULL)
                    }
                }
            }

            fun chain(first: JsonObject?, depth: Int): String? {

                if (first == null) return null

                val bid = identify(first, depth)

                val type = first["type"]!!.jsonPrimitive.content

                val f = fields(first)

                require(f.values.all { it.length <= 16384 }) { "Block fields exceed 16 KB" }

                if (type == "fs_variable") {

                    vars +=
                        Variable(
                            f["NAME"] ?: "",
                            Type.valueOf(f["TYPE"] ?: "STRING"),
                            Scope.valueOf(f["SCOPE"] ?: "LOCAL"),
                            if (f["DEFAULT"].isNullOrEmpty()) Value.NULL
                            else if (f["TYPE"] == "LIST" && !f["ELEMENTTYPE"].isNullOrBlank())
                                Value(
                                    Type.LIST,
                                    items =
                                        f["DEFAULT"]!!.split('|').map {
                                            Value.parse(Type.valueOf(f["ELEMENTTYPE"]!!), it)
                                        },
                                    elementType = Type.valueOf(f["ELEMENTTYPE"]!!),
                                )
                            else Value.parse(Type.valueOf(f["TYPE"] ?: "STRING"), f["DEFAULT"]!!),
                            elementType =
                                f["ELEMENTTYPE"]?.takeIf { it.isNotBlank() }?.let(Type::valueOf),
                        )

                    return chain(first["next"]?.jsonObject?.get("block")?.jsonObject, depth)
                }

                val op = type.removePrefix("fs_")

                if (op !in actions) issues += Issue("BLOCK", "Unsupported action $type", bid)

                val expressions = mutableMapOf<String, Expr>()

                val expressionNames =
                    when (op) {
                        "if",
                        "while",
                        "assert",
                        "waitCondition" -> listOf("TEST")

                        "set",
                        "switch",
                        "waitUntil" -> listOf("VALUE")

                        "call" ->
                            (if (f["INPUTNAME"].isNullOrEmpty()) emptyList() else listOf("INPUT")) +
                                (f["INPUTNAMES"] ?: "")
                                    .split(',')
                                    .filter { it.isNotBlank() }
                                    .indices
                                    .map { "PARAM$it" }

                        "return" ->
                            if (input(first, "VALUE") == null) emptyList() else listOf("VALUE")

                        else -> emptyList()
                    }

                expressionNames.forEach {
                    expressions[it] = expression(input(first, it), depth + 1)
                }

                val branches = mutableMapOf<String, String?>()

                val names =
                    when (op) {
                        "if" -> listOf("YES", "NO")

                        "ask" ->
                            listOf("YES", "NO", "OTHER", "CANCEL", "TIMEOUT") +
                                (if (f["KIND"] == "choice")
                                    (f["OPTIONS"] ?: "")
                                        .split('|')
                                        .filter { it.isNotBlank() }
                                        .indices
                                        .map { "CHOICE$it" }
                                else emptyList())

                        "checklist" -> listOf("DONE", "CANCEL", "TIMEOUT")

                        "repeat",
                        "while",
                        "waitCondition" -> listOf("DO")

                        "try" -> listOf("DO", "ERROR")

                        "parallel" -> listOf("A", "B")

                        "switch" -> listOf("YES", "NO", "OTHER")

                        else -> emptyList()
                    }

                names.forEach { branches[it] = chain(input(first, it), depth + 1) }

                val next = chain(first["next"]?.jsonObject?.get("block")?.jsonObject, depth)

                nodes += Node(bid, op, f, expressions, branches, next)

                return bid
            }

            val triggers = tops.filter {
                it.jsonObject["type"]?.jsonPrimitive?.content == "fs_trigger"
            }

            if (triggers.size != 1 || tops.size != 1)
                issues +=
                    Issue("ROOT", "Exactly one trigger is required; connect all actions to it")

            val top = triggers.firstOrNull()?.jsonObject ?: throw ValidationException(issues)

            identify(top, 0)

            val f = fields(top)

            val trigger =
                Trigger(
                    kind = f["KIND"] ?: "manual",
                    times = (f["TIMES"] ?: "09:00").split(',').map { it.trim() },
                    zone = f["ZONE"] ?: "device",
                    recurrence = f["RECURRENCE"] ?: "daily",
                    days =
                        (f["DAYS"] ?: "1,2,3,4,5,6,7")
                            .split(',')
                            .filter { it.isNotBlank() }
                            .map { it.trim().toInt() },
                    dates =
                        (f["DATES"] ?: "").split(',').map { it.trim() }.filter { it.isNotEmpty() },
                    intervalDays = (f["INTERVAL"] ?: "1").toInt(),
                    startDate = f["START"] ?: "",
                    endDate = f["END"] ?: "",
                    locationId = f["LOCATION"] ?: "",
                    transition = f["TRANSITION"] ?: "exit",
                    cooldownSeconds = (f["COOLDOWN"] ?: "300").toLong(),
                    catchUp = f["CATCHUP"] ?: "skip",
                    windowDay = (f["WINDOWDAY"] ?: "5").toInt(),
                    eligibleFrom = f["ELIGIBLEFROM"] ?: "",
                    eligibleTo = f["ELIGIBLETO"] ?: "",
                    concurrency = f["CONCURRENCY"] ?: "parallel",
                    maxActive = (f["MAXACTIVE"] ?: "4").toInt(),
                    frequency = f["FREQUENCY"] ?: "cooldown",
                    priority = (f["PRIORITY"] ?: "0").toInt(),
                )

            val entry = chain(top["next"]?.jsonObject?.get("block")?.jsonObject, 0)

            val d =
                Definition(
                    id = id,
                    version = version,
                    entry = entry,
                    nodes = nodes,
                    variables = vars,
                    trigger = trigger,
                    maxSteps = (f["MAXSTEPS"] ?: "10000").toInt(),
                    maxIterations = (f["MAXITERATIONS"] ?: "1000").toInt(),
                    maxBurst = (f["BURST"] ?: "100").toInt(),
                    notificationRate = (f["RATE"] ?: "30").toInt(),
                    maxCallDepth = (f["CALLDEPTH"] ?: "8").toInt(),
                    checklists =
                        templates.filterKeys { template ->
                            nodes.any { it.fields["TEMPLATE"] == template }
                        },
                )

            issues +=
                validate(d, locations, workflows).map {
                    if (it.block.isBlank()) it.copy(block = top["id"]!!.jsonPrimitive.content)
                    else it
                }

            if (issues.isNotEmpty()) throw ValidationException(issues)

            return d
        } catch (e: ValidationException) {
            throw e
        } catch (e: Exception) {
            throw ValidationException(
                listOf(
                    Issue(
                        "FIELD",
                        e.message ?: "Invalid field value",
                        currentBlock,
                        "Check the highlighted block’s field values and required inputs",
                    )
                )
            )
        }
    }

    fun validate(
        d: Definition,
        locations: Set<String> = emptySet(),
        workflows: Set<String> = emptySet(),
    ): List<Issue> {

        val errors = mutableListOf<Issue>()

        fun issue(message: String, id: String = "") {

            val (code, correction) =
                when {
                    message.contains("budget", true) || message.contains("limit", true) ->
                        "BUDGET" to
                            "Choose a value within the displayed limits; simplify the workflow if it exhausts its budget"
                    message.contains("type", true) ||
                        message.contains("Boolean", true) ||
                        message.contains("variable", true) ||
                        message.contains("element", true) ->
                        "TYPE" to
                            "Declare the referenced variable and connect an expression matching its type, scope and list element type"
                    message.contains("location", true) ||
                        message.contains("workflow", true) ||
                        message.contains("template", true) ->
                        "REFERENCE" to
                            "Select an existing saved resource and remove missing references"
                    message.contains("time", true) ||
                        message.contains("zone", true) ||
                        message.contains("date", true) ||
                        message.contains("day", true) ->
                        "SCHEDULE" to
                            "Use valid clock/date fields and a device or IANA timezone; check recurrence eligibility"
                    else -> "VALIDATION" to "Correct the highlighted configuration: $message"
                }
            errors += Issue(code, message, id, correction)
        }

        if (
            d.schema != 1 ||
                d.nodes.size > 2000 ||
                d.maxSteps !in 1..10000 ||
                d.maxIterations !in 1..1000 ||
                d.maxBurst !in 1..100 ||
                d.notificationRate !in 1..120 ||
                d.maxCallDepth !in 1..8
        )
            issue("Unsupported schema or unsafe budget")

        val ids = d.nodes.map { it.id }.toSet()

        if (ids.size != d.nodes.size) issue("Duplicate node IDs")

        if (d.entry == null || d.entry !in ids) issue("Connect an action to the trigger")

        val declarations = d.variables.associateBy { Expressions.key(it.scope, it.name, d.id) }

        if (declarations.size != d.variables.size) issue("Duplicate scoped variable")

        d.variables.forEach {
            if (it.elementType != null && it.type != Type.LIST)
                issue("Only list variables can declare an element type")
            if (!it.accepts(it.default))
                issue("Variable default has an incompatible list element type")
            if (it.default.type != Type.NULL && it.default.type != it.type)
                issue("Variable default must match its declared type")

            if (!it.name.matches(Regex("[A-Za-z][A-Za-z0-9_]{0,63}")))
                issue(
                    "Variable names must start with a letter and contain only letters, numbers or underscores"
                )
        }

        fun infer(e: Expr, node: String, depth: Int = 0): Type {

            if (depth > 64) {

                issue("Expression too deep", node)

                return Type.NULL
            }

            if (e.op in Dependencies.locationOps && e.name !in locations)
                issue("Select an existing saved location", node)
            if (e.op == "literal") return e.value?.type ?: Type.NULL

            if (e.op == "list" && e.name.isNotBlank()) {

                val element = runCatching { Type.valueOf(e.name) }.getOrNull()

                if (
                    element == null ||
                        e.args.any { infer(it, node, depth + 1) !in setOf(element, Type.NULL) }
                )
                    issue("List items must match the selected element type", node)
            }

            if (e.op == "get")
                return declarations[Expressions.key(e.scope, e.name, d.id)]?.type
                    ?: Type.NULL.also { issue("Declare variable ${e.name} first", node) }

            val arity =
                when (e.op) {
                    in unary,
                    "beforeTime",
                    "afterTime" -> 1

                    in binary -> 2

                    "range" -> 3

                    in zero -> 0

                    "list" -> e.args.size

                    else -> -1
                }

            if (e.args.size != arity || arity < 0) {

                issue("Invalid expression ${e.op}", node)

                return Type.NULL
            }

            val types = e.args.map { infer(it, node, depth + 1) }

            if (e.op in setOf("and", "or", "xor", "not") && types.any { it != Type.BOOLEAN })
                issue("Boolean input required", node)

            if (
                e.op in setOf("add", "subtract", "multiply", "divide", "mod", "range") &&
                    types.any { !Expressions.numeric(it) }
            )
                issue("Numeric input required; convert explicitly", node)

            if (
                e.op in setOf("eq", "ne", "gt", "ge", "lt", "le") &&
                    types.size == 2 &&
                    types[0] != types[1] &&
                    !types.all(Expressions::numeric)
            )
                issue("Comparison types differ", node)

            if (
                e.op in
                    setOf(
                        "trim",
                        "upper",
                        "lower",
                        "toNumber",
                        "beforeTime",
                        "afterTime",
                        "concat",
                        "starts",
                        "ends",
                        "betweenTime",
                    ) && types.any { it != Type.STRING }
            )
                issue("String input required", node)

            if (
                e.op in setOf("append", "remove", "item", "join") &&
                    types.firstOrNull() != Type.LIST
            )
                issue("List input required", node)

            if (e.op == "item" && types.getOrNull(1) != Type.INTEGER)
                issue("List index must be Integer", node)

            if (e.op == "join" && types.getOrNull(1) != Type.STRING)
                issue("Join separator must be String", node)

            if (
                e.op in setOf("elapsed", "format", "addDuration", "subtractDuration") &&
                    types.firstOrNull() != Type.INSTANT
            )
                issue("Timestamp input required", node)

            if (
                e.op in setOf("addDuration", "subtractDuration") &&
                    types.getOrNull(1) != Type.DURATION
            )
                issue("Duration input required", node)

            return when (e.op) {
                "and",
                "or",
                "xor",
                "not",
                "eq",
                "ne",
                "gt",
                "ge",
                "lt",
                "le",
                "range",
                "empty",
                "contains",
                "starts",
                "ends",
                "weekend",
                "betweenTime",
                "beforeTime",
                "afterTime" -> Type.BOOLEAN

                "now",
                "lastEntry",
                "lastExit",
                "lastDwell",
                "addDuration",
                "subtractDuration" -> Type.INSTANT

                "elapsed",
                "dwellDuration" -> Type.DURATION

                "add",
                "subtract",
                "multiply",
                "mod" -> if (types.all { it == Type.INTEGER }) Type.INTEGER else Type.DECIMAL

                "divide",
                "latitude",
                "longitude",
                "radius",
                "toNumber" -> Type.DECIMAL

                "weekday",
                "month",
                "length" -> Type.INTEGER

                "list",
                "append",
                "remove" -> Type.LIST

                "item" -> Type.NULL

                else -> Type.STRING
            }
        }

        d.nodes.forEach { n ->
            if (n.op in setOf("ask", "checklist", "message", "notifyUpdate")) {

                if ((n.fields["CHANNEL"] ?: "normal") !in setOf("low", "normal", "high"))
                    issue("Choose low, normal or high notification channel", n.id)

                if ((n.fields["CATEGORY"] ?: "reminder") !in setOf("reminder", "event", "status"))
                    issue("Choose reminder, event or status category", n.id)

                if ((n.fields["EXPIRE"] ?: "0").toLongOrNull()?.let { it in 0..31536000 } != true)
                    issue("Invalid notification expiration", n.id)

                if (n.op == "notifyUpdate" && n.fields["TARGET"].isNullOrBlank())
                    issue("Name the owned notification to update", n.id)
            }

            if (n.op !in actions) issue("Unsupported node ${n.op}", n.id)

            (n.branches.values + n.next).filterNotNull().forEach {
                if (it !in ids) issue("Missing target $it", n.id)
            }

            n.expressions.forEach { (key, e) ->
                val t = infer(e, n.id)

                if (key == "TEST" && t != Type.BOOLEAN) issue("Condition must be Boolean", n.id)
            }

            if (
                n.op in setOf("repeat", "while") &&
                    (n.fields["LIMIT"]?.toIntOrNull() ?: 0) !in 1..d.maxIterations
            )
                issue("Set loop limit between 1 and ${d.maxIterations}", n.id)

            if (
                n.op in setOf("wait", "setTimeout") &&
                    (n.fields["SECONDS"]?.toLongOrNull() ?: 0) !in 1..31536000
            )
                issue("Wait must be 1 second to 1 year", n.id)

            if (n.op == "waitClock")
                try {

                    LocalTime.parse(n.fields["TIME"] ?: "")

                    val zone = n.fields["ZONE"] ?: "device"

                    if (zone != "device") ZoneId.of(zone)
                } catch (failure: Exception) {

                    issue("Choose a valid clock time and timezone", n.id)
                }

            if (n.op == "waitCondition" && (n.fields["SECONDS"]?.toLongOrNull() ?: 0) < 60)
                issue("Condition checks require at least 60 seconds", n.id)

            if (
                n.op == "call" &&
                    (n.fields["WORKFLOW"] !in workflows || n.fields["WORKFLOW"] == d.id)
            )
                issue("Invalid or recursive workflow reference", n.id)

            if (
                n.op == "ask" &&
                    n.fields["KIND"] in setOf("text", "number") &&
                    (n.fields["SCOPE"] ?: "LOCAL") != "LOCAL"
            )
                issue("Store responses locally, then SET persistent values", n.id)

            if (n.op in setOf("ask", "checklist")) {

                val snooze =
                    (n.fields["SNOOZE"] ?: "5,15").split(',').map { it.trim().toLongOrNull() }

                if (snooze.size !in 1..5 || snooze.any { it == null || it !in 1..1440 })
                    issue("Provide 1–5 snooze durations from 1 to 1440 minutes", n.id)

                if ((n.fields["MAXSNOOZE"] ?: "3").toIntOrNull()?.let { it in 0..10 } != true)
                    issue("Maximum snoozes must be 0–10", n.id)

                if ((n.fields["FOLLOWUP"] ?: "0").toLongOrNull()?.let { it in 0..604800 } != true)
                    issue("Follow-up must be disabled (0) or within seven days", n.id)

                if (!n.fields["TEMPLATE"].isNullOrBlank() && n.fields["TEMPLATE"] !in d.checklists)
                    issue("Select an existing checklist template", n.id)

                val options =
                    (n.fields["OPTIONS"] ?: "Option 1|Option 2").split('|').filter {
                        it.isNotBlank()
                    }

                if (
                    options.size > 100 ||
                        options.size != options.toSet().size ||
                        (n.op == "checklist" || n.fields["KIND"] == "choice") &&
                            options.isEmpty() &&
                            n.fields["TEMPLATE"].isNullOrBlank()
                )
                    issue("Provide 1–100 distinct options/items", n.id)

                if (((n.fields["TIMEOUT"] ?: "900").toLongOrNull() ?: -1L) !in 0L..31536000L)
                    issue("Invalid interaction timeout", n.id)

                if (n.op == "ask") {

                    val kind = n.fields["KIND"] ?: "choice"

                    if (kind !in setOf("choice", "yesno", "confirm", "text", "number"))
                        issue("Unsupported question kind", n.id)

                    val responseName = n.fields["NAME"].orEmpty()

                    if (responseName.isNotBlank()) {

                        val v = declarations[responseName]

                        val expected = if (kind == "number") Type.DECIMAL else Type.STRING

                        if (
                            v?.scope != Scope.LOCAL ||
                                v.type != expected ||
                                (n.fields["SCOPE"] ?: "LOCAL") != "LOCAL"
                        )
                            issue("Response requires a declared local $expected variable", n.id)
                    }

                    if (kind == "text") {

                        val min = n.fields["MIN"].orEmpty().ifEmpty { "0" }.toIntOrNull()

                        val max = n.fields["MAX"].orEmpty().ifEmpty { "16384" }.toIntOrNull()

                        if (min == null || max == null || min !in 0..16384 || max !in min..16384)
                            issue("Text bounds must be between 0 and 16384, min <= max", n.id)
                    }

                    if (kind == "number") {

                        val min =
                            n.fields["MIN"]
                                .orEmpty()
                                .ifEmpty { (-Double.MAX_VALUE).toString() }
                                .toDoubleOrNull()

                        val max =
                            n.fields["MAX"]
                                .orEmpty()
                                .ifEmpty { Double.MAX_VALUE.toString() }
                                .toDoubleOrNull()

                        if (
                            min == null ||
                                max == null ||
                                !min.isFinite() ||
                                !max.isFinite() ||
                                min > max
                        )
                            issue("Choose finite numeric bounds with min <= max", n.id)
                    }
                }
            }

            if (n.op == "set") {

                val v =
                    declarations[
                        Expressions.key(
                            Scope.valueOf(n.fields["SCOPE"] ?: "LOCAL"),
                            n.fields["NAME"] ?: "",
                            d.id,
                        )]

                val t = n.expressions["VALUE"]?.let { infer(it, n.id) }

                if (
                    v == null ||
                        t != v.type ||
                        v.elementType != null &&
                            n.expressions["VALUE"]?.let {
                                it.op == "list" && it.name != v.elementType.name
                            } == true
                )
                    issue("Assignment needs a declared variable with matching type", n.id)
            }
        }

        try {

            Scheduling.validate(d.trigger)

            if (d.trigger.kind == "location" && d.trigger.locationId !in locations)
                issue("Select an existing location")
        } catch (e: Exception) {

            issue(e.message ?: "Invalid trigger")
        }

        // Imported IR must be acyclic. Looping is represented only by guarded loop nodes and

        // durable frames.

        val visited = mutableSetOf<String>()

        val visiting = mutableSetOf<String>()

        fun walk(id: String?, depth: Int) {

            if (id == null || id in visited) return

            if (depth > 128 || !visiting.add(id)) {

                issue("Cycle or excessive graph depth", id)

                return
            }

            val n = d.nodes.find { it.id == id }

            n?.branches?.values?.forEach { walk(it, depth + 1) }

            walk(n?.next, depth + 1)

            visiting.remove(id)

            visited.add(id)
        }

        walk(d.entry, 0)

        if (visited.size != d.nodes.size) issue("Disconnected executable nodes")

        val contexts = mutableSetOf<Pair<String, Int>>()

        fun checkLoop(id: String?, loops: Int, depth: Int) {

            if (id == null || depth > 128 || !contexts.add(id to loops)) return

            val node = d.nodes.find { it.id == id } ?: return

            if (node.op in setOf("break", "continue") && loops == 0)
                issue("BREAK/CONTINUE must be inside a loop", id)

            node.branches.forEach { (name, target) ->
                checkLoop(
                    target,
                    if (node.op == "parallel") 0
                    else loops + if (node.op in setOf("repeat", "while") && name == "DO") 1 else 0,
                    depth + 1,
                )
            }

            checkLoop(node.next, loops, depth + 1)
        }

        checkLoop(d.entry, 0, 0)

        return errors
    }

    fun validateSharedVariables(definitions: Collection<Definition>): List<Issue> {

        val variables = mutableMapOf<String, Pair<Type, Type?>>()

        val errors = mutableListOf<Issue>()

        definitions.forEach { d ->
            d.variables
                .filter { it.scope != Scope.LOCAL }
                .forEach { v ->
                    val old =
                        variables.putIfAbsent(
                            Expressions.key(v.scope, v.name, d.id),
                            v.type to v.elementType,
                        )

                    if (old != null && old != (v.type to v.elementType))
                        errors +=
                            Issue(
                                if (v.scope == Scope.GLOBAL) "GLOBAL_TYPE" else "AUTOMATION_TYPE",
                                "Persistent variable ${v.name} has incompatible declarations",
                            )
                }
        }

        return errors
    }

    fun warnings(d: Definition): List<Issue> =
        d.nodes.mapNotNull { n ->
            if (n.op in setOf("ask", "checklist") && n.fields["TIMEOUT"] == "0")
                Issue(
                    "UNBOUNDED_INTERACTION",
                    "This question can remain pending indefinitely",
                    n.id,
                    "Set a timeout or use SET execution timeout when indefinite waiting is unnecessary",
                    "WARNING",
                )
            else if (n.op == "message" && n.fields["CHANNEL"] == "high")
                Issue(
                    "HIGH_PRIORITY",
                    "This reminder uses a high priority channel",
                    n.id,
                    "Use normal or low priority for routine reminders",
                    "WARNING",
                )
            else null
        }

    fun validateCalls(definitions: Collection<Definition>): List<Issue> {
        val all = definitions.associateBy { it.id }
        return definitions.flatMap { d ->
            d.nodes
                .filter { it.op == "call" }
                .flatMap { n ->
                    val child = all[n.fields["WORKFLOW"]]
                    val issues = mutableListOf<Issue>()
                    fun issue(message: String) {
                        issues +=
                            Issue(
                                "CALL_BINDING",
                                message,
                                n.id,
                                "Map declared local parameters and outputs with matching types",
                            )
                    }
                    val inputs =
                        (n.fields["INPUTNAMES"] ?: "")
                            .split(',')
                            .map { it.trim() }
                            .filter { it.isNotBlank() } +
                            listOfNotNull(n.fields["INPUTNAME"]?.takeIf { it.isNotBlank() })
                    if (inputs.size > 20 || inputs.toSet().size != inputs.size)
                        issue("Provide at most 20 distinct parameter names")
                    if (child != null)
                        inputs.forEach { name ->
                            if (child.variables.none { it.name == name && it.scope == Scope.LOCAL })
                                issue("Sub-workflow has no local parameter $name")
                        }
                    val outputs = (n.fields["OUTPUTS"] ?: "").split(',').filter { it.isNotBlank() }
                    if (outputs.size > 20) issue("At most 20 output bindings are supported")
                    val targets = mutableSetOf<String>()
                    outputs.forEach { binding ->
                        val parts = binding.split(':').map { it.trim() }
                        if (parts.size != 2) issue("Use callee:caller for each output binding")
                        else {
                            val source =
                                child?.variables?.find {
                                    it.scope == Scope.LOCAL && it.name == parts[0]
                                }
                            val target =
                                d.variables.find { it.scope == Scope.LOCAL && it.name == parts[1] }
                            if (
                                target == null ||
                                    child != null &&
                                        (source == null ||
                                            source.type != target.type ||
                                            source.elementType != target.elementType)
                            )
                                issue(
                                    "Output $binding must reference compatible declared local variables"
                                )
                            if (!targets.add(parts[1]))
                                issue("Multiple outputs cannot target the same variable")
                        }
                    }
                    n.fields["STATUSNAME"]
                        ?.takeIf { it.isNotBlank() }
                        ?.let { name ->
                            if (
                                d.variables.none {
                                    it.name == name &&
                                        it.scope == Scope.LOCAL &&
                                        it.type == Type.STRING
                                }
                            )
                                issue("Declare a local STRING for returned execution status")
                        }
                    issues
                }
        }
    }

    fun validatePersistentValues(
        definitions: Collection<Definition>,
        values: Map<String, Value>,
    ): List<Issue> = definitions.flatMap { d ->
        d.variables
            .filter { it.scope != Scope.LOCAL }
            .mapNotNull { v ->
                val stored = values[Expressions.key(v.scope, v.name, d.id)]

                if (stored != null && stored.type != Type.NULL && !v.accepts(stored))
                    Issue(
                        "PERSISTENT_TYPE",
                        "Persistent variable ${v.name} already has a different type; use a new name",
                    )
                else null
            }
    }
}

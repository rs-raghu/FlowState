package dev.flowstate.engine

/** Resource references include nested expressions, not just the trigger's location. */
object Dependencies {
    val locationOps =
        setOf(
            "occupancy",
            "latitude",
            "longitude",
            "radius",
            "lastEntry",
            "lastExit",
            "lastDwell",
            "dwellDuration",
        )

    fun locations(d: Definition): Set<String> {
        val result = mutableSetOf<String>()
        if (d.trigger.kind == "location") result += d.trigger.locationId
        fun visit(e: Expr) {
            if (e.op in locationOps) result += e.name
            e.args.forEach(::visit)
        }
        d.nodes.forEach { it.expressions.values.forEach(::visit) }
        return result
    }

    fun describe(d: Definition): String = buildList {
        locations(d).takeIf { it.isNotEmpty() }?.let { add("Locations: " + it.joinToString()) }
        d.variables
            .takeIf { it.isNotEmpty() }
            ?.let { add("Variables: " + it.joinToString { v -> "${v.scope}.${v.name}" }) }
        d.nodes
            .mapNotNull { it.fields["TEMPLATE"]?.takeIf(String::isNotBlank) }
            .distinct()
            .takeIf { it.isNotEmpty() }
            ?.let { add("Checklists: " + it.joinToString()) }
        d.nodes
            .filter { it.op == "call" }
            .mapNotNull { it.fields["WORKFLOW"] }
            .distinct()
            .takeIf { it.isNotEmpty() }
            ?.let { add("Workflows: " + it.joinToString()) }
    }
        .joinToString("\n")
        .ifEmpty { "No resource dependencies" }
}

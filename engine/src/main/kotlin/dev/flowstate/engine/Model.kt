package dev.flowstate.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val codec = Json {
    encodeDefaults = true
    ignoreUnknownKeys = false
    explicitNulls = false
}

@Serializable
enum class Type {
    BOOLEAN,
    INTEGER,
    DECIMAL,
    STRING,
    INSTANT,
    DURATION,
    LIST,
    NULL,
}

@Serializable
enum class Scope {
    LOCAL,
    AUTOMATION,
    GLOBAL,
}

@Serializable
data class Value(val type: Type, val text: String = "", val items: List<Value> = emptyList()) {
    fun boolean(): Boolean {
        require(type == Type.BOOLEAN)
        return text.toBooleanStrict()
    }

    fun number(): Double {
        require(type == Type.INTEGER || type == Type.DECIMAL)
        return text.toDouble().also { require(it.isFinite()) }
    }

    fun display(): String =
        if (type == Type.LIST) items.joinToString(", ") { it.display() } else text

    companion object {
        fun parse(type: Type, text: String): Value =
            when (type) {
                Type.BOOLEAN -> bool(text.toBooleanStrict())
                Type.INTEGER -> integer(text.toLong())
                Type.DECIMAL -> number(text.toDouble())
                Type.INSTANT -> Value(type, java.time.Instant.parse(text).toEpochMilli().toString())
                Type.DURATION -> Value(type, Math.multiplyExact(text.toLong(), 1000).toString())
                Type.STRING -> string(text)
                Type.LIST ->
                    Value(type, items = text.split('|').filter { it.isNotEmpty() }.map(::string))
                Type.NULL -> NULL
            }

        fun bool(v: Boolean) = Value(Type.BOOLEAN, v.toString())

        fun number(v: Double) = Value(Type.DECIMAL, v.toString()).also { require(v.isFinite()) }

        fun integer(v: Long) = Value(Type.INTEGER, v.toString())

        fun string(v: String) = Value(Type.STRING, v).also { require(v.length <= 16384) }

        val NULL = Value(Type.NULL)
    }
}

@Serializable
data class Expr(
    val op: String,
    val value: Value? = null,
    val name: String = "",
    val scope: Scope = Scope.LOCAL,
    val args: List<Expr> = emptyList(),
)

@Serializable
data class Variable(
    val name: String,
    val type: Type,
    val scope: Scope,
    val default: Value = Value.NULL,
)

@Serializable
data class Trigger(
    val kind: String = "manual",
    val times: List<String> = listOf("09:00"),
    val zone: String = "device",
    val recurrence: String = "daily",
    val days: List<Int> = (1..7).toList(),
    val dates: List<String> = emptyList(),
    val intervalDays: Int = 1,
    val startDate: String = "",
    val endDate: String = "",
    val locationId: String = "",
    val transition: String = "exit",
    val cooldownSeconds: Long = 300,
    val catchUp: String = "skip",
    val graceSeconds: Long = 3600,
    val windowDay: Int = 5,
)

@Serializable
data class Node(
    val id: String,
    val op: String,
    val fields: Map<String, String> = emptyMap(),
    val expressions: Map<String, Expr> = emptyMap(),
    val branches: Map<String, String?> = emptyMap(),
    val next: String? = null,
)

@Serializable
data class Definition(
    val schema: Int = 1,
    val id: String,
    val version: Int = 1,
    val entry: String?,
    val nodes: List<Node>,
    val variables: List<Variable> = emptyList(),
    val trigger: Trigger = Trigger(),
    val maxSteps: Int = 10000,
    val maxIterations: Int = 1000,
)

@Serializable
enum class State {
    CREATED,
    QUEUED,
    RUNNING,
    WAITING_FOR_USER,
    WAITING_FOR_TIME,
    WAITING_FOR_CONDITION,
    PAUSED,
    COMPLETED,
    CANCELLED,
    FAILED,
    EXPIRED,
}

@Serializable
data class Frame(
    val kind: String,
    val returnTo: String?,
    val body: String? = null,
    val remaining: Int = 0,
    val node: String = "",
    val definition: Definition? = null,
    val locals: Map<String, Value> = emptyMap(),
    val outputName: String = "",
)

@Serializable
data class Interaction(
    val token: String,
    val node: String,
    val kind: String,
    val title: String,
    val options: List<String>,
    val deadline: Long?,
    val variable: String = "",
    val scope: Scope = Scope.LOCAL,
    val required: List<Int> = emptyList(),
    val completed: List<Int> = emptyList(),
    val snoozes: Int = 0,
    val snoozedUntil: Long? = null,
)

@Serializable data class Trace(val at: Long, val node: String, val detail: String)

@Serializable
data class Execution(
    val id: String,
    val definition: Definition,
    val cursor: String? = definition.entry,
    val state: State = State.CREATED,
    val frames: List<Frame> = emptyList(),
    val locals: Map<String, Value> = emptyMap(),
    val interaction: Interaction? = null,
    val wakeAt: Long? = null,
    val steps: Int = 0,
    val started: Long,
    val trace: List<Trace> = emptyList(),
    val error: String? = null,
    val library: Map<String, Definition> = emptyMap(),
    val deadline: Long? = null,
)

@Serializable
data class Effect(
    val id: String,
    val kind: String,
    val title: String,
    val body: String = "",
    val interaction: Interaction? = null,
)

data class Tick(
    val execution: Execution,
    val effects: List<Effect>,
    val persistent: Map<String, Value>,
)

data class EvaluationContext(
    val now: Long,
    val zone: String,
    val locals: Map<String, Value>,
    val persistent: Map<String, Value>,
    val automationId: String,
    val occupancy: Map<String, Pair<String, Long>> = emptyMap(),
)

data class Issue(
    val code: String,
    val message: String,
    val block: String = "",
    val correction: String = "Review this block's inputs",
    val severity: String = "ERROR",
)

class ValidationException(val issues: List<Issue>) :
    IllegalArgumentException(
        issues.joinToString("\n") { "${it.code}: ${it.message} [${it.block}]" }
    )

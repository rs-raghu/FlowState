package dev.flowstate.engine

import java.math.BigDecimal
import java.time.*
import java.time.format.DateTimeFormatter

object Expressions {
    fun key(scope: Scope, name: String, automation: String): String =
        when (scope) {
            Scope.LOCAL -> name
            Scope.AUTOMATION -> "$automation:$name"
            Scope.GLOBAL -> "global:$name"
        }

    fun evaluate(e: Expr, c: EvaluationContext, depth: Int = 0): Value {
        require(depth <= 64) { "Expression nesting exceeds 64" }
        fun arg(i: Int) = evaluate(e.args[i], c, depth + 1)
        val time = Instant.ofEpochMilli(c.now).atZone(ZoneId.of(c.zone))
        fun str(i: Int): String = arg(i).also { require(it.type == Type.STRING) }.text
        return when (e.op) {
            "literal" -> requireNotNull(e.value)
            "get" ->
                if (e.scope == Scope.LOCAL) c.locals[e.name] ?: Value.NULL
                else c.persistent[key(e.scope, e.name, c.automationId)] ?: Value.NULL
            "and" -> Value.bool(arg(0).boolean() && arg(1).boolean())
            "or" -> Value.bool(arg(0).boolean() || arg(1).boolean())
            "xor" -> Value.bool(arg(0).boolean() xor arg(1).boolean())
            "not" -> Value.bool(!arg(0).boolean())
            "eq",
            "ne" -> {
                val a = arg(0)
                val b = arg(1)
                require(a.type == b.type || numeric(a.type) && numeric(b.type))
                val same = if (numeric(a.type)) compareNumbers(a, b) == 0 else a == b
                Value.bool(if (e.op == "eq") same else !same)
            }
            "gt",
            "lt",
            "ge",
            "le" -> {
                val a = arg(0)
                val b = arg(1)
                val cmp =
                    if (numeric(a.type) && numeric(b.type)) compareNumbers(a, b)
                    else {
                        require(
                            a.type == b.type &&
                                a.type in listOf(Type.INSTANT, Type.DURATION, Type.STRING)
                        )
                        if (a.type == Type.STRING) a.text.compareTo(b.text)
                        else a.text.toLong().compareTo(b.text.toLong())
                    }
                Value.bool(
                    when (e.op) {
                        "gt" -> cmp > 0
                        "lt" -> cmp < 0
                        "ge" -> cmp >= 0
                        else -> cmp <= 0
                    }
                )
            }
            "add",
            "subtract",
            "multiply",
            "divide",
            "mod" -> {
                val left = arg(0)
                val right = arg(1)
                require(numeric(left.type) && numeric(right.type))
                if (left.type == Type.INTEGER && right.type == Type.INTEGER && e.op != "divide") {
                    val a = left.text.toLong()
                    val b = right.text.toLong()
                    Value.integer(
                        when (e.op) {
                            "add" -> Math.addExact(a, b)
                            "subtract" -> Math.subtractExact(a, b)
                            "multiply" -> Math.multiplyExact(a, b)
                            else -> {
                                require(b != 0L)
                                a % b
                            }
                        }
                    )
                } else {
                    val a = left.number()
                    val b = right.number()
                    Value.number(
                        when (e.op) {
                            "add" -> a + b
                            "subtract" -> a - b
                            "multiply" -> a * b
                            "divide" -> {
                                require(b != 0.0)
                                a / b
                            }
                            else -> {
                                require(b != 0.0)
                                a % b
                            }
                        }
                    )
                }
            }
            "range" -> {
                val value = arg(0)
                Value.bool(compareNumbers(value, arg(1)) >= 0 && compareNumbers(value, arg(2)) <= 0)
            }
            "empty" -> {
                val v = arg(0)
                Value.bool(
                    v.type == Type.NULL ||
                        v.type == Type.LIST && v.items.isEmpty() ||
                        v.type == Type.STRING && v.text.isEmpty()
                )
            }
            "contains" -> {
                val a = arg(0)
                Value.bool(
                    if (a.type == Type.LIST) a.items.contains(arg(1))
                    else {
                        require(a.type == Type.STRING)
                        a.text.contains(str(1))
                    }
                )
            }
            "starts",
            "ends" ->
                Value.bool(
                    if (e.op == "starts") str(0).startsWith(str(1)) else str(0).endsWith(str(1))
                )
            "concat" -> Value.string(str(0) + str(1))
            "trim" -> Value.string(str(0).trim())
            "upper" -> Value.string(str(0).uppercase(java.util.Locale.ROOT))
            "lower" -> Value.string(str(0).lowercase(java.util.Locale.ROOT))
            "length" -> {
                val v = arg(0)
                require(v.type == Type.LIST || v.type == Type.STRING)
                Value.integer((if (v.type == Type.LIST) v.items.size else v.text.length).toLong())
            }
            "list" ->
                Value(Type.LIST, items = e.args.map { evaluate(it, c, depth + 1) }).also {
                    require(it.items.size <= 1000)
                }
            "append" -> {
                val a = arg(0)
                require(a.type == Type.LIST)
                Value(Type.LIST, items = a.items + arg(1)).also { require(it.items.size <= 1000) }
            }
            "remove" -> {
                val a = arg(0)
                require(a.type == Type.LIST)
                Value(Type.LIST, items = a.items.filter { it != arg(1) })
            }
            "item" -> {
                val a = arg(0)
                require(a.type == Type.LIST)
                a.items[arg(1).number().toInt()]
            }
            "join" -> {
                val a = arg(0)
                require(a.type == Type.LIST)
                Value.string(a.items.joinToString(str(1)) { it.display() })
            }
            "toString" -> Value.string(arg(0).display())
            "toNumber" -> Value.number(str(0).toDouble())
            "now" -> Value(Type.INSTANT, c.now.toString())
            "date" -> Value.string(time.toLocalDate().toString())
            "time" -> Value.string(time.toLocalTime().toString())
            "weekday" -> Value.integer(time.dayOfWeek.value.toLong())
            "month" -> Value.integer(time.monthValue.toLong())
            "weekend" -> Value.bool(time.dayOfWeek.value >= 6)
            "betweenTime" ->
                Value.bool(
                    inTimeRange(
                        time.toLocalTime(),
                        LocalTime.parse(str(0)),
                        LocalTime.parse(str(1)),
                    )
                )
            "beforeTime" -> Value.bool(time.toLocalTime() < LocalTime.parse(str(0)))
            "afterTime" -> Value.bool(time.toLocalTime() >= LocalTime.parse(str(0)))
            "elapsed" -> {
                val a = arg(0)
                require(a.type == Type.INSTANT)
                Value(Type.DURATION, (c.now - a.text.toLong()).toString())
            }
            "addDuration",
            "subtractDuration" -> {
                val a = arg(0)
                val b = arg(1)
                require(a.type == Type.INSTANT && b.type == Type.DURATION)
                Value(
                    Type.INSTANT,
                    Math.addExact(
                            a.text.toLong(),
                            if (e.op == "addDuration") b.text.toLong() else -b.text.toLong(),
                        )
                        .toString(),
                )
            }
            "format" -> {
                val a = arg(0)
                require(a.type == Type.INSTANT)
                Value.string(
                    Instant.ofEpochMilli(a.text.toLong())
                        .atZone(ZoneId.of(c.zone))
                        .format(DateTimeFormatter.ofPattern(str(1)))
                )
            }
            "occupancy" -> {
                val v = c.occupancy[e.name]
                Value.string(if (v == null || c.now - v.second > 3600000) "UNKNOWN" else v.first)
            }
            else -> error("Unsupported expression ${e.op}")
        }
    }

    fun numeric(t: Type) = t == Type.INTEGER || t == Type.DECIMAL

    private fun compareNumbers(a: Value, b: Value): Int {
        require(numeric(a.type) && numeric(b.type))
        return BigDecimal(a.text).compareTo(BigDecimal(b.text))
    }

    fun inTimeRange(now: LocalTime, start: LocalTime, end: LocalTime): Boolean =
        if (start <= end) now >= start && now < end else now >= start || now < end
}

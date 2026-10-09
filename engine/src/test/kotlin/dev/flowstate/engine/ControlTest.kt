package dev.flowstate.engine

import java.time.Instant
import kotlin.test.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Test

class ControlTest {
    @Test
    fun suspendedCallerDeclarationsRemainProtectedInsideChildWorkflow() {
        val caller =
            Definition(
                id = "caller",
                entry = "call",
                variables = listOf(Variable("owned", Type.INTEGER, Scope.AUTOMATION)),
                nodes = listOf(Node("call", "call", fields = mapOf("WORKFLOW" to "child"))),
            )
        val child =
            Definition(
                id = "child",
                entry = "wait",
                nodes = listOf(Node("wait", "wait", fields = mapOf("SECONDS" to "10"))),
            )
        val runtime = Runtime { if (it == "child") child else null }
        val waiting = runtime.tick(runtime.start("caller", caller, 0), 0, "UTC").execution
        assertEquals("child", waiting.definition.id)
        assertTrue(waiting.capturedDefinitions().any { it.id == "caller" })
        val edited =
            caller.copy(
                version = 2,
                variables = listOf(Variable("owned", Type.STRING, Scope.AUTOMATION)),
            )
        assertTrue(
            Compiler.validateSharedVariables(waiting.capturedDefinitions() + edited).isNotEmpty()
        )
    }

    @Test
    fun textBoundsAndResponseVariableAreValidatedBeforeAndAfterWaiting() {
        val q =
            Node(
                "q",
                "ask",
                fields = mapOf("KIND" to "text", "NAME" to "answer", "MIN" to "2", "MAX" to "4"),
            )
        val d =
            Definition(
                id = "a",
                entry = "q",
                variables = listOf(Variable("answer", Type.STRING, Scope.LOCAL)),
                nodes = listOf(q),
            )
        assertTrue(Compiler.validate(d).isEmpty())
        assertTrue(Compiler.validate(d.copy(variables = emptyList())).isNotEmpty())
        assertTrue(
            Compiler.validate(d.copy(nodes = listOf(q.copy(fields = q.fields + ("MAX" to "1")))))
                .isNotEmpty()
        )
        val runtime = Runtime()
        val e = runtime.tick(runtime.start("text", d, 0), 0, "UTC").execution
        val token = requireNotNull(e.interaction).token
        assertFails { runtime.respond(e, token, "12345", 1) }
        assertFails { runtime.respond(e, token, "1", 1) }
        assertEquals(Value.string("123"), runtime.respond(e, token, "123", 1).locals["answer"])
    }

    @Test
    fun orphanPersistentValuesAndSuspendedVersionTypesAreProtected() {
        val d =
            Definition(
                id = "a",
                entry = null,
                nodes = emptyList(),
                variables =
                    listOf(
                        Variable("shared", Type.INTEGER, Scope.GLOBAL),
                        Variable("owned", Type.INTEGER, Scope.AUTOMATION),
                    ),
            )
        assertEquals(
            2,
            Compiler.validatePersistentValues(
                    listOf(d),
                    mapOf("global:shared" to Value.string("old"), "a:owned" to Value.bool(true)),
                )
                .size,
        )
        assertTrue(
            Compiler.validatePersistentValues(listOf(d), mapOf("global:shared" to Value.NULL))
                .isEmpty()
        )
        val edited =
            d.copy(
                version = 2,
                variables =
                    listOf(
                        Variable("shared", Type.INTEGER, Scope.GLOBAL),
                        Variable("owned", Type.STRING, Scope.AUTOMATION),
                    ),
            )
        assertEquals(
            "AUTOMATION_TYPE",
            Compiler.validateSharedVariables(listOf(d, edited)).single().code,
        )
    }

    @Test
    fun locationOvernightWindowUsesItsStartingDayAndDate() {
        val t =
            Trigger(
                kind = "location",
                locationId = "home",
                days = listOf(5),
                eligibleFrom = "22:00",
                eligibleTo = "02:00",
                startDate = "2026-10-09",
                endDate = "2026-10-09",
                zone = "UTC",
            )
        Scheduling.validate(t)
        val zone = java.time.ZoneId.of("Asia/Kolkata")
        fun eligible(time: String) =
            Scheduling.locationEligible(t, Instant.parse(time).toEpochMilli(), zone)
        assertTrue(eligible("2026-10-09T23:00:00Z"))
        assertTrue(eligible("2026-10-10T01:59:59Z"))
        assertFalse(eligible("2026-10-10T02:00:00Z"))
        assertFalse(eligible("2026-10-10T23:00:00Z"))
        assertFalse(eligible("2026-10-09T12:00:00Z"))
        assertFails { Scheduling.validate(t.copy(eligibleTo = "")) }
        assertTrue(
            Scheduling.locationEligible(
                t.copy(eligibleFrom = "", eligibleTo = ""),
                Instant.parse("2026-10-09T12:00:00Z").toEpochMilli(),
                zone,
            )
        )
    }

    @Test
    fun timeArithmeticRejectsOverflowWithoutNegatingLongMinValue() {
        val context = EvaluationContext(0, "UTC", emptyMap(), emptyMap(), "a")
        val expression =
            Expr(
                "subtractDuration",
                args =
                    listOf(
                        Expr("literal", Value(Type.INSTANT, "0")),
                        Expr("literal", Value(Type.DURATION, Long.MIN_VALUE.toString())),
                    ),
            )
        assertFailsWith<ArithmeticException> { Expressions.evaluate(expression, context) }
    }

    @Test
    fun executionDeadlineCapsWaitAndSurvivesSerialization() {
        val d =
            Definition(
                id = "a",
                entry = "timeout",
                nodes =
                    listOf(
                        Node(
                            "timeout",
                            "setTimeout",
                            fields = mapOf("SECONDS" to "10"),
                            next = "wait",
                        ),
                        Node("wait", "wait", fields = mapOf("SECONDS" to "100"), next = "late"),
                        Node("late", "message"),
                    ),
            )
        assertTrue(Compiler.validate(d).isEmpty())
        val runtime = Runtime()
        val waiting = runtime.tick(runtime.start("deadline", d, 0), 0, "UTC").execution
        assertEquals(10000L, waiting.wakeAt)
        val restored = codec.decodeFromString<Execution>(codec.encodeToString(waiting))
        val expired = runtime.tick(restored, 10000, "UTC")
        assertEquals(State.EXPIRED, expired.execution.state)
        assertEquals(listOf("cancel"), expired.effects.map { it.kind })
    }

    @Test
    fun executionDeadlineRejectsOtherwiseValidUserResponse() {
        val d =
            Definition(
                id = "a",
                entry = "timeout",
                nodes =
                    listOf(
                        Node(
                            "timeout",
                            "setTimeout",
                            fields = mapOf("SECONDS" to "10"),
                            next = "ask",
                        ),
                        Node("ask", "ask", fields = mapOf("TIMEOUT" to "0")),
                    ),
            )
        val runtime = Runtime()
        val e = runtime.tick(runtime.start("question", d, 0), 0, "UTC").execution
        val i = requireNotNull(e.interaction)
        assertEquals(e, runtime.respond(e, i.token, i.options.first(), 10000))
        assertEquals(State.EXPIRED, runtime.tick(e, 10000, "UTC").execution.state)
    }

    @Test
    fun waitClockResolvesDstGapAndChoosesNextDayAfterTime() {
        val d =
            Definition(
                id = "a",
                entry = "clock",
                nodes =
                    listOf(
                        Node(
                            "clock",
                            "waitClock",
                            fields = mapOf("TIME" to "02:30", "ZONE" to "America/New_York"),
                        )
                    ),
            )
        val runtime = Runtime()
        val now = Instant.parse("2026-03-08T06:00:00Z").toEpochMilli()
        assertEquals(
            Instant.parse("2026-03-08T07:30:00Z").toEpochMilli(),
            runtime.tick(runtime.start("clock", d, now), now, "UTC").execution.wakeAt,
        )
        val after = Instant.parse("2026-03-08T08:00:00Z").toEpochMilli()
        assertEquals(
            Instant.parse("2026-03-09T06:30:00Z").toEpochMilli(),
            runtime.tick(runtime.start("clock", d, after), after, "UTC").execution.wakeAt,
        )
    }

    @Test
    fun breakpointsPauseOnlySimulation() {
        val d =
            Definition(
                id = "a",
                entry = "breakpoint",
                nodes =
                    listOf(
                        Node("breakpoint", "breakpoint", next = "message"),
                        Node("message", "message"),
                    ),
            )
        val runtime = Runtime()
        val simulation =
            runtime.tick(runtime.start("simulation", d, 0), 0, "UTC", simulationBreakpoints = true)
        assertEquals(State.PAUSED, simulation.execution.state)
        assertTrue(simulation.effects.isEmpty())
        assertEquals("message", simulation.execution.cursor)
        assertEquals(
            State.COMPLETED,
            runtime.tick(runtime.start("production", d, 0), 0, "UTC").execution.state,
        )
    }

    @Test
    fun loopControlAndGlobalTypesAreValidated() {
        val outside = Definition(id = "a", entry = "break", nodes = listOf(Node("break", "break")))
        assertTrue(Compiler.validate(outside).any { it.message.contains("inside a loop") })
        val inside =
            outside.copy(
                entry = "loop",
                nodes =
                    outside.nodes +
                        Node(
                            "loop",
                            "repeat",
                            fields = mapOf("LIMIT" to "2"),
                            branches = mapOf("DO" to "break"),
                        ),
            )
        assertTrue(Compiler.validate(inside).isEmpty())
        val a = inside.copy(variables = listOf(Variable("shared", Type.STRING, Scope.GLOBAL)))
        val b = a.copy(id = "b", variables = listOf(Variable("shared", Type.INTEGER, Scope.GLOBAL)))
        assertEquals("GLOBAL_TYPE", Compiler.validateSharedVariables(listOf(a, b)).single().code)
        assertTrue(Compiler.validateSharedVariables(listOf(a, a.copy(id = "b"))).isEmpty())
    }

    @Test
    fun simulatedVariableWritesDoNotMutateTheirInput() {
        val production = mapOf("global:count" to Value.integer(8))
        val d =
            Definition(
                id = "a",
                entry = "set",
                variables = listOf(Variable("count", Type.INTEGER, Scope.GLOBAL)),
                nodes =
                    listOf(
                        Node(
                            "set",
                            "set",
                            fields = mapOf("NAME" to "count", "SCOPE" to "GLOBAL"),
                            expressions = mapOf("VALUE" to Expr("literal", Value.integer(9))),
                        )
                    ),
            )
        val runtime = Runtime()
        val tick = runtime.tick(runtime.start("simulation", d, 0), 0, "UTC", production)
        assertEquals(Value.integer(8), production["global:count"])
        assertEquals(Value.integer(9), tick.persistent["global:count"])
    }
}

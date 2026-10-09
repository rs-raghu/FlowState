package dev.flowstate.engine

import kotlin.test.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Test

class ParallelTest {
    private val rt = Runtime()

    private fun definition(
        a: List<Node>,
        b: List<Node>,
        variables: List<Variable> = emptyList(),
        next: Node? = null,
    ) =
        Definition(
            id = "parallel",
            entry = "fork",
            variables = variables,
            nodes =
                listOf(
                    Node(
                        "fork",
                        "parallel",
                        branches = mapOf("A" to a.firstOrNull()?.id, "B" to b.firstOrNull()?.id),
                        next = next?.id,
                    )
                ) + a + b + listOfNotNull(next),
        )

    private fun drive(
        original: Execution,
        now: Long = 0,
        values: Map<String, Value> = emptyMap(),
        breakpoints: Boolean = false,
    ): Tick {
        var e = original
        var persistent = values
        val effects = mutableListOf<Effect>()
        repeat(100) {
            val result = rt.tick(e, now, "UTC", persistent, simulationBreakpoints = breakpoints)
            e = result.execution
            persistent = result.persistent
            effects += result.effects
            if (e.state !in setOf(State.CREATED, State.QUEUED, State.RUNNING))
                return Tick(e, effects, persistent)
        }
        error("Run did not suspend or terminate")
    }

    private fun question(id: String) =
        Node(id, "ask", fields = mapOf("KIND" to "yesno", "TITLE" to id, "TIMEOUT" to "0"))

    private fun integer(value: Long) = Expr("literal", Value.integer(value))

    private fun set(
        id: String,
        name: String,
        value: Long,
        next: String? = null,
        scope: Scope = Scope.LOCAL,
    ) =
        Node(
            id,
            "set",
            fields = mapOf("NAME" to name, "SCOPE" to scope.name),
            expressions = mapOf("VALUE" to integer(value)),
            next = next,
        )

    @Test
    fun sleepingBranchDoesNotDelaySiblingAndJoinWaitsForBoth() {
        val d =
            definition(
                listOf(
                    Node("wait", "wait", fields = mapOf("SECONDS" to "10"), next = "a"),
                    Node("a", "message", fields = mapOf("TITLE" to "A")),
                ),
                listOf(Node("b", "message", fields = mapOf("TITLE" to "B"))),
                next = Node("join", "message", fields = mapOf("TITLE" to "JOIN")),
            )
        val suspended = drive(rt.start("run", d, 0))
        assertEquals(listOf("B"), suspended.effects.map { it.title })
        assertEquals(State.WAITING_FOR_BRANCHES, suspended.execution.state)
        assertEquals(10_000, suspended.execution.wakeAt)
        val restored = codec.decodeFromString<Execution>(codec.encodeToString(suspended.execution))
        val finished = drive(restored, 10_000)
        assertEquals(listOf("A", "JOIN"), finished.effects.map { it.title })
        assertEquals(State.COMPLETED, finished.execution.state)
    }

    @Test
    fun concurrentQuestionsResumeIndependentlyInEitherOrder() {
        val d = definition(listOf(question("a")), listOf(question("b")))
        val waiting = drive(rt.start("run", d, 0)).execution
        assertEquals(listOf("a", "b"), waiting.pendingInteractions().map { it.title })
        val (a, b) = waiting.pendingInteractions()
        val restored = codec.decodeFromString<Execution>(codec.encodeToString(waiting))
        val first = drive(rt.respond(restored, b.token, "Yes", 1), 1).execution
        assertEquals(listOf(a.token), first.pendingInteractions().map { it.token })
        assertEquals(first, rt.respond(first, b.token, "No", 2))
        val done = drive(rt.respond(first, a.token, "No", 2), 2).execution
        assertEquals(State.COMPLETED, done.state, done.toString())
        assertTrue(done.pendingInteractions().isEmpty())
    }

    @Test
    fun differentLocalWritesMergeAtJoin() {
        val variables =
            listOf(
                Variable("x", Type.INTEGER, Scope.LOCAL, Value.integer(0)),
                Variable("y", Type.INTEGER, Scope.LOCAL, Value.integer(0)),
            )
        val d = definition(listOf(set("a", "x", 1)), listOf(set("b", "y", 2)), variables)
        val done = drive(rt.start("run", d, 0)).execution
        assertEquals(State.COMPLETED, done.state)
        assertEquals(Value.integer(1), done.locals["x"])
        assertEquals(Value.integer(2), done.locals["y"])
    }

    @Test
    fun conflictingLocalWritesFailAndTryCanHandleJoinFailure() {
        val d =
            definition(
                listOf(set("a", "x", 1)),
                listOf(set("b", "x", 2)),
                listOf(Variable("x", Type.INTEGER, Scope.LOCAL, Value.integer(0))),
            )
        val failed = drive(rt.start("run", d, 0)).execution
        assertEquals(State.FAILED, failed.state)
        assertTrue(failed.error!!.contains("conflict"))
        val guarded =
            d.copy(
                entry = "guard",
                nodes =
                    d.nodes +
                        Node(
                            "guard",
                            "try",
                            branches = mapOf("DO" to "fork", "ERROR" to "handled"),
                        ) +
                        Node("handled", "message", fields = mapOf("TITLE" to "Handled")),
            )
        val handled = drive(rt.start("guarded", guarded, 0))
        assertEquals(State.COMPLETED, handled.execution.state)
        assertEquals(listOf("Handled"), handled.effects.map { it.title })
        assertEquals(Value.integer(0), handled.execution.locals["x"])
    }

    @Test
    fun persistentWritesUseDeterministicRoundRobinOrder() {
        val d =
            definition(
                listOf(
                    set("a1", "x", 1, "a2", Scope.GLOBAL),
                    set("a2", "x", 3, scope = Scope.GLOBAL),
                ),
                listOf(
                    set("b1", "x", 2, "b2", Scope.GLOBAL),
                    set("b2", "x", 4, scope = Scope.GLOBAL),
                ),
                listOf(Variable("x", Type.INTEGER, Scope.GLOBAL, Value.integer(0))),
            )
        val done = drive(rt.start("run", d, 0))
        assertEquals(Value.integer(4), done.persistent["global:x"])
        assertEquals(
            listOf("a1", "b1", "a2", "b2"),
            done.execution.trace.filter { it.detail.contains("Execute set") }.map { it.node },
        )
    }

    @Test
    fun branchFailureCancelsSiblingInteractionsAndParentCancelRejectsResponses() {
        val d =
            definition(
                listOf(question("a")),
                listOf(
                    Node(
                        "bad",
                        "assert",
                        expressions = mapOf("TEST" to Expr("literal", Value.bool(false))),
                    )
                ),
            )
        val result = drive(rt.start("run", d, 0))
        assertEquals(State.FAILED, result.execution.state)
        assertTrue(result.effects.any { it.kind == "cancel" && it.cancelToken.isNotBlank() })
        assertTrue(result.execution.pendingInteractions().isEmpty())
        val waiting =
            drive(rt.start("cancel", definition(listOf(question("a")), listOf(question("b"))), 0))
                .execution
        val cancelled = waiting.copy(state = State.CANCELLED, wakeAt = null)
        assertEquals(
            cancelled,
            rt.respond(cancelled, waiting.pendingInteractions().first().token, "Yes", 1),
        )
        assertTrue(rt.tick(cancelled, 1, "UTC").effects.isEmpty())
    }

    @Test
    fun branchDeadlinesAndSnoozeAreIndependent() {
        val d =
            definition(
                listOf(
                    Node("deadline", "setTimeout", fields = mapOf("SECONDS" to "1"), next = "a"),
                    question("a"),
                ),
                listOf(question("b")),
            )
        val waiting = drive(rt.start("run", d, 0)).execution
        val a = waiting.pendingInteractions().first { it.title == "a" }
        val b = waiting.pendingInteractions().first { it.title == "b" }
        assertFalse(waiting.acceptsInteraction(a.token, 1000))
        assertEquals(waiting, rt.respond(waiting, a.token, "Yes", 1000))
        assertTrue(waiting.acceptsInteraction(b.token, 1000))
        val snoozed = waiting.updateInteraction(b.token) { it.copy(snoozedUntil = 5000) }
        assertEquals(1000, snoozed.wakeAt)
        assertEquals(null, snoozed.pendingInteractions().first { it.token == a.token }.snoozedUntil)
        val expired = drive(snoozed, 1000)
        assertEquals(State.FAILED, expired.execution.state)
        assertTrue(expired.effects.any { it.cancelToken == b.token })
    }

    @Test
    fun parallelBreakpointsResumeAndSharedStepBudgetRemainsBounded() {
        val d =
            definition(
                listOf(Node("pause", "breakpoint", next = "a"), Node("a", "log")),
                listOf(Node("b", "log")),
            )
        val paused = drive(rt.start("run", d, 0), breakpoints = true).execution
        assertEquals(State.PAUSED, paused.state)
        assertEquals(
            State.COMPLETED,
            drive(paused.resumePausedBranches(), breakpoints = true).execution.state,
        )
        val bounded = drive(rt.start("bounded", d.copy(maxSteps = 2), 0)).execution
        assertEquals(State.FAILED, bounded.state)
        assertTrue(bounded.error!!.contains("budget"))
    }

    @Test
    fun nestedBranchesAndCallerDeclarationsSurviveSnapshot() {
        val d =
            definition(
                listOf(
                    Node("nested", "parallel", branches = mapOf("A" to "a", "B" to "b")),
                    question("a"),
                    question("b"),
                ),
                listOf(question("c")),
            )
        val waiting = drive(rt.start("run", d, 0)).execution
        assertEquals(setOf("a", "b", "c"), waiting.pendingInteractions().map { it.title }.toSet())
        assertTrue(waiting.capturedDefinitions().all { it.id == d.id })
        var restored = codec.decodeFromString<Execution>(codec.encodeToString(waiting))
        waiting.pendingInteractions().reversed().forEach { i ->
            restored = drive(rt.respond(restored, i.token, "Yes", 1), 1).execution
        }
        assertEquals(State.COMPLETED, restored.state)
    }

    @Test
    fun loopControlCannotCrossParallelBranchBoundary() {
        val d =
            definition(listOf(Node("break", "break")), emptyList())
                .copy(
                    entry = "repeat",
                    nodes =
                        definition(listOf(Node("break", "break")), emptyList()).nodes +
                            Node(
                                "repeat",
                                "repeat",
                                fields = mapOf("COUNT" to "2", "LIMIT" to "2"),
                                branches = mapOf("DO" to "fork"),
                            ),
                )
        assertTrue(Compiler.validate(d).any { it.block == "break" })
    }

    @Test
    fun emptyBranchesJoinWithoutConsumingExtraNodeBudget() {
        val d = definition(emptyList(), emptyList()).copy(maxSteps = 1)
        assertEquals(State.COMPLETED, drive(rt.start("empty", d, 0)).execution.state)
    }

    @Test
    fun callerTryRestoresCallerWhenChildParallelBranchFails() {
        val child =
            definition(
                    listOf(
                        Node(
                            "bad",
                            "assert",
                            expressions = mapOf("TEST" to Expr("literal", Value.bool(false))),
                        )
                    ),
                    listOf(question("b")),
                )
                .copy(id = "child")
        val caller =
            Definition(
                id = "caller",
                entry = "try",
                variables = listOf(Variable("saved", Type.INTEGER, Scope.LOCAL, Value.integer(7))),
                nodes =
                    listOf(
                        Node("try", "try", branches = mapOf("DO" to "call", "ERROR" to "handled")),
                        Node("call", "call", fields = mapOf("WORKFLOW" to "child")),
                        Node("handled", "message", fields = mapOf("TITLE" to "Caller handler")),
                    ),
            )
        val runtime = Runtime { if (it == "child") child else null }
        var e = runtime.start("caller", caller, 0)
        val effects = mutableListOf<Effect>()
        repeat(20) {
            if (e.state !in Runtime.terminal) {
                val result = runtime.tick(e, 0, "UTC")
                e = result.execution
                effects += result.effects
            }
        }
        assertEquals(State.COMPLETED, e.state)
        assertEquals("caller", e.definition.id)
        assertEquals(Value.integer(7), e.locals["saved"])
        assertEquals(
            listOf("Caller handler"),
            effects.filter { it.kind == "message" }.map { it.title },
        )
    }
}

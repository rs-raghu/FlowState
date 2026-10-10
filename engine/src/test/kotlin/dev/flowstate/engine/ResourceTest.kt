package dev.flowstate.engine

import kotlin.test.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Test

class ResourceTest {
    @Test
    fun editorBudgetsCompileAndRejectUnsafeValues() {
        val source =
            """{"blocks":{"blocks":[{"type":"fs_trigger","id":"trigger","fields":{"BURST":2,"MAXSTEPS":10,"MAXITERATIONS":5,"RATE":3},"next":{"block":{"type":"fs_stop","id":"stop"}}}]}}"""
        val d = Compiler.compile(source, "a", 1)
        assertEquals(2, d.maxBurst)
        assertEquals(10, d.maxSteps)
        assertEquals(5, d.maxIterations)
        assertEquals(3, d.notificationRate)
        val failure =
            assertFailsWith<ValidationException> {
                Compiler.compile(source.replace("\"RATE\":3", "\"RATE\":121"), "a", 1)
            }
        assertTrue(failure.issues.all { it.block == "trigger" })
    }

    @Test
    fun smallBurstsPreserveContinuation() {
        val d =
            Definition(
                id = "a",
                entry = "one",
                maxBurst = 1,
                nodes = listOf(Node("one", "message", next = "two"), Node("two", "message")),
            )
        val rt = Runtime()
        val first = rt.tick(rt.start("run", d, 0), 0, "UTC")
        assertEquals(State.RUNNING, first.execution.state)
        assertEquals("two", first.execution.cursor)
        assertEquals(1, first.effects.size)
        val recovered = codec.decodeFromString<Execution>(codec.encodeToString(first.execution))
        val last = rt.tick(recovered, 0, "UTC")
        assertEquals(1, last.effects.size)
        assertEquals(State.COMPLETED, rt.tick(last.execution, 0, "UTC").execution.state)
    }

    @Test
    fun notificationWindowIsDurableAndResetsAfterOneMinute() {
        val d =
            Definition(
                id = "a",
                entry = "one",
                maxBurst = 1,
                notificationRate = 1,
                nodes = listOf(Node("one", "message", next = "two"), Node("two", "message")),
            )
        val rt = Runtime()
        val first = rt.tick(rt.start("run", d, 0), 0, "UTC")
        val recovered = codec.decodeFromString<Execution>(codec.encodeToString(first.execution))
        val blocked = rt.tick(recovered, 59999, "UTC")
        assertEquals(State.FAILED, blocked.execution.state)
        assertTrue(blocked.effects.isEmpty())
        assertTrue(blocked.execution.error!!.contains("frequency"))
        assertEquals(1, rt.tick(recovered, 60000, "UTC").effects.size)
    }

    @Test
    fun parallelBranchesShareNotificationBudget() {
        val d =
            Definition(
                id = "a",
                entry = "fork",
                notificationRate = 1,
                nodes =
                    listOf(
                        Node("fork", "parallel", branches = mapOf("A" to "one", "B" to "two")),
                        Node("one", "message"),
                        Node("two", "message"),
                    ),
            )
        val rt = Runtime()
        var tick = rt.tick(rt.start("run", d, 0), 0, "UTC")
        val effects = tick.effects.toMutableList()
        repeat(10) {
            if (tick.execution.state !in Runtime.terminal) {
                tick = rt.tick(tick.execution, 0, "UTC")
                effects += tick.effects
            }
        }
        assertEquals(State.FAILED, tick.execution.state)
        assertEquals(1, effects.count { it.kind == "message" })
    }

    @Test
    fun storageOverflowFailsWithoutPublishingEffectsOrNewValues() {
        val d = Definition(id = "a", entry = "stop", nodes = listOf(Node("stop", "stop")))
        val e = Runtime().start("run", d, 0)
        val large = Value(Type.LIST, items = List(100) { Value.string("a".repeat(16000)) })
        val previous = mapOf("global:safe" to Value.integer(1))
        val result =
            ResourceLimits.bound(
                Tick(
                    e,
                    listOf(Effect("m", "message", "Must not show")),
                    previous + ("global:large" to large),
                ),
                previous,
            )
        assertEquals(State.FAILED, result.execution.state)
        assertEquals(previous, result.persistent)
        assertEquals(listOf("cancel"), result.effects.map { it.kind })
        val oversized = e.copy(locals = mapOf("large" to large, "again" to large))
        val snapshot = ResourceLimits.bound(Tick(oversized, emptyList(), previous), previous)
        assertTrue(snapshot.execution.error!!.contains("snapshot"))
        assertTrue(codec.encodeToString(snapshot.execution).length < 2000)
    }

    @Test
    fun capturedLibraryCannotGrowWithoutBound() {
        val d =
            Definition(
                id = "a",
                entry = "message",
                nodes =
                    List(30) {
                        Node("$it", "message", fields = mapOf("BODY" to "a".repeat(16000)))
                    },
            )
        assertTrue(
            assertFailsWith<IllegalArgumentException> { Runtime().start("run", d, 0) }
                .message!!
                .contains("256 KB")
        )
    }

    @Test
    fun inputLimitsCountUtf8AndPermitLargeBackupEnvelope() {
        val large = "\"" + "a".repeat(2_100_000) + "\""
        assertFailsWith<IllegalArgumentException> { SafeInput.json(large) }
        assertEquals(large, SafeInput.json(large, 16_000_000))
        assertFailsWith<IllegalArgumentException> {
            SafeInput.json("\"" + "₹".repeat(700000) + "\"")
        }
        assertFailsWith<IllegalArgumentException> {
            SafeInput.json("[".repeat(129) + "]".repeat(129), 16_000_000)
        }
    }

    @Test
    fun nestedLocationReferencesAreProtectedAndValidated() {
        val expression =
            Expr(
                "concat",
                args =
                    listOf(
                        Expr("literal", Value.string("Place: ")),
                        Expr("occupancy", name = "home"),
                    ),
            )
        val d =
            Definition(
                id = "a",
                entry = "set",
                variables = listOf(Variable("state", Type.STRING, Scope.LOCAL)),
                nodes =
                    listOf(
                        Node(
                            "set",
                            "set",
                            fields = mapOf("NAME" to "state"),
                            expressions = mapOf("VALUE" to expression),
                        )
                    ),
            )
        assertEquals(setOf("home"), Dependencies.locations(d))
        assertTrue(
            Compiler.validate(d).any { it.block == "set" && it.message.contains("location") }
        )
        assertTrue(Compiler.validate(d, setOf("home")).isEmpty())
    }

    @Test
    fun locationSimulationHistoryAndCoordinatesReplayTogether() {
        val d = Definition(id = "a", entry = "stop", nodes = listOf(Node("stop", "stop")))
        val s = Simulation(d, emptyList(), 0, "UTC")
        s.location("home", 21.0, 79.0, 150.0)
        s.event(1000, "enter", "home")
        val inside = s.history.lastIndex
        s.event(2000, "dwell", "home")
        assertEquals(1000L, s.locations.getValue("home").entered)
        assertEquals(2000L, s.locations.getValue("home").dwell)
        s.event(3000, "exit", "home")
        s.location("home", 10.0, 20.0, 300.0)
        s.replay(inside)
        assertEquals(21.0, s.locations.getValue("home").latitude)
        assertEquals("INSIDE", s.occupancy.getValue("home").first)
        assertNull(s.locations.getValue("home").exited)
        assertNull(s.locations.getValue("home").dwell)
    }
}

package dev.flowstate.engine

import kotlin.test.*
import org.junit.Test

class ResourceTest {
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

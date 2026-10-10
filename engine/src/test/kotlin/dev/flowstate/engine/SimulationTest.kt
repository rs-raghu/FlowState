package dev.flowstate.engine

import kotlin.test.*
import org.junit.Test

class SimulationTest {
    @Test
    fun replayRestoresValuesEffectsPermissionsAndTokens() {
        val d =
            Definition(
                id = "a",
                entry = "ask",
                nodes = listOf(Node("ask", "ask", fields = mapOf("KIND" to "yesno"))),
            )
        val s = Simulation(d, emptyList(), 0, "UTC")
        s.run()
        val first = s.execution.interaction!!.token
        s.replay(0)
        s.run()
        assertEquals(first, s.execution.interaction!!.token)
        s.event(1, "notification", "false")
        val position = s.history.lastIndex
        s.event(2, "notification", "true")
        s.replay(position)
        assertEquals(false, s.permissions["notifications"])
        assertEquals(1, s.effects.size)
    }

    @Test
    fun breakpointsAndStepOverCallsAreBounded() {
        val child =
            Definition(id = "child", entry = "message", nodes = listOf(Node("message", "message")))
        val d =
            Definition(
                id = "a",
                entry = "call",
                nodes =
                    listOf(
                        Node("call", "call", fields = mapOf("WORKFLOW" to "child"), next = "after"),
                        Node("after", "message"),
                    ),
            )
        val s = Simulation(d, listOf(child), 0, "UTC")
        s.stepOver()
        assertEquals("after", s.execution.cursor)
        assertEquals("a", s.execution.definition.id)
        s.breakpoints = setOf("after")
        assertFalse(s.step(true))
        assertEquals(State.PAUSED, s.execution.state)
        s.run()
        assertEquals(State.COMPLETED, s.execution.state)
        assertEquals(2, s.effects.size)
    }

    @Test
    fun eventsRejectBackwardsClockAndExplainMissingPermission() {
        val d =
            Definition(
                id = "a",
                entry = "message",
                nodes = listOf(Node("message", "message")),
                trigger = Trigger(kind = "location", locationId = "home", transition = "enter"),
            )
        val s = Simulation(d, emptyList(), 0, "UTC")
        s.permissions = s.permissions + ("background" to false)
        s.event(10, "enter", "home")
        assertTrue(s.explanation.contains("block"))
        assertTrue(s.effects.isEmpty())
        assertFailsWith<IllegalArgumentException> { s.event(9, "exit", "home") }
        s.permissions = s.permissions + ("background" to true)
        s.event(11, "enter", "home")
        assertEquals(State.COMPLETED, s.execution.state)
    }
}

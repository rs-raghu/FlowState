package dev.flowstate.engine

import kotlin.test.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Test

class ReminderTest {
    @Test
    fun checklistSnapshotAndCompletionAreIndependent() {
        val template =
            ChecklistTemplate(
                "t",
                "Essentials",
                listOf(
                    ChecklistItem("Keys", note = "Front door", group = "Home"),
                    ChecklistItem("Water", required = false),
                ),
            )
        val d =
            Definition(
                id = "a",
                entry = "check",
                nodes = listOf(Node("check", "checklist", fields = mapOf("TEMPLATE" to "t"))),
            )
        val rt = Runtime()
        val a = rt.tick(rt.start("a", d, 0, mapOf("t" to template)), 0, "UTC").execution
        val b =
            rt.tick(
                    rt.start(
                        "b",
                        d,
                        0,
                        mapOf("t" to template.copy(items = listOf(ChecklistItem("Wallet")))),
                    ),
                    0,
                    "UTC",
                )
                .execution
        assertEquals(listOf("Keys", "?Water"), a.interaction!!.options)
        assertEquals("Front door", a.interaction.notes[0])
        assertEquals(listOf("Wallet"), b.interaction!!.options)
        val loaded =
            codec.decodeFromString<Execution>(
                codec.encodeToString(
                    a.updateInteraction(a.interaction.token) { it.copy(completed = listOf(0)) }
                )
            )
        assertEquals(listOf(0), loaded.interaction!!.completed)
        assertTrue(b.interaction.completed.isEmpty())
    }

    @Test
    fun followupIsBoundedAndSurvivesRestart() {
        val d =
            Definition(
                id = "a",
                entry = "ask",
                nodes =
                    listOf(
                        Node(
                            "ask",
                            "ask",
                            fields =
                                mapOf(
                                    "KIND" to "yesno",
                                    "TIMEOUT" to "60",
                                    "FOLLOWUP" to "10",
                                    "SNOOZE" to "2,30",
                                    "MAXSNOOZE" to "1",
                                ),
                        )
                    ),
            )
        val rt = Runtime()
        var e = rt.tick(rt.start("e", d, 0), 0, "UTC").execution
        assertEquals(10000L, e.wakeAt)
        val t = rt.tick(codec.decodeFromString(codec.encodeToString(e)), 10000, "UTC")
        e = t.execution
        assertEquals(1, e.interaction!!.reminders)
        assertEquals(listOf(2L, 30L), e.interaction.snoozeMinutes)
        assertEquals("high", t.effects.single().notification.channel)
        assertTrue(rt.tick(e, 11000, "UTC").effects.isEmpty())
        assertEquals(State.EXPIRED, rt.tick(e, 60000, "UTC").execution.state)
    }

    @Test
    fun messagesHaveSeparateOwnedTargetsAndExplicitUpdates() {
        val d =
            Definition(
                id = "a",
                entry = "first",
                nodes =
                    listOf(
                        Node("first", "message", next = "second"),
                        Node("second", "notifyUpdate", fields = mapOf("TARGET" to "first")),
                    ),
            )
        val effects = Runtime().tick(Runtime().start("e", d, 0), 0, "UTC").effects
        assertEquals(listOf("first", "first"), effects.map { it.notification.target })
        val invalid =
            d.copy(
                nodes =
                    listOf(
                        Node("first", "ask", fields = mapOf("KIND" to "yesno", "MAXSNOOZE" to "99"))
                    )
            )
        assertTrue(Compiler.validate(invalid).any { it.message.contains("snoozes") })
    }
}

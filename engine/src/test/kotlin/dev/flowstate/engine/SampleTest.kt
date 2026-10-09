package dev.flowstate.engine

import java.io.File
import java.time.Instant
import kotlin.test.*
import kotlinx.serialization.json.*
import org.junit.Test

class SampleTest {
    private fun definitions(): List<Definition> {
        val file =
            listOf(
                    File("../sample-workflows/examples.json"),
                    File("sample-workflows/examples.json"),
                )
                .first { it.exists() }
        val backup = codec.parseToJsonElement(file.readText()).jsonObject
        val automations = backup["automations"]!!.jsonArray.map { it.jsonObject }
        val locations =
            backup["locations"]!!
                .jsonArray
                .map { it.jsonObject["id"]!!.jsonPrimitive.content }
                .toSet()
        val workflows = automations.map { it["id"]!!.jsonPrimitive.content }.toSet()
        assertEquals(10, automations.size)
        return automations.map {
            Compiler.compile(
                it["workspace"]!!.jsonPrimitive.content,
                it["id"]!!.jsonPrimitive.content,
                1,
                locations,
                workflows,
            )
        }
    }

    @Test
    fun allExamplesCompileAndCompleteTheirFirstResponsePath() {
        val definitions = definitions()
        val runtime = Runtime { id -> definitions.find { it.id == id } }
        var values = emptyMap<String, Value>()
        val monday = Instant.parse("2026-10-12T09:00:00Z").toEpochMilli()
        definitions.forEach { d ->
            var clock = monday
            var e = runtime.start("sample-${d.id}", d, clock)
            repeat(100) {
                if (e.state !in Runtime.terminal) {
                    val tick = runtime.tick(e, clock, "UTC", values)
                    e = tick.execution
                    values = tick.persistent
                    e.interaction?.let { i ->
                        val response =
                            if (i.kind == "checklist") i.required.joinToString(",")
                            else i.options.first()
                        e = runtime.respond(e, i.token, response, clock)
                    }
                    if (e.state == State.WAITING_FOR_TIME) clock = requireNotNull(e.wakeAt)
                }
            }
            assertEquals(State.COMPLETED, e.state, "${d.id}: ${e.error}")
        }
    }

    @Test
    fun exampleTimeoutCannotBeResurrected() {
        val d = definitions().single { it.id == "02365582-3707-4047-8d36-d159275d98ac" }
        val runtime = Runtime()
        val e = runtime.tick(runtime.start("timeout", d, 0), 0, "UTC").execution
        val i = requireNotNull(e.interaction)
        val late = runtime.respond(e, i.token, i.options.first(), requireNotNull(i.deadline))
        assertEquals(e, late)
        assertEquals(State.EXPIRED, runtime.tick(late, i.deadline, "UTC").execution.state)
    }
}

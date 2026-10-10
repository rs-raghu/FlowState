package dev.flowstate.engine

import kotlin.test.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Test

class BindingTest {
    @Test
    fun multipleParametersOutputsAndStatusSurviveWait() {
        val child =
            Definition(
                id = "child",
                entry = "wait",
                variables =
                    listOf(
                        Variable("number", Type.INTEGER, Scope.LOCAL),
                        Variable("label", Type.STRING, Scope.LOCAL),
                    ),
                nodes = listOf(Node("wait", "wait", fields = mapOf("SECONDS" to "1"))),
            )
        val parent =
            Definition(
                id = "parent",
                entry = "call",
                variables =
                    listOf(
                        Variable("out", Type.INTEGER, Scope.LOCAL),
                        Variable("text", Type.STRING, Scope.LOCAL),
                        Variable("status", Type.STRING, Scope.LOCAL),
                    ),
                nodes =
                    listOf(
                        Node(
                            "call",
                            "call",
                            fields =
                                mapOf(
                                    "WORKFLOW" to child.id,
                                    "INPUTNAMES" to "number,label",
                                    "OUTPUTS" to "number:out,label:text",
                                    "STATUSNAME" to "status",
                                ),
                            expressions =
                                mapOf(
                                    "PARAM0" to Expr("literal", Value.integer(42)),
                                    "PARAM1" to Expr("literal", Value.string("Packed")),
                                ),
                        )
                    ),
            )
        assertTrue(Compiler.validateCalls(listOf(parent, child)).isEmpty())
        val rt = Runtime { if (it == child.id) child else null }
        val pending = rt.tick(rt.start("e", parent, 0), 0, "UTC").execution
        assertEquals(State.WAITING_FOR_TIME, pending.state)
        val resumed =
            Runtime()
                .tick(codec.decodeFromString(codec.encodeToString(pending)), 1000, "UTC")
                .execution
        assertEquals(State.COMPLETED, resumed.state)
        assertEquals(Value.integer(42), resumed.locals["out"])
        assertEquals(Value.string("Packed"), resumed.locals["text"])
        assertEquals(Value.string("COMPLETED"), resumed.locals["status"])
    }

    @Test
    fun incompatibleOutputBindingIsRejected() {
        val child =
            Definition(
                id = "child",
                nodes = emptyList(),
                variables = listOf(Variable("x", Type.INTEGER, Scope.LOCAL)),
            )
        val parent =
            Definition(
                id = "parent",
                nodes =
                    listOf(
                        Node(
                            "call",
                            "call",
                            fields = mapOf("WORKFLOW" to "child", "OUTPUTS" to "x:output"),
                        )
                    ),
                variables = listOf(Variable("output", Type.STRING, Scope.LOCAL)),
            )
        assertTrue(Compiler.validateCalls(listOf(parent, child)).any { it.code == "CALL_BINDING" })
    }

    @Test
    fun typedListsRejectMixedValuesAndKeepTypeThroughUpdates() {
        val c = EvaluationContext(0, "UTC", emptyMap(), emptyMap(), "a")
        val expression =
            Expr(
                "list",
                name = "INTEGER",
                args = listOf(Expr("literal", Value.integer(1)), Expr("literal", Value.integer(2))),
            )
        val value = Expressions.evaluate(expression, c)
        assertEquals(Type.INTEGER, value.elementType)
        assertFailsWith<IllegalArgumentException> {
            Expressions.evaluate(
                expression.copy(args = expression.args + Expr("literal", Value.string("x"))),
                c,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            Expressions.evaluate(
                Expr(
                    "append",
                    args = listOf(Expr("literal", value), Expr("literal", Value.string("x"))),
                ),
                c,
            )
        }
        assertTrue(
            Variable("list", Type.LIST, Scope.LOCAL, elementType = Type.INTEGER).accepts(value)
        )
        assertFalse(
            Variable("list", Type.LIST, Scope.LOCAL, elementType = Type.STRING).accepts(value)
        )
    }

    @Test
    fun malformedFieldGetsBlockAndRemediation() {
        val source =
            """{"blocks":{"blocks":[{"type":"fs_trigger","id":"trigger","fields":{"INTERVAL":"bad"},"next":{"block":{"type":"fs_message","id":"message"}}}]}}"""
        val e = assertFailsWith<ValidationException> { Compiler.compile(source, "a", 1) }
        assertEquals("trigger", e.issues.single().block)
        assertEquals("FIELD", e.issues.single().code)
        assertTrue(e.issues.single().correction.isNotBlank())
    }
}

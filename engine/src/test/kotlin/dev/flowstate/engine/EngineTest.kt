package dev.flowstate.engine

import kotlin.test.*
import org.junit.Test
import java.time.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

class EngineTest {
    private val runtime=Runtime()
    private fun def(vararg nodes: Node)=Definition(id="a",entry=nodes.first().id,nodes=nodes.toList())
    @Test fun nestedBranches() {
        val d=def(Node("if","if",expressions=mapOf("TEST" to Expr("literal",Value.bool(true))),branches=mapOf("YES" to "inner","NO" to "bad")),Node("inner","if",expressions=mapOf("TEST" to Expr("literal",Value.bool(false))),branches=mapOf("NO" to "good")),Node("good","message",fields=mapOf("TITLE" to "correct")),Node("bad","message",fields=mapOf("TITLE" to "wrong")))
        val tick=runtime.tick(runtime.start("e",d,0),0,"UTC"); assertEquals(State.COMPLETED,tick.execution.state); assertEquals(listOf("correct"),tick.effects.map { it.title })
    }
    @Test fun waitPersistsAndResumesWithSnapshot() {
        val d=def(Node("w","wait",fields=mapOf("SECONDS" to "5"),next="m"),Node("m","message",fields=mapOf("TITLE" to "old definition")))
        val wait=runtime.tick(runtime.start("e",d,0),0,"UTC").execution
        val restored=codec.decodeFromString<Execution>(codec.encodeToString(wait))
        assertEquals(State.WAITING_FOR_TIME,runtime.tick(restored,4999,"UTC").execution.state)
        assertEquals("old definition",runtime.tick(restored,5000,"UTC").effects.single().title)
    }
    @Test fun duplicateAndExpiredResponsesIgnored() {
        val d=def(Node("q","ask",fields=mapOf("TITLE" to "Where?","OPTIONS" to "A|B","TIMEOUT" to "2"),next="m"),Node("m","message"))
        val waiting=runtime.tick(runtime.start("e",d,0),0,"UTC").execution; val token=waiting.interaction!!.token
        assertEquals(waiting,runtime.respond(waiting,"wrong","A",1))
        assertEquals(waiting,runtime.respond(waiting,token,"A",2000))
        val resumed=runtime.respond(waiting,token,"A",1); assertEquals(resumed,runtime.respond(resumed,token,"A",2))
        assertEquals(State.EXPIRED,runtime.tick(waiting,2000,"UTC").execution.state)
    }
    @Test fun loopBudget() {
        val d=def(Node("loop","while",fields=mapOf("LIMIT" to "3"),expressions=mapOf("TEST" to Expr("literal",Value.bool(true))),branches=mapOf("DO" to "body")),Node("body","log"))
        assertEquals(State.FAILED,runtime.tick(runtime.start("e",d,0),0,"UTC").execution.state)
    }
    @Test fun repeatAndBreak() {
        val d=def(Node("r","repeat",fields=mapOf("LIMIT" to "5"),branches=mapOf("DO" to "b"),next="end"),Node("b","break"),Node("end","message"))
        assertEquals(1,runtime.tick(runtime.start("e",d,0),0,"UTC").effects.size)
    }
    @Test fun shortCircuitAndStrictTypes() {
        val bad=Expr("divide",args=listOf(Expr("literal",Value.number(1.0)),Expr("literal",Value.number(0.0))))
        val c=EvaluationContext(0,"UTC",emptyMap(),emptyMap(),"a")
        assertEquals(Value.bool(false),Expressions.evaluate(Expr("and",args=listOf(Expr("literal",Value.bool(false)),bad)),c))
        assertFails { Expressions.evaluate(Expr("add",args=listOf(Expr("literal",Value.string("1")),Expr("literal",Value.number(1.0)))),c) }
    }
    @Test fun overnight() {
        assertTrue(Expressions.inTimeRange(LocalTime.of(23,0),LocalTime.of(22,0),LocalTime.of(2,0)))
        assertTrue(Expressions.inTimeRange(LocalTime.of(1,0),LocalTime.of(22,0),LocalTime.of(2,0)))
        assertFalse(Expressions.inTimeRange(LocalTime.of(2,0),LocalTime.of(22,0),LocalTime.of(2,0)))
    }
    @Test fun weeklyWindowSundayRecovery() {
        val zone=ZoneId.of("Asia/Kolkata"); val t=Trigger(kind="time",recurrence="weeklyWindow",times=listOf("00:00"),catchUp="window")
        val friday=ZonedDateTime.of(2026,10,9,0,0,0,0,zone).toInstant().toEpochMilli(); val sunday=friday+2*86400000
        assertEquals(friday,Scheduling.recovery(t,sunday,friday,zone)!!.at)
        assertEquals(Scheduling.windowKey(t,friday,zone),Scheduling.windowKey(t,sunday,zone))
        assertNotEquals(Scheduling.windowKey(t,friday,zone),Scheduling.windowKey(t,friday+7*86400000,zone))
    }
    @Test fun dstGapAndOverlap() {
        val zone=ZoneId.of("America/New_York")
        val gap=Instant.ofEpochMilli(Scheduling.resolve(LocalDate.of(2026,3,8),LocalTime.of(2,30),zone)).atZone(zone)
        assertEquals(LocalTime.of(3,30),gap.toLocalTime())
        val overlap=Instant.ofEpochMilli(Scheduling.resolve(LocalDate.of(2026,11,1),LocalTime.of(1,30),zone)).atZone(zone)
        assertEquals(ZoneOffset.ofHours(-4),overlap.offset)
    }
    @Test fun monthlyClampsAndIntervals() {
        val zone=ZoneId.of("UTC"); val t=Trigger(kind="time",recurrence="monthly",startDate="2026-01-31")
        assertEquals(LocalDate.of(2026,2,28),Instant.ofEpochMilli(Scheduling.next(t,Instant.parse("2026-02-01T00:00:00Z").toEpochMilli(),zone)!!.at).atZone(zone).toLocalDate())
        assertTrue(Scheduling.eligible(t.copy(recurrence="interval",intervalDays=2),LocalDate.of(2026,2,2)))
    }
    @Test fun compilerRoundtripAndMalformed() {
        val source="""{"blocks":{"blocks":[{"type":"fs_trigger","id":"t","fields":{"KIND":"manual"},"next":{"block":{"type":"fs_message","id":"m","fields":{"TITLE":"Hello"}}}}]}}"""
        val d=Compiler.compile(source,"a",1); assertEquals("message",d.nodes.single().op)
        assertEquals(d,codec.decodeFromString<Definition>(codec.encodeToString(d)))
        assertFails { Compiler.compile("{}","a",1) }
        assertFails { Compiler.compile(source.replace("fs_message","fs_unknown"),"a",1) }
        assertFails { Compiler.compile(source.replace("\"id\":\"m\"","\"id\":\"t\""),"a",1) }
    }
    @Test fun staleOccupancyIsUnknown() {
        val c=EvaluationContext(3600001,"UTC",emptyMap(),emptyMap(),"a",mapOf("home" to ("INSIDE" to 0L)))
        assertEquals("UNKNOWN",Expressions.evaluate(Expr("occupancy",name="home"),c).text)
    }
    @Test fun checklistRequiredAndOptional() {
        val d=def(Node("c","checklist",fields=mapOf("OPTIONS" to "Keys|?Hat")))
        val e=runtime.tick(runtime.start("e",d,0),0,"UTC").execution
        assertFails { runtime.respond(e,e.interaction!!.token,"1",1) }
        assertEquals(State.RUNNING,runtime.respond(e,e.interaction!!.token,"0",1).state)
    }
    @Test fun tryHandlesFailure() {
        val d=def(Node("t","try",branches=mapOf("DO" to "fail","ERROR" to "handle")),Node("fail","assert",expressions=mapOf("TEST" to Expr("literal",Value.bool(false)))),Node("handle","message"))
        val first=runtime.tick(runtime.start("e",d,0),0,"UTC"); assertEquals(State.RUNNING,first.execution.state)
        assertEquals(State.COMPLETED,runtime.tick(first.execution,0,"UTC").execution.state)
    }
    @Test fun cyclicImportedIrRejected() {
        assertTrue(Compiler.validate(def(Node("a","message",next="a"))).any { it.message.contains("Cycle") })
    }
    @Test fun randomMalformedGraphsFailSafely() {
        repeat(100) { size -> val d=Definition(id="a",entry="0",nodes=(0..size).map { Node(it.toString(),"message",next=((it+1)%(size+1)).toString()) }); assertTrue(Compiler.validate(d).isNotEmpty()) }
    }
}

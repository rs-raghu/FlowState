package dev.flowstate.engine

import java.time.*
import kotlin.test.*
import org.junit.Test

class WindowTest {
    private val utc = ZoneId.of("UTC")

    private fun at(s: String) = Instant.parse(s).toEpochMilli()

    @Test
    fun dailyWindowRecoveryAndClosure() {
        val t =
            Trigger(
                kind = "time",
                recurrence = "dailyWindow",
                times = listOf("07:00"),
                catchUp = "window",
            )
        val missed = at("2026-10-10T07:00:00Z")
        val recovered = Scheduling.recovery(t, at("2026-10-11T06:59:00Z"), missed, utc)!!
        assertEquals(missed, recovered.at)
        assertEquals(recovered.key, Scheduling.occurrenceKey(t, missed, utc))
        assertNotEquals(recovered.key, Scheduling.windowKey(t, at("2026-10-11T07:00:00Z"), utc))
    }

    @Test
    fun monthlyWindowClampsAndReturnsOriginalAnchor() {
        val t =
            Trigger(
                kind = "time",
                recurrence = "monthlyWindow",
                startDate = "2026-01-31",
                times = listOf("09:00"),
                catchUp = "window",
            )
        val feb = at("2026-02-28T09:00:00Z")
        assertEquals(feb, Scheduling.next(t, at("2026-02-01T00:00:00Z"), utc)!!.at)
        assertEquals(feb, Scheduling.windowStart(t, at("2026-03-30T22:00:00Z"), utc))
        assertEquals(feb, Scheduling.recovery(t, at("2026-03-01T00:00:00Z"), feb, utc)!!.at)
    }

    @Test
    fun oldWeeklyKeysRemainStable() {
        val t =
            Trigger(
                kind = "time",
                recurrence = "weeklyWindow",
                times = listOf("09:00"),
                windowDay = 5,
            )
        assertEquals(
            "window:UTC:2026-10-09",
            Scheduling.windowKey(t, at("2026-10-11T12:00:00Z"), utc),
        )
    }

    @Test
    fun locationHistoryExpressionsRespectUnknownAndExit() {
        val c =
            EvaluationContext(
                5000,
                "UTC",
                emptyMap(),
                emptyMap(),
                "a",
                occupancy = mapOf("home" to ("INSIDE" to 1000L)),
                locations = mapOf("home" to LocationState(21.0, 79.0, 150.0, entered = 1000)),
            )
        assertEquals(
            Value(Type.DURATION, "4000"),
            Expressions.evaluate(Expr("dwellDuration", name = "home"), c),
        )
        assertEquals(
            Value.NULL,
            Expressions.evaluate(Expr("dwellDuration", name = "home"), c.copy(now = 4_000_000)),
        )
        assertEquals(
            Value.NULL,
            Expressions.evaluate(
                Expr("dwellDuration", name = "home"),
                c.copy(occupancy = emptyMap()),
            ),
        )
        assertEquals(Value.number(21.0), Expressions.evaluate(Expr("latitude", name = "home"), c))
        assertEquals(Value.NULL, Expressions.evaluate(Expr("lastExit", name = "home"), c))
        assertEquals(
            Value.NULL,
            Expressions.evaluate(
                Expr("dwellDuration", name = "home"),
                c.copy(
                    locations = mapOf("home" to c.locations.getValue("home").copy(exited = 2000))
                ),
            ),
        )
    }
}

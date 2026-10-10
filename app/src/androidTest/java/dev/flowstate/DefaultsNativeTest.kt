package dev.flowstate

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import dev.flowstate.data.*
import dev.flowstate.engine.*
import dev.flowstate.ui.FlowViewModel
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class DefaultsNativeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun preferencesValidateBeforeWritingAndEveryPresetCompiles() = runBlocking {
        val prefs = Preferences(context)
        val old = prefs.snapshot()
        try {
            val custom =
                Preferences.defaults +
                    mapOf(
                        "cooldown" to "600",
                        "expiration" to "1200",
                        "timezone" to "Asia/Kolkata",
                        "retention" to "90",
                        "radius" to "500",
                    )
            prefs.restore(custom)
            assertEquals(custom, prefs.options.first())
            var rejected = false
            try {
                prefs.restore(mapOf("radius" to "99", "theme" to "dark"))
            } catch (_: IllegalArgumentException) {
                rejected = true
            }
            assertTrue(rejected)
            assertEquals(custom, prefs.options.first())
            val location = UUID.randomUUID().toString()
            for (name in FlowViewModel.templates) {
                val d =
                    Compiler.compile(
                        FlowViewModel.template(name, location, custom),
                        UUID.randomUUID().toString(),
                        1,
                        setOf(location),
                    )
                assertEquals(600, d.trigger.cooldownSeconds)
                assertEquals("Asia/Kolkata", d.trigger.zone)
                if (name in WorkflowPresets.locations) assertEquals("location", d.trigger.kind)
                if (
                    name in
                        setOf(
                            "Morning Routine",
                            "Bedtime Routine",
                            "Daily Planning",
                            "Weekly Checklist",
                        )
                )
                    assertEquals("time", d.trigger.kind)
                if (name == "Leaving Hostel")
                    assertTrue(d.nodes.any { it.op == "ask" && it.branches["CHOICE2"] != null })
            }
        } finally {
            prefs.restore(old)
        }
    }

    @Test
    fun geofenceFilteringPreservesOccupancyAndLedger() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, FlowDatabase::class.java).build()
        try {
            val location = UUID.randomUUID().toString()
            val id = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            db.dao()
                .saveLocation(
                    LocationEntity(
                        location,
                        "Fixture",
                        21.0,
                        79.0,
                        150f,
                        created = now,
                        updated = now,
                        dwellSeconds = 120,
                        cooldownSeconds = 30,
                    )
                )
            val d =
                Definition(
                    id = id,
                    entry = "message",
                    nodes = listOf(Node("message", "message")),
                    trigger =
                        Trigger(
                            kind = "location",
                            locationId = location,
                            transition = "enter",
                            cooldownSeconds = 0,
                            frequency = "unlimited",
                        ),
                )
            db.dao()
                .saveAutomation(
                    AutomationEntity(
                        id,
                        "Fixture",
                        enabled = true,
                        workspace = "{}",
                        definition = codec.encodeToString(d),
                        updated = now,
                    )
                )
            val coordinator = Coordinator(db, context)
            coordinator.geofence(location, "enter", now - 5000)
            coordinator.geofence(location, "enter", now - 5000)
            coordinator.geofence(location, "enter", now - 1000)
            assertEquals(1, db.dao().executionsForAutomation(id).size)
            assertEquals("INSIDE", db.dao().location(location)!!.occupancy)
            assertEquals(now - 1000, db.dao().location(location)!!.eventAt)
            assertNotNull(db.dao().lastLocationEvent(location, "enter"))
            coordinator.geofence(location, "exit", now)
            assertEquals("OUTSIDE", db.dao().location(location)!!.occupancy)
        } finally {
            db.close()
        }
    }
}

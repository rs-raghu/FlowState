package dev.flowstate

import androidx.test.platform.app.InstrumentationRegistry
import dev.flowstate.data.*
import dev.flowstate.engine.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PlatformNativeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as FlowStateApp

    private fun shell(command: String) =
        instrumentation.uiAutomation.executeShellCommand(command).use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().use { r ->
                r.readText()
            }
        }

    private fun awaitState(message: String, condition: () -> Boolean) {
        repeat(200) {
            if (condition()) return
            Thread.sleep(50)
        }
        assertTrue(message, condition())
    }

    @Test
    fun alarmAndWorkerDeliverOneDurableContinuation() = runBlocking {
        val id = UUID.randomUUID().toString()
        try {
            val source =
                """{"blocks":{"blocks":[{"type":"fs_trigger","id":"trigger","fields":{"KIND":"manual"},"next":{"block":{"type":"fs_wait","id":"wait","fields":{"SECONDS":2},"next":{"block":{"type":"fs_message","id":"message","fields":{"TITLE":"Timer delivered"}}}}}}]}}"""
            app.coordinator.save(id, "Timer fixture", source)
            val run = app.coordinator.start(id)!!
            assertEquals("WAITING_FOR_TIME", app.database.dao().execution(run)!!.state)
            awaitState("Platform delivered durable timer") {
                runBlocking { app.database.dao().execution(run)!!.state == "COMPLETED" }
            }
            val complete = app.database.dao().execution(run)!!.snapshot
            app.coordinator.drive(run)
            assertEquals(complete, app.database.dao().execution(run)!!.snapshot)
            assertEquals(1, app.database.dao().executionsForAutomation(id).size)
        } finally {
            app.coordinator.delete(id)
        }
    }
}

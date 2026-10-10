package dev.flowstate

import android.app.NotificationManager
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import dev.flowstate.data.*
import dev.flowstate.engine.*
import dev.flowstate.platform.Platform
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
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
    fun deniedNotificationRemainsDurableAndRestoresWithoutRepostingDismissal() = runBlocking {
        val id = UUID.randomUUID().toString()
        val platform = Platform(app)
        try {
            if (Build.VERSION.SDK_INT >= 33)
                shell("pm grant dev.flowstate android.permission.POST_NOTIFICATIONS")
            shell("cmd appops set dev.flowstate POST_NOTIFICATION ignore")
            awaitState("Notification denial applied") { !platform.notificationsAllowed() }
            val source =
                """{"blocks":{"blocks":[{"type":"fs_trigger","id":"trigger","fields":{"KIND":"manual"},"next":{"block":{"type":"fs_ask","id":"ask","fields":{"KIND":"yesno","TITLE":"Permission recovery question","TIMEOUT":0}}}}]}}"""
            app.coordinator.save(id, "Permission fixture", source)
            val run = app.coordinator.start(id)!!
            val e = codec.decodeFromString<Execution>(app.database.dao().execution(run)!!.snapshot)
            assertEquals(State.WAITING_FOR_USER, e.state)
            assertNotNull(e.interaction)
            assertFalse(platform.hasInteractionNotification(run, e.interaction!!.token))
            assertTrue(
                app.database.dao().observeDiagnostics().first().any {
                    it.message.contains("permission denied")
                }
            )
            shell("cmd appops set dev.flowstate POST_NOTIFICATION allow")
            awaitState("Notifications restored") { platform.notificationsAllowed() }
            app.coordinator.reconcile()
            val token = e.interaction!!.token
            awaitState("Pending question redisplayed") {
                platform.hasInteractionNotification(run, token)
            }
            platform.rememberDismissal(run, token)
            app.coordinator.dismiss(run, token)
            app.getSystemService(NotificationManager::class.java).cancel("$run:question:$token", 1)
            awaitState("Dismissal applied") { !platform.hasInteractionNotification(run, token) }
            app.coordinator.reconcile()
            assertFalse(platform.hasInteractionNotification(run, token))
            assertTrue(
                codec
                    .decodeFromString<Execution>(app.database.dao().execution(run)!!.snapshot)
                    .interaction!!
                    .dismissed
            )
        } finally {
            shell("cmd appops set dev.flowstate POST_NOTIFICATION allow")
            app.coordinator.delete(id)
        }
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

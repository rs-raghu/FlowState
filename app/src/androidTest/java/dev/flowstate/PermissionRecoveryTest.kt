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

class PermissionRecoveryTest {
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
        org.junit.Assume.assumeTrue(
            InstrumentationRegistry.getArguments().getString("permissionPhase") == "verify"
        )
        val id = UUID.randomUUID().toString()
        val platform = Platform(app)
        try {
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
            if (Build.VERSION.SDK_INT >= 33)
                shell("pm grant dev.flowstate android.permission.POST_NOTIFICATIONS")
            shell("cmd appops set --uid dev.flowstate POST_NOTIFICATION allow")
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
            if (Build.VERSION.SDK_INT >= 33)
                shell("pm grant dev.flowstate android.permission.POST_NOTIFICATIONS")
            shell("cmd appops set --uid dev.flowstate POST_NOTIFICATION allow")
            shell("cmd appops set dev.flowstate POST_NOTIFICATION allow")
            app.coordinator.delete(id)
        }
    }
}

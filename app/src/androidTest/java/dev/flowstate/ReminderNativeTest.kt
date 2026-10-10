package dev.flowstate

import android.app.NotificationManager
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import dev.flowstate.data.*
import dev.flowstate.engine.*
import dev.flowstate.platform.Platform
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class ReminderNativeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun awaitCount(manager: NotificationManager, id: String, expected: Int) {
        for (attempt in 0 until 100) {
            if (
                manager.activeNotifications.count { it.tag?.startsWith("$id:message:") == true } ==
                    expected
            )
                return
            Thread.sleep(50)
        }
        assertEquals(
            expected,
            manager.activeNotifications.count { it.tag?.startsWith("$id:message:") == true },
        )
    }

    @Test
    fun namedNotificationUpdateAndCancellationStayOwned() {
        val p = Platform(context)
        val id = "notification-test-${UUID.randomUUID()}"
        val manager = context.getSystemService(NotificationManager::class.java)
        try {
            val config =
                ReminderConfig(
                    target = "reminder",
                    channel = "low",
                    ongoing = true,
                    group = "personal",
                )
            val first = p.notification(id, Effect("1", "message", "First", notification = config))
            assertEquals(p.notificationsAllowed(), first)
            if (first) {
                p.notification(id, Effect("2", "message", "Updated", notification = config))
                p.notification(
                    id,
                    Effect("3", "message", "Second", notification = config.copy(target = "second")),
                )
                awaitCount(manager, id, 2)
                assertEquals(
                    2,
                    manager.activeNotifications.count {
                        it.tag?.startsWith("$id:message:") == true
                    },
                )
                p.notification(id, Effect("4", "cancelOwned", "", notification = config))
                awaitCount(manager, id, 1)
                assertEquals(
                    1,
                    manager.activeNotifications.count {
                        it.tag?.startsWith("$id:message:") == true
                    },
                )
                assertTrue(manager.activeNotifications.any { it.tag == "$id:message:second" })
            }
        } finally {
            p.cancelAllExecutionNotifications(id)
        }
    }

    @Test
    fun templateEditsDoNotChangePendingInstancesAndDeletionIsProtected() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, FlowDatabase::class.java).build()
        val c = Coordinator(db, context)
        val template =
            ChecklistTemplate(
                UUID.randomUUID().toString(),
                "Essentials",
                listOf(ChecklistItem("Keys", note = "Door")),
            )
        val d =
            Definition(
                id = UUID.randomUUID().toString(),
                entry = "check",
                nodes =
                    listOf(Node("check", "checklist", fields = mapOf("TEMPLATE" to template.id))),
            )
        var run: String? = null
        try {
            c.saveChecklist(template)
            db.dao()
                .saveAutomation(
                    AutomationEntity(
                        d.id,
                        "Checklist",
                        workspace = "{}",
                        definition = codec.encodeToString(d),
                        updated = 0,
                    )
                )
            run = c.start(d.id)!!
            c.saveChecklist(template.copy(items = listOf(ChecklistItem("Wallet"))))
            val captured = codec.decodeFromString<Execution>(db.dao().execution(run)!!.snapshot)
            val interaction = requireNotNull(captured.interaction)
            assertEquals(listOf("Keys"), interaction.options)
            assertEquals("Door", interaction.notes[0])
            var rejected = false
            try {
                c.deleteChecklist(template.id)
            } catch (_: IllegalArgumentException) {
                rejected = true
            }
            assertTrue(rejected)
            c.dismiss(run, interaction.token)
            assertTrue(
                codec
                    .decodeFromString<Execution>(db.dao().execution(run)!!.snapshot)
                    .interaction!!
                    .dismissed
            )
        } finally {
            run?.let { c.cancel(it) }
            db.close()
        }
    }
}

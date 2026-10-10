package dev.flowstate

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import dev.flowstate.data.*
import dev.flowstate.engine.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.*
import org.junit.Assert.*

class PolicyTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    @get:Rule val migration = MigrationTestHelper(instrumentation, FlowDatabase::class.java)

    @Test
    fun upgradePreservesExecutionsAndCreatesDedupLedger() {
        val name = "migration-policy.db"
        instrumentation.targetContext.deleteDatabase(name)
        migration.createDatabase(name, 1).apply {
            execSQL(
                "INSERT INTO automations (id,name,enabled,version,workspace,definition,updated,status,nextAt,lastStarted) VALUES ('a','A',0,1,'{}','{}',0,'Disabled',NULL,0)"
            )
            execSQL(
                "INSERT INTO executions (id,automationId,eventKey,snapshot,state,updated,wakeAt) VALUES ('e','a','unique-event','{}','COMPLETED',123,NULL)"
            )
            close()
        }
        migration.runMigrationsAndValidate(name, 2, true, FlowDatabase.MIGRATION_1_2).apply {
            query("SELECT outcome FROM event_ledger WHERE `key`='unique-event'").use {
                assertTrue(it.moveToFirst())
                assertEquals("COMPLETED", it.getString(0))
            }
            query("SELECT COUNT(*) FROM executions").use {
                assertTrue(it.moveToFirst())
                assertEquals(1, it.getInt(0))
            }
            close()
        }
        instrumentation.targetContext.deleteDatabase(name)
    }

    @Test
    fun queueAndReplacePersistIndependentSnapshots() = runBlocking {
        val db =
            Room.inMemoryDatabaseBuilder(instrumentation.targetContext, FlowDatabase::class.java)
                .build()
        try {
            val d =
                Definition(
                    id = "policy",
                    entry = "wait",
                    nodes = listOf(Node("wait", "wait", fields = mapOf("SECONDS" to "86400"))),
                    trigger = Trigger(concurrency = "queue", maxActive = 1),
                )
            db.dao()
                .saveAutomation(
                    AutomationEntity(
                        d.id,
                        "Policy",
                        workspace = "{}",
                        definition = codec.encodeToString(d),
                        updated = 0,
                    )
                )
            val c = Coordinator(db, instrumentation.targetContext)
            val first = c.start(d.id)!!
            val second = c.start(d.id)!!
            assertEquals("WAITING_FOR_TIME", db.dao().execution(first)!!.state)
            assertEquals("QUEUED", db.dao().execution(second)!!.state)
            c.drive(second)
            assertEquals("QUEUED", db.dao().execution(second)!!.state)
            c.cancel(first)
            c.drive(second)
            assertEquals("WAITING_FOR_TIME", db.dao().execution(second)!!.state)
            val a = db.dao().automation(d.id)!!
            db.dao()
                .saveAutomation(
                    a.copy(
                        definition =
                            codec.encodeToString(
                                d.copy(trigger = d.trigger.copy(concurrency = "replace"))
                            )
                    )
                )
            val third = c.start(d.id)!!
            assertEquals("CANCELLED", db.dao().execution(second)!!.state)
            assertEquals("WAITING_FOR_TIME", db.dao().execution(third)!!.state)
            c.cancel(third)
        } finally {
            db.close()
        }
    }
}

package dev.flowstate

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import dev.flowstate.data.*
import dev.flowstate.ui.FlowViewModel
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class DatabaseTest {
    private lateinit var db: FlowDatabase

    @Before
    fun open() {
        db =
            Room.inMemoryDatabaseBuilder(
                    InstrumentationRegistry.getInstrumentation().targetContext,
                    FlowDatabase::class.java,
                )
                .build()
    }

    @After
    fun close() {
        db.close()
    }

    @Test
    fun transactionRollsBack() = runBlocking {
        try {
            db.withTransaction {
                db.dao()
                    .saveLocation(
                        LocationEntity("l", "Home", 20.0, 78.0, 150f, created = 0, updated = 0)
                    )
                error("Injected failure")
            }
        } catch (_: IllegalStateException) {}
        assertTrue(db.dao().locations().isEmpty())
    }

    @Test
    fun eventIdentityIsUnique() = runBlocking {
        val dao = db.dao()
        dao.saveAutomation(
            AutomationEntity("a", "A", workspace = "{}", definition = "{}", updated = 0)
        )
        dao.saveExecution(ExecutionEntity("e1", "a", "same-event", "{}", "COMPLETED", 0))
        var rejected = false
        try {
            dao.saveExecution(ExecutionEntity("e2", "a", "same-event", "{}", "COMPLETED", 0))
        } catch (_: android.database.sqlite.SQLiteConstraintException) {
            rejected = true
        }
        assertTrue(rejected)
        assertEquals("e1", dao.byEvent("same-event")?.id)
    }

    @Test
    fun malformedImportHasNoWrites() = runBlocking {
        try {
            Backups(db).import("{invalid}")
            fail("Must reject malformed JSON")
        } catch (_: IllegalArgumentException) {}
        assertTrue(db.dao().automations().isEmpty())
        assertTrue(db.dao().locations().isEmpty())
    }

    @Test
    fun backupWorkspaceRoundtrip() = runBlocking {
        val id = UUID.randomUUID().toString()
        val source = FlowViewModel.template("Morning Routine")
        val backup =
            """{"schema":1,"automations":[{"id":"$id","name":"Morning","workspace":${kotlinx.serialization.json.JsonPrimitive(source)}}],"locations":[]}"""
        assertEquals(1, Backups(db).import(backup))
        val saved = db.dao().automation(id)!!
        assertFalse(saved.enabled)
        val other =
            Room.inMemoryDatabaseBuilder(
                    InstrumentationRegistry.getInstrumentation().targetContext,
                    FlowDatabase::class.java,
                )
                .build()
        try {
            assertEquals(1, Backups(other).import(Backups(db).export()))
            assertEquals(saved.workspace, other.dao().automation(id)!!.workspace)
            assertEquals(saved.definition, other.dao().automation(id)!!.definition)
        } finally {
            other.close()
        }
    }
}

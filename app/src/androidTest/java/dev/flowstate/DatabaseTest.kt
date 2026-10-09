package dev.flowstate

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import dev.flowstate.data.*
import dev.flowstate.engine.*
import dev.flowstate.ui.FlowViewModel
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
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
        dao.insertExecution(ExecutionEntity("e1", "a", "same-event", "{}", "COMPLETED", 0))
        var rejected = false
        try {
            dao.insertExecution(ExecutionEntity("e2", "a", "same-event", "{}", "COMPLETED", 0))
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

    @Test
    fun pendingSnapshotSurvivesClosingAndReopeningDatabase() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val filename = "recovery-${UUID.randomUUID()}.db"
        fun openFile() = Room.databaseBuilder(context, FlowDatabase::class.java, filename).build()
        val definition = Compiler.compile(FlowViewModel.template("Recovery checklist"), "a", 1)
        val execution =
            Runtime().tick(Runtime().start("e", definition, 1000), 1000, "UTC").execution
        var file = openFile()
        try {
            file.withTransaction {
                file
                    .dao()
                    .saveAutomation(
                        AutomationEntity(
                            "a",
                            "Recovery",
                            workspace = "{}",
                            definition = codec.encodeToString(definition),
                            updated = 1000,
                        )
                    )
                file
                    .dao()
                    .saveExecution(
                        ExecutionEntity(
                            "e",
                            "a",
                            "recovery-event",
                            codec.encodeToString(execution),
                            execution.state.name,
                            1000,
                            execution.wakeAt,
                        )
                    )
            }
            file.close()
            file = openFile()
            val restored = codec.decodeFromString<Execution>(file.dao().execution("e")!!.snapshot)
            assertEquals(execution, restored)
            assertEquals(State.WAITING_FOR_USER, restored.state)
            assertEquals(execution.interaction!!.token, restored.interaction!!.token)
            val resumed = Runtime().respond(restored, restored.interaction!!.token, "0,1", 2000)
            assertEquals(State.COMPLETED, Runtime().tick(resumed, 2000, "UTC").execution.state)
        } finally {
            file.close()
            context.deleteDatabase(filename)
        }
    }

    @Test
    fun deletingExecutionCascadesPendingEffects() = runBlocking {
        val dao = db.dao()
        dao.saveAutomation(
            AutomationEntity("a", "A", workspace = "{}", definition = "{}", updated = 0)
        )
        dao.saveExecution(ExecutionEntity("e", "a", "event", "{}", "CANCELLED", 0))
        dao.saveOutbox(OutboxEntity("effect", "e", "{}"))
        dao.deleteExecutions("a")
        assertTrue(dao.outbox().isEmpty())
        dao.deleteAutomation("a")
        assertNull(dao.automation("a"))
    }

    @Test
    fun bundledExamplesImportAsIndependentDisabledCopies() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(10, Backups(db).import(BuiltInExamples.source(context)))
        val first = db.dao().automations().map { it.id }.toSet()
        assertEquals(10, Backups(db).import(BuiltInExamples.source(context)))
        val all = db.dao().automations()
        assertEquals(20, all.size)
        assertTrue(all.none { it.enabled })
        assertEquals(6, db.dao().locations().size)
        val copies = all.filter { it.id !in first }
        val definitions = copies.map { codec.decodeFromString<Definition>(it.definition) }
        val ids = copies.map { it.id }.toSet()
        assertTrue(
            definitions
                .flatMap { it.nodes }
                .filter { it.op == "call" }
                .all { it.fields["WORKFLOW"] in ids }
        )
        assertTrue(
            definitions
                .filter { it.trigger.kind == "location" }
                .all { db.dao().location(it.trigger.locationId) != null }
        )
    }
}

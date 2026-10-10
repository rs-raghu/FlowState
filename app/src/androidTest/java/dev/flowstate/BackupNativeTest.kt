package dev.flowstate

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import dev.flowstate.data.*
import dev.flowstate.engine.*
import dev.flowstate.ui.FlowViewModel
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class BackupNativeTest {
    private fun database() =
        Room.inMemoryDatabaseBuilder(
                InstrumentationRegistry.getInstrumentation().targetContext,
                FlowDatabase::class.java,
            )
            .build()

    @Test
    fun backupLargerThanWorkspaceLimitRoundtripsWithoutWeakeningWorkspaceBounds() = runBlocking {
        val db = database()
        val restored = database()
        try {
            val source =
                FlowViewModel.template("Blank message")
                    .replace("Your workflow is running", "a".repeat(12000))
            val backup =
                Backup(
                    automations =
                        List(201) {
                            BackupAutomation(UUID.randomUUID().toString(), "Workflow $it", source)
                        },
                    locations = emptyList(),
                )
            val data = codec.encodeToString(backup)
            assertTrue("Large backup fixture", data.toByteArray(Charsets.UTF_8).size > 2_000_000)
            assertEquals(201, Backups(db).import(data))
            assertEquals(201, Backups(restored).import(Backups(db).export()))
        } finally {
            db.close()
            restored.close()
        }
    }

    @Test
    fun mergeCompilesNewWorkflowsWithTheRetainedTemplate() = runBlocking {
        val db = database()
        try {
            val current =
                ChecklistTemplate(
                    UUID.randomUUID().toString(),
                    "Essentials",
                    listOf(ChecklistItem("Current item")),
                )
            db.dao()
                .saveChecklist(
                    ChecklistEntity(current.id, current.name, codec.encodeToString(current), 0)
                )
            val incoming =
                current.copy(
                    id = UUID.randomUUID().toString(),
                    items = listOf(ChecklistItem("Incoming item")),
                )
            val id = UUID.randomUUID().toString()
            val source =
                """{"blocks":{"blocks":[{"type":"fs_trigger","id":"trigger","next":{"block":{"type":"fs_checklist","id":"check","fields":{"TEMPLATE":"${incoming.id}"}}}}]}}"""
            Backups(db)
                .import(
                    codec.encodeToString(
                        Backup(
                            automations = listOf(BackupAutomation(id, "New workflow", source)),
                            locations = emptyList(),
                            templates = listOf(incoming),
                        )
                    ),
                    "merge",
                )
            val definition =
                codec.decodeFromString<Definition>(db.dao().automation(id)!!.definition)
            assertEquals(
                "Current item",
                definition.checklists.getValue(current.id).items.single().label,
            )
            assertEquals(1, db.dao().checklists().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun fullBackupRestoresTemplatesValuesLedgerAndArchivesPendingRun() = runBlocking {
        val db = database()
        val restored = database()
        try {
            val id = UUID.randomUUID().toString()
            val template =
                ChecklistTemplate(
                    UUID.randomUUID().toString(),
                    "Essentials",
                    listOf(ChecklistItem("Keys", note = "Door")),
                )
            val source =
                """{"blocks":{"blocks":[{"id":"trigger","type":"fs_trigger","fields":{"KIND":"manual"},"next":{"block":{"id":"check","type":"fs_checklist","fields":{"TEMPLATE":"${template.id}","TITLE":"Packed?"}}}}]}}"""
            val backup =
                Backup(
                    automations = listOf(BackupAutomation(id, "Packing", source)),
                    locations = emptyList(),
                    templates = listOf(template),
                    variables =
                        listOf(
                            VariableEntity(
                                "global",
                                "prepared",
                                codec.encodeToString(Value.bool(true)),
                            )
                        ),
                )
            Backups(db).import(codec.encodeToString(backup))
            val d = codec.decodeFromString<Definition>(db.dao().automation(id)!!.definition)
            val e =
                Runtime()
                    .tick(Runtime().start(UUID.randomUUID().toString(), d, 0), 0, "UTC")
                    .execution
            db.dao()
                .insertExecution(
                    ExecutionEntity(
                        e.id,
                        id,
                        "$id:event",
                        codec.encodeToString(e),
                        e.state.name,
                        0,
                        e.wakeAt,
                    )
                )
            db.dao().saveEvent(EventEntity("$id:event", id, 0, "STARTED"))
            assertEquals(1, Backups(restored).import(Backups(db).export()))
            assertEquals(
                template,
                codec.decodeFromString<ChecklistTemplate>(
                    restored.dao().checklists().single().payload
                ),
            )
            assertEquals(
                Value.bool(true),
                codec.decodeFromString<Value>(restored.dao().variables().single().value),
            )
            assertEquals("CANCELLED", restored.dao().execution(e.id)!!.state)
            assertTrue(restored.dao().active().isEmpty())
            assertNotNull(restored.dao().event("$id:event"))
        } finally {
            db.close()
            restored.close()
        }
    }

    @Test
    fun mergeKeepsExistingAndOverwriteIncrementsVersion() = runBlocking {
        val db = database()
        try {
            val first =
                BackupAutomation(
                    UUID.randomUUID().toString(),
                    "Morning",
                    FlowViewModel.template("Blank message"),
                )
            val original = Backup(automations = listOf(first), locations = emptyList())
            Backups(db).import(codec.encodeToString(original))
            val incoming =
                original.copy(
                    automations =
                        listOf(
                            first.copy(
                                id = UUID.randomUUID().toString(),
                                workspace = FlowViewModel.template("Morning Routine"),
                            )
                        )
                )
            assertEquals(0, Backups(db).import(codec.encodeToString(incoming), "merge"))
            assertEquals(first.workspace, db.dao().automation(first.id)!!.workspace)
            val replacement =
                original.copy(
                    automations =
                        listOf(first.copy(workspace = FlowViewModel.template("Morning Routine")))
                )
            assertEquals(1, Backups(db).import(codec.encodeToString(replacement), "overwrite"))
            assertEquals(2, db.dao().automation(first.id)!!.version)
            assertFalse(db.dao().automation(first.id)!!.enabled)
        } finally {
            db.close()
        }
    }

    @Test
    fun invalidPersistentValueRejectsWholeImport() = runBlocking {
        val db = database()
        try {
            val backup =
                Backup(
                    automations =
                        listOf(
                            BackupAutomation(
                                UUID.randomUUID().toString(),
                                "New",
                                FlowViewModel.template("Blank message"),
                            )
                        ),
                    locations = emptyList(),
                    variables =
                        listOf(
                            VariableEntity(
                                "global",
                                "bad",
                                codec.encodeToString(Value(Type.BOOLEAN, "invalid")),
                            )
                        ),
                )
            var rejected = false
            try {
                Backups(db).import(codec.encodeToString(backup))
            } catch (_: IllegalArgumentException) {
                rejected = true
            }
            assertTrue(rejected)
            assertTrue(db.dao().automations().isEmpty())
            assertTrue(db.dao().variables().isEmpty())
        } finally {
            db.close()
        }
    }
}

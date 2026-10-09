package dev.flowstate

import android.content.Context
import android.os.Process
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import dev.flowstate.engine.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import org.junit.Assert.*
import org.junit.Test

/** Executed twice by test-process-recovery.sh with a force-stop/reopen between invocations. */
class ProcessRecoveryTest {
    @Test
    fun suspendedBranchesRetainCapturedVersionAcrossNewProcess() = runBlocking {
        val app =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as FlowStateApp
        val fixture = app.getSharedPreferences("process-recovery-test", Context.MODE_PRIVATE)
        val phase = InstrumentationRegistry.getArguments().getString("recoveryPhase")
        when (phase) {
            "prepare" -> {
                fixture.getString("automation", null)?.let { app.coordinator.delete(it) }
                val id = UUID.randomUUID().toString()
                val workspace =
                    """{"blocks":{"languageVersion":0,"blocks":[{"type":"fs_trigger","id":"trigger","fields":{"KIND":"manual"},"next":{"block":{"type":"fs_parallel","id":"fork","inputs":{"A":{"block":{"type":"fs_ask","id":"question","fields":{"KIND":"yesno","TITLE":"Before restart","TIMEOUT":0}}},"B":{"block":{"type":"fs_wait","id":"wait","fields":{"SECONDS":86400}}}}}}}]}}"""
                app.coordinator.save(id, "Process recovery fixture", workspace)
                val run = app.coordinator.start(id)!!
                val e =
                    codec.decodeFromString<Execution>(app.database.dao().execution(run)!!.snapshot)
                assertEquals(State.WAITING_FOR_BRANCHES, e.state)
                val token = e.pendingInteractions().single().token
                val wake = e.wakeAt!!
                app.coordinator.save(
                    id,
                    "Edited while suspended",
                    workspace.replace("Before restart", "After edit"),
                )
                assertEquals(2, app.database.dao().automation(id)!!.version)
                assertTrue(
                    fixture
                        .edit()
                        .putString("automation", id)
                        .putString("execution", run)
                        .putString("token", token)
                        .putLong("wake", wake)
                        .putInt("pid", Process.myPid())
                        .commit()
                )
            }
            "verify" -> {
                val id =
                    fixture.getString("automation", null) ?: error("Prepare phase did not persist")
                val run = fixture.getString("execution", null)!!
                val token = fixture.getString("token", null)!!
                assertNotEquals(fixture.getInt("pid", 0), Process.myPid())
                ActivityScenario.launch(MainActivity::class.java).use {
                    app.coordinator.reconcile()
                    val e =
                        codec.decodeFromString<Execution>(
                            app.database.dao().execution(run)!!.snapshot
                        )
                    assertEquals(State.WAITING_FOR_BRANCHES, e.state)
                    assertEquals(1, e.definition.version)
                    assertEquals(2, app.database.dao().automation(id)!!.version)
                    assertEquals(token, e.pendingInteractions().single().token)
                    assertEquals("Before restart", e.pendingInteractions().single().title)
                    assertEquals(fixture.getLong("wake", 0), e.wakeAt)
                    app.coordinator.respond(run, token, "Yes")
                    val resumed =
                        codec.decodeFromString<Execution>(
                            app.database.dao().execution(run)!!.snapshot
                        )
                    assertEquals(State.WAITING_FOR_BRANCHES, resumed.state)
                    assertTrue(resumed.pendingInteractions().isEmpty())
                    assertEquals(fixture.getLong("wake", 0), resumed.wakeAt)
                    app.coordinator.respond(run, token, "Yes")
                    assertEquals(
                        resumed,
                        codec.decodeFromString<Execution>(
                            app.database.dao().execution(run)!!.snapshot
                        ),
                    )
                    app.coordinator.cancel(run)
                    assertEquals("CANCELLED", app.database.dao().execution(run)!!.state)
                }
                app.coordinator.delete(id)
                assertTrue(fixture.edit().clear().commit())
            }
            else -> error("Run this test through scripts/test-process-recovery.sh")
        }
    }
}

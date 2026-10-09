package dev.flowstate

import android.app.NotificationManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import dev.flowstate.data.Preferences
import dev.flowstate.engine.Execution
import dev.flowstate.engine.codec
import dev.flowstate.engine.pendingInteractions
import dev.flowstate.platform.Platform
import dev.flowstate.ui.FlowViewModel
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import org.junit.*
import org.junit.Assert.*

class AppSmokeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app
        get() =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as FlowStateApp

    private var scenario: ActivityScenario<MainActivity>? = null
    private var automationId: String? = null

    @Before fun prepare() = runBlocking { Preferences(app).onboard() }

    @After
    fun clean() {
        scenario?.close()
        runBlocking { automationId?.let { app.coordinator.delete(it) } }
    }

    private fun open() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("FlowState").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun navigate(name: String) {
        compose.onNode(hasText(name) and hasClickAction()).performClick()
        compose.waitForIdle()
    }

    @Test
    fun nativeSectionsRemainUsableAfterActivityRecreation() {
        open()
        navigate("Automations")
        compose.onNodeWithText("Your logic, saved on this device.").assertIsDisplayed()
        navigate("Locations")
        compose.onNodeWithText("Geofences use at least a 100 m radius.").assertIsDisplayed()
        navigate("Activity")
        compose
            .onNodeWithText("Respond to questions and inspect execution history.")
            .assertIsDisplayed()
        navigate("Settings")
        compose.onNodeWithText("Permission health").assertExists()
        scenario!!.recreate()
        compose.onNodeWithText("Permission health").assertExists()
        navigate("Dashboard")
        compose.onNodeWithText("Build routines that follow your decisions.").assertIsDisplayed()
    }

    @Test
    fun checklistProgressSurvivesRecreationAndDuplicateResponseIsIgnored() {
        val id = UUID.randomUUID().toString()
        automationId = id
        val executionId = runBlocking {
            app.coordinator.save(id, "Device checklist", FlowViewModel.template("Device checklist"))
            app.coordinator.start(id)!!
        }
        val original = runBlocking {
            codec.decodeFromString<Execution>(app.database.dao().execution(executionId)!!.snapshot)
        }
        val token = original.interaction!!.token
        open()
        navigate("Activity")
        compose.onAllNodes(isToggleable())[0].performClick()
        compose.waitUntil(10_000) {
            runBlocking {
                codec
                    .decodeFromString<Execution>(
                        app.database.dao().execution(executionId)!!.snapshot
                    )
                    .interaction
                    ?.completed == listOf(0)
            }
        }
        scenario!!.recreate()
        compose.onAllNodes(isToggleable())[0].assertIsOn()
        compose.onAllNodes(isToggleable())[1].performClick()
        compose.onNodeWithText("Complete checklist").performClick()
        compose.waitUntil(10_000) {
            runBlocking { app.database.dao().execution(executionId)!!.state == "COMPLETED" }
        }
        runBlocking {
            val completed = app.database.dao().execution(executionId)!!.snapshot
            app.coordinator.respond(executionId, token, "0,1")
            assertEquals(completed, app.database.dao().execution(executionId)!!.snapshot)
            assertNull(codec.decodeFromString<Execution>(completed).interaction)
        }
    }

    @Test
    fun concurrentBranchQuestionsHaveSeparateNotificationsAndResponses() {
        val id = UUID.randomUUID().toString()
        automationId = id
        val source =
            """{"blocks":{"languageVersion":0,"blocks":[{"type":"fs_trigger","id":"trigger","fields":{"KIND":"manual"},"next":{"block":{"type":"fs_parallel","id":"fork","inputs":{"A":{"block":{"type":"fs_ask","id":"a","fields":{"KIND":"yesno","TITLE":"Branch A question","TIMEOUT":0}}},"B":{"block":{"type":"fs_ask","id":"b","fields":{"KIND":"yesno","TITLE":"Branch B question","TIMEOUT":0}}}}}}}]}}"""
        val executionId = runBlocking {
            app.coordinator.save(id, "Concurrent questions", source)
            app.coordinator.start(id)!!
        }
        fun pending() = runBlocking {
            codec
                .decodeFromString<Execution>(app.database.dao().execution(executionId)!!.snapshot)
                .pendingInteractions()
        }
        assertEquals(2, pending().size)
        if (Platform(app).notificationsAllowed()) {
            compose.waitUntil(10_000) {
                app.getSystemService(NotificationManager::class.java).activeNotifications.count {
                    it.tag?.startsWith("$executionId:question:") == true
                } == 2
            }
        }
        open()
        navigate("Activity")
        compose.onAllNodesWithText("Yes")[1].performScrollTo().performClick()
        compose.waitUntil(10_000) { pending().size == 1 }
        compose.onNodeWithText("Branch A question").assertExists()
        compose.onNodeWithText("Branch B question").assertDoesNotExist()
        compose.onNodeWithText("Yes").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            runBlocking { app.database.dao().execution(executionId)!!.state == "COMPLETED" }
        }
        assertTrue(pending().isEmpty())
    }
}

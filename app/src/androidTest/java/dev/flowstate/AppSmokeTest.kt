package dev.flowstate

import android.app.NotificationManager
import androidx.compose.ui.geometry.Offset
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
    private var locationId: String? = null

    @Before fun prepare() = runBlocking { Preferences(app).onboard() }

    @After
    fun clean() {
        scenario?.close()
        runBlocking { automationId?.let { app.coordinator.delete(it) } }
        runBlocking { locationId?.let { app.coordinator.deleteLocation(it) } }
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
    fun largerTextKeepsNavigationAndSettingsActionsAccessible() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(command: String) =
            automation.executeShellCommand(command).use {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(it)
                    .bufferedReader()
                    .readText()
                    .trim()
            }
        val old = shell("settings get system font_scale")
        try {
            shell("settings put system font_scale 1.6")
            open()
            compose.onNodeWithText("Create automation").assertIsDisplayed()
            navigate("Settings")
            saveUiScreenshot(app, "settings-folders-large-text")
            compose.onNodeWithText("Personal defaults").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Test notification").assertDoesNotExist()
            compose
                .onNode(hasText("Tools & data") and hasClickAction())
                .performScrollTo()
                .performClick()
            compose
                .onNode(hasText("Test actions") and hasClickAction())
                .performScrollTo()
                .performClick()
            compose.onNodeWithText("Test notification").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Copy diagnostic report").assertDoesNotExist()
            compose.onNodeWithText("‹ Tools & data").performScrollTo().performClick()
            compose
                .onNode(hasText("Diagnostics") and hasClickAction())
                .performScrollTo()
                .performClick()
            compose.onNodeWithText("Copy diagnostic report").performScrollTo().assertIsDisplayed()
            saveUiScreenshot(app, "settings-diagnostics-large-text")
            scenario!!.recreate()
            compose.onNodeWithText("Copy diagnostic report").performScrollTo().assertIsDisplayed()
        } finally {
            shell(
                "settings put system font_scale " +
                    old.takeIf { it.toFloatOrNull() != null }.orEmpty().ifEmpty { "1.0" }
            )
        }
    }

    @Test
    fun mapCanChoosePanZoomRestoreAndSaveLocation() {
        open()
        navigate("Locations")
        compose.onNodeWithText("Add location").performClick()
        val name = "Map test ${UUID.randomUUID()}"
        compose.onNodeWithTag("location-name").performTextReplacement(name)
        compose.onNodeWithTag("location-name").performImeAction()
        compose.onNodeWithTag("save-location").performClick()
        compose.onNodeWithTag("location-error").assertExists()
        compose.onNodeWithTag("location-map").performScrollTo().performTouchInput { click(center) }
        compose.onNodeWithTag("location-error").assertDoesNotExist()
        fun pin() =
            compose
                .onNodeWithTag("location-pin")
                .fetchSemanticsNode()
                .config[androidx.compose.ui.semantics.SemanticsProperties.Text]
                .joinToString()
        fun scale() =
            compose
                .onNodeWithTag("map-scale")
                .fetchSemanticsNode()
                .config[androidx.compose.ui.semantics.SemanticsProperties.Text]
                .joinToString()
        val originalPin = pin()
        saveUiScreenshot(app, "location-map-overview")
        val originalScale = scale()
        repeat(3) { compose.onNodeWithText("Zoom in +").performScrollTo().performClick() }
        assertNotEquals(originalScale, scale())
        val zoomed = scale()
        compose.onNodeWithText("Zoom out −").performClick()
        assertNotEquals(zoomed, scale())
        compose.onNodeWithTag("location-map").performScrollTo().performTouchInput {
            swipe(center, center + Offset(100f, 0f), 600)
        }
        compose.onNodeWithTag("location-map").performTouchInput { click(center) }
        assertNotEquals("Panning changes the coordinate under the center", originalPin, pin())
        compose.onNodeWithText("Center on pin & show radius").performScrollTo().performClick()
        compose.onNodeWithTag("location-map").performScrollTo().assertIsDisplayed()
        val selected = pin()
        val fitted = scale()
        saveUiScreenshot(app, "location-map-radius")
        scenario!!.recreate()
        compose.onNodeWithTag("location-name").assertTextContains(name)
        assertEquals(selected, pin())
        assertEquals(fitted, scale())
        compose.onNodeWithTag("location-map").performScrollTo()
        saveUiScreenshot(app, "location-map-restored")
        compose.onNodeWithTag("save-location").assertIsDisplayed().performClick()
        compose.waitUntil(15000) {
            runBlocking { app.database.dao().locations().any { it.name == name } }
        }
        val saved = runBlocking { app.database.dao().locations().single { it.name == name } }
        locationId = saved.id
        assertTrue(saved.latitude.isFinite() && saved.longitude.isFinite())
        assertEquals(150f, saved.radius)
        compose.waitUntil(15000) {
            compose.onAllNodesWithTag("location-map").fetchSemanticsNodes().isEmpty()
        }
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
        compose.waitUntil(10_000) {
            pending().singleOrNull()?.title == "Branch A question" &&
                compose.onAllNodesWithText("Branch B question").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("Branch A question").assertExists()
        compose.onNodeWithText("Branch B question").assertDoesNotExist()
        compose.onNodeWithText("Yes").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            runBlocking { app.database.dao().execution(executionId)!!.state == "COMPLETED" }
        }
        assertTrue(pending().isEmpty())
    }
}

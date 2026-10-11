package dev.flowstate

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dev.flowstate.data.*
import dev.flowstate.engine.codec
import dev.flowstate.ui.FlowViewModel
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import org.junit.*
import org.junit.Assert.*

class EditorNativeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
            as FlowStateApp
    private var scenario: ActivityScenario<MainActivity>? = null
    private var id = UUID.randomUUID().toString()

    private fun web(view: View): WebView? =
        if (view is WebView) view
        else if (view is ViewGroup)
            (0 until view.childCount).firstNotNullOfOrNull { web(view.getChildAt(it)) }
        else null

    private fun js(script: String): String {
        val latch = CountDownLatch(1)
        var result = "null"
        scenario!!.onActivity { activity ->
            web(activity.window.decorView)?.evaluateJavascript(script) {
                result = it
                latch.countDown()
            } ?: latch.countDown()
        }
        assertTrue("JavaScript callback", latch.await(10, TimeUnit.SECONDS))
        return result
    }

    private fun ready() {
        for (i in 0 until 100) {
            compose.waitForIdle()
            if (
                js("typeof window.FlowEditor") == "\"object\"" &&
                    js("JSON.stringify(FlowEditor.snapshot())").contains("fs_trigger")
            ) {
                painted()
                return
            }
            Thread.sleep(100)
        }
        fail(
            "Offline editor did not initialize: " +
                js(
                    "JSON.stringify({errors:window.FlowEditorErrors,url:location.href,editor:typeof window.FlowEditor,bridge:typeof window.FlowBridge,status:document.getElementById('status').textContent})"
                )
        )
    }

    private fun painted() {
        js(
            "window.FlowEditorFrameReady=false;requestAnimationFrame(function(){requestAnimationFrame(function(){window.FlowEditorFrameReady=true})})"
        )
        for (i in 0 until 100) {
            if (js("window.FlowEditorFrameReady") == "true") break
            Thread.sleep(50)
        }
        val metrics =
            js(
                "JSON.stringify({viewport:[innerWidth,innerHeight],body:document.body.getBoundingClientRect().toJSON(),stage:document.getElementById('stage').getBoundingClientRect().toJSON(),workspace:document.getElementById('workspace').getBoundingClientRect().toJSON()})"
            )
        assertEquals(
            "Workspace must occupy the available screen: $metrics",
            "true",
            js("document.getElementById('workspace').getBoundingClientRect().height > 300"),
        )
        if (js("document.getElementById('picker').hidden") == "false") {
            assertEquals(
                "Block library must be visible: $metrics",
                "true",
                js(
                    "document.querySelector('.block-choice').getBoundingClientRect().height >= 44 && document.getElementById('picker').getBoundingClientRect().height > 300"
                ),
            )
        }
    }

    @After
    fun clean() {
        scenario?.close()
        EditorDrafts(app).clear(id)
        runBlocking { app.coordinator.delete(id) }
    }

    @Test
    fun createFromDashboardBrowseBlocksSaveAndRun() {
        runBlocking { Preferences(app).onboard() }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(15000) {
            compose.onAllNodesWithText("Create automation").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Create automation").performClick()
        val name = "Created through UI ${UUID.randomUUID()}"
        compose.onNode(hasSetTextAction()).performTextReplacement(name)
        compose.onNodeWithText("Create").performClick()
        compose.waitUntil(15000) {
            runBlocking { app.database.dao().automations().any { it.name == name } }
        }
        id = runBlocking { app.database.dao().automations().single { it.name == name }.id }
        ready()
        assertEquals("0", js("document.querySelectorAll('#search').length"))
        assertEquals("32", js("document.querySelectorAll('.block-choice').length"))
        saveUiScreenshot(app, "editor-block-library")
        // Exercise the same HTML button as a tap, with the real WebView/native save bridge.
        js("document.querySelector('[data-block=fs_message]').click()")
        assertTrue(js("JSON.stringify(FlowEditor.snapshot())").contains("fs_message"))
        js("document.getElementById('save').click()")
        compose.waitUntil(15000) {
            runBlocking { app.database.dao().automation(id)!!.version >= 2 }
        }
        assertFalse(
            js("document.getElementById('status').textContent")
                .contains("uncaught", ignoreCase = true)
        )
        painted()
        saveUiScreenshot(app, "editor-created-workflow")
        js("document.getElementById('close').click()")
        compose.waitUntil(15000) {
            compose.onAllNodesWithText("Create automation").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasText("Automations") and hasClickAction()).performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement(name)
        compose.onNodeWithText("Run").performScrollTo().performClick()
        compose.waitUntil(15000) {
            runBlocking {
                app.database.dao().executionsForAutomation(id).any { it.state == "COMPLETED" }
            }
        }
    }

    @Test
    fun bundledEditorValidatesSavesAndRestoresDraftAfterRotation() {
        runBlocking {
            Preferences(app).onboard()
            app.coordinator.save(id, "Editor fixture", FlowViewModel.template("Blank message"))
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(15000) {
            compose.onAllNodesWithText("Edit blocks").fetchSemanticsNodes().isNotEmpty()
        }
        compose
            .onAllNodesWithText("Edit blocks")
            .onFirst()
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitForIdle()
        ready()
        js("document.getElementById('validate').click()")
        var status = ""
        for (i in 0 until 100) {
            status = js("document.getElementById('status').textContent")
            if (status.contains("valid")) break
            Thread.sleep(50)
        }
        assertTrue(status, status.contains("valid"))
        val draft =
            js("JSON.stringify(FlowEditor.snapshot())")
                .let { codec.decodeFromString<String>(it) }
                .replace("Reminder", "Draft survives rotation")
        js("FlowBridge.postMessage(JSON.stringify({action:'dirty',workspace:$draft}))")
        for (i in 0 until 100) {
            if (EditorDrafts(app).source(id) != null) break
            Thread.sleep(50)
        }
        assertNotNull(EditorDrafts(app).source(id))
        scenario!!.recreate()
        ready()
        assertTrue(js("JSON.stringify(FlowEditor.snapshot())").contains("Draft survives rotation"))
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_VIEW_RENDERER_TERMINATE)) {
            scenario!!.onActivity { activity ->
                val renderer =
                    WebViewCompat.getWebViewRenderProcess(
                        requireNotNull(web(activity.window.decorView))
                    )
                assertNotNull("Editor has a renderer", renderer)
                assertTrue("Terminate renderer for recovery check", renderer!!.terminate())
            }
            compose.waitUntil(15000) {
                compose.onAllNodesWithText("Reload editor").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Reload editor").performClick()
            compose.waitForIdle()
            ready()
            assertTrue(
                js("JSON.stringify(FlowEditor.snapshot())").contains("Draft survives rotation")
            )
        }
        js("document.getElementById('save').click()")
        compose.waitUntil(15000) {
            runBlocking {
                app.database.dao().automation(id)?.workspace?.contains("Draft survives rotation") ==
                    true
            }
        }
        compose.waitUntil(15000) { EditorDrafts(app).source(id) == null }
        assertNull(EditorDrafts(app).source(id))
        val saved = runBlocking { app.database.dao().automation(id)!!.workspace }
        js("FlowBridge.postMessage('{malformed')")
        Thread.sleep(100)
        assertEquals(saved, runBlocking { app.database.dao().automation(id)!!.workspace })
    }
}

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
    private val id = UUID.randomUUID().toString()

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
            if (
                js("typeof window.FlowEditor") == "\"object\"" &&
                    js("JSON.stringify(FlowEditor.snapshot())").contains("fs_trigger")
            )
                return
            Thread.sleep(100)
        }
        fail(
            "Offline editor did not initialize: " +
                js(
                    "JSON.stringify({errors:window.FlowEditorErrors,url:location.href,editor:typeof window.FlowEditor,bridge:typeof window.FlowBridge,status:document.getElementById('status').textContent})"
                )
        )
    }

    @After
    fun clean() {
        scenario?.close()
        EditorDrafts(app).clear(id)
        runBlocking { app.coordinator.delete(id) }
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

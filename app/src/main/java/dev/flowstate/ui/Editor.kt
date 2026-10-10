package dev.flowstate.ui

import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.webkit.*
import dev.flowstate.data.AutomationEntity
import dev.flowstate.data.EditorDrafts
import dev.flowstate.engine.*
import java.io.ByteArrayInputStream
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Editor(vm: FlowViewModel, a: AutomationEntity, onClose: () -> Unit) {
    val automations by vm.automations.collectAsStateWithLifecycle()
    val locations by vm.locations.collectAsStateWithLifecycle()
    var view by remember { mutableStateOf<WebView?>(null) }
    var dirty by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }
    var simulation by remember { mutableStateOf<Definition?>(null) }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val drafts = remember { EditorDrafts(vm.application) }
    fun close() {
        if (dirty) confirmClose = true else onClose()
    }
    BackHandler { close() }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            val loader =
                WebViewAssetLoader.Builder()
                    .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                    .build()
            WebView(context).apply {
                view = this
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.domStorageEnabled = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                webChromeClient =
                    object : WebChromeClient() {
                        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                            if (dev.flowstate.BuildConfig.DEBUG)
                                android.util.Log.d(
                                    "FlowStateEditor",
                                    "${message.messageLevel()}: ${message.message()} (${message.lineNumber()})",
                                )
                            return true
                        }
                    }
                var editorInitialized = false
                var lastSavedWorkspace: JsonElement? = null
                fun receiveEditorMessage(data: String) {
                    if (data.length > 2_000_000) {
                        vm.message.value = "Editor message exceeds 2 MB"
                        return
                    }
                    try {
                        val immediate = codec.parseToJsonElement(SafeInput.json(data)).jsonObject
                        if (immediate["action"]?.jsonPrimitive?.content == "dirty") {
                            immediate["workspace"]?.let {
                                if (it != lastSavedWorkspace) {
                                    dirty = true
                                    drafts.save(a.id, it.toString())
                                }
                            }
                            return
                        }
                    } catch (e: Exception) {
                        vm.message.value = e.message
                        return
                    }
                    scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) {
                        try {
                            val payload = codec.parseToJsonElement(SafeInput.json(data)).jsonObject
                            when (payload["action"]?.jsonPrimitive?.content) {
                                "ready" -> {
                                    if (editorInitialized) return@launch
                                    editorInitialized = true
                                    fun resources(items: List<Pair<String, String>>) =
                                        buildJsonArray {
                                            items.forEach { (id, name) ->
                                                add(
                                                    buildJsonObject {
                                                        put("id", id)
                                                        put("name", name)
                                                    }
                                                )
                                            }
                                        }
                                    val r = buildJsonObject {
                                        put(
                                            "defaults",
                                            codec.encodeToJsonElement(
                                                dev.flowstate.data
                                                    .Preferences(vm.application)
                                                    .options
                                                    .first()
                                            ),
                                        )
                                        put(
                                            "locations",
                                            resources(vm.dao.locations().map { it.id to it.name }),
                                        )
                                        put(
                                            "templates",
                                            resources(vm.dao.checklists().map { it.id to it.name }),
                                        )
                                        put(
                                            "workflows",
                                            resources(
                                                vm.dao
                                                    .automations()
                                                    .filter { it.id != a.id }
                                                    .map { it.id to it.name }
                                            ),
                                        )
                                    }
                                    evaluateJavascript(
                                        "FlowEditor.resources($r);FlowEditor.load(${drafts.source(a.id) ?: a.workspace})",
                                        null,
                                    )
                                    dirty = drafts.source(a.id) != null
                                }
                                "dirty" -> {
                                    dirty = true
                                    payload["workspace"]?.let {
                                        drafts.save(a.id, it.toString())
                                    }
                                }
                                "close" -> close()
                                "save",
                                "copy",
                                "validate",
                                "simulate" -> {
                                    val source = requireNotNull(payload["workspace"]).toString()
                                    val action = payload["action"]!!.jsonPrimitive.content
                                    val id =
                                        if (action == "copy") UUID.randomUUID().toString() else a.id
                                    val d =
                                        Compiler.compile(
                                            source,
                                            id,
                                            a.version + 1,
                                            vm.locations.value.map { it.id }.toSet(),
                                            vm.automations.value.map { it.id }.toSet(),
                                            vm.dao.checklists().associate {
                                                it.id to
                                                    codec.decodeFromString<ChecklistTemplate>(
                                                        it.payload
                                                    )
                                            },
                                        )
                                    when (action) {
                                        "save",
                                        "copy" -> {
                                            vm.application.coordinator.save(
                                                id,
                                                if (action == "copy") a.name + " copy" else a.name,
                                                source,
                                            )
                                            lastSavedWorkspace = payload["workspace"]
                                            dirty = false
                                            drafts.clear(a.id)
                                            evaluateJavascript("FlowEditor.saved()", null)
                                            if (action == "copy") onClose()
                                        }
                                        "validate" ->
                                            evaluateJavascript(
                                                "FlowEditor.errors(${codec.encodeToString(Compiler.warnings(d))})",
                                                null,
                                            )
                                        "simulate" -> simulation = d
                                    }
                                }
                            }
                        } catch (e: ValidationException) {
                            val issues = buildJsonArray {
                                e.issues.forEach {
                                    add(
                                        buildJsonObject {
                                            put("code", it.code)
                                            put("severity", it.severity)
                                            put("block", it.block)
                                            put("message", it.message)
                                            put("correction", it.correction)
                                        }
                                    )
                                }
                            }
                            evaluateJavascript("FlowEditor.errors($issues)", null)
                        } catch (e: Exception) {
                            vm.message.value = e.message ?: "Editor operation failed"
                        }
                    }
                }
                webViewClient =
                    object : WebViewClient() {
                        override fun onPageFinished(v: WebView, url: String) {
                            if (
                                url ==
                                    "https://appassets.androidplatform.net/assets/editor/index.html"
                            )
                                receiveEditorMessage("{\"action\":\"ready\"}")
                        }

                        override fun shouldInterceptRequest(
                            v: WebView,
                            request: WebResourceRequest,
                        ): WebResourceResponse =
                            loader.shouldInterceptRequest(request.url)
                                ?: WebResourceResponse(
                                    "text/plain",
                                    "UTF-8",
                                    ByteArrayInputStream(ByteArray(0)),
                                )

                        override fun shouldOverrideUrlLoading(
                            v: WebView,
                            request: WebResourceRequest,
                        ) =
                            request.url.toString() !=
                                "https://appassets.androidplatform.net/assets/editor/index.html"
                    }
                if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                    WebViewCompat.addWebMessageListener(
                        this,
                        "FlowBridge",
                        setOf("https://appassets.androidplatform.net"),
                    ) { _, message, origin, main, _ ->
                        if (
                            main &&
                                origin.scheme == "https" &&
                                origin.host == "appassets.androidplatform.net" &&
                                origin.port in setOf(-1, 443)
                        )
                            message.data?.let(::receiveEditorMessage)
                    }
                } else {
                    // evaluateJavascript runs only in the top-level trusted document. No JavaScript
                    // interface is exposed to frames. Polling exists only while this editor is
                    // resumed.
                    scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) {
                        lifecycleOwner.lifecycle.repeatOnLifecycle(
                            androidx.lifecycle.Lifecycle.State.RESUMED
                        ) {
                            while (isActive) {
                                kotlinx.coroutines.delay(250)
                                if (
                                    url ==
                                        "https://appassets.androidplatform.net/assets/editor/index.html"
                                )
                                    evaluateJavascript(
                                        "window.FlowEditor && FlowEditor.takeMessage ? JSON.stringify(FlowEditor.takeMessage()) : null"
                                    ) { raw ->
                                        if (raw != null && raw != "null" && raw.length <= 4_000_000)
                                            try {
                                                receiveEditorMessage(
                                                    codec.decodeFromString<String>(raw)
                                                )
                                            } catch (e: Exception) {
                                                vm.message.value = e.message
                                            }
                                    }
                            }
                        }
                    }
                }
                loadUrl("https://appassets.androidplatform.net/assets/editor/index.html")
            }
        },
    )
    DisposableEffect(Unit) {
        onDispose {
            view?.let {
                it.stopLoading()
                if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER))
                    WebViewCompat.removeWebMessageListener(it, "FlowBridge")
                it.destroy()
            }
            view = null
        }
    }
    if (confirmClose)
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text("Discard unsaved changes?") },
            text = { Text("Save in the editor before leaving to keep your changes.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClose = false
                        drafts.clear(a.id)
                        onClose()
                    }
                ) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClose = false }) { Text("Keep editing") }
            },
        )
    simulation?.let { definition ->
        ModalBottomSheet(onDismissRequest = { simulation = null }) {
            Simulator(
                definition,
                automations.map {
                    codec.decodeFromJsonElement<Definition>(codec.parseToJsonElement(it.definition))
                },
                locationModels =
                    locations.associate {
                        it.id to LocationState(it.latitude, it.longitude, it.radius.toDouble())
                    },
                onHighlight = { id ->
                    view?.evaluateJavascript("FlowEditor.highlight(${JsonPrimitive(id)})", null)
                },
            )
        }
    }
}

package dev.flowstate.ui

import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.webkit.*
import dev.flowstate.data.AutomationEntity
import dev.flowstate.engine.*
import java.io.ByteArrayInputStream
import java.util.UUID
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Editor(vm: FlowViewModel, a: AutomationEntity, onClose: () -> Unit) {
    val automations by vm.automations.collectAsStateWithLifecycle()
    var view by remember { mutableStateOf<WebView?>(null) }
    var dirty by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }
    var simulation by remember { mutableStateOf<Definition?>(null) }
    val scope = rememberCoroutineScope()
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
                webViewClient =
                    object : WebViewClient() {
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
                            request.url.host != "appassets.androidplatform.net" ||
                                request.url.path?.startsWith("/assets/editor/") != true
                    }
                if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                    WebViewCompat.addWebMessageListener(
                        this,
                        "FlowBridge",
                        setOf("https://appassets.androidplatform.net"),
                    ) { _, message, origin, isMainFrame, _ ->
                        if (!isMainFrame || origin.host != "appassets.androidplatform.net")
                            return@addWebMessageListener
                        val data = message.data ?: return@addWebMessageListener
                        if (data.length > 2_000_000) {
                            vm.message.value = "Editor message exceeds 2 MB"
                            return@addWebMessageListener
                        }
                        scope.launch {
                            try {
                                val payload = codec.parseToJsonElement(data).jsonObject
                                when (payload["action"]?.jsonPrimitive?.content) {
                                    "ready" -> {
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
                                                "locations",
                                                resources(
                                                    vm.dao.locations().map { it.id to it.name }
                                                ),
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
                                            "FlowEditor.resources($r);FlowEditor.load(${a.workspace})",
                                            null,
                                        )
                                    }
                                    "dirty" -> dirty = true
                                    "close" -> close()
                                    "save",
                                    "copy",
                                    "validate",
                                    "simulate" -> {
                                        val source = requireNotNull(payload["workspace"]).toString()
                                        val action = payload["action"]!!.jsonPrimitive.content
                                        val id =
                                            if (action == "copy") UUID.randomUUID().toString()
                                            else a.id
                                        val d =
                                            Compiler.compile(
                                                source,
                                                id,
                                                a.version + 1,
                                                vm.locations.value.map { it.id }.toSet(),
                                                vm.automations.value.map { it.id }.toSet(),
                                            )
                                        when (action) {
                                            "save",
                                            "copy" -> {
                                                vm.application.coordinator.save(
                                                    id,
                                                    if (action == "copy") a.name + " copy"
                                                    else a.name,
                                                    source,
                                                )
                                                dirty = false
                                                evaluateJavascript("FlowEditor.saved()", null)
                                                if (action == "copy") onClose()
                                            }
                                            "validate" ->
                                                evaluateJavascript("FlowEditor.errors([])", null)
                                            "simulate" -> simulation = d
                                        }
                                    }
                                }
                            } catch (e: ValidationException) {
                                val issues = buildJsonArray {
                                    e.issues.forEach {
                                        add(
                                            buildJsonObject {
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
                    loadUrl("https://appassets.androidplatform.net/assets/editor/index.html")
                } else
                    vm.message.value =
                        "Update Android System WebView to use the secure editor bridge"
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
                onHighlight = { id ->
                    view?.evaluateJavascript("FlowEditor.highlight(${JsonPrimitive(id)})", null)
                },
            )
        }
    }
}

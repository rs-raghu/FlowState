package dev.flowstate.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.flowstate.BuildConfig
import dev.flowstate.data.*
import dev.flowstate.engine.*
import dev.flowstate.platform.Platform

@Composable
fun SettingChoice(
    label: String,
    value: String,
    choices: List<Pair<String, String>>,
    onChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Text(label, style = MaterialTheme.typography.titleSmall)
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                choices.firstOrNull { it.first == value }?.second ?: value,
                modifier = Modifier.weight(1f),
            )
            Text("▾")
        }
        DropdownMenu(expanded, { expanded = false }) {
            choices.forEach { (key, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onChange(key)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
fun PersonalSettings(vm: FlowViewModel, prefs: Preferences, section: String) {
    val saved by prefs.options.collectAsStateWithLifecycle(Preferences.defaults)
    var draft by remember(saved, section) { mutableStateOf(saved) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    if (section == "appearance" || section.startsWith("defaults-")) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (section.startsWith("defaults-"))
                Text(
                    "Used for new automations and locations. Your existing routines keep their current values."
                )
            val fields =
                when (section) {
                    "defaults-scheduling" ->
                        listOf("timezone" to "Timezone (device = follow your phone)")
                    "defaults-notifications" ->
                        listOf("expiration" to "Question expires after (seconds; 0 = never)")
                    "defaults-locations" -> listOf("radius" to "New location radius (metres)")
                    "defaults-execution" ->
                        listOf("cooldown" to "Minimum time between runs (seconds)")
                    else -> emptyList()
                }
            fields.forEach { (key, label) ->
                OutlinedTextField(
                    draft.getValue(key),
                    { draft = draft + (key to it) },
                    label = { Text(label) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            when (section) {
                "appearance" -> {
                    SettingChoice(
                        "Time format",
                        draft.getValue("timeFormat"),
                        listOf("system" to "Follow phone", "12" to "12 hour", "24" to "24 hour"),
                    ) {
                        draft = draft + ("timeFormat" to it)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Use phone wallpaper colors", modifier = Modifier.weight(1f))
                        Switch(
                            draft["dynamicColor"] == "true",
                            { draft = draft + ("dynamicColor" to it.toString()) },
                        )
                    }
                }
                "defaults-scheduling" ->
                    SettingChoice(
                        "When a scheduled run was missed",
                        draft.getValue("catchUp"),
                        listOf(
                            "skip" to "Skip it",
                            "grace" to "Run if recently missed",
                            "window" to "Run within its time window",
                            "record" to "Record only",
                            "ask" to "Ask me first",
                        ),
                    ) {
                        draft = draft + ("catchUp" to it)
                    }
                "defaults-notifications" ->
                    SettingChoice(
                        "Notification priority",
                        draft.getValue("channel"),
                        listOf("normal" to "Normal", "low" to "Quiet", "high" to "High"),
                    ) {
                        draft = draft + ("channel" to it)
                    }
                "defaults-execution" ->
                    SettingChoice(
                        "If the same automation is already running",
                        draft.getValue("concurrency"),
                        listOf(
                            "parallel" to "Run alongside it",
                            "ignore" to "Skip the new run",
                            "queue" to "Wait for the earlier run",
                            "replace" to "Replace the earlier run",
                        ),
                    ) {
                        draft = draft + ("concurrency" to it)
                    }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    try {
                        Preferences.validate(draft)
                        error = null
                        vm.work {
                            prefs.restore(draft)
                            vm.reconcile()
                            vm.message.value = "Settings saved"
                        }
                    } catch (e: Exception) {
                        error = e.message ?: "Check the values above"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save settings")
            }
        }
    }
    if (section == "data") {
        Text("Export a backup before deleting data. These actions ask for confirmation.")
        OutlinedTextField(
            draft.getValue("retention"),
            { draft = draft + ("retention" to it) },
            label = { Text("Keep history for (days, 1–3650)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                vm.work {
                    prefs.restore(mapOf("retention" to draft.getValue("retention")))
                    vm.message.value = "History preference saved"
                }
            }
        ) {
            Text("Save history preference")
        }
        listOf("Clear execution history", "Reset persistent variables", "Delete all app data")
            .forEach { action ->
                OutlinedButton(onClick = { confirm = action }, modifier = Modifier.fillMaxWidth()) {
                    Text(action)
                }
            }
    }
    if (section == "diagnostics") {
        Button(
            onClick = {
                vm.work {
                    val report = vm.application.coordinator.diagnosticReport()
                    context
                        .getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(ClipData.newPlainText("FlowState diagnostics", report))
                    vm.message.value = "Diagnostic report copied"
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Copy diagnostic report")
        }
    }
    if (section == "tests") {
        Text(
            "Try these before testing a location routine. Results appear in Activity and your notifications."
        )
        Button(
            onClick = {
                vm.work {
                    val delivered =
                        Platform(context)
                            .notification(
                                "diagnostic",
                                Effect(
                                    "test",
                                    "message",
                                    "FlowState test",
                                    "Notifications are working",
                                    notification = ReminderConfig(expireSeconds = 30),
                                ),
                            )
                    vm.message.value =
                        if (delivered) "Test notification posted"
                        else "Allow notifications in Settings → Permission health"
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Test notification")
        }
        OutlinedButton(
            onClick = {
                vm.work {
                    val id = java.util.UUID.randomUUID().toString()
                    vm.application.coordinator.save(
                        id,
                        "Manual test workflow",
                        FlowViewModel.template("Blank message", defaults = saved),
                    )
                    vm.application.coordinator.start(id)
                    vm.message.value = "Test workflow added and run. Find it in Automations."
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Test workflow")
        }
    }
    if (section == "about") {
        Text("FlowState ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleLarge)
        Text(
            "Create an automation, choose a preset, then tap a block in the block library. Edit its fields and Save. Use Run in Automations to try it before enabling automatic triggers."
        )
        Text(
            "Questions and checklists stay in Activity until answered, cancelled or expired. Dismissing a notification keeps its question in Activity."
        )
        Text(
            "Save your actual location, choose a radius and allow precise/background location for geofences. Android can delay location events and scheduled actions. Reopen after force-stop to restore delivery."
        )
        Text(
            "Your workflows, locations and history stay on this device. No account, backend, Internet permission or telemetry. JSON backups contain personal data."
        )
        OutlinedButton(
            onClick = {
                uriHandler.openUri(
                    "https://github.com/rs-raghu/FlowState/blob/main/docs/USER_GUIDE.md"
                )
            }
        ) {
            Text("Open full guide")
        }
        Text(
            "Notices: AndroidX, Kotlin, Hilt/Dagger, Blockly and Natural Earth. Blockly's license and map notices are bundled. Google Play Services is provided by Google.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    confirm?.let { action ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("$action?") },
            text = {
                Text(
                    when (action) {
                        "Clear execution history" ->
                            "Remove finished runs and diagnostics. Active runs and trigger deduplication records are retained."
                        "Reset persistent variables" ->
                            "Reset stored values. Cancel active runs first because they may depend on these values."
                        else ->
                            "Delete workflows, locations, templates, values, history and preferences. Pending work is cancelled. This cannot be undone; export a backup first."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirm = null
                        vm.work {
                            when (action) {
                                "Clear execution history" ->
                                    vm.application.coordinator.clearHistory()
                                "Reset persistent variables" ->
                                    vm.application.coordinator.resetVariables()
                                else -> vm.application.coordinator.resetAll()
                            }
                            vm.message.value = "$action completed"
                        }
                    }
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

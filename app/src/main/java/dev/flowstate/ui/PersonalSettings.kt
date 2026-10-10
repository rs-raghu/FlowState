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
fun PersonalSettings(vm: FlowViewModel, prefs: Preferences) {
    val saved by prefs.options.collectAsStateWithLifecycle(Preferences.defaults)
    var expanded by remember { mutableStateOf(false) }
    var draft by remember(saved) { mutableStateOf(saved) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { expanded = !expanded }) { Text("Personal defaults") }
            Text(
                "Defaults apply to new blocks and locations. Existing workflows keep their settings."
            )
            if (expanded) {
                for ((key, label) in
                    listOf(
                        "timezone" to "Default timezone (device or IANA zone)",
                        "cooldown" to "Automation cooldown seconds",
                        "expiration" to "Question expiry seconds (0 = never)",
                        "retention" to "History days (1–3650)",
                        "radius" to "New location radius metres",
                    )) {
                    OutlinedTextField(
                        draft.getValue(key),
                        { draft = draft + (key to it) },
                        label = { Text(label) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                for ((key, choices) in
                    mapOf(
                        "timeFormat" to listOf("system", "12", "24"),
                        "dynamicColor" to listOf("true", "false"),
                        "concurrency" to listOf("parallel", "ignore", "queue", "replace"),
                        "catchUp" to listOf("skip", "grace", "window", "record", "ask"),
                        "channel" to listOf("normal", "low", "high"),
                    )) {
                    Text(
                        when (key) {
                            "timeFormat" -> "Time format"
                            "dynamicColor" -> "Dynamic colors"
                            "catchUp" -> "Missed execution policy"
                            else -> key.replaceFirstChar(Char::uppercase)
                        }
                    )
                    for (choice in choices) FilterChip(
                        selected = draft[key] == choice,
                        onClick = { draft = draft + (key to choice) },
                        label = { Text(choice) },
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                TextButton(
                    onClick = {
                        try {
                            Preferences.validate(draft)
                            error = null
                            vm.work {
                                prefs.restore(draft)
                                vm.reconcile()
                                vm.message.value = "Defaults saved"
                            }
                        } catch (e: Exception) {
                            error = e.message
                        }
                    }
                ) {
                    Text("Save defaults")
                }
            }
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Data controls", style = MaterialTheme.typography.titleLarge)
            for (action in
                listOf(
                    "Clear execution history",
                    "Reset persistent variables",
                    "Delete all app data",
                )) TextButton(onClick = { confirm = action }) { Text(action) }
        }
    }
    TextButton(
        onClick = {
            vm.work {
                val report = vm.application.coordinator.diagnosticReport()
                context
                    .getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("FlowState diagnostics", report))
                vm.message.value = "Diagnostic report copied"
            }
        }
    ) {
        Text("Copy diagnostic report")
    }
    TextButton(
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
                    else "Notifications are unavailable; allow them in Permission health"
            }
        }
    ) {
        Text("Test notification")
    }
    TextButton(
        onClick = {
            vm.work {
                val id = java.util.UUID.randomUUID().toString()
                vm.application.coordinator.save(
                    id,
                    "Manual test workflow",
                    FlowViewModel.template("Blank message", defaults = saved),
                )
                vm.application.coordinator.start(id)
                vm.message.value = "Test workflow added and run. Edit or delete it in Automations."
            }
        }
    ) {
        Text("Test workflow")
    }
    Text("FlowState ${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE}")
    Text(
        "Privacy: workflows, locations and activity stay on this device. No account, backend, Internet permission or telemetry. JSON backups contain your personal data; store them somewhere you trust."
    )
    Text(
        "Notices: AndroidX, Kotlin, kotlinx.serialization, Hilt/Dagger, Blockly and Natural Earth assets. Blockly's Apache 2.0 notice is bundled with the editor; repository docs include dependency and map notices. Google Play Services is provided by Google."
    )
    confirm?.let { action ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(action + "?") },
            text = {
                Text(
                    when (action) {
                        "Clear execution history" ->
                            "Remove terminal runs and diagnostics. Active runs and trigger deduplication records are retained."
                        "Reset persistent variables" ->
                            "Reset all stored workflow/global values. Cancel active runs first; they may depend on these values."
                        else ->
                            "Delete workflows, locations, templates, variables, history and preferences. Pending work and notifications will be cancelled. This cannot be undone; export a backup first."
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
                            vm.message.value = action + " completed"
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

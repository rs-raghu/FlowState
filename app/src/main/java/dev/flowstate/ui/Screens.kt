package dev.flowstate.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource
import dev.flowstate.data.*
import dev.flowstate.engine.*
import dev.flowstate.platform.Platform
import java.time.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.decodeFromString

private fun date(at: Long) =
    if (at == 0L) "Never"
    else
        Instant.ofEpochMilli(at)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
            .toString()
            .replace('T', ' ')

@Composable
private fun Title(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium)
    Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Content(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
fun FlowState(vm: FlowViewModel, initialExecution: String?) {
    val context = LocalContext.current
    val prefs = remember { Preferences(context) }
    val theme by prefs.theme.collectAsStateWithLifecycle("system")
    val onboarded by prefs.onboarded.collectAsStateWithLifecycle(true)
    val dark = theme == "dark" || theme == "system" && isSystemInDarkTheme()
    val scheme =
        if (dark) darkColorScheme(primary = Color(0xFFACE0B5))
        else lightColorScheme(primary = Color(0xFF366A47), surface = Color(0xFFF7FAF5))
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val automations by vm.automations.collectAsStateWithLifecycle()
    val executions by vm.executions.collectAsStateWithLifecycle()
    val locations by vm.locations.collectAsStateWithLifecycle()
    val diagnostics by vm.diagnostics.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var create by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(initialExecution) }
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            snack.showSnackbar(it)
            vm.message.value = null
        }
    }
    LaunchedEffect(initialExecution) { if (initialExecution != null) nav.navigate("activity") }
    MaterialTheme(colorScheme = scheme) {
        Scaffold(
            snackbarHost = { SnackbarHost(snack) },
            bottomBar = {
                if (route?.startsWith("editor") != true)
                    NavigationBar {
                        listOf(
                                "dashboard" to "⌂",
                                "automations" to "⚡",
                                "locations" to "◎",
                                "activity" to "◷",
                                "settings" to "⚙",
                            )
                            .forEach { (name, icon) ->
                                NavigationBarItem(
                                    selected = route == name,
                                    onClick = {
                                        nav.navigate(name) {
                                            popUpTo("dashboard") { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Text(icon) },
                                    label = {
                                        Text(
                                            name.replaceFirstChar(Char::uppercase),
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    },
                                )
                            }
                    }
            },
        ) { padding ->
            Surface(Modifier.padding(padding).fillMaxSize()) {
                NavHost(nav, startDestination = "dashboard") {
                    composable("dashboard") {
                        Content {
                            Title("FlowState", "Build routines that follow your decisions.")
                            Panel {
                                Text(
                                    "${automations.count { it.enabled }} active · ${automations.count { !it.enabled }} disabled",
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                Text(
                                    "Next scheduled: ${automations.mapNotNull { it.nextAt }.minOrNull()?.let(::date) ?: "None"}"
                                )
                                Text(
                                    "${executions.count { it.state=="WAITING_FOR_USER" }} pending interactions"
                                )
                            }
                            Button(
                                onClick = { create = true },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Create automation")
                            }
                            if (!Platform(context).notificationsAllowed())
                                Panel {
                                    Text("Notifications unavailable. Questions remain in Activity.")
                                    TextButton(onClick = { nav.navigate("settings") }) {
                                        Text("Permission health")
                                    }
                                }
                            if (automations.isEmpty())
                                Text("Create an automation, then connect blocks below its trigger.")
                            automations.take(4).forEach { a ->
                                Panel {
                                    Text(a.name, style = MaterialTheme.typography.titleMedium)
                                    Text(a.status)
                                    Row {
                                        TextButton(onClick = { vm.start(a) }) { Text("Run now") }
                                        TextButton(onClick = { nav.navigate("editor/${a.id}") }) {
                                            Text("Edit blocks")
                                        }
                                    }
                                }
                            }
                            Text("Recent activity", style = MaterialTheme.typography.titleMedium)
                            executions.take(4).forEach { row ->
                                TextButton(
                                    onClick = {
                                        expanded = row.id
                                        nav.navigate("activity")
                                    }
                                ) {
                                    Text(
                                        "${automations.find { it.id==row.automationId }?.name ?: "Workflow"} · ${row.state}"
                                    )
                                }
                            }
                            diagnostics.take(3).forEach {
                                Text(it.message, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    composable("automations") {
                        Automations(
                            vm,
                            automations,
                            executions,
                            { create = true },
                            { nav.navigate("editor/$it") },
                        )
                    }
                    composable("locations") { Locations(vm, locations) }
                    composable("activity") {
                        Activity(vm, executions, automations, expanded, { expanded = it })
                    }
                    composable("settings") { SettingsScreen(vm, prefs, theme, diagnostics) }
                    composable("editor/{id}") { back ->
                        val a = automations.find { it.id == back.arguments?.getString("id") }
                        if (a != null) Editor(vm, a) { nav.popBackStack() }
                        else Content { Text("Loading automation…") }
                    }
                }
            }
        }
        if (!onboarded)
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Welcome to FlowState") },
                text = {
                    Text(
                        "Build workflows with blocks and run them offline. Automatic location monitoring needs precise and background location. Android can delay alarms and geofences. Questions stay in Activity when notifications are denied. Set up permissions individually in Settings."
                    )
                },
                confirmButton = {
                    TextButton(onClick = { scope.launch { prefs.onboard() } }) {
                        Text("Get started")
                    }
                },
            )
        if (create)
            CreateDialog({ create = false }) { name, template ->
                vm.create(name, template) {
                    create = false
                    nav.navigate("editor/$it")
                }
            }
    }
}

@Composable
private fun CreateDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("New automation") }
    var template by rememberSaveable { mutableStateOf(FlowViewModel.templates.first()) }
    var expanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create automation") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Name") })
                TextButton(onClick = { expanded = true }) { Text("Template: $template") }
                DropdownMenu(expanded, { expanded = false }) {
                    FlowViewModel.templates.forEach { t ->
                        DropdownMenuItem(
                            text = { Text(t) },
                            onClick = {
                                template = t
                                expanded = false
                            },
                        )
                    }
                }
                Text("Configure a time or location trigger in the editor.")
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name, template) }, enabled = name.isNotBlank()) {
                Text("Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Automations(
    vm: FlowViewModel,
    items: List<AutomationEntity>,
    executions: List<ExecutionEntity>,
    onCreate: () -> Unit,
    onEdit: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("All") }
    var sort by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<AutomationEntity?>(null) }
    var renaming by remember { mutableStateOf<AutomationEntity?>(null) }
    var name by remember { mutableStateOf("") }
    Content {
        Title("Automations", "Your logic, saved on this device.")
        Button(onClick = onCreate) { Text("New automation") }
        OutlinedTextField(
            query,
            { query = it },
            label = { Text("Search automations") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row {
            listOf("All", "Enabled", "Disabled").forEach { f ->
                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f) })
            }
        }
        TextButton(onClick = { sort = !sort }) {
            Text(if (sort) "Sort: name" else "Sort: recently edited")
        }
        val filtered =
            items
                .filter {
                    it.name.contains(query, true) &&
                        (filter == "All" || it.enabled == (filter == "Enabled"))
                }
                .let { if (sort) it.sortedBy { a -> a.name.lowercase() } else it }
        filtered.forEach { a ->
            Panel {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        a.name,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(a.enabled, { vm.enable(a, it) })
                }
                val d = remember(a.definition) { codec.decodeFromString<Definition>(a.definition) }
                Text("${d.trigger.kind} · v${a.version} · ${a.status}")
                Text(
                    "Last started: ${date(a.lastStarted)} · ${executions.count { it.automationId==a.id }} retained runs"
                )
                Row {
                    TextButton(onClick = { onEdit(a.id) }) { Text("Edit") }
                    TextButton(onClick = { vm.start(a) }) { Text("Run") }
                    TextButton(onClick = { vm.duplicate(a) }) { Text("Copy") }
                }
                Row {
                    TextButton(
                        onClick = {
                            name = a.name
                            renaming = a
                        }
                    ) {
                        Text("Rename")
                    }
                    TextButton(onClick = { deleting = a }) { Text("Delete") }
                }
            }
        }
        if (filtered.isEmpty()) Text("No matching automations.")
    }
    deleting?.let { a ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${a.name}?") },
            text = {
                Text(
                    "Executions will be cancelled and removed. Referenced workflows are protected."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.delete(a)
                        deleting = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
    renaming?.let { a ->
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename automation") },
            text = { OutlinedTextField(name, { name = it }) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.rename(a, name)
                        renaming = null
                    }
                ) {
                    Text("Save")
                }
            },
        )
    }
}

@Composable
private fun Locations(vm: FlowViewModel, locations: List<LocationEntity>) {
    var adding by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<LocationEntity?>(null) }
    var deleting by remember { mutableStateOf<LocationEntity?>(null) }
    Content {
        Title("Locations", "Geofences use at least a 100 m radius.")
        Text(
            "Detection can be delayed by minutes. New occupancy is unknown; reported state becomes stale after one hour."
        )
        Button(
            onClick = {
                selected = null
                adding = true
            }
        ) {
            Text("Add location")
        }
        locations.forEach { l ->
            Panel {
                Text(l.name, style = MaterialTheme.typography.titleLarge)
                Text("${l.latitude}, ${l.longitude} · ${l.radius.toInt()} m")
                SelectionContainer {
                    Text("ID: ${l.id}", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "${l.registration} · ${if(System.currentTimeMillis()-l.eventAt>3600000) "UNKNOWN (stale)" else l.occupancy}"
                )
                Row {
                    TextButton(
                        onClick = {
                            selected = l
                            adding = true
                        }
                    ) {
                        Text("Edit")
                    }
                    TextButton(onClick = { deleting = l }) { Text("Delete") }
                }
            }
        }
        if (locations.isEmpty())
            Text("Add a location and copy its ID into the trigger's Location field.")
    }
    if (adding) LocationDialog(vm, selected) { adding = false }
    deleting?.let { l ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete location?") },
            text = { Text("Locations used by automations cannot be deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteLocation(l)
                        deleting = null
                    }
                ) {
                    Text("Delete")
                }
            },
        )
    }
}

@Composable
private fun LocationDialog(vm: FlowViewModel, l: LocationEntity?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(l?.name ?: "") }
    var lat by remember { mutableStateOf(l?.latitude?.toString() ?: "") }
    var lon by remember { mutableStateOf(l?.longitude?.toString() ?: "") }
    var radius by remember { mutableStateOf(l?.radius?.toInt()?.toString() ?: "150") }
    var description by remember { mutableStateOf(l?.description ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(l?.enabled ?: true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (l == null) "Save location" else "Edit location") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") })
                OutlinedTextField(lat, { lat = it }, label = { Text("Latitude") })
                OutlinedTextField(lon, { lon = it }, label = { Text("Longitude") })
                OutlinedTextField(
                    radius,
                    { radius = it },
                    label = { Text("Radius metres (100–100000)") },
                )
                LocationMap(
                    lat.toDoubleOrNull() ?: 20.0,
                    lon.toDoubleOrNull() ?: 78.0,
                    radius.toFloatOrNull() ?: 150f,
                ) { latitude, longitude ->
                    lat = latitude.toString()
                    lon = longitude.toString()
                }
                Row {
                    listOf(100, 150, 300).forEach { r ->
                        TextButton(onClick = { radius = r.toString() }) { Text("$r m") }
                    }
                }
                TextButton(
                    onClick = {
                        scope.launch {
                            try {
                                require(Platform(context).precise()) {
                                    "Grant precise location in Settings first"
                                }
                                val pos =
                                    LocationServices.getFusedLocationProviderClient(context)
                                        .getCurrentLocation(
                                            Priority.PRIORITY_HIGH_ACCURACY,
                                            CancellationTokenSource().token,
                                        )
                                        .await()
                                        ?: error("Position unavailable; enter coordinates manually")
                                lat = pos.latitude.toString()
                                lon = pos.longitude.toString()
                            } catch (e: SecurityException) {
                                error =
                                    "Location permission was revoked; grant it again in Settings"
                            } catch (e: Exception) {
                                error = e.message
                            }
                        }
                    }
                ) {
                    Text("Use device position")
                }
                OutlinedTextField(
                    description,
                    { description = it },
                    label = { Text("Description") },
                )
                Row {
                    Checkbox(enabled, { enabled = it })
                    Text("Location enabled")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    try {
                        val latitude = lat.toDouble()
                        val longitude = lon.toDouble()
                        val r = radius.toFloat()
                        require(
                            name.isNotBlank() &&
                                latitude in -90.0..90.0 &&
                                longitude in -180.0..180.0 &&
                                r in 100f..100000f
                        )
                        vm.saveLocation(l?.id, name, latitude, longitude, r, description, enabled)
                        onDismiss()
                    } catch (e: Exception) {
                        error = "Enter a name, valid coordinates and radius ≥100 m"
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Activity(
    vm: FlowViewModel,
    rows: List<ExecutionEntity>,
    automations: List<AutomationEntity>,
    expanded: String?,
    onExpand: (String?) -> Unit,
) {
    var simulation by remember { mutableStateOf<Definition?>(null) }
    var simulationLibrary by remember { mutableStateOf<List<Definition>>(emptyList()) }
    Content {
        Title("Activity", "Respond to questions and inspect execution history.")
        rows.forEach { row ->
            Panel {
                val e = remember(row.snapshot) { codec.decodeFromString<Execution>(row.snapshot) }
                Text(
                    automations.find { it.id == row.automationId }?.name ?: "Workflow",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text("${row.state} · definition v${e.definition.version}")
                Text(date(row.updated))
                e.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                e.interaction?.let { InteractionPanel(vm, row.id, it) }
                e.wakeAt?.let { Text("Scheduled wake: ${date(it)}") }
                Row {
                    TextButton(onClick = { onExpand(if (expanded == row.id) null else row.id) }) {
                        Text("Trace")
                    }
                    TextButton(
                        onClick = {
                            simulation = e.definition
                            simulationLibrary = e.library.values.toList()
                        }
                    ) {
                        Text("Debug")
                    }
                    if (e.state !in Runtime.terminal)
                        TextButton(onClick = { vm.cancel(row.id) }) { Text("Cancel run") }
                }
                if (expanded == row.id) {
                    Text("Local values: ${e.locals.mapValues {it.value.display()}}")
                    e.trace.takeLast(40).forEach {
                        Text("${it.node}: ${it.detail}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (rows.isEmpty()) Text("No executions yet. Run an automation manually to test it.")
        simulation?.let { d ->
            Simulator(d, simulationLibrary)
        }
    }
}

@Composable
private fun InteractionPanel(vm: FlowViewModel, id: String, i: Interaction) {
    var input by remember(i.token) { mutableStateOf("") }
    var checked by remember(i.token) { mutableStateOf(i.completed.toSet()) }
    Text(i.title, style = MaterialTheme.typography.titleMedium)
    i.deadline?.let { Text("Respond before ${date(it)}") }
    when (i.kind) {
        "text",
        "number" -> {
            OutlinedTextField(
                input,
                { input = it },
                label = { Text(if (i.kind == "number") "Number" else "Your answer") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { vm.respond(id, i.token, input) }) { Text("Submit") }
        }
        "checklist" -> {
            i.options.forEachIndexed { index, label ->
                Row {
                    Checkbox(
                        index in checked,
                        {
                            checked = if (it) checked + index else checked - index
                            vm.checklist(id, i.token, checked)
                        },
                    )
                    Text(
                        label.removePrefix("?") + (if (index !in i.required) " (optional)" else "")
                    )
                }
            }
            Button(
                enabled = i.required.all { it in checked },
                onClick = { vm.respond(id, i.token, checked.joinToString(",")) },
            ) {
                Text("Complete checklist")
            }
        }
        else ->
            i.options.forEach { label ->
                OutlinedButton(
                    onClick = { vm.respond(id, i.token, label) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(label)
                }
            }
    }
    Row {
        TextButton(onClick = { vm.respond(id, i.token, "__cancel") }) { Text("Cancel") }
        TextButton(enabled = i.snoozes < 3, onClick = { vm.snooze(id, 5) }) { Text("Snooze 5 min") }
        TextButton(enabled = i.snoozes < 3, onClick = { vm.snooze(id, 15) }) { Text("15 min") }
    }
}

@Composable
private fun SettingsScreen(
    vm: FlowViewModel,
    prefs: Preferences,
    theme: String,
    diagnostics: List<DiagnosticEntity>,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val platform = remember { Platform(context) }
    var healthVersion by remember { mutableIntStateOf(0) }
    var backgroundExplanation by remember { mutableStateOf(false) }
    val permissions =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            healthVersion++
            vm.reconcile()
        }
    val export =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            if (uri != null)
                vm.work {
                    val source = Backups(vm.application.database).export()
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(source.toByteArray(Charsets.UTF_8))
                    } ?: error("Cannot open export")
                    vm.message.value = "Backup exported"
                }
        }
    val import =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null)
                vm.work {
                    val source =
                        context.contentResolver.openInputStream(uri)?.use {
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            var read = it.read(buffer)
                            while (read != -1) {
                                require(output.size() + read <= 2_000_000) { "Backup exceeds 2 MB" }
                                output.write(buffer, 0, read)
                                read = it.read(buffer)
                            }
                            output.toString("UTF-8")
                        } ?: error("Cannot read backup")
                    val count = vm.application.coordinator.importBackup(source)
                    vm.message.value = "Imported $count disabled automations"
                }
        }
    Content {
        Title("Settings", "Permissions, backup and local diagnostics.")
        Text("Theme", style = MaterialTheme.typography.titleMedium)
        Row {
            listOf("system", "light", "dark").forEach { t ->
                FilterChip(
                    selected = theme == t,
                    onClick = { scope.launch { prefs.theme(t) } },
                    label = { Text(t) },
                )
            }
        }
        Panel {
            Text("Permission health", style = MaterialTheme.typography.titleLarge)
            key(healthVersion) {
                Text(
                    "Notifications: ${platform.notificationsAllowed()}\nPrecise location: ${platform.precise()}\nBackground location: ${platform.background()}\nLocation services: ${platform.locationEnabled()}\nGoogle Play Services: ${platform.playServices()}\nExact alarms: ${platform.exact()}"
                )
            }
            Text(
                "Android power restrictions and workers can delay actions. Inexact alarm fallback is automatic."
            )
            if (Build.VERSION.SDK_INT >= 33)
                TextButton(
                    onClick = {
                        permissions.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                    }
                ) {
                    Text("Allow notifications")
                }
            TextButton(
                onClick = {
                    permissions.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        )
                    )
                }
            ) {
                Text("Allow foreground location")
            }
            TextButton(onClick = { backgroundExplanation = true }) {
                Text("Background location setup")
            }
            if (Build.VERSION.SDK_INT >= 31)
                TextButton(
                    onClick = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                Uri.parse("package:${context.packageName}"),
                            )
                        )
                    }
                ) {
                    Text("Exact alarm special access")
                }
            TextButton(
                onClick = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}"),
                        )
                    )
                }
            ) {
                Text("App and battery settings")
            }
            TextButton(
                onClick = {
                    healthVersion++
                    vm.reconcile()
                }
            ) {
                Text("Reconcile and refresh")
            }
        }
        Panel {
            Text("Local backup", style = MaterialTheme.typography.titleLarge)
            Text(
                "Export workspaces and saved locations. History and variables stay on this installation. Validated imports start disabled; duplicate IDs are rejected."
            )
            Row {
                TextButton(onClick = { export.launch("FlowState-backup.json") }) { Text("Export") }
                TextButton(onClick = { import.launch(arrayOf("application/json", "text/plain")) }) {
                    Text("Import")
                }
            }
        }
        Text("Diagnostics", style = MaterialTheme.typography.titleMedium)
        diagnostics.forEach {
            Text("${date(it.at)}\n${it.message}", style = MaterialTheme.typography.bodySmall)
        }
        Text(
            "No account, backend or telemetry. Force-stop blocks delivery until reopening. Geofence accuracy and latency depend on the device."
        )
    }
    if (backgroundExplanation)
        AlertDialog(
            onDismissRequest = { backgroundExplanation = false },
            title = { Text("Background location") },
            text = {
                Text(
                    "Grant precise foreground location first. For geofences while closed, choose 'Allow all the time'. On Android 11+, use the app's permission settings."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        backgroundExplanation = false
                        if (Build.VERSION.SDK_INT == 29)
                            permissions.launch(
                                arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            )
                        else
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}"),
                                )
                            )
                    }
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { backgroundExplanation = false }) { Text("Cancel") }
            },
        )
}

package dev.flowstate.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.flowstate.engine.*
import java.time.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Simulator(
    d: Definition,
    definitions: List<Definition>,
    onHighlight: (String) -> Unit = {},
    initial: Execution? = null,
    locationModels: Map<String, LocationState> = emptyMap(),
) {
    val simulation =
        remember(d, initial?.id) {
            Simulation(
                d,
                definitions,
                initial?.started ?: System.currentTimeMillis(),
                ZoneId.systemDefault().id,
                initial,
                locationModels,
            )
        }
    var revision by remember { mutableIntStateOf(0) }
    var clock by remember { mutableStateOf(Instant.ofEpochMilli(simulation.now).toString()) }
    var zone by remember { mutableStateOf(simulation.zone) }
    var error by remember { mutableStateOf<String?>(null) }
    var location by remember { mutableStateOf(d.trigger.locationId) }
    var latitude by remember {
        mutableStateOf((locationModels[d.trigger.locationId]?.latitude ?: 0.0).toString())
    }
    var longitude by remember {
        mutableStateOf((locationModels[d.trigger.locationId]?.longitude ?: 0.0).toString())
    }
    var radius by remember {
        mutableStateOf((locationModels[d.trigger.locationId]?.radius ?: 150.0).toString())
    }
    var variable by remember { mutableStateOf("") }
    var variableValue by remember { mutableStateOf("") }
    var variableScope by remember { mutableStateOf(Scope.LOCAL) }
    var response by remember { mutableStateOf("") }
    var breakpoints by remember { mutableStateOf("") }
    var events by remember { mutableStateOf("") }
    fun action(block: () -> Unit) {
        try {
            error = null
            simulation.now = Instant.parse(clock).toEpochMilli()
            ZoneId.of(zone)
            simulation.zone = zone
            simulation.breakpoints =
                breakpoints.split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet()
            block()
            clock = Instant.ofEpochMilli(simulation.now).toString()
            zone = simulation.zone
            onHighlight(
                simulation.execution.cursor ?: simulation.execution.trace.lastOrNull()?.node ?: ""
            )
        } catch (e: Exception) {
            error = e.message
        }
        revision++
    }
    val e = remember(revision) { simulation.execution }
    Column(
        Modifier.fillMaxWidth()
            .heightIn(max = 680.dp)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Workflow simulator", style = MaterialTheme.typography.headlineSmall)
        Text(
            "All clocks, events, effects and variable writes remain in memory. ${if(initial!=null) "Opened the captured execution snapshot." else "Started a fresh simulation."}"
        )
        OutlinedTextField(
            clock,
            { clock = it },
            label = { Text("Instant (ISO 8601)") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            zone,
            { zone = it },
            label = { Text("Timezone") },
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow {
            TextButton(
                onClick = {
                    action {
                        simulation.resume()
                        simulation.step()
                    }
                }
            ) {
                Text("Step into")
            }
            TextButton(onClick = { action { simulation.stepOver() } }) { Text("Step over") }
            TextButton(onClick = { action { simulation.stepOut() } }) { Text("Step out") }
            TextButton(onClick = { action { simulation.run() } }) { Text("Run / continue") }
            TextButton(onClick = { action { simulation.pause() } }) { Text("Pause") }
            TextButton(onClick = { action { simulation.stop() } }) { Text("Stop") }
            TextButton(onClick = { action { simulation.restart() } }) { Text("Restart") }
        }
        OutlinedTextField(
            breakpoints,
            { breakpoints = it },
            label = { Text("Breakpoint node IDs, separated by commas") },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "${e.state} · current ${e.cursor ?: "end"} · previous ${e.trace.lastOrNull()?.node ?: "none"} · ${e.steps} steps"
        )
        OutlinedTextField(
            location,
            { location = it },
            label = { Text("Simulated location ID") },
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow {
            listOf("enter", "exit", "dwell").forEach { kind ->
                TextButton(
                    onClick = { action { simulation.event(simulation.now, kind, location) } }
                ) {
                    Text(kind)
                }
            }
        }
        OutlinedTextField(latitude, { latitude = it }, label = { Text("Fake latitude") })
        OutlinedTextField(longitude, { longitude = it }, label = { Text("Fake longitude") })
        OutlinedTextField(radius, { radius = it }, label = { Text("Fake radius metres") })
        TextButton(
            onClick = {
                action {
                    simulation.location(
                        location,
                        latitude.toDouble(),
                        longitude.toDouble(),
                        radius.toDouble(),
                    )
                }
            }
        ) {
            Text("Set simulated coordinates")
        }
        Text("Location history: ${simulation.locations[location]}")
        Text("Occupancy: ${simulation.occupancy.mapValues {it.value.first}}")
        Text("Simulated permissions", style = MaterialTheme.typography.titleMedium)
        simulation.permissions.forEach { (name, allowed) ->
            Row {
                Checkbox(
                    allowed,
                    { action { simulation.permissions = simulation.permissions + (name to it) } },
                )
                Text(name)
            }
        }
        OutlinedTextField(
            variable,
            { variable = it },
            label = { Text("Declared variable name") },
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow {
            Scope.entries.forEach { scope ->
                FilterChip(
                    selected = variableScope == scope,
                    onClick = { variableScope = scope },
                    label = { Text(scope.name) },
                )
            }
        }
        OutlinedTextField(
            variableValue,
            { variableValue = it },
            label = { Text("Value; instant ISO / duration seconds / list |") },
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            onClick = { action { simulation.variable(variable, variableScope, variableValue) } }
        ) {
            Text("Set simulated value")
        }
        TextButton(
            onClick = {
                action {
                    val next = Scheduling.next(d.trigger, simulation.now, ZoneId.of(zone))
                    error =
                        "Next trigger: ${next?.let {Instant.ofEpochMilli(it.at).toString()+" · "+it.key} ?: "none"}"
                }
            }
        ) {
            Text("Inspect next trigger")
        }
        e.wakeAt?.let { wake ->
            Text("Wait until ${Instant.ofEpochMilli(wake)}")
            TextButton(
                onClick = {
                    clock = Instant.ofEpochMilli(wake).toString()
                    action { simulation.run() }
                }
            ) {
                Text("Advance to wake time")
            }
        }
        e.pendingInteractions().forEach { i ->
            Text(i.title, style = MaterialTheme.typography.titleMedium)
            Text("${i.kind}: ${i.options.joinToString(" | ")}")
            OutlinedTextField(
                response,
                { response = it },
                label = {
                    Text(if (i.kind == "checklist") "Checked item indices: 0,1" else "Response")
                },
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { action { simulation.respond(i.token, response) } }) {
                Text("Respond")
            }
        }
        OutlinedTextField(
            events,
            { events = it },
            label = {
                Text("Event stream: ISO instant|enter/exit/dwell/response/clock/notification|value")
            },
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            onClick = {
                action {
                    val lines = events.lines().filter { it.isNotBlank() }
                    require(lines.size <= 100 && events.length <= 16384)
                    lines.forEach {
                        val parts = it.split('|', limit = 3)
                        require(parts.size == 3)
                        simulation.event(
                            Instant.parse(parts[0].trim()).toEpochMilli(),
                            parts[1].trim(),
                            parts[2],
                        )
                    }
                }
            }
        ) {
            Text("Replay event stream")
        }
        Text(simulation.explanation)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        ExecutionInspector(e)
        Text("Persistent values: ${simulation.values.mapValues {it.value.display()}}")
        simulation.effects.forEach { effect ->
            Text(
                "${if(simulation.permissions["notifications"]==true) "Would display" else "Permission would suppress"} ${effect.kind}: ${effect.title} ${effect.body}"
            )
        }
        Text("Recorded simulation states", style = MaterialTheme.typography.titleMedium)
        simulation.history.takeLast(20).forEachIndexed { index, s ->
            val position = simulation.history.size - simulation.history.takeLast(20).size + index
            TextButton(onClick = { action { simulation.replay(position) } }) {
                Text(
                    "Replay ${s.execution.steps} steps · ${s.execution.state} · ${Instant.ofEpochMilli(s.now)}"
                )
            }
        }
    }
}

@Composable
fun ExecutionInspector(e: Execution) {
    Text("${e.definition.id} v${e.definition.version}: ${e.state}")
    Text("Locals: ${e.locals.mapValues {it.value.display()}}")
    e.trace.takeLast(30).forEach {
        Text("${it.node}: ${it.detail}", style = MaterialTheme.typography.bodySmall)
    }
    e.branches.forEachIndexed { index, child ->
        Text("Branch ${index+1}", style = MaterialTheme.typography.titleMedium)
        ExecutionInspector(child)
    }
}

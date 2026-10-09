package dev.flowstate.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.flowstate.engine.*
import dev.flowstate.engine.State
import java.time.*

@Composable
fun Simulator(d: Definition, definitions: List<Definition>, onHighlight: (String) -> Unit = {}) {
    val runtime = remember(d) { Runtime { id -> definitions.find { it.id == id } } }
    var clock by remember { mutableStateOf(Instant.now().toString()) }
    var zone by remember { mutableStateOf(ZoneId.systemDefault().id) }
    var e by
        remember(d) { mutableStateOf(runtime.start("simulation", d, Instant.now().toEpochMilli())) }
    var values by remember { mutableStateOf<Map<String, Value>>(emptyMap()) }
    var effects by remember { mutableStateOf<List<Effect>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var response by remember { mutableStateOf("") }
    var pausedState by remember { mutableStateOf<State?>(null) }
    var occupancy by remember { mutableStateOf<Map<String, Pair<String, Long>>>(emptyMap()) }
    var mockLocation by remember { mutableStateOf(d.trigger.locationId) }
    var mockValue by remember { mutableStateOf("") }
    var mockVariable by remember { mutableStateOf("") }
    var notificationAllowed by remember { mutableStateOf(true) }
    fun step(single: Boolean) {
        try {
            val now = Instant.parse(clock).toEpochMilli()
            if (e.state == State.PAUSED) {
                e = e.copy(state = pausedState ?: State.RUNNING)
                pausedState = null
            }
            val tick =
                runtime.tick(
                    e,
                    now,
                    zone,
                    values,
                    occupancy,
                    singleStep = single,
                    simulationBreakpoints = true,
                )
            e = tick.execution
            values = tick.persistent
            effects = (effects + tick.effects).takeLast(100)
            onHighlight(e.trace.lastOrNull()?.node ?: "")
        } catch (ex: Exception) {
            error = ex.message
        }
    }
    Column(
        Modifier.fillMaxWidth()
            .heightIn(max = 620.dp)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Workflow simulator", style = MaterialTheme.typography.headlineSmall)
        Text("Clock, effects and variable writes stay inside this simulation.")
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { step(true) }) { Text("Step") }
            Button(
                onClick = {
                    for (slice in 0 until 100) {
                        if (
                            slice > 0 &&
                                e.state !in setOf(State.CREATED, State.QUEUED, State.RUNNING)
                        )
                            break
                        step(false)
                    }
                }
            ) {
                Text("Run")
            }
            TextButton(
                onClick = {
                    e = runtime.start("simulation", d, Instant.parse(clock).toEpochMilli())
                    values = emptyMap()
                    effects = emptyList()
                    pausedState = null
                }
            ) {
                Text("Restart")
            }
        }
        Row {
            TextButton(
                onClick = {
                    if (e.state !in Runtime.terminal && e.state != State.PAUSED) {
                        pausedState = e.state
                        e = e.copy(state = State.PAUSED)
                    }
                }
            ) {
                Text("Pause")
            }
            TextButton(
                onClick = {
                    if (e.state == State.PAUSED) {
                        e = e.copy(state = pausedState ?: State.RUNNING)
                        pausedState = null
                    }
                }
            ) {
                Text("Continue")
            }
            TextButton(onClick = { e = e.copy(state = State.CANCELLED) }) { Text("Stop") }
        }
        Text("${e.state} · node ${e.cursor ?: "end"} · ${e.steps} steps")
        Text("Mock location")
        OutlinedTextField(
            mockLocation,
            { mockLocation = it },
            label = { Text("Saved location ID") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row {
            listOf("INSIDE", "OUTSIDE", "UNKNOWN").forEach { status ->
                TextButton(
                    onClick = {
                        try {
                            occupancy =
                                occupancy +
                                    (mockLocation to
                                        (status to Instant.parse(clock).toEpochMilli()))
                        } catch (ex: Exception) {
                            error = ex.message
                        }
                    }
                ) {
                    Text(status)
                }
            }
        }
        Text("Occupancy: ${occupancy.mapValues { it.value.first }}")
        Text("Mock variable (declared name)")
        OutlinedTextField(
            mockVariable,
            { mockVariable = it },
            label = { Text("Variable name") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            mockValue,
            { mockValue = it },
            label = { Text("Value; instant ISO / duration seconds / list |") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row {
            Scope.entries.forEach { scope ->
                TextButton(
                    onClick = {
                        try {
                            val declaration =
                                e.definition.variables.single {
                                    it.name == mockVariable && it.scope == scope
                                }
                            val value = Value.parse(declaration.type, mockValue)
                            if (scope == Scope.LOCAL)
                                e = e.copy(locals = e.locals + (mockVariable to value))
                            else
                                values =
                                    values +
                                        (Expressions.key(scope, mockVariable, e.definition.id) to
                                            value)
                        } catch (ex: Exception) {
                            error = ex.message
                        }
                    }
                ) {
                    Text(scope.name)
                }
            }
        }
        Row {
            Checkbox(notificationAllowed, { notificationAllowed = it })
            Text("Mock notification permission")
        }
        TextButton(
            onClick = {
                try {
                    val now = Instant.parse(clock).toEpochMilli()
                    val schedule = Scheduling.next(d.trigger, now, ZoneId.of(zone))
                    error =
                        if (schedule == null)
                            "No scheduled occurrence (manual/location trigger or ended schedule)"
                        else "Would schedule ${Instant.ofEpochMilli(schedule.at)} · ${schedule.key}"
                } catch (ex: Exception) {
                    error = ex.message
                }
            }
        ) {
            Text("Inspect next trigger")
        }
        e.wakeAt?.let {
            Text("Wait until ${Instant.ofEpochMilli(it)}")
            TextButton(
                onClick = {
                    clock = Instant.ofEpochMilli(it).toString()
                    step(false)
                }
            ) {
                Text("Advance to wake time")
            }
        }
        e.interaction?.let { i ->
            Text(i.title)
            Text("${i.kind}: ${i.options.joinToString(" | ")}")
            OutlinedTextField(
                response,
                { response = it },
                label = {
                    Text(if (i.kind == "checklist") "Checked item indices: 0,1,…" else "Response")
                },
            )
            Button(
                onClick = {
                    try {
                        e =
                            runtime.respond(
                                e,
                                i.token,
                                response,
                                Instant.parse(clock).toEpochMilli(),
                            )
                        step(false)
                    } catch (ex: Exception) {
                        error = ex.message
                    }
                }
            ) {
                Text("Respond")
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        e.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text(
            "Variables: ${e.locals.mapValues { it.value.display() }} · persistent: ${values.mapValues { it.value.display() }}"
        )
        effects.forEach {
            Text(
                "${if(notificationAllowed || it.kind == "cancel") "Would" else "Permission would block"} ${it.kind}: ${it.title} ${it.body}"
            )
        }
        e.trace.takeLast(30).forEach {
            Text("${it.node}: ${it.detail}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

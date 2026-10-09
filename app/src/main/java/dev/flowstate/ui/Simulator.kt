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
    fun step(single: Boolean) {
        try {
            val now = Instant.parse(clock).toEpochMilli()
            val tick = runtime.tick(e, now, zone, values, singleStep = single)
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
                    repeat(100) {
                        if (e.state !in Runtime.terminal && e.state != State.WAITING_FOR_USER)
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
                }
            ) {
                Text("Restart")
            }
        }
        Row {
            TextButton(onClick = { e = e.copy(state = State.PAUSED) }) { Text("Pause") }
            TextButton(
                onClick = { if (e.state == State.PAUSED) e = e.copy(state = State.RUNNING) }
            ) {
                Text("Continue")
            }
            TextButton(onClick = { e = e.copy(state = State.CANCELLED) }) { Text("Stop") }
        }
        Text("${e.state} · node ${e.cursor ?: "end"} · ${e.steps} steps")
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
        effects.forEach { Text("Would ${it.kind}: ${it.title} ${it.body}") }
        e.trace.takeLast(30).forEach {
            Text("${it.node}: ${it.detail}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

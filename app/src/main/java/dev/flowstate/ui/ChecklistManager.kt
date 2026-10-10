package dev.flowstate.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.flowstate.engine.*
import java.util.UUID
import kotlinx.serialization.decodeFromString

@Composable
fun ChecklistManager(vm: FlowViewModel) {
    val rows by vm.checklists.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<ChecklistTemplate?>(null) }
    Text("Reusable checklists", style = MaterialTheme.typography.titleLarge)
    Text("Each run gets its own captured items and completion state.")
    TextButton(
        onClick = {
            editing =
                ChecklistTemplate(
                    UUID.randomUUID().toString(),
                    "New checklist",
                    listOf(ChecklistItem("Keys")),
                )
        }
    ) {
        Text("Create checklist template")
    }
    rows.forEach { row ->
        Text(row.name, style = MaterialTheme.typography.titleMedium)
        Row {
            TextButton(onClick = { editing = codec.decodeFromString(row.payload) }) { Text("Edit") }
            TextButton(
                onClick = {
                    vm.work {
                        val original = codec.decodeFromString<ChecklistTemplate>(row.payload)
                        vm.application.coordinator.saveChecklist(
                            original.copy(
                                id = UUID.randomUUID().toString(),
                                name = original.name + " copy",
                            )
                        )
                    }
                }
            ) {
                Text("Duplicate")
            }
            TextButton(
                onClick = { vm.work { vm.application.coordinator.deleteChecklist(row.id) } }
            ) {
                Text("Delete")
            }
        }
    }
    editing?.let { template ->
        var name by remember(template.id) { mutableStateOf(template.name) }
        var items by remember(template.id) { mutableStateOf(template.items) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Checklist template") },
            text = {
                Column(
                    Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") })
                    items.forEachIndexed { index, item ->
                        fun change(value: ChecklistItem) {
                            items = items.toMutableList().apply { this[index] = value }
                        }
                        OutlinedTextField(
                            item.label,
                            { change(item.copy(label = it)) },
                            label = { Text("Item ${index+1}") },
                        )
                        OutlinedTextField(
                            item.note,
                            { change(item.copy(note = it)) },
                            label = { Text("Note") },
                        )
                        OutlinedTextField(
                            item.group,
                            { change(item.copy(group = it)) },
                            label = { Text("Group") },
                        )
                        Row {
                            Checkbox(item.required, { change(item.copy(required = it)) })
                            Text("Required")
                        }
                        Row {
                            TextButton(
                                enabled = index > 0,
                                onClick = {
                                    items =
                                        items.toMutableList().apply {
                                            add(index - 1, removeAt(index))
                                        }
                                },
                            ) {
                                Text("Up")
                            }
                            TextButton(
                                enabled = index < items.lastIndex,
                                onClick = {
                                    items =
                                        items.toMutableList().apply {
                                            add(index + 1, removeAt(index))
                                        }
                                },
                            ) {
                                Text("Down")
                            }
                            TextButton(
                                enabled = items.size > 1,
                                onClick = { items = items.filterIndexed { i, _ -> i != index } },
                            ) {
                                Text("Remove")
                            }
                        }
                    }
                    TextButton(
                        enabled = items.size < 100,
                        onClick = { items = items + ChecklistItem("Item ${items.size+1}") },
                    ) {
                        Text("Add item")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.work {
                            vm.application.coordinator.saveChecklist(
                                template.copy(name = name, items = items)
                            )
                            editing = null
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
}

package dev.flowstate.data

import androidx.room.withTransaction
import dev.flowstate.engine.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.*
import kotlinx.serialization.json.*

@Serializable data class BackupAutomation(val id: String, val name: String, val workspace: String)

@Serializable
data class BackupLocation(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radius: Float,
    val description: String = "",
)

@Serializable
data class Backup(
    val schema: Int = 2,
    val automations: List<BackupAutomation>,
    val locations: List<BackupLocation>,
    val templates: List<ChecklistTemplate> = emptyList(),
    val variables: List<VariableEntity> = emptyList(),
    val history: List<ExecutionEntity> = emptyList(),
    val events: List<EventEntity> = emptyList(),
    val settings: Map<String, String> = emptyMap(),
)

class Backups(private val db: FlowDatabase, private val preferences: Preferences? = null) {
    suspend fun export(): String = db.withTransaction {
        codec.encodeToString(
            Backup(
                templates =
                    db.dao().checklists().map {
                        codec.decodeFromString<ChecklistTemplate>(it.payload)
                    },
                variables = db.dao().variables(),
                history = db.dao().observeExecutions().first(),
                events = db.dao().events(),
                settings = preferences?.snapshot() ?: emptyMap(),
                automations =
                    db.dao().automations().map { BackupAutomation(it.id, it.name, it.workspace) },
                locations =
                    db.dao().locations().map {
                        BackupLocation(
                            it.id,
                            it.name,
                            it.latitude,
                            it.longitude,
                            it.radius,
                            it.description,
                        )
                    },
            )
        )
    }

    suspend fun import(source: String, mode: String = "new"): Int {
        require(mode in setOf("new", "merge", "overwrite"))
        require(source.length <= 16_000_000) { "Backup exceeds 16 MB" }
        var backup = codec.decodeFromString<Backup>(SafeInput.json(source))
        val remap = mutableMapOf<String, String>()
        if (mode == "merge") {
            val automations = db.dao().automations().associateBy { it.name }
            val locations = db.dao().locations().associateBy { it.name }
            val templates = db.dao().checklists().associateBy { it.name }
            backup.automations.forEach { a -> automations[a.name]?.let { remap[a.id] = it.id } }
            backup.locations.forEach { l -> locations[l.name]?.let { remap[l.id] = it.id } }
            backup.templates.forEach { t -> templates[t.name]?.let { remap[t.id] = it.id } }
            fun rewrite(e: JsonElement): JsonElement =
                when (e) {
                    is JsonObject -> JsonObject(e.mapValues { rewrite(it.value) })
                    is JsonArray -> JsonArray(e.map { rewrite(it) })
                    is JsonPrimitive ->
                        if (e.isString && e.content in remap)
                            JsonPrimitive(remap.getValue(e.content))
                        else e
                }
            backup =
                backup.copy(
                    automations =
                        backup.automations.map {
                            it.copy(
                                id = remap[it.id] ?: it.id,
                                workspace =
                                    rewrite(codec.parseToJsonElement(SafeInput.json(it.workspace)))
                                        .toString(),
                            )
                        },
                    locations = backup.locations.map { it.copy(id = remap[it.id] ?: it.id) },
                    templates = backup.templates.map { it.copy(id = remap[it.id] ?: it.id) },
                    variables =
                        backup.variables.map { it.copy(owner = remap[it.owner] ?: it.owner) },
                    history = emptyList(),
                    events = emptyList(),
                )
        }
        require(backup.schema in 1..2)
        require(
            backup.templates.size <= 500 &&
                backup.variables.size <= 10000 &&
                backup.history.size <= 200 &&
                backup.events.size <= 100000
        )
        require(backup.templates.map { it.id }.toSet().size == backup.templates.size)
        backup.templates.forEach { t ->
            java.util.UUID.fromString(t.id)
            require(t.name.isNotBlank() && t.name.length <= 120 && t.items.size in 1..100)
            require(
                t.items.map { it.label }.toSet().size == t.items.size &&
                    t.items.all {
                        it.label.isNotBlank() &&
                            it.label.length <= 120 &&
                            it.note.length <= 1024 &&
                            it.group.length <= 120
                    }
            )
        }
        require(backup.settings.keys.all { it in setOf("theme", "onboarded") })
        backup.settings["theme"]?.let { require(it in listOf("system", "light", "dark")) }
        backup.settings["onboarded"]?.toBooleanStrict()
        require(backup.automations.size <= 500 && backup.locations.size <= 500)
        require(backup.automations.map { it.id }.toSet().size == backup.automations.size)
        require(backup.locations.map { it.id }.toSet().size == backup.locations.size)
        val dao = db.dao()
        val now = System.currentTimeMillis()
        backup.locations.forEach {
            require(
                it.latitude.isFinite() &&
                    it.longitude.isFinite() &&
                    it.latitude in -90.0..90.0 &&
                    it.longitude in -180.0..180.0 &&
                    it.radius.isFinite() &&
                    it.radius in 100f..100000f &&
                    it.name.isNotBlank()
            )
            java.util.UUID.fromString(it.id)
        }
        val locations = (dao.locations().map { it.id } + backup.locations.map { it.id }).toSet()
        val workflows = (dao.automations().map { it.id } + backup.automations.map { it.id }).toSet()
        val compiled =
            backup.automations
                .filter { mode != "merge" || dao.automation(it.id) == null }
                .map { a ->
                    java.util.UUID.fromString(a.id)
                    require(a.name.isNotBlank() && a.name.length <= 120)
                    require(mode != "new" || dao.automation(a.id) == null) {
                        "Automation ID already exists; import into a clean database"
                    }
                    a to
                        Compiler.compile(
                            a.workspace,
                            a.id,
                            (dao.automation(a.id)?.version ?: 0) + 1,
                            locations,
                            workflows,
                            (dao.checklists().associate {
                                it.id to codec.decodeFromString<ChecklistTemplate>(it.payload)
                            } + backup.templates.associateBy { it.id }),
                        )
                }
        val definitions = compiled.associate { it.first.id to it.second }
        val existing =
            dao.automations()
                .filter { it.id !in definitions }
                .map { codec.decodeFromString<Definition>(it.definition) }
        val callIssues = Compiler.validateCalls(existing + definitions.values)
        if (callIssues.isNotEmpty()) throw ValidationException(callIssues)
        val active =
            dao.active().flatMap { row ->
                val execution = codec.decodeFromString<Execution>(row.snapshot)
                execution.capturedDefinitions()
            }
        val sharedIssues = Compiler.validateSharedVariables(existing + active + definitions.values)
        if (sharedIssues.isNotEmpty()) throw ValidationException(sharedIssues)
        val importedVariables =
            backup.variables.filter {
                mode != "merge" ||
                    dao.variables().none { old -> old.owner == it.owner && old.name == it.name }
            }
        importedVariables.forEach {
            require(it.owner == "global" || it.owner in workflows)
            require(it.name.matches(Regex("[A-Za-z_][A-Za-z0-9_]{0,63}")))
            val value = codec.decodeFromString<Value>(SafeInput.json(it.value))
            validateValue(value)
        }
        val stored =
            (dao.variables() + importedVariables).associate {
                "${it.owner}:${it.name}" to codec.decodeFromString<Value>(it.value)
            }
        val valueIssues =
            Compiler.validatePersistentValues(existing + active + definitions.values, stored)
        if (valueIssues.isNotEmpty()) throw ValidationException(valueIssues)
        fun check(id: String, path: Set<String>) {
            require(id !in path) { "Recursive dependency in backup" }
            (definitions[id] ?: existing.find { it.id == id })
                ?.nodes
                ?.filter { it.op == "call" }
                ?.forEach { check(it.fields["WORKFLOW"] ?: "", path + id) }
        }
        definitions.keys.forEach { check(it, emptySet()) }
        val history =
            backup.history.map { row ->
                require(
                    row.automationId in workflows &&
                        row.id.length <= 120 &&
                        row.eventKey.length <= 500
                )
                val execution = codec.decodeFromString<Execution>(SafeInput.json(row.snapshot))
                require(
                    execution.id == row.id &&
                        (execution.frames.firstNotNullOfOrNull { it.definition }
                                ?: execution.definition)
                            .id == row.automationId &&
                        execution.steps in 0..10000
                )
                execution.capturedDefinitions().forEach { d ->
                    val issues =
                        Compiler.validate(
                            d,
                            locations,
                            execution.capturedDefinitions().map { it.id }.toSet(),
                        )
                    if (issues.isNotEmpty()) throw ValidationException(issues)
                }
                val restored =
                    if (execution.state in Runtime.terminal) execution
                    else
                        execution.copy(
                            state = State.CANCELLED,
                            interaction = null,
                            wakeAt = null,
                            trace =
                                execution.trace +
                                    Trace(
                                        now,
                                        "",
                                        "Imported historical snapshot; background continuation cancelled",
                                    ),
                        )
                row.copy(
                    snapshot = codec.encodeToString(restored),
                    state = restored.state.name,
                    wakeAt = null,
                )
            }
        db.withTransaction {
            backup.templates.forEach {
                if (mode != "merge" || dao.checklists().none { old -> old.id == it.id }) {
                    require(mode != "new" || dao.checklists().none { old -> old.id == it.id })
                    dao.saveChecklist(
                        ChecklistEntity(it.id, it.name, codec.encodeToString(it), now)
                    )
                }
            }
            importedVariables.forEach { dao.saveVariable(it) }
            backup.locations.forEach {
                if (mode == "merge" && dao.location(it.id) != null) return@forEach
                require(mode != "new" || dao.location(it.id) == null) {
                    "Location ID already exists"
                }
                dao.saveLocation(
                    LocationEntity(
                        it.id,
                        it.name,
                        it.latitude,
                        it.longitude,
                        it.radius,
                        it.description,
                        created = now,
                        updated = now,
                    )
                )
            }
            compiled.forEach { (a, d) ->
                require(mode != "new" || dao.automation(a.id) == null) {
                    "Automation ID already exists"
                }
                dao.saveAutomation(
                    AutomationEntity(
                        a.id,
                        a.name,
                        false,
                        d.version,
                        a.workspace,
                        codec.encodeToString(d),
                        now,
                    )
                )
            }
            history.forEach {
                if (dao.execution(it.id) == null && dao.byEvent(it.eventKey) == null)
                    dao.insertExecution(it)
            }
            backup.events.forEach {
                require(it.automationId in workflows && it.key.length <= 500)
                if (dao.event(it.key) == null) dao.saveEvent(it)
            }
        }
        preferences?.restore(backup.settings)
        return compiled.size
    }

    private fun validateValue(v: Value, depth: Int = 0) {
        require(depth <= 32 && v.text.length <= 16384 && v.items.size <= 1000)
        when (v.type) {
            Type.BOOLEAN -> v.boolean()
            Type.INTEGER,
            Type.INSTANT,
            Type.DURATION -> v.text.toLong()
            Type.DECIMAL -> v.number()
            Type.LIST -> {
                v.items.forEach { validateValue(it, depth + 1) }
                require(
                    v.elementType == null ||
                        v.items.all { it.type == v.elementType || it.type == Type.NULL }
                )
            }
            else -> Unit
        }
    }
}

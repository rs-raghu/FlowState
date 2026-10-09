package dev.flowstate.data

import androidx.room.withTransaction
import dev.flowstate.engine.*
import kotlinx.serialization.*

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
    val schema: Int = 1,
    val automations: List<BackupAutomation>,
    val locations: List<BackupLocation>,
)

class Backups(private val db: FlowDatabase) {
    suspend fun export(): String = db.withTransaction {
        codec.encodeToString(
            Backup(
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

    suspend fun import(source: String): Int {
        require(source.length <= 2_000_000) { "Backup exceeds 2 MB" }
        val backup = codec.decodeFromString<Backup>(SafeInput.json(source))
        require(backup.schema == 1)
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
            backup.automations.map { a ->
                java.util.UUID.fromString(a.id)
                require(a.name.isNotBlank() && a.name.length <= 120)
                require(dao.automation(a.id) == null) {
                    "Automation ID already exists; import into a clean database"
                }
                a to Compiler.compile(a.workspace, a.id, 1, locations, workflows)
            }
        val definitions = compiled.associate { it.first.id to it.second }
        val existing = dao.automations().map { codec.decodeFromString<Definition>(it.definition) }
        val sharedIssues = Compiler.validateSharedVariables(existing + definitions.values)
        if (sharedIssues.isNotEmpty()) throw ValidationException(sharedIssues)
        fun check(id: String, path: Set<String>) {
            require(id !in path) { "Recursive dependency in backup" }
            definitions[id]
                ?.nodes
                ?.filter { it.op == "call" }
                ?.forEach { check(it.fields["WORKFLOW"] ?: "", path + id) }
        }
        definitions.keys.forEach { check(it, emptySet()) }
        db.withTransaction {
            backup.locations.forEach {
                require(dao.location(it.id) == null) { "Location ID already exists" }
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
                require(dao.automation(a.id) == null) { "Automation ID already exists" }
                dao.saveAutomation(
                    AutomationEntity(
                        a.id,
                        a.name,
                        false,
                        1,
                        a.workspace,
                        codec.encodeToString(d),
                        now,
                    )
                )
            }
        }
        return compiled.size
    }
}

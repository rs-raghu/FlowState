package dev.flowstate.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "automations")
data class AutomationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val enabled: Boolean = false,
    val version: Int = 1,
    val workspace: String,
    val definition: String,
    val updated: Long,
    val status: String = "Disabled",
    val nextAt: Long? = null,
    val lastStarted: Long = 0,
)

@Entity(tableName = "locations")
data class LocationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radius: Float,
    val description: String = "",
    val enabled: Boolean = true,
    val created: Long,
    val updated: Long,
    val occupancy: String = "UNKNOWN",
    val eventAt: Long = 0,
    val registration: String = "Not registered",
)

@Entity(
    tableName = "executions",
    indices = [Index(value = ["eventKey"], unique = true), Index("automationId")],
    foreignKeys =
        [
            ForeignKey(
                entity = AutomationEntity::class,
                parentColumns = ["id"],
                childColumns = ["automationId"],
                onDelete = ForeignKey.RESTRICT,
            )
        ],
)
data class ExecutionEntity(
    @PrimaryKey val id: String,
    val automationId: String,
    val eventKey: String,
    val snapshot: String,
    val state: String,
    val updated: Long,
    val wakeAt: Long? = null,
)

@Entity(tableName = "variables", primaryKeys = ["owner", "name"])
data class VariableEntity(val owner: String, val name: String, val value: String)

@Entity(
    tableName = "outbox",
    indices = [Index("executionId")],
    foreignKeys =
        [
            ForeignKey(
                entity = ExecutionEntity::class,
                parentColumns = ["id"],
                childColumns = ["executionId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
)
data class OutboxEntity(@PrimaryKey val id: String, val executionId: String, val payload: String)

@Entity(tableName = "diagnostics")
data class DiagnosticEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val message: String,
)

@Entity(tableName = "event_ledger")
data class EventEntity(
    @PrimaryKey val key: String,
    val automationId: String,
    val at: Long,
    val outcome: String,
)

@Entity(tableName = "location_events", indices = [Index("locationId")])
data class LocationEventEntity(
    @PrimaryKey val key: String,
    val locationId: String,
    val transition: String,
    val at: Long,
)

@Entity(tableName = "checklist_templates")
data class ChecklistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val payload: String,
    val updated: Long,
)

@Dao
interface FlowDao {
    @Query("SELECT * FROM automations ORDER BY updated DESC")
    fun observeAutomations(): Flow<List<AutomationEntity>>

    @Query("SELECT * FROM locations ORDER BY name")
    fun observeLocations(): Flow<List<LocationEntity>>

    @Query("SELECT * FROM executions ORDER BY updated DESC LIMIT 200")
    fun observeExecutions(): Flow<List<ExecutionEntity>>

    @Query("SELECT * FROM diagnostics ORDER BY at DESC LIMIT 100")
    fun observeDiagnostics(): Flow<List<DiagnosticEntity>>

    @Query("SELECT * FROM automations") suspend fun automations(): List<AutomationEntity>

    @Query("SELECT * FROM automations WHERE id=:id")
    suspend fun automation(id: String): AutomationEntity?

    @Upsert suspend fun saveAutomation(value: AutomationEntity)

    @Query("DELETE FROM automations WHERE id=:id") suspend fun deleteAutomation(id: String)

    @Query("UPDATE automations SET name=:name, updated=:at WHERE id=:id")
    suspend fun renameAutomation(id: String, name: String, at: Long)

    @Query("SELECT * FROM locations") suspend fun locations(): List<LocationEntity>

    @Query("SELECT * FROM locations WHERE id=:id") suspend fun location(id: String): LocationEntity?

    @Upsert suspend fun saveLocation(value: LocationEntity)

    @Query("DELETE FROM locations WHERE id=:id") suspend fun deleteLocation(id: String)

    @Query("SELECT * FROM executions WHERE id=:id")
    suspend fun execution(id: String): ExecutionEntity?

    @Query("SELECT * FROM executions WHERE eventKey=:key")
    suspend fun byEvent(key: String): ExecutionEntity?

    @Query(
        "SELECT * FROM executions WHERE state NOT IN ('COMPLETED','CANCELLED','FAILED','EXPIRED')"
    )
    suspend fun active(): List<ExecutionEntity>

    @Upsert suspend fun saveExecution(value: ExecutionEntity)

    @Insert suspend fun insertExecution(value: ExecutionEntity)

    @Query("DELETE FROM executions WHERE automationId=:id") suspend fun deleteExecutions(id: String)

    @Query("SELECT * FROM variables") suspend fun variables(): List<VariableEntity>

    @Upsert suspend fun saveVariable(value: VariableEntity)

    @Query("DELETE FROM variables WHERE owner=:owner AND name=:name")
    suspend fun deleteVariable(owner: String, name: String)

    @Upsert suspend fun saveOutbox(value: OutboxEntity)

    @Query("SELECT * FROM outbox") suspend fun outbox(): List<OutboxEntity>

    @Query("DELETE FROM outbox WHERE id=:id") suspend fun deleteOutbox(id: String)

    @Query("DELETE FROM outbox WHERE executionId=:id") suspend fun deleteExecutionOutbox(id: String)

    @Query("SELECT * FROM executions WHERE automationId=:id")
    suspend fun executionsForAutomation(id: String): List<ExecutionEntity>

    @Insert suspend fun diagnostic(value: DiagnosticEntity)

    @Query("SELECT * FROM event_ledger WHERE `key`=:key")
    suspend fun event(key: String): EventEntity?

    @Upsert suspend fun saveEvent(value: EventEntity)

    @Query("SELECT * FROM event_ledger ORDER BY at DESC LIMIT 200")
    fun observeEvents(): Flow<List<EventEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun locationEvent(value: LocationEventEntity): Long

    @Query("SELECT * FROM location_events ORDER BY at DESC LIMIT 200")
    fun observeLocationEvents(): Flow<List<LocationEventEntity>>

    @Query(
        "SELECT * FROM location_events WHERE locationId=:id AND transition=:transition ORDER BY at DESC LIMIT 1"
    )
    suspend fun lastLocationEvent(id: String, transition: String): LocationEventEntity?

    @Query(
        "DELETE FROM executions WHERE state IN ('COMPLETED','CANCELLED','FAILED','EXPIRED') AND updated<:before"
    )
    suspend fun pruneExecutions(before: Long)

    @Query(
        "DELETE FROM diagnostics WHERE id NOT IN (SELECT id FROM diagnostics ORDER BY at DESC LIMIT 1000)"
    )
    suspend fun pruneDiagnostics()

    @Query(
        "DELETE FROM location_events WHERE at<:before AND at < (SELECT MAX(newer.at) FROM location_events AS newer WHERE newer.locationId=location_events.locationId AND newer.transition=location_events.transition)"
    )
    suspend fun pruneLocationEvents(before: Long)

    @Query("DELETE FROM event_ledger WHERE at<:before AND `key` NOT LIKE '%:frequency:entry:%'")
    suspend fun pruneEvents(before: Long)

    @Query("SELECT * FROM checklist_templates ORDER BY name")
    fun observeChecklists(): Flow<List<ChecklistEntity>>

    @Query("SELECT * FROM checklist_templates") suspend fun checklists(): List<ChecklistEntity>

    @Upsert suspend fun saveChecklist(value: ChecklistEntity)

    @Query("DELETE FROM checklist_templates WHERE id=:id") suspend fun deleteChecklist(id: String)
}

@Database(
    entities =
        [
            AutomationEntity::class,
            LocationEntity::class,
            ExecutionEntity::class,
            VariableEntity::class,
            OutboxEntity::class,
            DiagnosticEntity::class,
            EventEntity::class,
            LocationEventEntity::class,
            ChecklistEntity::class,
        ],
    version = 3,
    exportSchema = true,
)
abstract class FlowDatabase : RoomDatabase() {
    abstract fun dao(): FlowDao

    companion object {
        val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS checklist_templates (id TEXT NOT NULL, name TEXT NOT NULL, payload TEXT NOT NULL, updated INTEGER NOT NULL, PRIMARY KEY(id))"
                    )
                }
            }
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS event_ledger (`key` TEXT NOT NULL, automationId TEXT NOT NULL, at INTEGER NOT NULL, outcome TEXT NOT NULL, PRIMARY KEY(`key`))"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS location_events (`key` TEXT NOT NULL, locationId TEXT NOT NULL, transition TEXT NOT NULL, at INTEGER NOT NULL, PRIMARY KEY(`key`))"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_location_events_locationId ON location_events (locationId)"
                    )
                    db.execSQL(
                        "INSERT OR IGNORE INTO event_ledger SELECT eventKey, automationId, updated, state FROM executions"
                    )
                }
            }
    }
}

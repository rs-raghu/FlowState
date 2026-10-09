package dev.flowstate.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "automations")
data class AutomationEntity(@PrimaryKey val id: String, val name: String, val enabled: Boolean = false, val version: Int = 1, val workspace: String, val definition: String, val updated: Long, val status: String = "Disabled", val nextAt: Long? = null, val lastStarted: Long = 0)
@Entity(tableName = "locations")
data class LocationEntity(@PrimaryKey val id: String, val name: String, val latitude: Double, val longitude: Double, val radius: Float, val description: String = "", val enabled: Boolean = true, val created: Long, val updated: Long, val occupancy: String = "UNKNOWN", val eventAt: Long = 0, val registration: String = "Not registered")
@Entity(tableName = "executions", indices = [Index(value = ["eventKey"], unique = true), Index("automationId")], foreignKeys = [ForeignKey(entity = AutomationEntity::class, parentColumns = ["id"], childColumns = ["automationId"], onDelete = ForeignKey.RESTRICT)])
data class ExecutionEntity(@PrimaryKey val id: String, val automationId: String, val eventKey: String, val snapshot: String, val state: String, val updated: Long, val wakeAt: Long? = null)
@Entity(tableName = "variables", primaryKeys = ["owner", "name"])
data class VariableEntity(val owner: String, val name: String, val value: String)
@Entity(tableName = "outbox", indices = [Index("executionId")], foreignKeys = [ForeignKey(entity = ExecutionEntity::class, parentColumns = ["id"], childColumns = ["executionId"], onDelete = ForeignKey.CASCADE)])
data class OutboxEntity(@PrimaryKey val id: String, val executionId: String, val payload: String)
@Entity(tableName = "diagnostics")
data class DiagnosticEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val at: Long, val message: String)

@Dao
interface FlowDao {
    @Query("SELECT * FROM automations ORDER BY updated DESC") fun observeAutomations(): Flow<List<AutomationEntity>>
    @Query("SELECT * FROM locations ORDER BY name") fun observeLocations(): Flow<List<LocationEntity>>
    @Query("SELECT * FROM executions ORDER BY updated DESC LIMIT 200") fun observeExecutions(): Flow<List<ExecutionEntity>>
    @Query("SELECT * FROM diagnostics ORDER BY at DESC LIMIT 100") fun observeDiagnostics(): Flow<List<DiagnosticEntity>>
    @Query("SELECT * FROM automations") suspend fun automations(): List<AutomationEntity>
    @Query("SELECT * FROM automations WHERE id=:id") suspend fun automation(id: String): AutomationEntity?
    @Upsert suspend fun saveAutomation(value: AutomationEntity)
    @Query("DELETE FROM automations WHERE id=:id") suspend fun deleteAutomation(id: String)
    @Query("SELECT * FROM locations") suspend fun locations(): List<LocationEntity>
    @Query("SELECT * FROM locations WHERE id=:id") suspend fun location(id: String): LocationEntity?
    @Upsert suspend fun saveLocation(value: LocationEntity)
    @Query("DELETE FROM locations WHERE id=:id") suspend fun deleteLocation(id: String)
    @Query("SELECT * FROM executions WHERE id=:id") suspend fun execution(id: String): ExecutionEntity?
    @Query("SELECT * FROM executions WHERE eventKey=:key") suspend fun byEvent(key: String): ExecutionEntity?
    @Query("SELECT * FROM executions WHERE state NOT IN ('COMPLETED','CANCELLED','FAILED','EXPIRED')") suspend fun active(): List<ExecutionEntity>
    @Upsert suspend fun saveExecution(value: ExecutionEntity)
    @Query("DELETE FROM executions WHERE automationId=:id") suspend fun deleteExecutions(id: String)
    @Query("SELECT * FROM variables") suspend fun variables(): List<VariableEntity>
    @Upsert suspend fun saveVariable(value: VariableEntity)
    @Query("DELETE FROM variables WHERE owner=:owner AND name=:name") suspend fun deleteVariable(owner: String, name: String)
    @Upsert suspend fun saveOutbox(value: OutboxEntity)
    @Query("SELECT * FROM outbox") suspend fun outbox(): List<OutboxEntity>
    @Query("DELETE FROM outbox WHERE id=:id") suspend fun deleteOutbox(id: String)
    @Insert suspend fun diagnostic(value: DiagnosticEntity)
}
@Database(entities = [AutomationEntity::class, LocationEntity::class, ExecutionEntity::class, VariableEntity::class, OutboxEntity::class, DiagnosticEntity::class], version = 1, exportSchema = true)
abstract class FlowDatabase : RoomDatabase() { abstract fun dao(): FlowDao }

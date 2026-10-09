package dev.flowstate.data

import android.content.Context
import androidx.room.withTransaction
import dev.flowstate.engine.*
import dev.flowstate.platform.Platform
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

class Coordinator(private val db: FlowDatabase, context: Context) {
    private val dao = db.dao()
    private val platform = Platform(context)
    private val operations = Mutex()

    suspend fun save(id: String, name: String, source: String) = operations.withLock {
        saveInternal(id, name, source)
    }

    suspend fun enable(a: AutomationEntity, value: Boolean) = operations.withLock {
        enableInternal(a, value)
    }

    suspend fun delete(id: String) = operations.withLock { deleteInternal(id) }

    suspend fun start(
        id: String,
        event: String = UUID.randomUUID().toString(),
        automatic: Boolean = false,
    ) = operations.withLock { startInternal(id, event, automatic) }

    suspend fun trigger(id: String, key: String, at: Long, version: Int) = operations.withLock {
        triggerInternal(id, key, at, version)
    }

    suspend fun drive(id: String) = operations.withLock { driveInternal(id) }

    suspend fun respond(id: String, token: String, response: String) = operations.withLock {
        respondInternal(id, token, response)
    }

    suspend fun cancel(id: String) = operations.withLock { cancelInternal(id) }

    suspend fun snooze(id: String, minutes: Long) = operations.withLock {
        snoozeInternal(id, minutes)
    }

    suspend fun drain() = operations.withLock { drainInternal() }

    suspend fun geofence(id: String, transition: String, at: Long) = operations.withLock {
        geofenceInternal(id, transition, at)
    }

    suspend fun reconcile() = operations.withLock { reconcileInternal() }

    suspend fun importBackup(source: String) = operations.withLock {
        val result = Backups(db).import(source)
        reconcileInternal()
        result
    }

    suspend fun saveLocation(value: LocationEntity) = operations.withLock {
        require(
            value.name.isNotBlank() &&
                value.name.length <= 120 &&
                value.latitude.isFinite() &&
                value.longitude.isFinite() &&
                value.latitude in -90.0..90.0 &&
                value.longitude in -180.0..180.0 &&
                value.radius.isFinite() &&
                value.radius in 100f..100000f
        )
        dao.saveLocation(value)
        reconcileInternal()
    }

    suspend fun deleteLocation(id: String) = operations.withLock {
        require(
            dao.automations().none {
                codec.decodeFromString<Definition>(it.definition).trigger.locationId == id
            }
        ) {
            "This location is referenced by an automation"
        }
        dao.deleteLocation(id)
        reconcileInternal()
    }

    suspend fun checklist(id: String, token: String, checked: Set<Int>) = operations.withLock {
        db.withTransaction {
            val row = dao.execution(id) ?: return@withTransaction
            val e = codec.decodeFromString<Execution>(row.snapshot)
            val i = e.interaction ?: return@withTransaction
            if (
                i.token != token ||
                    e.state != State.WAITING_FOR_USER ||
                    i.deadline?.let { now >= it } == true
            )
                return@withTransaction
            require(i.kind == "checklist" && checked.all { it in i.options.indices })
            dao.saveExecution(
                entity(
                    e.copy(interaction = i.copy(completed = checked.sorted())),
                    row.automationId,
                    row.eventKey,
                )
            )
        }
    }

    private val now
        get() = System.currentTimeMillis()

    private suspend fun runtime(): Runtime {
        val definitions =
            dao.automations()
                .filter { it.enabled }
                .associate { it.id to codec.decodeFromString<Definition>(it.definition) }
        return Runtime { definitions[it] }
    }

    private fun entity(e: Execution, automation: String, event: String) =
        ExecutionEntity(
            e.id,
            automation,
            event,
            codec.encodeToString(e),
            e.state.name,
            now,
            e.wakeAt,
        )

    private suspend fun saveInternal(id: String, name: String, source: String): AutomationEntity {
        require(name.isNotBlank() && name.length <= 120)
        val locations = dao.locations().map { it.id }.toSet()
        val definitions = dao.automations()
        val old = dao.automation(id)
        val d =
            Compiler.compile(
                source,
                id,
                (old?.version ?: 0) + 1,
                locations,
                definitions.map { it.id }.toSet(),
            )
        // Prevent indirect dependency cycles before accepting an edit.
        val all =
            definitions
                .associate { it.id to codec.decodeFromString<Definition>(it.definition) }
                .toMutableMap()
        all[id] = d
        val sharedIssues = Compiler.validateSharedVariables(all.values)
        if (sharedIssues.isNotEmpty()) throw ValidationException(sharedIssues)
        val stored =
            dao.variables().associate {
                "${it.owner}:${it.name}" to codec.decodeFromString<Value>(it.value)
            }
        d.variables
            .filter { it.scope != Scope.LOCAL }
            .forEach { v ->
                val value = stored[Expressions.key(v.scope, v.name, d.id)]
                require(value == null || value.type == Type.NULL || value.type == v.type) {
                    "Persistent variable ${v.name} already has a different type; use a new name"
                }
            }
        fun check(current: String, path: Set<String>) {
            require(current !in path) { "Recursive workflow dependency" }
            all[current]
                ?.nodes
                ?.filter { it.op == "call" }
                ?.forEach { check(it.fields["WORKFLOW"] ?: "", path + current) }
        }
        check(id, emptySet())
        platform.cancelAlarm("automation", id)
        val value =
            AutomationEntity(
                id,
                name,
                old?.enabled ?: false,
                d.version,
                source,
                codec.encodeToString(d),
                now,
                status = "Saved",
            )
        dao.saveAutomation(value)
        reconcileInternal()
        return value
    }

    private suspend fun enableInternal(a: AutomationEntity, value: Boolean) {
        val current = dao.automation(a.id) ?: return
        dao.saveAutomation(
            current.copy(
                enabled = value,
                status = if (value) "Pending reconciliation" else "Disabled",
                nextAt = null,
            )
        )
        platform.cancelAlarm("automation", a.id)
        reconcileInternal()
    }

    private suspend fun deleteInternal(id: String) {
        require(
            dao.automations().none {
                it.id != id &&
                    codec.decodeFromString<Definition>(it.definition).nodes.any { n ->
                        n.op == "call" && n.fields["WORKFLOW"] == id
                    }
            }
        ) {
            "Another workflow calls this automation"
        }
        platform.cancelAlarm("automation", id)
        dao.executionsForAutomation(id).forEach {
            platform.cancelAlarm("execution", it.id)
            platform.cancelAllExecutionNotifications(it.id)
        }
        db.withTransaction {
            dao.deleteExecutions(id)
            dao.deleteAutomation(id)
            dao.variables()
                .filter { it.owner == id }
                .forEach { dao.deleteVariable(it.owner, it.name) }
        }
        reconcileInternal()
    }

    private suspend fun startInternal(
        id: String,
        event: String = UUID.randomUUID().toString(),
        automatic: Boolean = false,
        overrideDefinition: Definition? = null,
    ): String? {
        val rt = runtime()
        var execution: String? = null
        db.withTransaction {
            val a = dao.automation(id) ?: return@withTransaction
            if (automatic && !a.enabled || dao.byEvent("$id:$event") != null) return@withTransaction
            val d = overrideDefinition ?: codec.decodeFromString<Definition>(a.definition)
            if (automatic && now - a.lastStarted < d.trigger.cooldownSeconds * 1000)
                return@withTransaction
            require(dao.active().size < 32) { "Global active-execution limit reached" }
            require(dao.active().count { it.automationId == id } < 4) {
                "This automation already has four active executions"
            }
            val e = rt.start(UUID.randomUUID().toString(), d, now)
            dao.saveExecution(entity(e, id, "$id:$event"))
            dao.saveAutomation(a.copy(lastStarted = now))
            execution = e.id
        }
        execution?.let { driveInternal(it) }
        return execution
    }

    private suspend fun triggerInternal(id: String, key: String, at: Long, version: Int) {
        val a = dao.automation(id) ?: return
        if (!a.enabled) return
        val t = codec.decodeFromString<Definition>(a.definition).trigger
        if (
            !Scheduling.acceptsDelivery(t, at, key, version, a.version, now, ZoneId.systemDefault())
        )
            return
        startInternal(id, "time:$key", true)
        reconcileInternal()
    }

    private suspend fun driveInternal(id: String) {
        val rt = runtime()
        var latest: Execution? = null
        for (slice in 0 until 5) {
            db.withTransaction {
                val row = dao.execution(id) ?: return@withTransaction
                var e = codec.decodeFromString<Execution>(row.snapshot)
                val interaction = e.interaction
                if (interaction != null && interaction.snoozedUntil?.let { it <= now } == true)
                    e =
                        e.copy(
                            interaction = interaction.copy(snoozedUntil = null),
                            wakeAt = interaction.deadline,
                        )
                val persistent =
                    dao.variables()
                        .associate {
                            "${it.owner}:${it.name}" to codec.decodeFromString<Value>(it.value)
                        }
                        .toMutableMap()
                        .apply {
                            (listOf(e.definition) + e.library.values).forEach { d ->
                                d.variables
                                    .filter { it.scope != Scope.LOCAL }
                                    .forEach { v ->
                                        putIfAbsent(
                                            Expressions.key(v.scope, v.name, d.id),
                                            v.default,
                                        )
                                    }
                            }
                        }
                val occupancy = dao.locations().associate { it.id to (it.occupancy to it.eventAt) }
                val tick = rt.tick(e, now, ZoneId.systemDefault().id, persistent, occupancy)
                dao.saveExecution(entity(tick.execution, row.automationId, row.eventKey))
                tick.persistent.forEach { (key, value) ->
                    val split = key.lastIndexOf(':')
                    dao.saveVariable(
                        VariableEntity(
                            key.substring(0, split),
                            key.substring(split + 1),
                            codec.encodeToString(value),
                        )
                    )
                }
                tick.effects.forEach {
                    dao.saveOutbox(OutboxEntity(it.id, id, codec.encodeToString(it)))
                }
                latest = tick.execution
            }
            if (latest?.state != State.RUNNING) break
        }
        drainInternal()
        latest?.let { e ->
            if (e.state == State.RUNNING)
                platform.enqueue("execution", id, key = "slice:${e.steps}")
            else if (e.wakeAt != null && e.state !in Runtime.terminal)
                platform.schedule("execution", id, e.wakeAt!!)
            else {
                platform.cancelAlarm("execution", id)
                if (e.state in Runtime.terminal) platform.cancelNotification(id)
            }
        }
    }

    private suspend fun respondInternal(id: String, token: String, response: String) {
        val rt = runtime()
        var accepted = false
        db.withTransaction {
            val row = dao.execution(id) ?: return@withTransaction
            val e = codec.decodeFromString<Execution>(row.snapshot)
            val resumed = rt.respond(e, token, response, now)
            if (resumed != e) {
                dao.saveExecution(entity(resumed, row.automationId, row.eventKey))
                accepted = true
            }
        }
        if (accepted) {
            platform.cancelNotification(id)
            driveInternal(id)
        } else driveInternal(id) // Drives expiration without resurrecting stale responses.
    }

    private suspend fun cancelInternal(id: String) {
        db.withTransaction {
            val row = dao.execution(id) ?: return@withTransaction
            val e = codec.decodeFromString<Execution>(row.snapshot)
            dao.saveExecution(
                entity(
                    e.copy(state = State.CANCELLED, interaction = null, wakeAt = null),
                    row.automationId,
                    row.eventKey,
                )
            )
            dao.deleteExecutionOutbox(id)
        }
        platform.cancelAlarm("execution", id)
        platform.cancelAllExecutionNotifications(id)
    }

    private suspend fun snoozeInternal(id: String, minutes: Long) {
        require(minutes in 1..60)
        db.withTransaction {
            val row = dao.execution(id) ?: return@withTransaction
            val e = codec.decodeFromString<Execution>(row.snapshot)
            val i = e.interaction ?: return@withTransaction
            require(i.snoozes < 3)
            require(i.deadline?.let { now < it } ?: true)
            require(e.deadline?.let { now < it } ?: true)
            val at = now + minutes * 60000
            val changed =
                e.copy(
                    interaction = i.copy(snoozes = i.snoozes + 1, snoozedUntil = at),
                    wakeAt = minOf(at, i.deadline ?: Long.MAX_VALUE, e.deadline ?: Long.MAX_VALUE),
                )
            dao.saveExecution(entity(changed, row.automationId, row.eventKey))
            dao.saveOutbox(
                OutboxEntity(
                    "${id}:snooze:${i.snoozes}",
                    id,
                    codec.encodeToString(
                        Effect("snooze", "interaction", i.title, interaction = changed.interaction)
                    ),
                )
            )
        }
        platform.cancelNotification(id)
        val e = dao.execution(id) ?: return
        platform.schedule("execution", id, e.wakeAt ?: return)
    }

    private suspend fun drainInternal() {
        dao.outbox().forEach { row ->
            val effect = codec.decodeFromString<Effect>(row.payload)
            val e =
                dao.execution(row.executionId)?.let {
                    codec.decodeFromString<Execution>(it.snapshot)
                }
            val pending = effect.interaction
            if (
                pending != null &&
                    (e?.interaction?.token != pending.token || e.state != State.WAITING_FOR_USER)
            ) {
                dao.deleteOutbox(row.id)
                return@forEach
            }
            if (e?.interaction?.snoozedUntil?.let { it > now } == true) return@forEach
            if (!platform.notification(row.executionId, effect))
                dao.diagnostic(
                    DiagnosticEntity(
                        at = now,
                        message =
                            "Notification permission denied; interaction remains available in Activity",
                    )
                )
            dao.deleteOutbox(row.id)
        }
    }

    private suspend fun geofenceInternal(id: String, transition: String, at: Long) {
        var accepted = false
        db.withTransaction {
            val l = dao.location(id) ?: return@withTransaction
            if (!l.enabled || at < l.eventAt) return@withTransaction
            accepted = true
            if (at >= l.eventAt)
                dao.saveLocation(
                    l.copy(
                        occupancy = if (transition == "exit") "OUTSIDE" else "INSIDE",
                        eventAt = at,
                    )
                )
        }
        if (!accepted) return
        dao.automations()
            .filter { it.enabled }
            .forEach { a ->
                val t = codec.decodeFromString<Definition>(a.definition).trigger
                if (
                    t.kind == "location" &&
                        t.locationId == id &&
                        t.transition == transition &&
                        Scheduling.locationEligible(t, at, ZoneId.systemDefault())
                )
                    startInternal(a.id, "geo:$id:$transition:$at", true)
            }
    }

    private suspend fun reconcileInternal() {
        val device = ZoneId.systemDefault()
        val enabled = dao.automations().filter { it.enabled }
        enabled.forEach { a ->
            val t = codec.decodeFromString<Definition>(a.definition).trigger
            if (t.kind == "time") {
                val missedKey = a.nextAt?.let { Scheduling.occurrenceKey(t, it, device) }
                val delivered = missedKey?.let { dao.byEvent("${a.id}:time:$it") != null } ?: false
                if (
                    a.nextAt?.let { it <= now } == true &&
                        !delivered &&
                        t.catchUp in setOf("skip", "record")
                )
                    dao.diagnostic(
                        DiagnosticEntity(
                            at = now,
                            message =
                                "Missed '${a.name}' occurrence $missedKey; policy ${t.catchUp}",
                        )
                    )
                val missed = Scheduling.recovery(t, now, a.nextAt, device)
                if (missed != null && !delivered) {
                    if (t.catchUp == "ask") {
                        val original = codec.decodeFromString<Definition>(a.definition)
                        val question =
                            Node(
                                "recovery-question",
                                "ask",
                                fields =
                                    mapOf(
                                        "KIND" to "yesno",
                                        "TITLE" to "Run missed ${a.name}?",
                                        "TIMEOUT" to "3600",
                                    ),
                                branches = mapOf("YES" to original.entry, "NO" to "recovery-skip"),
                            )
                        val ask =
                            original.copy(
                                entry = question.id,
                                nodes = original.nodes + question + Node("recovery-skip", "stop"),
                            )
                        startInternal(a.id, "time:${missed.key}", true, ask)
                    } else startInternal(a.id, "time:${missed.key}", true)
                }
                val next = Scheduling.next(t, now, device)
                dao.saveAutomation(
                    (dao.automation(a.id) ?: a).copy(
                        nextAt = next?.at,
                        status =
                            if (next == null) "No future occurrence"
                            else if (platform.exact()) "Exact alarm; worker delivery may be delayed"
                            else "Inexact scheduling",
                    )
                )
                if (next != null)
                    platform.schedule("automation", a.id, next.at, next.key, a.version)
                else platform.cancelAlarm("automation", a.id)
            } else if (t.kind == "manual") dao.saveAutomation(a.copy(status = "Manual"))
        }
        val referenced =
            enabled
                .map { codec.decodeFromString<Definition>(it.definition).trigger }
                .filter { it.kind == "location" }
                .map { it.locationId }
                .toSet()
        val locations = dao.locations().filter { it.enabled && it.id in referenced }
        locations.forEach { dao.saveLocation(it.copy(registration = "Registration pending")) }
        val failure = platform.register(locations)
        dao.locations().forEach {
            dao.saveLocation(
                it.copy(
                    registration =
                        if (it.id !in referenced || !it.enabled) "Not monitored"
                        else failure ?: "Registered"
                )
            )
        }
        enabled
            .filter { codec.decodeFromString<Definition>(it.definition).trigger.kind == "location" }
            .forEach { a ->
                val location =
                    dao.location(
                        codec.decodeFromString<Definition>(a.definition).trigger.locationId
                    )
                dao.saveAutomation(a.copy(status = location?.registration ?: "Location missing"))
            }
        if (failure != null) dao.diagnostic(DiagnosticEntity(at = now, message = failure))
        dao.active().forEach { row ->
            if (row.wakeAt == null || row.wakeAt <= now || row.state in setOf("CREATED", "RUNNING"))
                driveInternal(row.id)
            else platform.schedule("execution", row.id, row.wakeAt)
        }
        drainInternal()
        platform.periodicRecovery()
    }
}

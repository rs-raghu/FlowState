package dev.flowstate.data

import android.content.Context
import androidx.room.withTransaction
import dev.flowstate.engine.*
import dev.flowstate.platform.Platform
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.time.ZoneId
import java.util.UUID

class Coordinator(private val db: FlowDatabase,context: Context) {
    private val dao=db.dao(); private val platform=Platform(context)
    private val now get()=System.currentTimeMillis()
    private suspend fun runtime(): Runtime { val definitions=dao.automations().filter { it.enabled }.associate { it.id to codec.decodeFromString<Definition>(it.definition) }; return Runtime { definitions[it] } }
    private fun entity(e: Execution,automation: String,event: String)=ExecutionEntity(e.id,automation,event,codec.encodeToString(e),e.state.name,now,e.wakeAt)
    suspend fun save(id: String,name: String,source: String): AutomationEntity {
        require(name.isNotBlank() && name.length<=120)
        val locations=dao.locations().map { it.id }.toSet(); val definitions=dao.automations()
        val old=dao.automation(id)
        val d=Compiler.compile(source,id,(old?.version ?: 0)+1,locations,definitions.map { it.id }.toSet())
        // Prevent indirect dependency cycles before accepting an edit.
        val all=definitions.associate { it.id to codec.decodeFromString<Definition>(it.definition) }.toMutableMap(); all[id]=d
        fun check(current: String,path: Set<String>) { require(current !in path) { "Recursive workflow dependency" }; all[current]?.nodes?.filter { it.op=="call" }?.forEach { check(it.fields["WORKFLOW"] ?: "",path+current) } }
        check(id,emptySet())
        platform.cancelAlarm("automation",id)
        val value=AutomationEntity(id,name,old?.enabled ?: false,d.version,source,codec.encodeToString(d),now,status="Saved")
        dao.saveAutomation(value); reconcile(); return value
    }
    suspend fun enable(a: AutomationEntity,value: Boolean) { dao.saveAutomation(a.copy(enabled=value,status=if(value) "Pending reconciliation" else "Disabled",nextAt=null)); platform.cancelAlarm("automation",a.id); reconcile() }
    suspend fun delete(id: String) {
        require(dao.automations().none { it.id!=id && codec.decodeFromString<Definition>(it.definition).nodes.any { n -> n.op=="call" && n.fields["WORKFLOW"]==id } }) { "Another workflow calls this automation" }
        platform.cancelAlarm("automation",id)
        dao.active().filter { it.automationId==id }.forEach { platform.cancelAlarm("execution",it.id); platform.cancelNotification(it.id) }
        db.withTransaction { dao.deleteExecutions(id); dao.deleteAutomation(id); dao.variables().filter { it.owner==id }.forEach { dao.deleteVariable(it.owner,it.name) } }; reconcile()
    }
    suspend fun start(id: String,event: String=UUID.randomUUID().toString(),automatic: Boolean=false): String? {
        val rt=runtime(); var execution: String?=null
        db.withTransaction {
            val a=dao.automation(id) ?: return@withTransaction
            if(automatic && !a.enabled || dao.byEvent("$id:$event")!=null) return@withTransaction
            val d=codec.decodeFromString<Definition>(a.definition)
            if(automatic && now-a.lastStarted<d.trigger.cooldownSeconds*1000) return@withTransaction
            require(dao.active().size<32) { "Global active-execution limit reached" }
            require(dao.active().count { it.automationId==id }<4) { "This automation already has four active executions" }
            val e=rt.start(UUID.randomUUID().toString(),d,now)
            dao.saveExecution(entity(e,id,"$id:$event")); dao.saveAutomation(a.copy(lastStarted=now)); execution=e.id
        }
        execution?.let { drive(it) }; return execution
    }
    suspend fun trigger(id: String,key: String,at: Long) {
        val a=dao.automation(id) ?: return
        if(!a.enabled || at!=a.nextAt) return
        start(id,"time:$key",true); reconcile()
    }
    suspend fun drive(id: String) {
        val rt=runtime(); var latest: Execution?=null
        repeat(5) {
            db.withTransaction {
                val row=dao.execution(id) ?: return@withTransaction
                val e=codec.decodeFromString<Execution>(row.snapshot)
                val persistent=dao.variables().associate { "${it.owner}:${it.name}" to codec.decodeFromString<Value>(it.value) }
                val occupancy=dao.locations().associate { it.id to (it.occupancy to it.eventAt) }
                val tick=rt.tick(e,now,ZoneId.systemDefault().id,persistent,occupancy)
                dao.saveExecution(entity(tick.execution,row.automationId,row.eventKey))
                tick.persistent.forEach { (key,value) -> val split=key.lastIndexOf(':'); dao.saveVariable(VariableEntity(key.substring(0,split),key.substring(split+1),codec.encodeToString(value))) }
                tick.effects.forEach { dao.saveOutbox(OutboxEntity(it.id,id,codec.encodeToString(it))) }; latest=tick.execution
            }
            if(latest?.state!=State.RUNNING) return@repeat
        }
        drain()
        latest?.let { e ->
            if(e.state==State.RUNNING) platform.enqueue("execution",id,key="slice:${e.steps}")
            else if(e.wakeAt!=null && e.state !in Runtime.terminal) platform.schedule("execution",id,e.wakeAt!!)
            else { platform.cancelAlarm("execution",id); if(e.state in Runtime.terminal) platform.cancelNotification(id) }
        }
    }
    suspend fun respond(id: String,token: String,response: String) {
        val rt=runtime(); var accepted=false
        db.withTransaction { val row=dao.execution(id) ?: return@withTransaction; val e=codec.decodeFromString<Execution>(row.snapshot); val resumed=rt.respond(e,token,response,now); if(resumed!=e) { dao.saveExecution(entity(resumed,row.automationId,row.eventKey)); accepted=true } }
        if(accepted) { platform.cancelNotification(id); drive(id) } else drive(id) // Drives expiration without resurrecting stale responses.
    }
    suspend fun cancel(id: String) { db.withTransaction { val row=dao.execution(id) ?: return@withTransaction; val e=codec.decodeFromString<Execution>(row.snapshot); dao.saveExecution(entity(e.copy(state=State.CANCELLED,interaction=null,wakeAt=null),row.automationId,row.eventKey)) }; platform.cancelAlarm("execution",id); platform.cancelNotification(id) }
    suspend fun snooze(id: String,minutes: Long) {
        require(minutes in 1..60)
        db.withTransaction { val row=dao.execution(id) ?: return@withTransaction; val e=codec.decodeFromString<Execution>(row.snapshot); val i=e.interaction ?: return@withTransaction; require(i.snoozes<3); require(i.deadline==null || now<i.deadline); val at=now+minutes*60000; val changed=e.copy(interaction=i.copy(snoozes=i.snoozes+1),wakeAt=minOf(at,i.deadline ?: Long.MAX_VALUE)); dao.saveExecution(entity(changed,row.automationId,row.eventKey)); dao.saveOutbox(OutboxEntity("${id}:snooze:${i.snoozes}",id,codec.encodeToString(Effect("snooze","interaction",i.title,interaction=changed.interaction)))) }
        platform.cancelNotification(id)
        val e=dao.execution(id) ?: return; platform.schedule("execution",id,e.wakeAt ?: return)
    }
    suspend fun drain() {
        dao.outbox().forEach { row ->
            val effect=codec.decodeFromString<Effect>(row.payload)
            val e=dao.execution(row.executionId)?.let { codec.decodeFromString<Execution>(it.snapshot) }
            if(effect.interaction!=null && (e?.interaction?.token!=effect.interaction.token || e.state!=State.WAITING_FOR_USER || e.wakeAt!=null && e.wakeAt!!>now && e.interaction!!.snoozes>0)) return@forEach
            if(!platform.notification(row.executionId,effect)) dao.diagnostic(DiagnosticEntity(at=now,message="Notification permission denied; interaction remains available in Activity"))
            dao.deleteOutbox(row.id)
        }
    }
    suspend fun geofence(id: String,transition: String,at: Long) {
        db.withTransaction { val l=dao.location(id) ?: return@withTransaction; if(at>=l.eventAt) dao.saveLocation(l.copy(occupancy=if(transition=="exit") "OUTSIDE" else "INSIDE",eventAt=at)) }
        dao.automations().filter { it.enabled }.forEach { a -> val t=codec.decodeFromString<Definition>(a.definition).trigger; if(t.kind=="location" && t.locationId==id && t.transition==transition) start(a.id,"geo:$id:$transition:$at",true) }
    }
    suspend fun reconcile() {
        val device=ZoneId.systemDefault(); val enabled=dao.automations().filter { it.enabled }
        enabled.forEach { a -> val t=codec.decodeFromString<Definition>(a.definition).trigger
            if(t.kind=="time") {
                val missed=Scheduling.recovery(t,now,a.nextAt,device)
                if(missed!=null) {
                    if(t.catchUp=="ask") dao.diagnostic(DiagnosticEntity(at=now,message="Missed '${a.name}': start manually to catch up")) else start(a.id,"time:${missed.key}",true)
                }
                val next=Scheduling.next(t,now,device)
                dao.saveAutomation((dao.automation(a.id) ?: a).copy(nextAt=next?.at,status=if(next==null) "No future occurrence" else if(platform.exact()) "Exact alarm; worker delivery may be delayed" else "Inexact scheduling"))
                if(next!=null) platform.schedule("automation",a.id,next.at,next.key) else platform.cancelAlarm("automation",a.id)
            } else if(t.kind=="manual") dao.saveAutomation(a.copy(status="Manual"))
        }
        val referenced=enabled.map { codec.decodeFromString<Definition>(it.definition).trigger }.filter { it.kind=="location" }.map { it.locationId }.toSet()
        val locations=dao.locations().filter { it.enabled && it.id in referenced }
        locations.forEach { dao.saveLocation(it.copy(registration="Registration pending")) }
        val failure=platform.register(locations)
        dao.locations().forEach { dao.saveLocation(it.copy(registration=if(it.id !in referenced || !it.enabled) "Not monitored" else failure ?: "Registered")) }
        enabled.filter { codec.decodeFromString<Definition>(it.definition).trigger.kind=="location" }.forEach { a -> val location=dao.location(codec.decodeFromString<Definition>(a.definition).trigger.locationId); dao.saveAutomation(a.copy(status=location?.registration ?: "Location missing")) }
        if(failure!=null) dao.diagnostic(DiagnosticEntity(at=now,message=failure))
        dao.active().forEach { row -> if(row.wakeAt==null || row.wakeAt<=now || row.state in setOf("CREATED","RUNNING")) drive(row.id) else platform.schedule("execution",row.id,row.wakeAt) }
        drain(); platform.periodicRecovery()
    }
}

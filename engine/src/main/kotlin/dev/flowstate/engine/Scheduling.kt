package dev.flowstate.engine

import java.time.*
import java.time.temporal.TemporalAdjusters

data class Occurrence(val at: Long, val key: String)
object Scheduling {
    fun zone(t: Trigger, device: ZoneId) = if(t.zone=="device") device else ZoneId.of(t.zone)
    fun validate(t: Trigger) {
        require(t.kind in setOf("manual","time","location")) { "Unknown trigger" }
        require(t.cooldownSeconds in 0..31536000 && t.graceSeconds in 0..604800)
        if(t.kind=="location") { require(t.locationId.isNotBlank()); require(t.transition in setOf("enter","exit","dwell")) }
        if(t.kind!="time") return
        zone(t,ZoneId.of("UTC")); require(t.times.isNotEmpty() && t.times.size<=24); t.times.forEach(LocalTime::parse)
        require(t.days.isNotEmpty() && t.days.all { it in 1..7 }); require(t.intervalDays in 1..3650); require(t.windowDay in 1..7)
        require(t.recurrence in setOf("daily","weekdays","weekly","monthly","dates","interval","once","weeklyWindow"))
        require(t.catchUp in setOf("skip","grace","window","record","ask"))
        if(t.startDate.isNotEmpty()) LocalDate.parse(t.startDate)
        if(t.endDate.isNotEmpty()) { LocalDate.parse(t.endDate); require(t.startDate.isEmpty() || t.endDate>=t.startDate) }
        t.dates.forEach(LocalDate::parse)
        if(t.recurrence in setOf("dates","once")) require(t.dates.isNotEmpty())
        if(t.recurrence in setOf("interval","monthly")) require(t.startDate.isNotEmpty())
    }
    // java.time moves gap times forward by the gap and picks the earlier offset during overlaps.
    fun resolve(date: LocalDate, time: LocalTime, zone: ZoneId): Long = date.atTime(time).atZone(zone).withEarlierOffsetAtOverlap().toInstant().toEpochMilli()
    fun eligible(t: Trigger,date: LocalDate): Boolean {
        if(t.startDate.isNotEmpty() && date<LocalDate.parse(t.startDate) || t.endDate.isNotEmpty() && date>LocalDate.parse(t.endDate)) return false
        return when(t.recurrence) {
            "daily" -> true
            "weekdays","weekly" -> date.dayOfWeek.value in t.days
            "weeklyWindow" -> date.dayOfWeek.value==t.windowDay
            "monthly" -> date.dayOfMonth==minOf(LocalDate.parse(t.startDate).dayOfMonth,date.lengthOfMonth())
            "dates","once" -> date.toString() in t.dates
            "interval" -> java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(t.startDate),date).let { it>=0 && it%t.intervalDays==0L }
            else -> false
        }
    }
    fun next(t: Trigger, after: Long, device: ZoneId): Occurrence? {
        if(t.kind!="time") return null
        validate(t); val z=zone(t,device); val date=Instant.ofEpochMilli(after).atZone(z).toLocalDate()
        for(i in 0..3660) { val day=date.plusDays(i.toLong()); if(eligible(t,day)) { val found=t.times.map { resolve(day,LocalTime.parse(it),z) }.filter { it>after }.minOrNull(); if(found!=null) return Occurrence(found,if(t.recurrence=="weeklyWindow") windowKey(t,found,device) else "${z.id}:$day:${Instant.ofEpochMilli(found).atZone(z).toLocalTime()}") } }
        return null
    }
    fun windowStart(t: Trigger, now: Long, device: ZoneId): Long {
        val z=zone(t,device); val current=Instant.ofEpochMilli(now).atZone(z); var date=current.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.of(t.windowDay)))
        var start=resolve(date,LocalTime.parse(t.times.first()),z); if(start>now) { date=date.minusWeeks(1); start=resolve(date,LocalTime.parse(t.times.first()),z) }; return start
    }
    fun windowKey(t: Trigger,now: Long,device: ZoneId) = "window:${zone(t,device).id}:${Instant.ofEpochMilli(windowStart(t,now,device)).atZone(zone(t,device)).toLocalDate()}"
    fun recovery(t: Trigger, now: Long, scheduled: Long?, device: ZoneId): Occurrence? {
        if(t.kind!="time" || t.catchUp in setOf("skip","record") || scheduled==null || scheduled>now) return null
        if(t.recurrence=="weeklyWindow" && t.catchUp=="window") {
            val start=windowStart(t,now,device)
            val date=Instant.ofEpochMilli(start).atZone(zone(t,device)).toLocalDate()
            return if(eligible(t,date)) Occurrence(start,windowKey(t,now,device)) else null
        }
        return if(now-scheduled<=t.graceSeconds*1000) Occurrence(scheduled,occurrenceKey(t,scheduled,device)) else null
    }
    fun occurrenceKey(t: Trigger,at: Long,device: ZoneId): String {
        if(t.recurrence=="weeklyWindow") return windowKey(t,at,device)
        val z=zone(t,device);val local=Instant.ofEpochMilli(at).atZone(z);return "${z.id}:${local.toLocalDate()}:${local.toLocalTime()}"
    }
}

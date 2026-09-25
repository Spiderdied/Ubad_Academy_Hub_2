package com.ubad.academy.data.repository

import com.ubad.academy.core.Web
import com.ubad.academy.data.local.db.UbadDatabase
import com.ubad.academy.data.local.db.toDomain
import com.ubad.academy.data.local.db.toEntity
import com.ubad.academy.domain.model.CalendarEvent
import com.ubad.academy.domain.model.ScheduleEntry
import com.ubad.academy.domain.model.Task
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Calendar events, tasks and the weekly study schedule (web: state.events / tasks / schedule). */
@Singleton
class PlannerRepository @Inject constructor(private val db: UbadDatabase) {
    private val dao get() = db.planner()

    val events: Flow<List<CalendarEvent>> = dao.observeEvents().map { l -> l.map { it.toDomain() } }
    val tasks: Flow<List<Task>> = dao.observeTasks().map { l -> l.map { it.toDomain() } }
    val schedule: Flow<List<ScheduleEntry>> = dao.observeSchedule().map { l -> l.map { it.toDomain() } }

    // ── events (openEventModal) ──
    suspend fun saveEvent(existing: CalendarEvent?, title: String, date: String, time: String, desc: String) {
        require(title.isNotBlank() && Web.isYmd(date))
        val t = title.trim().take(120)
        val d = desc.trim().take(500)
        val hm = if (Web.isHm(time)) time else ""
        if (existing != null) {
            val pos = dao.events().indexOfFirst { it.id == existing.id }.coerceAtLeast(0)
            dao.upsertEvent(existing.copy(title = t, date = date, time = hm, desc = d).toEntity(pos))
        } else {
            dao.upsertEvent(CalendarEvent(Web.uid(), t, d, date, hm, System.currentTimeMillis()).toEntity(dao.nextEventPos()))
        }
    }

    suspend fun deleteEvent(id: String) = dao.deleteEvent(id)

    // ── tasks ──
    /** Dashboard quick add: `state.tasks.unshift({id,title,done:false,due:'',createdAt})`. */
    suspend fun addQuickTask(title: String) {
        val t = title.trim().take(120)
        if (t.isEmpty()) return
        dao.upsertTask(Task(Web.uid(), t, false, "", System.currentTimeMillis()).toEntity(dao.firstTaskPos()))
    }

    /** Returns the new done state. */
    suspend fun toggleTask(id: String): Boolean {
        dao.toggleTask(id)
        return dao.tasks().firstOrNull { it.id == id }?.done ?: false
    }

    suspend fun deleteTask(id: String) = dao.deleteTask(id)

    // ── schedule (weekly study plan) ──
    suspend fun saveSchedule(existing: ScheduleEntry?, title: String, days: List<Int>, start: String, end: String) {
        val t = title.trim().take(120)
        val entry = (existing ?: ScheduleEntry(Web.uid(), t, days, start, end, emptyMap(), System.currentTimeMillis()))
            .copy(title = t, days = days.distinct().filter { it in 0..6 }, start = start, end = end)
        val pos = existing?.let { e -> dao.schedule().firstOrNull { it.id == e.id }?.position } ?: dao.firstSchedulePos()
        dao.upsertSchedule(entry.toEntity(pos))
    }

    /** Toggle today's occurrence: `x.doneDates[td] = !x.doneDates[td]`. */
    suspend fun toggleScheduleDone(id: String, date: String) {
        val all = dao.schedule()
        val i = all.indexOfFirst { it.id == id }
        if (i < 0) return
        val e = all[i].toDomain()
        val dd = e.doneDates.toMutableMap()
        dd[date] = !(dd[date] ?: false)
        dao.upsertSchedule(e.copy(doneDates = dd).toEntity(all[i].position))
    }

    suspend fun deleteSchedule(id: String) = dao.deleteSchedule(id)

    companion object {
        /** `scheduleForDate(ds)` — entries whose JS weekday matches the date. */
        fun scheduleFor(list: List<ScheduleEntry>, ymd: String): List<ScheduleEntry> {
            val jsDay = Web.parseYmd(ymd).dayOfWeek.value % 7
            return list.filter { jsDay in it.days }
        }

        /** Sat → Fri, the web's SCHED_DAY_ORDER. */
        val SCHED_DAY_ORDER = listOf(6, 0, 1, 2, 3, 4, 5)
    }
}

package com.ubad.academy.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ubad.academy.R
import com.ubad.academy.core.Feedback
import com.ubad.academy.core.Web
import com.ubad.academy.data.local.prefs.SettingsStore
import com.ubad.academy.data.repository.CourseRepository
import com.ubad.academy.data.repository.IslamRepository
import com.ubad.academy.data.repository.NoteRepository
import com.ubad.academy.data.repository.PlannerRepository
import com.ubad.academy.domain.model.CalendarEvent
import com.ubad.academy.domain.model.IslamState
import com.ubad.academy.domain.model.Note
import com.ubad.academy.ui.components.MessageQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A row in "Today's tasks": a manual task or today's occurrence of a schedule entry. */
data class TaskRow(
    val id: String,
    val title: String,
    val done: Boolean,
    val fromSchedule: Boolean,
    val due: String = "",
    val start: String = "",
    val end: String = "",
)

data class DashboardState(
    val name: String,
    val today: String,
    val prayersDone: Int,
    val courseCount: Int,
    val pendingCount: Int,
    val noteCount: Int,
    val tasks: List<TaskRow>,
    val upcoming: List<CalendarEvent>,
    val recentNotes: List<Note>,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    settings: SettingsStore,
    courses: CourseRepository,
    notes: NoteRepository,
    private val planner: PlannerRepository,
    islam: IslamRepository,
    private val feedback: Feedback,
) : ViewModel() {
    val messages = MessageQueue()

    private val plannerData = combine(planner.tasks, planner.schedule, planner.events) { t, s, e -> Triple(t, s, e) }

    val state: StateFlow<DashboardState?> = combine(
        settings.settings, courses.courses, notes.notes, plannerData, islam.state,
    ) { s, c, n, (tasks, schedule, events), isl ->
        val td = Web.today()
        val rows = tasks.map { TaskRow(it.id, it.title, it.done, false, due = it.due) } +
            PlannerRepository.scheduleFor(schedule, td).map {
                TaskRow(it.id, it.title, it.doneDates[td] == true, true, due = td, start = it.start, end = it.end)
            }
        DashboardState(
            name = s.name,
            today = td,
            prayersDone = prayersDone(isl),
            courseCount = c.size,
            pendingCount = rows.count { !it.done },
            noteCount = n.size,
            tasks = sortRows(rows).take(14),
            upcoming = events.filter { it.date >= td }.sortedBy { it.date + it.time }.take(5),
            recentNotes = n.take(3),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun addTask(title: String) = viewModelScope.launch {
        if (title.isBlank()) return@launch
        planner.addQuickTask(title)
        messages.send(R.string.toast_saved)
    }

    fun toggle(row: TaskRow) = viewModelScope.launch {
        if (row.fromSchedule) planner.toggleScheduleDone(row.id, Web.today())
        else if (planner.toggleTask(row.id)) feedback.celebrate()
    }

    fun delete(row: TaskRow) = viewModelScope.launch {
        if (row.fromSchedule) planner.deleteSchedule(row.id) else planner.deleteTask(row.id)
        messages.send(R.string.toast_deleted)
    }

    fun saveEvent(title: String, date: String, time: String, desc: String) = viewModelScope.launch {
        planner.saveEvent(null, title, date, time, desc)
        messages.send(R.string.toast_saved)
    }

    companion object {
        fun prayersDone(s: IslamState) = IslamState.PRAYER_KEYS.count { (s.prayers[it] ?: 0) > 0 }

        /** Open first; then by schedule time / due date (the web's exact string comparison). */
        fun sortRows(rows: List<TaskRow>): List<TaskRow> = rows.sortedWith { a, b ->
            if (a.done != b.done) return@sortedWith if (a.done) 1 else -1
            val at = if (a.fromSchedule) "${a.start}-${a.end}".ifEmpty { "99:99" } else a.due.ifEmpty { "9999" }
            val bt = if (b.fromSchedule) "${b.start}-${b.end}".ifEmpty { "99:99" } else b.due.ifEmpty { "9999" }
            at.compareTo(bt)
        }
    }
}

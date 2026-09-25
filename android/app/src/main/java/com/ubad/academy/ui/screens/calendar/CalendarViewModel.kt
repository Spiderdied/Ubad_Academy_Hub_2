package com.ubad.academy.ui.screens.calendar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.ubad.academy.R
import com.ubad.academy.core.Web
import com.ubad.academy.data.repository.PlannerRepository
import com.ubad.academy.domain.model.CalendarEvent
import com.ubad.academy.ui.components.MessageQueue
import com.ubad.academy.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    private val planner: PlannerRepository,
) : ViewModel() {
    val messages = MessageQueue()
    private val initial = handle.toRoute<Route.Calendar>().date?.takeIf { Web.isYmd(it) } ?: Web.today()

    val selected: StateFlow<String> = handle.getStateFlow("sel", initial)
    val month: StateFlow<String> = handle.getStateFlow("month", YearMonth.from(Web.parseYmd(initial)).toString())

    /** date → events of that day, sorted by time (web: `(a.time||'').localeCompare(b.time||'')`). */
    val eventsByDate: StateFlow<Map<String, List<CalendarEvent>>?> = planner.events.map { l ->
        l.groupBy { it.date }.mapValues { (_, v) -> v.sortedBy { it.time } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun select(ymd: String) { handle["sel"] = ymd }
    fun shift(months: Long) { handle["month"] = YearMonth.parse(month.value).plusMonths(months).toString() }
    fun goToday() {
        handle["sel"] = Web.today(); handle["month"] = YearMonth.from(LocalDate.now()).toString()
    }

    fun save(existing: CalendarEvent?, title: String, date: String, time: String, desc: String) = viewModelScope.launch {
        planner.saveEvent(existing, title, date, time, desc); messages.send(R.string.toast_saved)
    }

    fun delete(id: String) = viewModelScope.launch { planner.deleteEvent(id); messages.send(R.string.toast_deleted) }
}

package com.ubad.academy.ui.screens.study

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.ubad.academy.R
import com.ubad.academy.core.Feedback
import com.ubad.academy.data.local.prefs.SettingsStore
import com.ubad.academy.data.repository.PlannerRepository
import com.ubad.academy.data.repository.StudyRepository
import com.ubad.academy.domain.model.Deck
import com.ubad.academy.domain.model.FocusPhase
import com.ubad.academy.domain.model.FocusSettings
import com.ubad.academy.domain.model.LinkKind
import com.ubad.academy.domain.model.Quiz
import com.ubad.academy.domain.model.SavedLink
import com.ubad.academy.domain.model.ScheduleEntry
import com.ubad.academy.domain.model.TimerState
import com.ubad.academy.focus.FocusTimer
import com.ubad.academy.ui.components.MessageQueue
import com.ubad.academy.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class StudyTab(val key: String) {
    CARDS("cards"), QUIZZES("quizzes"), FOCUS("focus"), FORMS("forms"), SUMMARIES("summaries"), SCHEDULE("schedule");
    companion object { fun from(k: String?) = entries.firstOrNull { it.key == k } ?: CARDS }
}

@HiltViewModel
class StudyViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    private val study: StudyRepository,
    private val planner: PlannerRepository,
    private val settings: SettingsStore,
    val timer: FocusTimer,
    private val feedback: Feedback,
) : ViewModel() {
    val messages = MessageQueue()

    // Route tab (deep link study?tab=focus) wins on first open; afterwards the remembered tab.
    val tab: StateFlow<String> = handle.getStateFlow("studyTab", StudyTab.from(handle.toRoute<Route.Study>().tab).key)
    fun selectTab(t: StudyTab) { handle["studyTab"] = t.key; feedback.click() }

    private fun <T> stateOf(f: kotlinx.coroutines.flow.Flow<T>): StateFlow<T?> = f.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val decks: StateFlow<List<Deck>?> = stateOf(study.decks)
    val quizzes: StateFlow<List<Quiz>?> = stateOf(study.quizzes)
    val forms: StateFlow<List<SavedLink>?> = stateOf(study.links(LinkKind.FORMS).map(StudyRepository::sortLinks))
    val summaries: StateFlow<List<SavedLink>?> = stateOf(study.links(LinkKind.SUMMARIES).map(StudyRepository::sortLinks))
    val schedule: StateFlow<List<ScheduleEntry>?> = stateOf(planner.schedule)
    val timerState: StateFlow<TimerState?> = stateOf(timer.timer)
    val focus: StateFlow<FocusSettings?> = stateOf(timer.focus)

    // ── decks / quizzes ──
    fun createDeck(title: String) = viewModelScope.launch {
        if (title.isBlank()) return@launch
        study.createDeck(title); messages.send(R.string.toast_saved)
    }
    fun deleteDeck(id: String) = viewModelScope.launch { study.deleteDeck(id); messages.send(R.string.toast_deleted) }
    fun deleteQuiz(id: String) = viewModelScope.launch { study.deleteQuiz(id); messages.send(R.string.toast_deleted) }

    // ── links ──
    fun saveLink(kind: LinkKind, existing: SavedLink?, title: String, url: String, onOk: () -> Unit) = viewModelScope.launch {
        if (title.isBlank()) { messages.send(if (kind == LinkKind.FORMS) R.string.forms_needName else R.string.sum_needName, error = true); return@launch }
        if (!study.saveLink(kind, existing, title, url)) {
            messages.send(if (kind == LinkKind.FORMS) R.string.forms_invalidUrl else R.string.sum_invalidUrl, error = true); return@launch
        }
        messages.send(R.string.toast_saved); onOk()
    }
    fun togglePin(kind: LinkKind, id: String) = viewModelScope.launch { study.togglePin(kind, id) }
    fun markOpened(kind: LinkKind, id: String) = viewModelScope.launch { study.markOpened(kind, id) }
    fun deleteLink(kind: LinkKind, id: String) = viewModelScope.launch { study.deleteLink(kind, id); messages.send(R.string.toast_deleted) }

    // ── schedule ──
    fun saveSchedule(existing: ScheduleEntry?, title: String, days: List<Int>, start: String, end: String) = viewModelScope.launch {
        planner.saveSchedule(existing, title, days, start, end); messages.send(R.string.toast_saved)
    }

    // ── focus ──
    fun startPause(running: Boolean) = viewModelScope.launch { if (running) timer.pause() else timer.start(); feedback.click() }
    fun reset() = viewModelScope.launch { timer.reset(); feedback.click() }
    fun setDuration(phase: FocusPhase, mins: Int) = viewModelScope.launch { timer.setDuration(phase, mins); feedback.click() }
    fun timerNotificationsAllowed() = timer.notificationsAllowed()
    fun rescheduleIfRunning() { timer.reconcile() }
    fun tickDue() = viewModelScope.launch { timer.completeIfDue(fromAlarm = false) }

    suspend fun notificationAsked() = settings.notificationAsked()
    fun setNotificationAsked() = viewModelScope.launch { settings.setNotificationAsked() }
    suspend fun exactAlarmAsked() = settings.exactAlarmAsked()
    fun setExactAlarmAsked() = viewModelScope.launch { settings.setExactAlarmAsked() }
}

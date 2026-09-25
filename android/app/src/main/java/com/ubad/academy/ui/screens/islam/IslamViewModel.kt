package com.ubad.academy.ui.screens.islam

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ubad.academy.R
import com.ubad.academy.core.Feedback
import com.ubad.academy.core.Hijri
import com.ubad.academy.data.repository.IslamRepository
import com.ubad.academy.domain.model.IslamState
import com.ubad.academy.ui.components.MessageQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class IslamTab(val key: String) { PRAYERS("prayers"), SUNNAH("sunnah"), FASTING("fasting"), TASBIH("tasbih") }

/** Calendar facts for today, computed off the main thread (Umm al-Qura, offline). */
data class HijriInfo(
    val date: LocalDate,
    val ramadan: Hijri.Ramadan?,
    val upcoming: List<Hijri.UpcomingFast>,
    val isWhite: Boolean,
    val isMonThu: Boolean,
)

@HiltViewModel
class IslamViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    private val repo: IslamRepository,
    private val feedback: Feedback,
) : ViewModel() {
    val messages = MessageQueue()
    val state: StateFlow<IslamState?> = repo.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val tab: StateFlow<String> = handle.getStateFlow("islamTab", IslamTab.PRAYERS.key)
    fun selectTab(t: IslamTab) { handle["islamTab"] = t.key; feedback.click() }

    private val _hijri = MutableStateFlow<HijriInfo?>(null)
    val hijri: StateFlow<HijriInfo?> = _hijri.asStateFlow()

    /** Recomputed on resume so the page is right after midnight. */
    fun refreshCalendar() {
        val d = LocalDate.now()
        if (_hijri.value?.date == d) return
        viewModelScope.launch(Dispatchers.Default) {
            _hijri.value = HijriInfo(d, Hijri.ramadanInfo(d), Hijri.upcomingFasts(d), Hijri.isWhiteDay(d), Hijri.isMonThu(d))
        }
    }

    fun togglePrayer(key: String) = viewModelScope.launch {
        val before = state.value?.let { s -> IslamState.PRAYER_KEYS.count { (s.prayers[it] ?: 0) > 0 } } ?: 0
        val next = repo.togglePrayer(key)
        val after = IslamState.PRAYER_KEYS.count { (next.prayers[it] ?: 0) > 0 }
        if (after == 5 && before < 5) { feedback.celebrate(); messages.send(R.string.islam_prayersDone5) }
    }

    fun toggleRawatib(key: String) = viewModelScope.launch {
        val next = repo.toggleRawatib(key)
        if (IslamState.RAWATIB_KEYS.all { (next.rawatib[it] ?: 0) != 0 }) feedback.celebrate()
    }

    fun toggleFastToday() = viewModelScope.launch { repo.toggleFastToday() }

    fun tap() = viewModelScope.launch {
        val target = state.value?.tasbih?.target ?: 33
        if (repo.tasbihTap()) { messages.send(R.string.islam_tasbihDone, target); feedback.celebrate() }
    }
    fun select(id: String) = viewModelScope.launch { repo.tasbihSelect(id) }
    fun setTarget(t: Int) = viewModelScope.launch { repo.tasbihTarget(t) }
    fun resetCount() = viewModelScope.launch { repo.tasbihReset(); feedback.click() }
    fun saveDhikr(existingId: String?, text: String) = viewModelScope.launch {
        if (text.isBlank()) return@launch
        repo.tasbihSave(existingId, text); messages.send(R.string.toast_saved)
    }
    fun deleteDhikr(id: String) = viewModelScope.launch { repo.tasbihDelete(id) }
}

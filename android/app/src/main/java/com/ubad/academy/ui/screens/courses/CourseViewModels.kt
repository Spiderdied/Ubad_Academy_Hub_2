package com.ubad.academy.ui.screens.courses

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.ubad.academy.R
import com.ubad.academy.data.repository.CourseRepository
import com.ubad.academy.data.repository.CourseRepository.SaveResult
import com.ubad.academy.domain.model.ContentType
import com.ubad.academy.domain.model.Course
import com.ubad.academy.domain.model.CourseContent
import com.ubad.academy.domain.model.CourseUnit
import com.ubad.academy.ui.components.MessageQueue
import com.ubad.academy.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Loading wrapper: `null` while loading, then the value (which may itself be "not found"). */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
}

@HiltViewModel
class CoursesViewModel @Inject constructor(private val repo: CourseRepository) : ViewModel() {
    val messages = MessageQueue()
    val courses: StateFlow<Load<List<Course>>> = repo.courses.map { Load.Ready(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Load.Loading)

    fun save(existing: Course?, input: CourseRepository.CourseInput) = viewModelScope.launch {
        repo.saveCourse(existing, input); messages.send(R.string.toast_saved)
    }
}

@HiltViewModel
class CourseDetailViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repo: CourseRepository,
) : ViewModel() {
    val id = handle.toRoute<Route.CourseDetail>().id
    val messages = MessageQueue()

    /** Course plus its index (the web's accent colour cycles by list position). */
    val course: StateFlow<Load<Pair<Course, Int>?>> = repo.courses.map { l ->
        val i = l.indexOfFirst { it.id == id }
        Load.Ready(if (i >= 0) l[i] to i else null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Load.Loading)

    fun save(existing: Course, input: CourseRepository.CourseInput) = viewModelScope.launch {
        repo.saveCourse(existing, input); messages.send(R.string.toast_saved)
    }

    fun delete(c: Course, then: () -> Unit) = viewModelScope.launch { repo.deleteCourse(c); then() }

    fun addUnit(title: String) = viewModelScope.launch { repo.addUnit(id, title) }
}

@HiltViewModel
class UnitViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repo: CourseRepository,
) : ViewModel() {
    private val route = handle.toRoute<Route.UnitDetail>()
    val messages = MessageQueue()

    val unit: StateFlow<Load<Pair<Course, CourseUnit>?>> = repo.course(route.courseId).map { c ->
        val u = c?.units?.firstOrNull { it.id == route.unitId }
        Load.Ready(if (c != null && u != null) c to u else null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Load.Loading)

    /** Remembered active type tab (web `courseUnitTabs`), survives rotation/process death. */
    private val _tab = MutableStateFlow(handle.get<String>("tab")?.let { ContentType.from(it) })
    val tab: StateFlow<ContentType?> = _tab.asStateFlow()
    private val saved = handle
    fun selectTab(t: ContentType) { _tab.value = t; saved["tab"] = t.key }

    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()

    fun toggleDone(c: CourseContent) = viewModelScope.launch { repo.toggleDone(c) }
    fun deleteContent(c: CourseContent) = viewModelScope.launch { repo.deleteContent(c); messages.send(R.string.toast_deleted) }
    fun rename(title: String) = viewModelScope.launch { repo.renameUnit(route.unitId, title) }
    fun deleteUnit(u: CourseUnit, then: () -> Unit) = viewModelScope.launch { repo.deleteUnit(u); messages.send(R.string.toast_deleted); then() }

    /** Returns via [onDone] true when saved (dialog closes). */
    fun saveContent(editing: CourseContent?, input: CourseRepository.ContentInput, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        _saving.value = true
        val r = repo.saveContent(route.unitId, editing, input)
        _saving.value = false
        when (r) {
            SaveResult.Ok -> { messages.send(R.string.toast_saved); selectTab(input.type) }
            SaveResult.NeedTitle -> messages.send(R.string.courses_contentTitle, error = true)
            SaveResult.NeedText -> messages.send(R.string.courses_textPh, error = true)
            SaveResult.BadVideoUrl -> messages.send(R.string.courses_videoUrl, error = true)
            SaveResult.ChooseFile -> messages.send(R.string.courses_chooseFile, error = true)
            SaveResult.FileTooBig -> messages.send(R.string.courses_fileTooBig, error = true)
            SaveResult.BadFile -> messages.send(R.string.courses_badFile, error = true)
            SaveResult.Failed -> messages.send(R.string.toast_error, error = true)
        }
        onDone(r == SaveResult.Ok)
    }

    suspend fun exportAsset(id: String, dest: Uri) {
        if (repo.copyAssetTo(id, dest)) messages.send(R.string.toast_saved) else messages.send(R.string.toast_error, error = true)
    }

    fun assetFile(id: String) = repo.assetFile(id)
    fun uriFor(id: String): Uri = repo.uriFor(repo.assetFile(id))
}

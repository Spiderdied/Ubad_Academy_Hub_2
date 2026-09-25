package com.ubad.academy.ui.screens.viewer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.ubad.academy.core.pdf.PdfSession
import com.ubad.academy.data.local.files.FileStore
import com.ubad.academy.data.local.prefs.SettingsStore
import com.ubad.academy.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PdfViewerViewModel @Inject constructor(
    handle: SavedStateHandle,
    files: FileStore,
    private val settings: SettingsStore,
) : ViewModel() {
    private val route = handle.toRoute<Route.PdfViewer>()
    val title = route.title

    sealed interface State {
        data object Loading : State
        data class Ready(val session: PdfSession, val initialPage: Int) : State
        data object Missing : State
        data object Protected : State
        data object Broken : State
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state = _state.asStateFlow()
    private var session: PdfSession? = null
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            _state.value = when (val r = PdfSession.open(files.courseFile(route.assetId))) {
                is PdfSession.OpenResult.Ok -> {
                    session = r.session
                    val last = settings.pdfLastPage(route.assetId).coerceIn(0, r.session.pageCount - 1)
                    r.session.pageSize(0)
                    State.Ready(r.session, last)
                }
                PdfSession.OpenResult.Missing -> State.Missing
                PdfSession.OpenResult.Protected -> State.Protected
                PdfSession.OpenResult.Broken -> State.Broken
            }
        }
    }

    /** Debounced "remember last page" (web kept no position; this is the native addition). */
    fun onPageVisible(page: Int) {
        saveJob?.cancel()
        saveJob = viewModelScope.launch { delay(400); settings.setPdfLastPage(route.assetId, page) }
    }

    override fun onCleared() {
        session?.close()
        session = null
    }
}

package com.ubad.academy.ui.screens.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ubad.academy.R
import com.ubad.academy.core.Feedback
import com.ubad.academy.data.backup.BackupCodec
import com.ubad.academy.data.backup.BackupSection
import com.ubad.academy.data.backup.ParsedBackup
import com.ubad.academy.data.local.prefs.SettingsStore
import com.ubad.academy.data.repository.AppDataRepository
import com.ubad.academy.domain.model.AppLanguage
import com.ubad.academy.domain.model.ThemeId
import com.ubad.academy.domain.model.UserSettings
import com.ubad.academy.focus.FocusTimer
import com.ubad.academy.ui.components.MessageQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val appData: AppDataRepository,
    private val codec: BackupCodec,
    private val resolver: ContentResolver,
    private val feedback: Feedback,
    private val timer: FocusTimer,
) : ViewModel() {
    val messages = MessageQueue()
    val state: StateFlow<UserSettings?> = settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Which themes currently have a custom background (refreshes after changes). */
    private val bgTick = MutableStateFlow(0)
    val backgrounds: StateFlow<Set<ThemeId>> = bgTick.map { ThemeId.entries.filter(appData::hasBackground).toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** A parsed backup waiting for the user's section choice. */
    private val _pendingRestore = MutableStateFlow<ParsedBackup?>(null)
    val pendingRestore: StateFlow<ParsedBackup?> = _pendingRestore.asStateFlow()

    fun setLanguage(l: AppLanguage) = viewModelScope.launch { settings.setLanguage(l); feedback.click() }
    fun setTheme(t: ThemeId) = viewModelScope.launch { settings.setTheme(t) }
    fun setSound(on: Boolean) = viewModelScope.launch {
        settings.setSound(on)
        if (on) feedback.click()
    }

    fun saveName(v: String) = viewModelScope.launch {
        val n = v.trim()
        if (n.isEmpty()) { messages.send(R.string.set_needName, error = true); return@launch }
        settings.setName(n.take(40)); messages.send(R.string.set_nameSaved)
    }

    fun setBackground(theme: ThemeId, uri: Uri) = viewModelScope.launch {
        when (appData.setBackground(theme, uri)) {
            AppDataRepository.BgResult.OK -> messages.send(R.string.set_bgApplied)
            AppDataRepository.BgResult.BAD_TYPE -> messages.send(R.string.notes_badType, error = true)
            AppDataRepository.BgResult.TOO_BIG -> messages.send(R.string.notes_tooBig, error = true)
            AppDataRepository.BgResult.FAILED -> messages.send(R.string.toast_error, error = true)
        }
        bgTick.value++
    }
    fun removeBackground(theme: ThemeId) = viewModelScope.launch {
        appData.removeBackground(theme); bgTick.value++; messages.send(R.string.set_bgRemoved)
    }

    // ── backup ──
    fun export(uri: Uri, sections: Set<BackupSection>) = viewModelScope.launch {
        _busy.value = true
        try {
            val out = resolver.openOutputStream(uri, "wt") ?: error("no stream")
            out.use { codec.export(it, sections) }
            messages.send(R.string.set_exported)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            runCatching { android.provider.DocumentsContract.deleteDocument(resolver, uri) }
            messages.send(R.string.toast_error, error = true)
        } finally { _busy.value = false }
    }

    fun parseImport(uri: Uri) = viewModelScope.launch {
        _busy.value = true
        try {
            val parsed = (resolver.openInputStream(uri) ?: error("no stream")).use { codec.parse(it) }
            if (parsed.available.isEmpty()) { parsed.discard(); messages.send(R.string.set_importFailed, error = true) }
            else _pendingRestore.value = parsed
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            messages.send(R.string.set_importFailed, error = true)
        } finally { _busy.value = false }
    }

    fun cancelRestore() { _pendingRestore.value?.discard(); _pendingRestore.value = null }

    fun restore(sections: Set<BackupSection>, then: () -> Unit) {
        val parsed = _pendingRestore.value ?: return
        _pendingRestore.value = null
        viewModelScope.launch {
            _busy.value = true
            try {
                codec.restore(parsed, sections)
                appData.backgroundsChanged(); bgTick.value++
                timer.reconcile()
                feedback.toast(R.string.set_imported)
                then()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                messages.send(R.string.set_importFailed, error = true)
            } finally { _busy.value = false }
        }
    }

    fun wipe(then: () -> Unit) = viewModelScope.launch {
        _busy.value = true
        try {
            timer.reset()
            appData.wipeAll(); bgTick.value++
            feedback.toast(R.string.set_cleared)
            then()
        } finally { _busy.value = false }
    }

    override fun onCleared() { _pendingRestore.value?.discard() }
}

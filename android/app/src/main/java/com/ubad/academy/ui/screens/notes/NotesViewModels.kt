package com.ubad.academy.ui.screens.notes

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.toRoute
import com.ubad.academy.R
import com.ubad.academy.core.Web
import com.ubad.academy.data.repository.NoteRepository
import com.ubad.academy.data.repository.NoteRepository.AttachResult
import com.ubad.academy.domain.model.Note
import com.ubad.academy.domain.model.NoteAttachment
import com.ubad.academy.ui.components.MessageQueue
import com.ubad.academy.ui.navigation.Route
import com.ubad.academy.ui.screens.courses.Load
import com.ubad.academy.di.AppScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class NotesViewModel @Inject constructor(repo: NoteRepository) : ViewModel() {
    private val query = MutableStateFlow("")
    val q: StateFlow<String> = query.asStateFlow()
    fun setQuery(v: String) { query.value = v }

    /** Web filter: title + body + tags, case-insensitive substring. */
    val notes: StateFlow<Load<List<Note>>> = combine(repo.notes, query) { list, q ->
        val needle = q.lowercase()
        Load.Ready(if (needle.isEmpty()) list else list.filter { n -> (n.title + " " + n.body + " " + n.tags.joinToString(" ")).lowercase().contains(needle) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Load.Loading)
}

data class NoteDraft(
    val id: String,
    val title: String = "",
    val body: String = "",
    val tags: String = "",
    val pin: Boolean = false,
    val createdAt: Long,
    val images: List<NoteAttachment> = emptyList(),
    val audio: List<NoteAttachment> = emptyList(),
    val saved: Boolean = true,
    val exists: Boolean = false,
)

@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repo: NoteRepository,
    @ApplicationContext context: Context,
    @AppScope private val appScope: CoroutineScope,
) : ViewModel() {
    private val routeId = handle.toRoute<Route.NoteEditor>().id
    val messages = MessageQueue()

    private val _draft = MutableStateFlow<NoteDraft?>(null)
    val draft: StateFlow<NoteDraft?> = _draft.asStateFlow()
    private val pending = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            val ex = routeId?.let { repo.note(it) }
            _draft.value = if (ex != null) NoteDraft(
                ex.id, ex.title, ex.body, ex.tags.joinToString(", "), ex.pin, ex.createdAt, ex.images, ex.audio, exists = true,
            ) else NoteDraft(id = Web.uid(), createdAt = System.currentTimeMillis())
        }
    }

    private fun edit(f: (NoteDraft) -> NoteDraft) = _draft.update { d -> d?.let { f(it).copy(saved = false) } }
    fun setTitle(v: String) = edit { it.copy(title = v.take(120)) }
    fun setBody(v: String) = edit { it.copy(body = v.take(20_000)) }
    fun setTags(v: String) = edit { it.copy(tags = v) }
    fun togglePin() = edit { it.copy(pin = !it.pin) }
    fun removeImage(i: Int) = edit { d -> d.copy(images = d.images.filterIndexed { j, _ -> j != i }) }
    fun removeAudio(a: NoteAttachment) {
        if (playingId.value == a.fileId) stopAudio()
        edit { d -> d.copy(audio = d.audio - a) }
    }

    fun attach(uris: List<Uri>, kind: NoteRepository.Kind) = viewModelScope.launch {
        for (u in uris) when (val r = repo.importAttachment(u, kind)) {
            is AttachResult.Ok -> {
                pending += r.attachment.fileId
                edit { d -> if (kind == NoteRepository.Kind.IMAGE) d.copy(images = d.images + r.attachment) else d.copy(audio = d.audio + r.attachment) }
            }
            AttachResult.TooBig -> messages.send(R.string.notes_tooBig, error = true)
            AttachResult.BadType -> messages.send(R.string.notes_badType, error = true)
            AttachResult.Failed -> messages.send(R.string.toast_error, error = true)
        }
    }

    fun save(then: () -> Unit) = viewModelScope.launch {
        val d = _draft.value ?: return@launch
        repo.save(
            Note(
                id = d.id, title = d.title, body = d.body, tags = NoteRepository.parseTags(d.tags), pin = d.pin,
                createdAt = d.createdAt, updatedAt = System.currentTimeMillis(), images = d.images, audio = d.audio,
            ),
        )
        pending.clear()
        _draft.update { it?.copy(saved = true, exists = true) }
        messages.send(R.string.toast_saved)
        then()
    }

    fun discard(then: () -> Unit) = viewModelScope.launch {
        repo.discardPending(pending.toList()); pending.clear()
        _draft.update { it?.copy(saved = true) }
        then()
    }

    fun delete(then: () -> Unit) = viewModelScope.launch {
        _draft.value?.let { repo.delete(it.id) }
        repo.discardPending(pending.toList()); pending.clear()
        messages.send(R.string.toast_deleted)
        then()
    }

    fun file(a: NoteAttachment): File = repo.file(a)

    // ── Inline audio (web used an <audio controls> per attachment; one plays at a time) ──
    private val player: ExoPlayer = ExoPlayer.Builder(context).setHandleAudioBecomingNoisy(true).build()
    private val _playingId = MutableStateFlow<String?>(null)
    val playingId: StateFlow<String?> = _playingId.asStateFlow()
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()
    private val _progress = MutableStateFlow(0f to 0L)
    /** (fraction 0..1, duration ms) of the current item. */
    val progress = _progress.asStateFlow()

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { _isPlaying.value = isPlaying }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) { player.pause(); player.seekTo(0) }
            }
        })
        viewModelScope.launch {
            while (isActive) {
                val d = player.duration.takeIf { it > 0 } ?: 0L
                _progress.value = (if (d > 0) player.currentPosition.toFloat() / d else 0f) to d
                delay(if (_isPlaying.value) 250 else 600)
            }
        }
    }

    fun toggleAudio(a: NoteAttachment) {
        if (_playingId.value == a.fileId) {
            if (player.isPlaying) player.pause() else player.play()
            return
        }
        _playingId.value = a.fileId
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(repo.file(a))))
        player.prepare(); player.play()
    }

    fun seek(fraction: Float) { player.duration.takeIf { it > 0 }?.let { player.seekTo((it * fraction).toLong()) } }
    fun pauseAudio() = player.pause()
    private fun stopAudio() { player.stop(); _playingId.value = null }

    override fun onCleared() {
        player.release()
        // Editor closed without saving (e.g. process/task removal): drop orphaned imports.
        if (pending.isNotEmpty()) {
            val ids = pending.toList()
            appScope.launch { repo.discardPending(ids) }
        }
    }
}

package com.ubad.academy.ui.screens.blog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.ubad.academy.core.BlogHtml
import com.ubad.academy.data.repository.BlogRepository
import com.ubad.academy.domain.model.BlogPost
import com.ubad.academy.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

/** A post prepared for the list (HTML parsed once, off the main thread). */
data class BlogCard(val post: BlogPost, val title: String, val excerpt: String, val searchText: String)

@HiltViewModel
class BlogViewModel @Inject constructor(private val repo: BlogRepository) : ViewModel() {
    private val cards: StateFlow<List<BlogCard>?> = repo.posts
        .map { l -> l.map { p -> val text = BlogHtml.text(p.content); BlogCard(p, BlogHtml.title(p.title, p.content), if (text.length > 190) text.take(187) + "…" else text, "${p.title} $text".lowercase(Locale.ROOT)) } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    fun setQuery(q: String) { _query.value = q }

    val visible: StateFlow<List<BlogCard>?> = combine(cards, _query) { c, q ->
        val needle = q.trim().lowercase(Locale.ROOT)
        c?.filter { needle.isEmpty() || it.searchText.contains(needle) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val total: StateFlow<Int> = cards.map { it?.size ?: 0 }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val status: StateFlow<BlogRepository.Status> = repo.status
    val nextPageToken: StateFlow<String?> = repo.nextPageToken.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // Web: `if(!blogState.loaded) await blogLoad()` — once per app session.
        if (!repo.status.value.loadedThisSession) refresh()
    }

    fun refresh() { viewModelScope.launch { repo.load() } }
    fun loadMore() { viewModelScope.launch { repo.loadMore() } }
}

@HiltViewModel
class BlogPostViewModel @Inject constructor(handle: SavedStateHandle, private val repo: BlogRepository) : ViewModel() {
    val id = handle.toRoute<Route.BlogPost>().id

    data class Article(val post: BlogPost, val title: String, val blocks: List<BlogHtml.Block>)

    /** null = loading; Article(null…) never — a missing post is [missing]. */
    private val _article = MutableStateFlow<Article?>(null)
    val article: StateFlow<Article?> = _article.asStateFlow()
    private val _missing = MutableStateFlow(false)
    val missing: StateFlow<Boolean> = _missing.asStateFlow()

    init { load() }

    private fun load() = viewModelScope.launch {
        var p = repo.post(id)
        // Opened from a deep link before the list was ever fetched.
        if (p == null && !repo.status.value.loadedThisSession) { repo.load(); p = repo.post(id) }
        if (p == null) { _missing.value = true; return@launch }
        _article.value = withContext(Dispatchers.Default) { Article(p, BlogHtml.title(p.title, p.content), BlogHtml.blocks(p.content)) }
    }
}

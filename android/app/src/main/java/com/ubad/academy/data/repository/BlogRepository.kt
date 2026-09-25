package com.ubad.academy.data.repository

import com.ubad.academy.core.BlogHtml
import com.ubad.academy.data.local.db.BlogPostEntity
import com.ubad.academy.data.local.db.UbadDatabase
import com.ubad.academy.data.local.prefs.SettingsStore
import com.ubad.academy.data.remote.BlogApi
import com.ubad.academy.domain.model.BlogPost
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `blogLoad()` + the offline cache (web: localStorage `ubad_blog_cache_v1`; here: Room +
 * DataStore). A failed fetch keeps showing the last saved posts.
 */
@Singleton
class BlogRepository @Inject constructor(
    private val api: BlogApi,
    private val db: UbadDatabase,
    private val settings: SettingsStore,
) {
    data class Status(val loading: Boolean = false, val error: Boolean = false, val loadedThisSession: Boolean = false)

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()
    private val lock = Mutex()

    val posts: Flow<List<BlogPost>> = db.blog().observe().map { l -> l.map { it.toDomain() } }
    val nextPageToken: Flow<String?> = settings.blogNextToken

    suspend fun post(id: String): BlogPost? = db.blog().post(id)?.toDomain()

    /** First page (or refresh) replaces the cache; [pageToken] appends. */
    suspend fun load(pageToken: String? = null) {
        if (!lock.tryLock()) return // web: `if(blogState.loading) return`
        try {
            _status.update { it.copy(loading = true, error = false) }
            val page = api.page(pageToken)
            val incoming = page.posts.orEmpty().filter { !it.id.isNullOrBlank() }
            val start = if (pageToken == null) 0 else db.blog().nextPos()
            val entities = incoming.mapIndexed { i, p -> p.toEntity(start + i) }
            if (pageToken == null) db.blog().replaceAll(entities) else db.blog().insert(entities)
            settings.setBlogMeta(page.nextPageToken?.takeIf { it.isNotBlank() }, System.currentTimeMillis())
            _status.update { Status(loading = false, error = false, loadedThisSession = true) }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            _status.update { it.copy(loading = false, error = true) }
        } finally {
            lock.unlock()
        }
    }

    suspend fun loadMore() { nextPageToken.first()?.let { load(it) } }

    private fun BlogApi.Post.toEntity(pos: Int) = BlogPostEntity(
        id = id!!, title = title.orEmpty(), content = content.orEmpty(), published = published, updated = updated,
        url = BlogHtml.absUrl(url),
        imageUrl = images?.firstNotNullOfOrNull { BlogHtml.absUrl(it.url) } ?: BlogHtml.firstImage(content),
        position = pos,
    )

    private fun BlogPostEntity.toDomain() = BlogPost(id, title, content, published, updated, url, imageUrl)
}


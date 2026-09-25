package com.ubad.academy.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.CacheControl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The existing public Cloudflare Worker (`BLOG_API_URL`). It holds the Blogger API key
 * server-side; the app never sees or ships a key.
 */
@Singleton
class BlogApi internal constructor(private val client: OkHttpClient, private val baseUrl: String) {
    @Inject constructor(client: OkHttpClient) : this(client, BASE_URL)

    @Serializable data class Image(val url: String? = null)
    @Serializable data class Post(
        val id: String? = null,
        val title: String? = null,
        val content: String? = null,
        val published: String? = null,
        val updated: String? = null,
        val url: String? = null,
        val images: List<Image>? = null,
    )
    @Serializable data class Page(val posts: List<Post>? = null, val nextPageToken: String? = null)

    class HttpException(val code: Int) : IOException("HTTP $code")

    suspend fun page(pageToken: String? = null): Page = withContext(Dispatchers.IO) {
        val url = baseUrl.toHttpUrl().newBuilder().apply { if (pageToken != null) addQueryParameter("pageToken", pageToken) }.build()
        val req = Request.Builder().url(url).header("Accept", "application/json").cacheControl(CacheControl.FORCE_NETWORK).build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw HttpException(res.code)
            val body = res.body?.string() ?: throw IOException("empty body")
            JSON.decodeFromString(Page.serializer(), body)
        }
    }

    companion object {
        const val BASE_URL = "https://ubad-blog-api.abdalla-toaila34.workers.dev"
        private val JSON = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
    }
}

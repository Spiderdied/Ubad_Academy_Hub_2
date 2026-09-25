package com.ubad.academy.core

import java.net.URI
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/**
 * Faithful Kotlin ports of the small helpers in app.js (§1 utilities, link
 * validation, YouTube parsing). Keeping identical semantics matters because the
 * same rules are applied when importing a web backup.
 */
object Web {
    private val YMD = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    private val HM = Regex("^\\d{2}:\\d{2}$")
    private val YT_ID = Regex("^[A-Za-z0-9_-]{6,20}$")
    private val YT_PATH = Regex("^/(?:shorts|embed|live)/([A-Za-z0-9_-]{6,20})")
    private val SCHEME = Regex("^[a-z][a-z0-9+.-]*:", RegexOption.IGNORE_CASE)
    private val HTTP = Regex("^https?://", RegexOption.IGNORE_CASE)

    /** `uid()` — Date.now().toString(36) + 6 random base-36 chars. */
    fun uid(): String {
        val rand = buildString { repeat(6) { append("0123456789abcdefghijklmnopqrstuvwxyz"[Random.nextInt(36)]) } }
        return System.currentTimeMillis().toString(36) + rand
    }

    fun isYmd(s: String?): Boolean = s != null && YMD.matches(s)
    fun isHm(s: String?): Boolean = s != null && HM.matches(s)

    fun ymd(d: LocalDate): String = d.format(DateTimeFormatter.ISO_LOCAL_DATE)
    fun today(): String = ymd(LocalDate.now())
    fun parseYmd(s: String): LocalDate = LocalDate.parse(s)

    fun hmToMin(v: String?): Int? {
        if (v == null || !HM.matches(v)) return null
        return v.substring(0, 2).toInt() * 60 + v.substring(3, 5).toInt()
    }

    fun minToHm(n: Int): String {
        val c = n.coerceIn(0, 1439)
        return "%02d:%02d".format(c / 60, c % 60)
    }

    /** `safeHttpUrl` — only http(s) with a dotted host survives; bare domains get https://. */
    fun safeHttpUrl(raw: String?): String? {
        var s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null
        if (!HTTP.containsMatchIn(s)) {
            if (SCHEME.containsMatchIn(s)) return null
            s = "https://$s"
        }
        val uri = runCatching { URI(s) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host ?: return null
        if (!host.contains('.')) return null
        // Mirror URL.href normalisation for the common case (lower-case scheme/host,
        // root path) so round-tripped URLs stay byte-identical to the web app.
        val path = if (uri.rawPath.isNullOrEmpty()) "/" else uri.rawPath
        val port = if (uri.port == -1) "" else ":${uri.port}"
        val user = uri.rawUserInfo?.let { "$it@" } ?: ""
        val query = uri.rawQuery?.let { "?$it" } ?: ""
        val frag = uri.rawFragment?.let { "#$it" } ?: ""
        return "$scheme://$user${host.lowercase()}$port$path$query$frag"
    }

    fun youtubeVideoId(raw: String?): String? {
        val href = safeHttpUrl(raw) ?: return null
        val u = runCatching { URI(href) }.getOrNull() ?: return null
        val host = u.host.lowercase().removePrefix("www.")
        val path = u.rawPath.orEmpty()
        if (host == "youtu.be") {
            val id = path.split('/').firstOrNull { it.isNotEmpty() }.orEmpty()
            return id.takeIf { YT_ID.matches(it) }
        }
        if (host == "youtube.com" || host == "m.youtube.com" || host == "music.youtube.com") {
            if (path == "/watch") {
                val v = u.rawQuery.orEmpty().split('&')
                    .map { it.split('=', limit = 2) }
                    .firstOrNull { it[0] == "v" }?.getOrNull(1).orEmpty()
                return v.takeIf { YT_ID.matches(it) }
            }
            return YT_PATH.find(path)?.groupValues?.get(1)
        }
        return null
    }

    /** Canonical watch URL used for handing the video to the YouTube app / browser. */
    fun youtubeWatchUrl(id: String) = "https://www.youtube.com/watch?v=$id"
    fun youtubeThumb(id: String) = "https://img.youtube.com/vi/$id/hqdefault.jpg"
}

/** `scheduleTimeLabel(hm)` — 12-hour clock with ص/م in Arabic, AM/PM in English. */
fun scheduleTimeLabel(hm: String, arabic: Boolean): String {
    val n = Web.hmToMin(hm) ?: return hm
    val h = n / 60
    val m = n % 60
    val am = h < 12
    val hh = if (h % 12 == 0) 12 else h % 12
    val suffix = if (arabic) (if (am) "ص" else "م") else (if (am) "AM" else "PM")
    return "$hh:${m.toString().padStart(2, '0')} $suffix"
}

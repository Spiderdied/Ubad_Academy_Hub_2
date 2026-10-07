package com.ubad.academy.core.cloud

/**
 * Cloud object keys, a line-by-line port of the web's key building in
 * `buildCloudFileManifest()` (`app.js`).
 *
 * The layout under the user's prefix is shared with the web app, so a file
 * uploaded by either client is downloadable by the other:
 *
 * ```
 * users/<UID>/notes/<noteId>/images/<index>-<name>
 * users/<UID>/notes/<noteId>/audio/<index>-<name>
 * users/<UID>/courseAssets/<assetId>
 * users/<UID>/backgrounds/<theme>
 * ```
 *
 * The Worker independently re-derives the UID from the Firebase ID token and
 * rejects anything outside `users/<uid>/` (see `storage-backend/worker.js`), so
 * these helpers are about keeping the two clients in agreement, not about
 * enforcing security on the client.
 */
object CloudPaths {

    const val KIND_NOTE_IMAGE = "note-image"
    const val KIND_NOTE_AUDIO = "note-audio"
    const val KIND_COURSE_ASSET = "course-asset"
    const val KIND_BACKGROUND = "background"

    /** All kinds the sync engine knows how to mirror. */
    val kinds = listOf(KIND_NOTE_IMAGE, KIND_NOTE_AUDIO, KIND_COURSE_ASSET, KIND_BACKGROUND)

    fun userPrefix(uid: String): String = "users/${uid.trim()}/"

    fun noteImage(uid: String, noteId: String, index: Int, name: String?): String =
        "${userPrefix(uid)}notes/$noteId/images/$index-${safeFileName(name)}"

    fun noteAudio(uid: String, noteId: String, index: Int, name: String?): String =
        "${userPrefix(uid)}notes/$noteId/audio/$index-${safeFileName(name)}"

    fun courseAsset(uid: String, assetId: String): String =
        "${userPrefix(uid)}courseAssets/${safeFileName(assetId)}"

    fun background(uid: String, theme: String): String =
        "${userPrefix(uid)}backgrounds/${safeFileName(theme)}"

    /** Cleans a file name for a key segment, exactly like the web `safeFileName`. */
    fun safeFileName(value: String?): String {
        val raw = value?.takeIf { it.isNotEmpty() } ?: "file"
        val cleaned = raw.map { ch ->
            if (ch.isLetterOrDigit() && ch.code < 128 || ch == '.' || ch == '_' || ch == '-') ch else '_'
        }.joinToString("")
        return cleaned.take(100).ifEmpty { "_" }
    }

    /**
     * Client-side guard mirroring the Worker's `userPath`: the key must sit
     * strictly inside this user's prefix and must not contain a traversal
     * segment. The Worker enforces this regardless; failing early here keeps
     * obviously-wrong requests off the network.
     */
    fun isWithinUserScope(uid: String, key: String): Boolean {
        val prefix = userPrefix(uid)
        if (!key.startsWith(prefix) || key.length <= prefix.length) return false
        if (key.contains('\u0000')) return false
        return key.split('/').none { it == ".." }
    }
}

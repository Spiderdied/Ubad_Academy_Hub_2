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

    /*
     * Object keys are RELATIVE, exactly as the web stores them in the Firestore
     * file manifest (`buildCloudFileManifest` in app.js):
     *
     *     notes/<noteId>/images/<index>-<name>
     *     notes/<noteId>/audio/<index>-<name>
     *     courseAssets/<assetId>
     *     backgrounds/<theme>
     *
     * The `users/<uid>/` prefix is a *transport* detail: the web prepends it at
     * the Worker call site (`uploadFile(`users/${uid}/${f.key}`)`), and so does
     * CloudFileApi here. Never bake it into a stored key — the web would then
     * request `users/<uid>/users/<uid>/…` and every file would 404.
     */
    fun noteImage(noteId: String, index: Int, name: String?): String =
        "notes/$noteId/images/$index-${safeFileName(name)}"

    fun noteAudio(noteId: String, index: Int, name: String?): String =
        "notes/$noteId/audio/$index-${safeFileName(name)}"

    fun courseAsset(assetId: String): String = "courseAssets/${safeFileName(assetId)}"

    fun background(theme: String): String = "backgrounds/${safeFileName(theme)}"

    /** Full Worker object path for a relative key, validated against [uid]'s scope. */
    fun workerPath(uid: String, key: String): String {
        val prefix = userPrefix(uid)
        val relative = key.trimStart('/')
        /*
         * Tolerate a key that already carries the prefix instead of doubling it.
         *
         * An interim build of this app stored FULL paths in `CloudFileEntry.key`
         * before the web's relative convention was restored. A document written
         * by that build is still out there, and prefixing it again would make
         * every one of its files 404. Treating both conventions as valid is
         * exactly the "preserve, never destroy" behaviour the data-migration
         * rules call for, and it costs one comparison.
         */
        return if (relative.startsWith(prefix)) relative else prefix + relative
    }

    /** Cleans a file name for a key segment, exactly like the web `safeFileName`. */
    fun safeFileName(value: String?): String {
        val raw = value?.takeIf { it.isNotEmpty() } ?: "file"
        // JS: String(v||'file').replace(/[^a-zA-Z0-9._-]+/g,'_').slice(0,100)
        // A *run* of disallowed characters collapses to ONE underscore, so a
        // non-ASCII name maps to the same key on both clients. Replacing each
        // character individually ("ملف" -> "___" instead of "_") would send the
        // same file to two different objects and leave orphans behind.
        val sb = StringBuilder(raw.length)
        var pendingUnderscore = false
        for (ch in raw) {
            val allowed = (ch.code < 128 && ch.isLetterOrDigit()) || ch == '.' || ch == '_' || ch == '-'
            if (allowed) {
                if (pendingUnderscore) { sb.append('_'); pendingUnderscore = false }
                sb.append(ch)
            } else {
                pendingUnderscore = true
            }
        }
        if (pendingUnderscore) sb.append('_')
        return sb.toString().take(100).ifEmpty { "file" }
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

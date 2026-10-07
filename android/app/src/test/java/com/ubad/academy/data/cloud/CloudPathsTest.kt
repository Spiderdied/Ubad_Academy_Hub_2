package com.ubad.academy.data.cloud

import com.ubad.academy.core.cloud.CloudPaths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the cloud path/key contract.
 *
 * These assertions are the cheap half of the web-interop guarantee: the
 * Firestore document is written by both clients, so a key produced here must be
 * byte-identical to `buildCloudFileManifest()` in `app.js`. If someone
 * "helpfully" adds the `users/<uid>/` prefix back into a stored key, the web
 * would request `users/<uid>/users/<uid>/…` and every file download would 404 —
 * so that specific regression is pinned below.
 */
class CloudPathsTest {

    private val uid = "kJ8sQ2mVbNa7ExampleUid42"

    // ── keys are relative: the web prefixes the uid at the Worker call site ──

    @Test
    fun `note image key matches the web manifest shape`() {
        val key = CloudPaths.noteImage("n1", 0, "photo.png")
        assertEquals("notes/n1/images/0-photo.png", key)
    }

    @Test
    fun `note audio key matches the web manifest shape`() {
        val key = CloudPaths.noteAudio("n1", 3, "voice.m4a")
        assertEquals("notes/n1/audio/3-voice.m4a", key)
    }

    @Test
    fun `course asset key matches the web manifest shape`() {
        assertEquals("courseAssets/a1", CloudPaths.courseAsset("a1"))
    }

    @Test
    fun `background key matches the web manifest shape`() {
        assertEquals("backgrounds/dark", CloudPaths.background("dark"))
    }

    @Test
    fun `stored keys never contain the user prefix`() {
        // The regression guard described in the class comment.
        val keys = listOf(
            CloudPaths.noteImage("n1", 0, "a.png"),
            CloudPaths.noteAudio("n1", 0, "a.m4a"),
            CloudPaths.courseAsset("a1"),
            CloudPaths.background("dark"),
        )
        keys.forEach { assertFalse("stored key must stay relative: $it", it.startsWith("users/")) }
    }

    @Test
    fun `workerPath adds the user prefix to a relative key`() {
        val path = CloudPaths.workerPath(uid, CloudPaths.noteImage("n1", 0, "a.png"))
        assertEquals("users/$uid/notes/n1/images/0-a.png", path)
    }

    @Test
    fun `workerPath is idempotent for a key that already carries the prefix`() {
        // Documents written by an interim Android build stored full paths. Those
        // keys must keep working rather than doubling the prefix into a 404.
        val full = "users/$uid/notes/n1/images/0-a.png"
        assertEquals(full, CloudPaths.workerPath(uid, full))
        assertEquals(full, CloudPaths.workerPath(uid, CloudPaths.workerPath(uid, CloudPaths.noteImage("n1", 0, "a.png"))))
        // A leading slash is normalised, not doubled.
        assertEquals(full, CloudPaths.workerPath(uid, "/notes/n1/images/0-a.png"))
    }

    // ── file name sanitising, mirroring the web `safeFileName` ──

    /*
     * Expectations below were produced by running the web implementation
     * (`function safeFileName(v){ return String(v||'file').replace(/[^a-zA-Z0-9._-]+/g,'_').slice(0,100); }`)
     * under node, so the two clients are pinned to the same output.
     */

    @Test
    fun `safeFileName keeps the portable character set`() {
        assertEquals("unit-1_notes.pdf", CloudPaths.safeFileName("unit-1_notes.pdf"))
        assertEquals("image.png", CloudPaths.safeFileName("image.png"))
    }

    @Test
    fun `safeFileName collapses a run of disallowed characters to one underscore`() {
        // The critical parity case: "ملف.pdf" must NOT become "___pdf" or "_.pdf"
        // by accident of per-character replacement. JS collapses the run.
        assertEquals("_.pdf", CloudPaths.safeFileName("ملف.pdf"))
        assertEquals("_", CloudPaths.safeFileName("ملف"))
        assertEquals("_.png", CloudPaths.safeFileName("صورة ١.png"))
        assertEquals("a_b.pdf", CloudPaths.safeFileName("a  b.pdf"))
    }

    @Test
    fun `safeFileName handles null and empty input like the web fallback`() {
        assertEquals("file", CloudPaths.safeFileName(null))
        assertEquals("file", CloudPaths.safeFileName(""))
        // Whitespace is truthy in JS, so it becomes a single underscore.
        assertEquals("_", CloudPaths.safeFileName("   "))
    }

    @Test
    fun `safeFileName never exceeds 100 characters`() {
        val out = CloudPaths.safeFileName("a".repeat(400) + ".pdf")
        assertEquals(100, out.length)
    }

    // ── scope check ──

    @Test
    fun `isWithinUserScope accepts own paths`() {
        assertTrue(CloudPaths.isWithinUserScope(uid, "users/$uid/notes/n1/images/0-a.png"))
        assertTrue(CloudPaths.isWithinUserScope(uid, "users/$uid/backgrounds/dark"))
    }

    @Test
    fun `isWithinUserScope rejects other users and escapes`() {
        assertFalse(CloudPaths.isWithinUserScope(uid, "users/someoneelse/notes/n1/images/0-a.png"))
        assertFalse(CloudPaths.isWithinUserScope(uid, "users/$uid/../someoneelse/secret"))
        assertFalse(CloudPaths.isWithinUserScope(uid, "users/$uid/"))
        assertFalse(CloudPaths.isWithinUserScope(uid, "users/$uid"))
        // A prefix that merely *starts* the same must not pass.
        assertFalse(CloudPaths.isWithinUserScope(uid, "users/${uid}Extra/x"))
    }

    @Test
    fun `isWithinUserScope rejects null bytes`() {
        assertFalse(CloudPaths.isWithinUserScope(uid, "users/$uid/notes/\u0000bad"))
    }
}

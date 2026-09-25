package com.ubad.academy.data.local.files

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.ubad.academy.domain.model.ThemeId
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-private binary storage (replaces the web IndexedDB blob stores).
 * No storage permissions are needed; files are shared outward only via FileProvider.
 *
 *   filesDir/course_assets/<id>    ← IndexedDB `courseAssets`
 *   filesDir/note_files/<fileId>   ← note images/audio blobs
 *   filesDir/backgrounds/bg-<theme>← kv `bg-<theme>`
 */
@Singleton
class FileStore @Inject constructor(@ApplicationContext private val context: Context) {

    val courseDir: File get() = dir("course_assets")
    val noteDir: File get() = dir("note_files")
    val bgDir: File get() = dir("backgrounds")
    val tmpDir: File get() = File(context.cacheDir, "tmp").apply { mkdirs() }
    val shareDir: File get() = File(context.cacheDir, "shared").apply { mkdirs() }

    private fun dir(name: String) = File(context.filesDir, name).apply { mkdirs() }

    fun courseFile(assetId: String) = File(courseDir, safeName(assetId))
    fun noteFile(fileId: String) = File(noteDir, safeName(fileId))
    fun backgroundFile(theme: ThemeId) = File(bgDir, "bg-${theme.key}")

    fun newTempFile(prefix: String = "in"): File = File.createTempFile("ubad_$prefix", ".bin", tmpDir)

    /** Copies [input] into [target] atomically (temp file + rename). Returns bytes written. */
    fun writeAtomically(target: File, input: InputStream): Long {
        val tmp = newTempFile("w")
        try {
            val n = tmp.outputStream().use { out -> input.copyTo(out, BUFFER) }
            moveInto(tmp, target)
            return n
        } finally {
            tmp.delete()
        }
    }

    fun moveInto(src: File, target: File) {
        target.parentFile?.mkdirs()
        if (target.exists()) target.delete()
        if (!src.renameTo(target)) {
            src.inputStream().use { i -> target.outputStream().use { o -> i.copyTo(o, BUFFER) } }
            src.delete()
        }
    }

    fun uriFor(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    fun clearDir(d: File) { d.listFiles()?.forEach { it.deleteRecursively() } }

    fun clearAll() {
        clearDir(courseDir); clearDir(noteDir); clearDir(bgDir); clearDir(tmpDir); clearDir(shareDir)
    }

    companion object {
        const val BUFFER = 64 * 1024
        private val SAFE = Regex("^[A-Za-z0-9_-]{1,80}$")

        /** Web ids are `uid()` strings; anything else is hashed so it can never escape the dir. */
        fun safeName(id: String): String =
            if (SAFE.matches(id)) id
            else "h_" + MessageDigest.getInstance("SHA-256").digest(id.toByteArray())
                .joinToString("") { "%02x".format(it) }.take(40)
    }
}

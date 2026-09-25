package com.ubad.academy.data.repository

import com.ubad.academy.data.local.db.UbadDatabase
import com.ubad.academy.data.local.files.FileStore
import com.ubad.academy.data.local.prefs.SettingsStore
import com.ubad.academy.domain.model.ThemeId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import android.content.ContentResolver
import android.net.Uri
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Cross-cutting data operations: theme backgrounds and "erase all data". */
@Singleton
class AppDataRepository @Inject constructor(
    private val db: UbadDatabase,
    private val files: FileStore,
    private val settings: SettingsStore,
    private val resolver: ContentResolver,
) {
    private val bgVersion = MutableStateFlow(0)

    /** Current theme's custom background file (web `bgApply`). */
    val background: Flow<File?> = combine(settings.settings, bgVersion) { s, _ ->
        files.backgroundFile(s.theme).takeIf { it.exists() && it.length() > 0 }
    }

    fun hasBackground(theme: ThemeId) = files.backgroundFile(theme).exists()

    enum class BgResult { OK, BAD_TYPE, TOO_BIG, FAILED }

    /** `uploadBg(th,file)`: images only, max 5 MB. */
    suspend fun setBackground(theme: ThemeId, uri: Uri): BgResult = withContext(Dispatchers.IO) {
        if (resolver.getType(uri)?.startsWith("image/") != true) return@withContext BgResult.BAD_TYPE
        val target = files.backgroundFile(theme)
        val n = runCatching {
            resolver.openInputStream(uri)?.use { files.writeAtomically(target, LimitedInputStream(it, MAX_BG + 1)) }
        }.getOrNull() ?: return@withContext BgResult.FAILED
        if (n > MAX_BG) { target.delete(); return@withContext BgResult.TOO_BIG }
        bgVersion.value++
        BgResult.OK
    }

    suspend fun removeBackground(theme: ThemeId) = withContext(Dispatchers.IO) {
        files.backgroundFile(theme).delete(); bgVersion.value++
    }

    /** Called after a restore touches backgrounds. */
    fun backgroundsChanged() { bgVersion.value++ }

    /** `wipeAll()` — clears every store and resets preferences (onboarding stays done). */
    suspend fun wipeAll() = withContext(Dispatchers.IO) {
        db.clearAllTables()
        files.clearAll()
        settings.wipe()
        bgVersion.value++
    }

    companion object { const val MAX_BG = 5L * 1024 * 1024 }
}

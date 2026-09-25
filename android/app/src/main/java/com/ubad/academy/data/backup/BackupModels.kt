package com.ubad.academy.data.backup

import java.io.File
import kotlinx.serialization.json.JsonElement

/** `BACKUP_SECTIONS` — ids are written into `sections` exactly like the web. */
enum class BackupSection(val id: String) {
    USER("user"), COURSES("courses"), NOTES("notes"), CALENDAR("calendar"), STUDY("study"),
    ISLAM("islam"), SUMMARIES("summaries"), FORMS("forms"), BACKGROUND("background");

    companion object { fun from(id: String) = entries.firstOrNull { it.id == id } }
}

/** A binary value decoded from a data URL into a temp file while parsing. */
data class StagedFile(val file: File, val mime: String, val size: Long)

data class StagedAsset(val id: String, val staged: StagedFile)

data class StagedAttachment(val name: String, val staged: StagedFile)

data class StagedNote(
    val fields: Map<String, JsonElement>,
    val images: List<StagedAttachment>,
    val audio: List<StagedAttachment>,
)

/**
 * Result of reading a backup file. Nothing has been changed yet; the user picks
 * which [available] sections to restore (web `openImportChoice`).
 */
class ParsedBackup internal constructor(
    val data: Map<String, JsonElement>,
    val hasSections: Boolean,
    val courseAssets: List<StagedAsset>,
    val notes: List<StagedNote>?,
    val backgrounds: Map<String, StagedFile>?,
    /** Keys present in `data` (truthy per JS semantics) — drives section inference. */
    internal val presentKeys: Set<String>,
) {
    /** `inferBackupSections(parsed)` */
    val available: List<BackupSection> by lazy {
        val old = !hasSections
        val p = presentKeys
        buildList {
            if ("user" in p) add(BackupSection.USER)
            if ("courses" in p) add(BackupSection.COURSES)
            if ("notes" in p) add(BackupSection.NOTES)
            if ("events" in p || (old && "tasks" in p)) add(BackupSection.CALENDAR)
            if ("decks" in p || "quizzes" in p || "schedule" in p || (old && "focus" in p)) add(BackupSection.STUDY)
            if ("islam" in p) add(BackupSection.ISLAM)
            if ("summaries" in p) add(BackupSection.SUMMARIES)
            if ("forms" in p) add(BackupSection.FORMS)
            if ("backgrounds" in p) add(BackupSection.BACKGROUND)
        }
    }

    internal fun allStagedFiles(): List<File> =
        courseAssets.map { it.staged.file } +
            notes.orEmpty().flatMap { n -> (n.images + n.audio).map { it.staged.file } } +
            backgrounds.orEmpty().values.map { it.file }

    /** Deletes temp files (call when the user cancels or after restore). */
    fun discard() { allStagedFiles().forEach { it.delete() } }
}

sealed interface BackupError {
    /** Not JSON, or not an Ubad backup (`app !== 'ubad-academy-hub'` / no data object). */
    data object Invalid : BackupError
    data object NothingToRestore : BackupError
    data class Io(val cause: Throwable) : BackupError
}

class BackupException(val error: BackupError, cause: Throwable? = null) :
    Exception(error.toString() + (cause?.let { " ← $it @ " + it.stackTrace.take(3).joinToString(" < ") } ?: ""), cause)

package com.ubad.academy.data.repository

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.ubad.academy.core.Web
import com.ubad.academy.data.local.db.CourseAssetEntity
import com.ubad.academy.data.local.db.UbadDatabase
import com.ubad.academy.data.local.db.assembleCourses
import com.ubad.academy.data.local.db.toEntity
import com.ubad.academy.data.local.files.FileStore
import com.ubad.academy.domain.model.AssetRef
import com.ubad.academy.domain.model.ContentType
import com.ubad.academy.domain.model.Course
import com.ubad.academy.domain.model.CourseContent
import com.ubad.academy.domain.model.CourseUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Courses → units → contents plus their private files (web `state.courses` + `courseAssets`). */
@Singleton
class CourseRepository @Inject constructor(
    private val db: UbadDatabase,
    private val files: FileStore,
    private val resolver: ContentResolver,
) {
    private val dao get() = db.courses()

    val courses: Flow<List<Course>> =
        combine(dao.observeCourses(), dao.observeUnits(), dao.observeContents(), ::assembleCourses)

    fun course(id: String): Flow<Course?> = courses.map { l -> l.firstOrNull { it.id == id } }.distinctUntilChanged()

    fun assetFile(id: String): File = files.courseFile(id)
    suspend fun assetMime(id: String): String? = dao.asset(id)?.mime
    fun uriFor(file: File): Uri = files.uriFor(file)

    /** Writes a stored asset to a user-chosen document (web "download"). */
    suspend fun copyAssetTo(id: String, dest: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val src = files.courseFile(id)
            if (!src.exists()) return@runCatching false
            resolver.openOutputStream(dest, "w")?.use { out -> src.inputStream().use { it.copyTo(out) } } != null
        }.getOrDefault(false)
    }

    // ── courses ──
    data class CourseInput(val name: String, val code: String, val instructor: String, val credits: Double, val semester: String)

    /** openCourseModal save. Returns the course id. */
    suspend fun saveCourse(existing: Course?, input: CourseInput): String {
        val name = input.name.trim().take(80)
        require(name.isNotEmpty())
        val fields = { c: Course ->
            c.copy(
                name = name, code = input.code.trim().take(24), instructor = input.instructor.trim().take(80),
                credits = input.credits.coerceIn(0.0, 99.0), semester = input.semester.trim().take(40),
            )
        }
        return if (existing != null) {
            val pos = dao.course(existing.id)?.position ?: dao.nextCoursePos()
            dao.upsertCourse(fields(existing).toEntity(pos)); existing.id
        } else {
            val c = fields(Course(id = Web.uid(), name = name, createdAt = System.currentTimeMillis()))
            dao.upsertCourse(c.toEntity(dao.nextCoursePos())); c.id
        }
    }

    suspend fun deleteCourse(course: Course) {
        val ids = course.contents.flatMap { it.allAssetIds }
        dao.deleteCourse(course.id)
        deleteAssets(ids)
    }

    // ── units ──
    suspend fun addUnit(courseId: String, title: String) {
        val t = title.trim().take(80)
        if (t.isEmpty()) return
        dao.upsertUnit(CourseUnit(Web.uid(), t).toEntity(courseId, dao.nextUnitPos(courseId)))
    }

    suspend fun renameUnit(unitId: String, title: String) {
        val t = title.trim().take(80)
        val u = dao.unit(unitId) ?: return
        if (t.isNotEmpty()) dao.upsertUnit(u.copy(title = t))
    }

    suspend fun deleteUnit(unit: CourseUnit) {
        val ids = unit.contents.flatMap { it.allAssetIds }
        dao.deleteUnit(unit.id)
        deleteAssets(ids)
    }

    // ── contents ──
    suspend fun toggleDone(content: CourseContent) = dao.setDone(content.id, !content.done)

    suspend fun deleteContent(content: CourseContent) {
        dao.deleteContent(content.id)
        deleteAssets(content.allAssetIds)
    }

    sealed interface SaveResult {
        data object Ok : SaveResult
        data object NeedTitle : SaveResult
        data object NeedText : SaveResult
        data object BadVideoUrl : SaveResult
        data object ChooseFile : SaveResult
        data object FileTooBig : SaveResult
        data object BadFile : SaveResult
        data object Failed : SaveResult
    }

    /** What the content editor collected (web `openCourseContentModal`). */
    data class ContentInput(
        val title: String,
        val type: ContentType,
        val text: String = "",
        val videoSource: String = "local",
        val youtubeUrl: String = "",
        val file: Uri? = null,
        val images: List<Uri> = emptyList(),
    )

    suspend fun saveContent(unitId: String, editing: CourseContent?, input: ContentInput): SaveResult = withContext(Dispatchers.IO) {
        val title = input.title.trim()
        if (title.isEmpty()) return@withContext SaveResult.NeedTitle
        val oldAssetId = editing?.assetId.orEmpty()
        val base = CourseContent(
            id = editing?.id ?: Web.uid(), type = input.type.key, title = title.take(120),
            done = editing?.done ?: false, createdAt = editing?.createdAt ?: System.currentTimeMillis(),
        )
        val rec: CourseContent
        val toDelete = mutableListOf<String>()
        when (input.type) {
            ContentType.TEXT -> {
                val text = input.text.trim()
                if (text.isEmpty()) return@withContext SaveResult.NeedText
                if (oldAssetId.isNotEmpty()) toDelete += oldAssetId
                rec = base.copy(text = text.take(50_000))
            }
            ContentType.VIDEO -> {
                if (input.videoSource == "youtube") {
                    if (Web.youtubeVideoId(input.youtubeUrl.trim()) == null) return@withContext SaveResult.BadVideoUrl
                    if (oldAssetId.isNotEmpty()) toDelete += oldAssetId
                    rec = base.copy(source = "youtube", url = Web.safeHttpUrl(input.youtubeUrl.trim()).orEmpty())
                } else {
                    val file = input.file
                    if (file == null && editing == null) return@withContext SaveResult.ChooseFile
                    rec = if (file != null) {
                        when (val r = importAsset(file) { it.startsWith("video/") }) {
                            is Imported.Ok -> { if (oldAssetId.isNotEmpty()) toDelete += oldAssetId; base.copy(source = "local", assetId = r.id, name = r.name, mime = r.mime) }
                            is Imported.Err -> return@withContext r.result
                        }
                    } else {
                        if (editing!!.source == "youtube" && oldAssetId.isEmpty()) return@withContext SaveResult.ChooseFile
                        base.copy(source = "local", assetId = oldAssetId, name = editing.name, mime = editing.mime)
                    }
                }
            }
            ContentType.IMAGE -> {
                val existing = editing?.assets.orEmpty()
                val assets = mutableListOf<AssetRef>()
                if (input.images.isNotEmpty()) {
                    for (uri in input.images) {
                        when (val r = importAsset(uri) { it.startsWith("image/") }) {
                            is Imported.Ok -> assets += AssetRef(r.id, r.name.ifEmpty { "image" }, r.mime.ifEmpty { "image/*" })
                            is Imported.Err -> { deleteAssets(assets.map { it.id }); return@withContext r.result }
                        }
                    }
                    toDelete += existing.map { it.id }
                    if (oldAssetId.isNotEmpty()) toDelete += oldAssetId
                } else if (editing != null) {
                    existing.forEach { assets += AssetRef(it.id, it.name.ifEmpty { "image" }, it.mime.ifEmpty { "image/*" }) }
                    if (assets.isEmpty() && oldAssetId.isNotEmpty()) assets += AssetRef(oldAssetId, editing.name.ifEmpty { "image" }, editing.mime.ifEmpty { "image/*" })
                } else return@withContext SaveResult.ChooseFile
                rec = base.copy(assets = assets, name = assets.firstOrNull()?.name.orEmpty(), mime = assets.firstOrNull()?.mime.orEmpty())
            }
            ContentType.AUDIO, ContentType.PDF -> {
                val expected = if (input.type == ContentType.PDF) "application/pdf" else "audio/"
                val file = input.file
                if (file == null && editing == null) return@withContext SaveResult.ChooseFile
                rec = if (file != null) {
                    when (val r = importAsset(file) { it.startsWith(expected) }) {
                        is Imported.Ok -> { if (oldAssetId.isNotEmpty()) toDelete += oldAssetId; base.copy(assetId = r.id, name = r.name, mime = r.mime) }
                        is Imported.Err -> return@withContext r.result
                    }
                } else base.copy(assetId = oldAssetId, name = editing!!.name, mime = editing.mime)
            }
        }
        db.withTransaction {
            val pos = editing?.let { dao.content(it.id)?.position } ?: dao.nextContentPos(unitId)
            dao.upsertContent(rec.toEntity(unitId, pos))
        }
        deleteAssets(toDelete.filter { it !in rec.allAssetIds })
        SaveResult.Ok
    }

    private sealed interface Imported {
        data class Ok(val id: String, val name: String, val mime: String) : Imported
        data class Err(val result: SaveResult) : Imported
    }

    /** `courseAssetPut` from a picked document: validates type/size (50 MB like the web) and copies privately. */
    private suspend fun importAsset(uri: Uri, typeOk: (String) -> Boolean): Imported {
        val mime = resolver.getType(uri).orEmpty()
        if (!typeOk(mime)) return Imported.Err(SaveResult.BadFile)
        var name = ""
        var size: Long? = null
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it).orEmpty() }
                    c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { size = c.getLong(it) }
                }
            }
        }
        if ((size ?: 0) > MAX_FILE) return Imported.Err(SaveResult.FileTooBig)
        val id = Web.uid()
        val target = files.courseFile(id)
        val written = runCatching {
            resolver.openInputStream(uri)?.use { files.writeAtomically(target, LimitedInputStream(it, MAX_FILE + 1)) }
        }.getOrNull() ?: return Imported.Err(SaveResult.Failed)
        if (written > MAX_FILE) { target.delete(); return Imported.Err(SaveResult.FileTooBig) }
        dao.upsertAsset(CourseAssetEntity(id, mime, written))
        return Imported.Ok(id, name.take(160), mime.take(120))
    }

    private suspend fun deleteAssets(ids: Collection<String>) {
        ids.filter { it.isNotEmpty() }.distinct().forEach { id ->
            dao.deleteAsset(id)
            files.courseFile(id).delete()
        }
    }

    companion object {
        const val MAX_FILE = 50L * 1024 * 1024
    }
}

package com.ubad.academy.data.cloud

import com.ubad.academy.core.cloud.CloudConfig
import com.ubad.academy.core.cloud.CloudPaths
import com.ubad.academy.di.CloudUpload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backblaze B2, reached only through the project's existing Cloudflare Worker.
 *
 * The Worker holds `B2_KEY_ID` / `B2_APPLICATION_KEY` as secrets, validates the
 * caller's Firebase ID token with Firebase Auth REST, derives the UID from that
 * token server-side and refuses any path outside `users/<uid>/`. The app
 * therefore ships no B2 credential of any kind — this client is an authenticated
 * HTTP client and nothing more.
 *
 * Endpoints (see `storage-backend/worker.js`):
 *   POST   /upload            headers X-UBAD-Path, X-UBAD-Content-Type
 *   GET    /download?path=…   → bytes (Range passthrough)
 *   DELETE /delete?path=…     → deletes every live version of the object
 */
@Singleton
class CloudFileApi @Inject constructor(
    @CloudUpload private val client: OkHttpClient,
    private val config: CloudConfig,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Uploads [file] to [userId]'s cloud scope.
     *
     * The key is validated against the user's prefix before anything is sent, so
     * a bug here cannot ask the Worker for another user's object. Uploads are
     * streamed by OkHttp (the file is never loaded into memory whole), which
     * matters for the 50 MB course videos and PDFs the web app allows.
     */
    suspend fun upload(userId: String, key: String, file: File, contentType: String?, idToken: String) {
        requireInsideScope(userId, key)
        if (!file.exists() || file.length() == 0L) {
            throw CloudException("Local file is missing for $key", "cloud/local-file-missing")
        }
        if (file.length() > CloudConfig.MAX_UPLOAD_BYTES) {
            throw CloudException(
                "File is larger than the ${CloudConfig.MAX_UPLOAD_BYTES / 1024 / 1024} MB limit.",
                "cloud/file-too-large",
            )
        }

        val type = contentType?.takeIf { it.isNotBlank() } ?: "application/octet-stream"
        val request = Request.Builder()
            .url(config.workerUrl.trimEnd('/') + "/upload")
            .header("Authorization", "Bearer $idToken")
            .header("X-UBAD-Path", key)
            .header("X-UBAD-Content-Type", type)
            .post(file.asRequestBody(type.toMediaTypeOrNull()))
            .build()

        execute(request, "upload", key).close()
    }

    /** Streams `path` into [target] (overwritten). Returns the number of bytes written. */
    suspend fun download(userId: String, key: String, target: File, idToken: String): Long {
        requireInsideScope(userId, key)
        val request = Request.Builder()
            .url(config.workerUrl.trimEnd('/') + "/download?path=" + encodeKey(key))
            .header("Authorization", "Bearer $idToken")
            .get()
            .build()

        return use(request, "download", key) { body ->
            target.parentFile?.mkdirs()
            // Write to a sibling temp file first so a dropped connection can never
            // leave a truncated file in place of a good one.
            val temp = File(target.parentFile, target.name + ".part")
            try {
                temp.outputStream().use { out -> body.byteStream().copyTo(out) }
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                }
                target.length()
            } catch (t: Throwable) {
                temp.delete()
                throw t
            }
        }
    }

    suspend fun delete(userId: String, key: String, idToken: String) {
        requireInsideScope(userId, key)
        val request = Request.Builder()
            .url(config.workerUrl.trimEnd('/') + "/delete?path=" + encodeKey(key))
            .header("Authorization", "Bearer $idToken")
            .delete()
            .build()

        // A missing object is already the desired state, so a 404 is not an error.
        try {
            execute(request, "delete", key).close()
        } catch (e: CloudException) {
            if (e.code != "worker/404" && e.code != "not_found") throw e
        }
    }

    private fun requireInsideScope(userId: String, key: String) {
        if (!CloudPaths.isWithinUserScope(userId, key)) {
            throw CloudException("Refusing to address a path outside the signed-in user.", "cloud/path-scope")
        }
    }

    private fun encodeKey(key: String): String =
        key.split('/').joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8") }

    private suspend fun execute(request: Request, action: String, key: String) =
        use(request, action, key) { it }

    private suspend fun <T> use(
        request: Request,
        action: String,
        key: String,
        block: (okhttp3.ResponseBody) -> T,
    ): T = withContext(Dispatchers.IO) {
        val response = try {
            client.newCall(request).execute()
        } catch (e: java.io.IOException) {
            throw CloudException(friendlyNetworkMessage(action), "cloud/network", e)
        }
        response.use { res ->
            val body = res.body
            if (res.isSuccessful) {
                if (body == null) throw CloudException("Empty response from cloud.", "cloud/empty")
                return@withContext block(body)
            }
            throw CloudException(describeFailure(res.code, body?.string(), key), "worker/${res.code}")
        }
    }

    private fun friendlyNetworkMessage(action: String): String =
        "Could not reach cloud storage to $action. Check your connection and try again."

    /** Turns the Worker's `{ok:false,error,code}` payload into a readable message. */
    private fun describeFailure(status: Int, body: String?, key: String): String {
        val parsed: JsonObject? = body?.takeIf { it.isNotBlank() }?.let {
            runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull()
        }
        val message = parsed?.get("error")?.jsonPrimitive?.contentOrNull
        val code = parsed?.get("code")?.jsonPrimitive?.contentOrNull
        if (!message.isNullOrBlank()) return message
        return when {
            status == 401 -> "Sign in again: the cloud rejected the session."
            status == 403 -> "The cloud refused access to $key."
            status == 404 -> "Not found in cloud storage."
            status == 413 -> "File is too large for cloud storage."
            status >= 500 -> "Cloud storage is unavailable right now ($status)."
            else -> "Cloud request failed ($status${code?.let { ", $it" } ?: ""})."
        }
    }
}

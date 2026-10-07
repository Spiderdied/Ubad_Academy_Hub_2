package com.ubad.academy.data.cloud

import com.ubad.academy.core.cloud.CloudConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * One binary that the user's cloud mirror holds. Mirrors the entries the web
 * builds in `buildCloudFileManifest()` and stores in `cloudState.files`.
 */
@Serializable
data class CloudFileEntry(
    val kind: String,
    /** Full object key, `users/<uid>/...`. */
    val key: String,
    val noteId: String? = null,
    val index: Int? = null,
    val assetId: String? = null,
    val theme: String? = null,
    val name: String = "",
    @SerialName("type") val mimeType: String = "",
    val size: Long = 0,
)

/**
 * The `cloudState` map inside the Firestore document `users/<uid>`.
 *
 * The Firestore document also carries profile fields (`uid`, `name`, `email`,
 * `photoURL`, `provider`, `updatedAt`) written by [CloudProfile]; those are
 * handled separately so a profile write never clobbers `cloudState`, and vice
 * versa.
 */
@Serializable
data class CloudState(
    /** The structured UBAD payload — the same shape as the web `cloudPayload()`. */
    val appData: JsonObject? = null,
    val files: List<CloudFileEntry> = emptyList(),
    val syncVersion: Int = CloudConfig.SYNC_VERSION,
    val syncId: String = "",
    /** Client clock of the last write, used for last-write-wins ordering. */
    val clientUpdatedAt: Long = 0L,
)

/** Profile fields the web writes in `saveProfile(user)`. */
data class CloudProfile(
    val uid: String,
    val name: String = "",
    val email: String = "",
    val photoURL: String = "",
    val provider: String = "google",
)

/** The signed-in account, as the UI needs it. */
data class CloudUser(
    val uid: String,
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String = "",
) {
    val label: String get() = displayName.ifBlank { email }.ifBlank { uid }
}

/** What the user picked the first time local and cloud data both existed. */
enum class FirstSyncChoice(val wire: String) {
    UPLOAD("upload"),
    RESTORE("restore"),
    MERGE("merge"),
    CANCEL("cancel");

    companion object {
        fun from(wire: String?): FirstSyncChoice? = entries.firstOrNull { it.wire == wire }
    }
}

/** Thrown for cloud failures that the UI should surface verbatim. */
class CloudException(
    message: String,
    val code: String = "cloud/error",
    cause: Throwable? = null,
) : Exception(message, cause)

/** Shared JSON reader for cloud payloads. Lenient: the payload comes from the server. */
internal val CloudJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}

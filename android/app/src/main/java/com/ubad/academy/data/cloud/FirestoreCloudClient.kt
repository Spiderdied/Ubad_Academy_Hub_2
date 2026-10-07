package com.ubad.academy.data.cloud

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.ubad.academy.core.cloud.CloudConfig
import com.ubad.academy.core.cloud.awaitOrThrow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The `users/<uid>` Firestore document, field-for-field compatible with the web
 * app's `firebase-auth.js`.
 *
 * Document shape:
 * ```
 * users/<uid> {
 *   uid, name, email, photoURL, provider, updatedAt,   // profile
 *   cloudState: { appData, files[], syncVersion, syncId, clientUpdatedAt, updatedAt }
 * }
 * ```
 *
 * `firestore.rules` allows read/write only when `request.auth.uid == userId`
 * and denies everything else, so the server — not this client — enforces
 * ownership.
 */
@Singleton
class FirestoreCloudClient @Inject constructor(
    private val bootstrap: FirebaseBootstrap,
) {
    /**
     * Resolved on use, never at injection time: if Firebase could not start
     * (offline first launch, no Google Play services, unconfigured build) the app
     * must still open and work locally. Callers get a typed error instead of a
     * crash.
     */
    private fun db(): FirebaseFirestore = bootstrap.firestore()
        ?: throw CloudException(
            bootstrap.initFailure?.message ?: "Firestore is unavailable on this device.",
            "cloud/firestore-unavailable",
            bootstrap.initFailure,
        )

    private fun doc(uid: String) = db().collection(USERS).document(uid)

    /** Reads `cloudState`, or null when the document/field does not exist yet. */
    suspend fun getCloudState(uid: String): CloudState? {
        val snapshot = doc(uid).get().awaitOrThrow()
        if (!snapshot.exists()) return null
        return snapshot.get(CLOUD_STATE)?.let { decodeCloudState(it as? Map<*, *>) }
    }

    /**
     * Writes `cloudState` with `merge = true`, exactly like the web
     * `setCloudState(payload, meta, files)`. Profile fields are untouched.
     *
     * [appData] is written as a nested map (not a JSON string) so the web app can
     * read it back.
     */
    suspend fun setCloudState(
        uid: String,
        appData: JsonObject,
        files: List<CloudFileEntry>,
        syncId: String,
        clientUpdatedAt: Long,
    ) {
        val payload = mapOf(
            "appData" to FirestoreValues.toFirestoreMap(appData),
            "files" to files.map { entry ->
                buildMap<String, Any> {
                    put("kind", entry.kind)
                    put("key", entry.key)
                    entry.noteId?.let { put("noteId", it) }
                    entry.index?.let { put("index", it.toLong()) }
                    entry.assetId?.let { put("assetId", it) }
                    entry.theme?.let { put("theme", it) }
                    put("name", entry.name)
                    put("type", entry.mimeType)
                    put("size", entry.size)
                }
            },
            "syncVersion" to CloudConfig.SYNC_VERSION.toLong(),
            "syncId" to syncId,
            "clientUpdatedAt" to clientUpdatedAt,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        doc(uid).set(mapOf(CLOUD_STATE to payload), SetOptions.merge()).awaitOrThrow()
    }

    /** Live updates, mirroring the web `watchCloudState` onSnapshot subscription. */
    fun watchCloudState(uid: String): Flow<CloudState?> = callbackFlow {
        val registration = doc(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val state = snapshot?.get(CLOUD_STATE)?.let { decodeCloudState(it as? Map<*, *>) }
            trySend(state)
        }
        awaitClose { registration.remove() }
    }

    /** Mirrors the web `deleteCloudState()` (removes the field, keeps the profile). */
    suspend fun deleteCloudState(uid: String) {
        doc(uid).update(CLOUD_STATE, FieldValue.delete()).awaitOrThrow()
    }

    /** Mirrors the web `saveProfile(user)` — profile only, never `cloudState`. */
    suspend fun saveProfile(profile: CloudProfile) {
        doc(profile.uid).set(
            mapOf(
                "uid" to profile.uid,
                "name" to profile.name,
                "email" to profile.email,
                "photoURL" to profile.photoURL,
                "provider" to profile.provider,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).awaitOrThrow()
    }

    private fun decodeCloudState(map: Map<*, *>?): CloudState? {
        if (map == null) return null
        val appData = (map["appData"] as? Map<*, *>)?.let { FirestoreValues.fromFirestore(it) as? JsonObject }
        val files = (map["files"] as? List<*>).orEmpty().mapNotNull { decodeFileEntry(it as? Map<*, *>) }
        return CloudState(
            appData = appData,
            files = files,
            syncVersion = (map["syncVersion"] as? Number)?.toInt() ?: CloudConfig.SYNC_VERSION,
            syncId = map["syncId"] as? String ?: "",
            clientUpdatedAt = (map["clientUpdatedAt"] as? Number)?.toLong() ?: 0L,
        )
    }

    private fun decodeFileEntry(map: Map<*, *>?): CloudFileEntry? {
        if (map == null) return null
        val key = map["key"] as? String ?: return null
        val kind = map["kind"] as? String ?: return null
        return CloudFileEntry(
            kind = kind,
            key = key,
            noteId = map["noteId"] as? String,
            index = (map["index"] as? Number)?.toInt(),
            assetId = map["assetId"] as? String,
            theme = map["theme"] as? String,
            name = map["name"] as? String ?: "",
            mimeType = map["type"] as? String ?: "",
            size = (map["size"] as? Number)?.toLong() ?: 0L,
        )
    }

    private companion object {
        const val USERS = "users"
        const val CLOUD_STATE = "cloudState"
    }
}

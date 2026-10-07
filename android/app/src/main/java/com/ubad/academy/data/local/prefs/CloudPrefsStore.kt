package com.ubad.academy.data.local.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.cloudDataStore: DataStore<Preferences> by preferencesDataStore(name = "ubad_cloud")

/**
 * Cloud-sync bookkeeping, the Android home for the web's in-memory `cloudSync`
 * object (`localUpdatedAt`, `lastRemoteId`, `remoteFiles`) plus the remembered
 * first-sync answer.
 *
 * Kept in its own DataStore file, deliberately separate from [SettingsStore]:
 * the web's "erase all data" clears the app's preferences but **not** the
 * remembered cloud choice (`ubad.cloud.choice.<uid>`), and keeping the files
 * apart preserves that behaviour without special-casing `wipe()`.
 */
@Singleton
class CloudPrefsStore @Inject constructor(context: Context) {
    private val store = context.cloudDataStore

    private object K {
        val LOCAL_UPDATED_AT = longPreferencesKey("local_updated_at")
        val SYNC_ID = stringPreferencesKey("sync_id")
        val REMOTE_FILES = stringPreferencesKey("remote_files")
        /** `clientUpdatedAt` of the cloud state this device last applied. */
        val APPLIED_AT = longPreferencesKey("applied_at")
        fun choice(uid: String) = stringPreferencesKey("choice_$uid")
    }

    /** Set just before a push; the web uses it to stamp `clientUpdatedAt`. */
    suspend fun localUpdatedAt(): Long = store.data.first()[K.LOCAL_UPDATED_AT] ?: 0L
    suspend fun setLocalUpdatedAt(value: Long) = store.edit { it[K.LOCAL_UPDATED_AT] = value }

    suspend fun syncId(): String = store.data.first()[K.SYNC_ID] ?: ""
    suspend fun setSyncId(value: String) = store.edit { it[K.SYNC_ID] = value }

    /** Last known remote file list, serialised. Used to delete stale cloud keys. */
    suspend fun remoteFilesJson(): String = store.data.first()[K.REMOTE_FILES] ?: "[]"
    suspend fun setRemoteFilesJson(value: String) = store.edit { it[K.REMOTE_FILES] = value }

    suspend fun appliedAt(): Long = store.data.first()[K.APPLIED_AT] ?: 0L
    suspend fun setAppliedAt(value: Long) = store.edit { it[K.APPLIED_AT] = value }

    /** Remembered answer to the first-sync prompt for one account (web `firstSyncChoice`). */
    suspend fun choice(uid: String): String? = store.data.first()[K.choice(uid)]
    suspend fun setChoice(uid: String, value: String) = store.edit { it[K.choice(uid)] = value }
    suspend fun clearChoice(uid: String) = store.edit { it.remove(K.choice(uid)) }

    suspend fun clear() = store.edit { it.clear() }
}

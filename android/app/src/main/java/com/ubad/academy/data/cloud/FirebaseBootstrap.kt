package com.ubad.academy.data.cloud

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.ubad.academy.core.cloud.CloudConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Initialises Firebase from the app's public client configuration.
 *
 * The app deliberately does **not** use the `google-services` Gradle plugin or a
 * committed `google-services.json`:
 *
 *  - the repository's own security scan fails the build if `google-services.json`
 *    is tracked, and this project keeps build-time credentials out of git;
 *  - everything a `google-services.json` would supply that the app actually needs
 *    (project id, web API key, app id, sender id, storage bucket) is already
 *    public client configuration.
 *
 * Initialisation is lazy and failure-tolerant: if Firebase cannot start (no
 * network at first launch, GMS missing, misconfiguration) the app records the
 * reason and carries on fully offline — sync is simply unavailable until it can
 * start. Nothing here can crash the app.
 */
@Singleton
class FirebaseBootstrap @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: CloudConfig,
) {
    private val lock = Any()

    @Volatile private var app: FirebaseApp? = null

    @Volatile private var failure: Throwable? = null

    /** Populated when initialisation failed, for a user-facing message. */
    val initFailure: Throwable? get() = failure

    val isConfigured: Boolean get() = config.isFirebaseConfigured

    fun app(): FirebaseApp? {
        app?.let { return it }
        if (failure != null || !config.isFirebaseConfigured) return null
        synchronized(lock) {
            app?.let { return it }
            if (failure != null) return null
            return try {
                val existing = FirebaseApp.getApps(context).firstOrNull { it.name == APP_NAME }
                val created = existing
                    ?: FirebaseApp.initializeApp(context, options(), APP_NAME)
                    ?: error("FirebaseApp.initializeApp returned null")
                app = created
                created
            } catch (t: Throwable) {
                failure = t
                null
            }
        }
    }

    fun auth(): FirebaseAuth? = app()?.let {
        runCatching { FirebaseAuth.getInstance(it) }.getOrElse { e -> failure = e; null }
    }

    fun firestore(): FirebaseFirestore? = app()?.let {
        runCatching { FirebaseFirestore.getInstance(it) }.getOrElse { e -> failure = e; null }
    }

    private fun options(): FirebaseOptions = FirebaseOptions.Builder()
        .setApiKey(config.webApiKey)
        .setApplicationId(config.appId)
        .setProjectId(config.projectId)
        .setGcmSenderId(config.senderId)
        .setStorageBucket(config.storageBucket)
        .build()

    private companion object {
        /** Named app so this never clashes with a host app's default instance. */
        const val APP_NAME = "ubad-cloud"
    }
}

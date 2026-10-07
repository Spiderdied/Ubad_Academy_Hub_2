package com.ubad.academy.core.cloud

/**
 * Public cloud client configuration.
 *
 * Everything here is **public by design** and matches what the web app ships in
 * `firebase-auth.js` / `index.html`:
 *
 *  - [webApiKey] is the Firebase *web* API key. It only identifies the project;
 *    every access decision is made by `firestore.rules` / `storage.rules`, which
 *    are owner-only. It is not a secret.
 *  - [workerUrl] is a public Cloudflare Worker endpoint that authenticates each
 *    caller itself via a Firebase ID token.
 *
 * No secrets live here. `B2_KEY_ID`, `B2_APPLICATION_KEY` and the Worker's
 * `FIREBASE_API_KEY` are Cloudflare Worker secrets and are never shipped in the
 * APK; the app only ever talks to the Worker's public endpoints.
 *
 * A data class (rather than a global object) so unit tests can build their own.
 */
data class CloudConfig(
    val projectId: String,
    val webApiKey: String,
    val appId: String,
    val senderId: String,
    val storageBucket: String,
    val authDomain: String,
    val workerUrl: String,
    /**
     * Google OAuth **web** client id ("server" client id). Credential Manager
     * requires it, and it belongs to the Firebase project's OAuth clients, so it
     * cannot be derived. Empty means "this build has not been given one" — the
     * UI then says so instead of failing later at runtime.
     */
    val googleWebClientId: String = "",
) {
    /** True when the public Firebase client config is complete enough to initialise. */
    val isFirebaseConfigured: Boolean
        get() = webApiKey.isNotBlank() && projectId.isNotBlank() && appId.isNotBlank()

    /** True when Google sign-in can actually be attempted. */
    val isGoogleSignInConfigured: Boolean
        get() = isFirebaseConfigured && googleWebClientId.isNotBlank()

    val isWorkerConfigured: Boolean
        get() = workerUrl.startsWith("https://")

    companion object {
        /**
         * Firestore rejects a document larger than 1 MiB. The web app refuses to
         * push above 900 KB (`pushCloudState`) to stay clear of the limit, and
         * Android uses the same threshold so both clients behave identically.
         */
        const val MAX_CLOUD_STATE_BYTES = 900_000

        /** Web `uploadCloudFiles` / the Worker's `MAX_UPLOAD_BYTES` default. */
        const val MAX_UPLOAD_BYTES = 100L * 1024 * 1024

        /** Web `CLOUD_SYNC_VERSION`. */
        const val SYNC_VERSION = 1
    }
}

package com.ubad.academy.data.cloud

import android.app.Activity
import android.content.Context
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.ubad.academy.core.cloud.CloudConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Obtains a Google ID token through Credential Manager.
 *
 * This is the supported native path: Credential Manager returns a Google ID
 * token, which Firebase Auth exchanges for a real Firebase user (so the Firebase
 * UID stays the identity, exactly like the web `signInWithPopup` flow).
 *
 * Sign-in cannot start in a WebView (Google blocks embedded user-agents), which
 * is one of the reasons this app is native rather than a WebView shell.
 *
 * Robustness: the flow is attempted twice, matching Google's recommended
 * pattern — first restricted to accounts already authorised for this app
 * (one-tap), then with the full account picker. A user-initiated cancellation is
 * propagated instead of being retried, so the second prompt never appears after
 * the user has dismissed the first.
 */
@Singleton
class GoogleSignInClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: CloudConfig,
) {
    private val manager: CredentialManager by lazy { CredentialManager.create(context) }

    suspend fun idToken(activity: Activity): String {
        if (!config.isFirebaseConfigured) {
            throw CloudException(
                "Cloud sync is not configured in this build.",
                "auth/not-configured",
            )
        }
        if (!config.isGoogleSignInConfigured) {
            throw CloudException(
                "Google sign-in is not configured in this build.",
                "auth/google-client-id-missing",
            )
        }

        // Stage 1 — accounts already used with the app.
        try {
            return request(activity, filterByAuthorizedAccounts = true)
        } catch (e: GetCredentialCancellationException) {
            throw e
        } catch (_: Throwable) {
            // No saved credential, or the one-tap sheet was unavailable: fall through.
        }

        // Stage 2 — full account picker.
        return request(activity, filterByAuthorizedAccounts = false)
    }

    private suspend fun request(activity: Activity, filterByAuthorizedAccounts: Boolean): String {
        val option = GetGoogleIdOption.Builder()
            // The *web* (server) client id, not the Android one.
            .setServerClientId(config.googleWebClientId)
            .setFilterByAuthorizedAccounts(filterByAuthorizedAccounts)
            .setAutoSelectEnabled(false)
            .build()

        val response = manager.getCredential(
            context = activity,
            request = GetCredentialRequest.Builder().addCredentialOption(option).build(),
        )
        return extractIdToken(response.credential)
    }

    private fun extractIdToken(credential: Credential): String {
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        throw CloudException(
            "Google returned an unexpected credential type.",
            "auth/unexpected-credential",
        )
    }
}

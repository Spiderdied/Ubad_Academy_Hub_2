package com.ubad.academy.data.cloud

import android.app.Activity
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.ubad.academy.core.cloud.CloudConfig
import com.ubad.academy.core.cloud.awaitOrThrow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google sign-in and the signed-in state.
 *
 * The Firebase UID produced here is the same identity the web app uses, so an
 * account signed in on the web finds its `users/<uid>` Firestore document and
 * its `users/<uid>/…` B2 files from Android, and vice versa.
 *
 * Signing in is entirely optional: with no account the app behaves exactly as it
 * does today, using only Room, DataStore and private files.
 */
@Singleton
class CloudAuthRepository @Inject constructor(
    private val bootstrap: FirebaseBootstrap,
    private val googleSignIn: GoogleSignInClient,
    private val config: CloudConfig,
) {
    private val _user = MutableStateFlow<CloudUser?>(null)

    /** The signed-in account, or null. Also null when cloud is not configured. */
    val user: StateFlow<CloudUser?> = _user.asStateFlow()

    init {
        runCatching {
            bootstrap.auth()?.addAuthStateListener { auth ->
                _user.value = auth.currentUser?.toCloudUser()
            }
        }
    }

    val isConfigured: Boolean get() = bootstrap.isConfigured

    /** True when Google sign-in can actually be attempted in this build. */
    val isSignInAvailable: Boolean get() = config.isGoogleSignInConfigured

    val isSignedIn: Boolean get() = bootstrap.auth()?.currentUser != null

    /**
     * Signs in with Google. Must be called with a foreground [Activity], because
     * Credential Manager shows UI.
     */
    suspend fun signInWithGoogle(activity: Activity): CloudUser {
        val auth = bootstrap.auth()
            ?: throw CloudException(
                bootstrap.initFailure?.message ?: "Firebase is unavailable on this device.",
                "auth/not-initialized",
                bootstrap.initFailure,
            )

        val idToken = googleSignIn.idToken(activity)
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = auth.signInWithCredential(credential).awaitOrThrow()
        val firebaseUser = result.user
            ?: auth.currentUser
            ?: throw CloudException("Google sign-in did not return a user.", "auth/no-user")

        val user = firebaseUser.toCloudUser()
        _user.value = user
        return user
    }

    suspend fun signOut() {
        runCatching { bootstrap.auth()?.signOut() }
        _user.value = null
    }

    /**
     * A fresh Firebase ID token, used as the bearer credential for both the
     * Firestore document and the B2 Worker. The SDK caches and refreshes it
     * internally; [forceRefresh] is used after an auth/token failure.
     */
    suspend fun idToken(forceRefresh: Boolean = false): String? {
        val current = bootstrap.auth()?.currentUser ?: return null
        return runCatching { current.getIdToken(forceRefresh).awaitOrThrow().token }.getOrNull()
    }

    private fun FirebaseUser.toCloudUser() = CloudUser(
        uid = uid,
        displayName = displayName.orEmpty(),
        email = email.orEmpty(),
        photoUrl = photoUrl?.toString().orEmpty(),
    )
}

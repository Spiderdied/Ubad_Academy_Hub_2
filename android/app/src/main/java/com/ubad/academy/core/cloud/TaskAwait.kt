package com.ubad.academy.core.cloud

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Awaits a Play Services [Task] as a suspending function.
 *
 * Written by hand (about 15 lines) rather than adding
 * `kotlinx-coroutines-play-services`, because this is the only place the app
 * needs it.
 */
suspend fun <T : Any> Task<T>.awaitOrThrow(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        if (!cont.isActive) return@addOnCompleteListener
        val error = task.exception
        if (error != null) {
            cont.resumeWithException(error)
        } else {
            @Suppress("UNCHECKED_CAST", "KotlinRedundantDiagnosticSuppress")
            cont.resume(task.result as T)
        }
    }
}

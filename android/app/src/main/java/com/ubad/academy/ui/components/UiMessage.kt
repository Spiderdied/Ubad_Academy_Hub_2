package com.ubad.academy.ui.components

import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * A one-shot user message (web `toast(t(key), kind)`).
 *
 * [raw] carries text that has no string resource — cloud and network failures
 * come from the server/exception at runtime, and the brief requires those to be
 * shown clearly rather than replaced by a generic "something went wrong".
 */
data class UiMessage(
    @StringRes val text: Int = 0,
    val args: List<Any> = emptyList(),
    val error: Boolean = false,
    val raw: String? = null,
)

/** Simple one-shot event pipe for ViewModels. */
class MessageQueue {
    private val ch = Channel<UiMessage>(Channel.BUFFERED)
    val flow: Flow<UiMessage> = ch.receiveAsFlow()
    fun send(@StringRes text: Int, vararg args: Any, error: Boolean = false) {
        ch.trySend(UiMessage(text, args.toList(), error))
    }

    /** Shows [text] verbatim — for messages produced at runtime, not from resources. */
    fun sendRaw(text: String?, error: Boolean = false) {
        val body = text?.trim().orEmpty()
        if (body.isEmpty()) return
        ch.trySend(UiMessage(error = error, raw = body))
    }
}

@Composable
fun CollectMessages(messages: Flow<UiMessage>, host: SnackbarHostState) {
    val context = LocalContext.current
    LaunchedEffect(messages) {
        messages.collect { m ->
            host.currentSnackbarData?.dismiss()
            host.showSnackbar(
                m.raw ?: context.getString(m.text, *m.args.toTypedArray()),
                duration = if (m.error) SnackbarDuration.Long else SnackbarDuration.Short,
            )
        }
    }
}

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

/** A one-shot user message (web `toast(t(key), kind)`). */
data class UiMessage(@StringRes val text: Int, val args: List<Any> = emptyList(), val error: Boolean = false)

/** Simple one-shot event pipe for ViewModels. */
class MessageQueue {
    private val ch = Channel<UiMessage>(Channel.BUFFERED)
    val flow: Flow<UiMessage> = ch.receiveAsFlow()
    fun send(@StringRes text: Int, vararg args: Any, error: Boolean = false) { ch.trySend(UiMessage(text, args.toList(), error)) }
}

@Composable
fun CollectMessages(messages: Flow<UiMessage>, host: SnackbarHostState) {
    val context = LocalContext.current
    LaunchedEffect(messages) {
        messages.collect { m ->
            host.currentSnackbarData?.dismiss()
            host.showSnackbar(
                context.getString(m.text, *m.args.toTypedArray()),
                duration = if (m.error) SnackbarDuration.Long else SnackbarDuration.Short,
            )
        }
    }
}

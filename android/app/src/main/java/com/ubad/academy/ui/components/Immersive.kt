package com.ubad.academy.ui.components

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Hides status + navigation bars while [hidden] (swipe from an edge shows them briefly). */
@Composable
fun ImmersiveMode(hidden: Boolean) {
    val view = LocalView.current
    DisposableEffect(hidden) {
        var ctx = view.context
        while (ctx is ContextWrapper && ctx !is Activity) ctx = ctx.baseContext
        val window = (ctx as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (hidden) {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else controller?.show(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

package com.ubad.academy.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ubad.academy.di.AppScope
import com.ubad.academy.focus.FocusTimer
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Fires when a Focus/Break phase ends — works with the app closed (process is started for it). */
class FocusAlarmReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun timer(): FocusTimer
        @AppScope fun scope(): CoroutineScope
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val deps = EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java)
        val pending = goAsync()
        deps.scope().launch {
            try { deps.timer().completeIfDue(fromAlarm = true) } finally { pending.finish() }
        }
    }

    companion object { const val ACTION = "com.ubad.academy.action.FOCUS_PHASE_END" }
}

package com.ubad.academy.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fires when a Focus/Break phase ends (implemented in the Study Tools phase). */
class FocusAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}

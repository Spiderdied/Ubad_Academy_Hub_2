package com.ubad.academy.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import com.ubad.academy.R

object Notifications {
    const val CHANNEL_FOCUS = "focus_timer"
    const val CHANNEL_FOCUS_SILENT = "focus_timer_silent"
    const val ID_FOCUS = 1001

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val alarmUri = Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${R.raw.alarm}")
        val focus = NotificationChannel(
            CHANNEL_FOCUS, context.getString(R.string.notif_channel_focus), NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notif_channel_focus_desc)
            setSound(
                alarmUri,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            enableVibration(true)
        }
        // Used when the user turned interface sounds off (web: Sound off ⇒ no Alarm.mp3).
        val silent = NotificationChannel(
            CHANNEL_FOCUS_SILENT, context.getString(R.string.notif_channel_focus_silent), NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notif_channel_focus_desc)
            setSound(null, null)
            enableVibration(true)
        }
        nm.createNotificationChannels(listOf(focus, silent))
    }
}

package com.ubad.academy.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ubad.academy.MainActivity
import com.ubad.academy.R
import com.ubad.academy.data.local.prefs.SettingsStore
import com.ubad.academy.domain.model.FocusPhase
import com.ubad.academy.ui.navigation.DeepLinks
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** One notification per finished phase (same id, so they never pile up). */
@Singleton
class FocusNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore,
) {
    fun canPost(): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    suspend fun phaseEnded(ended: FocusPhase) {
        if (!canPost()) return
        val sound = settings.current().sound
        val text = context.getString(if (ended == FocusPhase.FOCUS) R.string.focus_doneMsg else R.string.focus_breakOver)
        val open = Intent(Intent.ACTION_VIEW, Uri.parse("${DeepLinks.BASE}study?tab=focus"), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(context, 1, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, if (sound) Notifications.CHANNEL_FOCUS else Notifications.CHANNEL_FOCUS_SILENT)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(context.getString(if (ended == FocusPhase.FOCUS) R.string.focus_session else R.string.focus_break))
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(Notifications.ID_FOCUS, n)
        } catch (e: SecurityException) { /* permission revoked meanwhile */ }
    }
}

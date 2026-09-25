package com.ubad.academy.focus

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.ubad.academy.core.Feedback
import com.ubad.academy.core.Web
import com.ubad.academy.data.local.prefs.SettingsStore
import com.ubad.academy.di.AppScope
import com.ubad.academy.domain.model.FocusPhase
import com.ubad.academy.domain.model.FocusSettings
import com.ubad.academy.domain.model.TimerState
import com.ubad.academy.notifications.FocusAlarmReceiver
import com.ubad.academy.notifications.FocusNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Native port of the web `Focus` object. The countdown is stored as an absolute end time
 * (like the web's `endsAt`), so it survives the app being closed or killed; an
 * [AlarmManager] alarm fires [FocusAlarmReceiver] at that moment, which completes the
 * phase and notifies. No foreground service is needed.
 */
@Singleton
class FocusTimer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore,
    private val feedback: Feedback,
    private val notifier: FocusNotifier,
    @AppScope private val scope: CoroutineScope,
) {
    val timer = settings.timer
    val focus = settings.focus
    private val lock = Mutex()

    /** Phase-end events shown in-app (snackbar) while the app is visible. */
    private val _ended = MutableSharedFlow<FocusPhase>(extraBufferCapacity = 2)
    val ended: SharedFlow<FocusPhase> = _ended

    private val alarms get() = context.getSystemService(AlarmManager::class.java)

    fun lengthSec(phase: FocusPhase, f: FocusSettings) = (if (phase == FocusPhase.FOCUS) f.focusMins else f.breakMins) * 60

    /** Seconds left right now for [t] (running → from endsAt; paused → stored; fresh → full length). */
    fun remaining(t: TimerState, f: FocusSettings, now: Long = System.currentTimeMillis()): Int = when {
        t.running -> ((t.endsAt - now) / 1000.0).roundToInt().coerceAtLeast(0)
        t.remainingSec >= 0 -> t.remainingSec
        else -> lengthSec(t.phase, f)
    }

    suspend fun start() = lock.withLock {
        val t = settings.currentTimer(); val f = settings.currentFocus()
        if (t.running) return@withLock
        val left = remaining(t, f).takeIf { it > 0 } ?: lengthSec(t.phase, f)
        val endsAt = System.currentTimeMillis() + left * 1000L
        settings.setTimer(t.copy(running = true, endsAt = endsAt, remainingSec = left))
        schedule(endsAt)
    }

    suspend fun pause() = lock.withLock {
        val t = settings.currentTimer(); val f = settings.currentFocus()
        if (!t.running) return@withLock
        cancelAlarm()
        settings.setTimer(t.copy(running = false, endsAt = 0L, remainingSec = remaining(t, f)))
    }

    suspend fun reset() = lock.withLock {
        cancelAlarm()
        settings.setTimer(settings.currentTimer().copy(running = false, endsAt = 0L, remainingSec = -1))
    }

    /** `Focus.setDuration` — only while stopped; resets the countdown if it's the current phase. */
    suspend fun setDuration(phase: FocusPhase, mins: Int) = lock.withLock {
        val t = settings.currentTimer()
        if (t.running) return@withLock
        val f = settings.currentFocus()
        val m = mins.coerceIn(FocusSettings.MIN_LEN, FocusSettings.MAX_LEN)
        settings.setFocus(if (phase == FocusPhase.FOCUS) f.copy(focusMins = m) else f.copy(breakMins = m))
        if (t.phase == phase) settings.setTimer(t.copy(remainingSec = -1))
    }

    /** Sessions completed today (web `Focus.done()` resets the counter on a new day). */
    fun doneToday(f: FocusSettings) = if (f.day == Web.today()) f.done else 0

    /**
     * Completes the phase if it is due. Called by the alarm and by the on-screen ticker;
     * only the first caller wins. [fromAlarm] decides whether to notify when in background.
     */
    suspend fun completeIfDue(fromAlarm: Boolean) {
        val ended = settings.completePhase(System.currentTimeMillis(), Web.today()) ?: return
        cancelAlarm()
        val visible = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (visible) {
            feedback.alarm()
            if (ended == FocusPhase.FOCUS) feedback.celebrate()
            _ended.tryEmit(ended)
        } else if (fromAlarm) {
            notifier.phaseEnded(ended)
        }
    }

    /** App start: finish a phase that ended while we were gone, or re-arm the alarm (e.g. after reboot). */
    fun reconcile() = scope.launch {
        val t = settings.currentTimer()
        if (!t.running) return@launch
        if (t.endsAt <= System.currentTimeMillis()) completeIfDue(fromAlarm = false) else schedule(t.endsAt)
    }

    fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms?.canScheduleExactAlarms() == true

    private fun schedule(at: Long) {
        val am = alarms ?: return
        val pi = pendingIntent()
        try {
            if (canScheduleExact()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) // may be a little late
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun cancelAlarm() { alarms?.cancel(pendingIntent()) }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, FocusAlarmReceiver::class.java).setAction(FocusAlarmReceiver.ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

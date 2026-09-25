package com.ubad.academy.data.local.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ubad.academy.domain.model.AppLanguage
import com.ubad.academy.domain.model.FocusPhase
import com.ubad.academy.domain.model.FocusSettings
import com.ubad.academy.domain.model.ThemeId
import com.ubad.academy.domain.model.TimerState
import com.ubad.academy.domain.model.UserSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "ubad_prefs")

/**
 * Lightweight preferences — the Android equivalent of localStorage `ubad.prefs.v1`,
 * `ubad.onboarding.v1` and the `state.focus` / settings slices of appdata.
 */
@Singleton
class SettingsStore @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.settingsDataStore

    private object K {
        val NAME = stringPreferencesKey("name")
        val LANG = stringPreferencesKey("lang")
        val SOUND = booleanPreferencesKey("sound")
        val THEME = stringPreferencesKey("theme")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val FOCUS_DAY = stringPreferencesKey("focus_day")
        val FOCUS_DONE = intPreferencesKey("focus_done")
        val FOCUS_MINS = intPreferencesKey("focus_mins")
        val BREAK_MINS = intPreferencesKey("break_mins")
        val T_PHASE = stringPreferencesKey("timer_phase")
        val T_RUNNING = booleanPreferencesKey("timer_running")
        val T_ENDS = longPreferencesKey("timer_ends_at")
        val T_REMAIN = intPreferencesKey("timer_remaining")
        val BLOG_NEXT = stringPreferencesKey("blog_next_token")
        val BLOG_SAVED = longPreferencesKey("blog_saved_at")
        val NOTIF_ASKED = booleanPreferencesKey("notif_asked")
        fun pdfPage(assetId: String) = intPreferencesKey("pdf_page_$assetId")
    }

    val settings: Flow<UserSettings> = store.data.map { p ->
        UserSettings(
            name = p[K.NAME] ?: UserSettings.DEFAULT_NAME,
            language = AppLanguage.from(p[K.LANG]),
            sound = p[K.SOUND] ?: true,
            theme = ThemeId.from(p[K.THEME]),
            onboarded = p[K.ONBOARDED] ?: false,
        )
    }.distinctUntilChanged()

    val focus: Flow<FocusSettings> = store.data.map { p ->
        FocusSettings(
            day = p[K.FOCUS_DAY] ?: "",
            done = p[K.FOCUS_DONE] ?: 0,
            focusMins = p[K.FOCUS_MINS] ?: 25,
            breakMins = p[K.BREAK_MINS] ?: 5,
        )
    }.distinctUntilChanged()

    val timer: Flow<TimerState> = store.data.map { p ->
        TimerState(
            phase = if (p[K.T_PHASE] == FocusPhase.BREAK.key) FocusPhase.BREAK else FocusPhase.FOCUS,
            running = p[K.T_RUNNING] ?: false,
            endsAt = p[K.T_ENDS] ?: 0L,
            remainingSec = p[K.T_REMAIN] ?: -1,
        )
    }.distinctUntilChanged()

    suspend fun current(): UserSettings = settings.first()
    suspend fun currentFocus(): FocusSettings = focus.first()
    suspend fun currentTimer(): TimerState = timer.first()

    suspend fun setName(name: String) = store.edit { it[K.NAME] = name.take(40) }
    suspend fun setLanguage(lang: AppLanguage) = store.edit { it[K.LANG] = lang.tag }
    suspend fun setSound(on: Boolean) = store.edit { it[K.SOUND] = on }
    suspend fun setTheme(theme: ThemeId) = store.edit { it[K.THEME] = theme.key }
    suspend fun setOnboarded() = store.edit { it[K.ONBOARDED] = true }

    suspend fun setFocus(f: FocusSettings) = store.edit {
        it[K.FOCUS_DAY] = f.day
        it[K.FOCUS_DONE] = f.done
        it[K.FOCUS_MINS] = f.focusMins.coerceIn(FocusSettings.MIN_LEN, FocusSettings.MAX_LEN)
        it[K.BREAK_MINS] = f.breakMins.coerceIn(FocusSettings.MIN_LEN, FocusSettings.MAX_LEN)
    }

    suspend fun setTimer(t: TimerState) = store.edit {
        it[K.T_PHASE] = t.phase.key
        it[K.T_RUNNING] = t.running
        it[K.T_ENDS] = t.endsAt
        it[K.T_REMAIN] = t.remainingSec
    }

    /**
     * Web `Focus.tick` when the countdown reaches zero, done atomically so the alarm
     * receiver and an on-screen ticker can't both count the same session. Returns the
     * phase that just ended, or null if nothing was due (already handled / not running).
     */
    suspend fun completePhase(now: Long, today: String): FocusPhase? {
        var ended: FocusPhase? = null
        store.edit { p ->
            val running = p[K.T_RUNNING] ?: false
            val endsAt = p[K.T_ENDS] ?: 0L
            if (!running || endsAt - now > 1_000) return@edit
            val phase = if (p[K.T_PHASE] == FocusPhase.BREAK.key) FocusPhase.BREAK else FocusPhase.FOCUS
            if (phase == FocusPhase.FOCUS) {
                val sameDay = p[K.FOCUS_DAY] == today
                p[K.FOCUS_DAY] = today
                p[K.FOCUS_DONE] = (if (sameDay) p[K.FOCUS_DONE] ?: 0 else 0) + 1
            }
            p[K.T_PHASE] = (if (phase == FocusPhase.FOCUS) FocusPhase.BREAK else FocusPhase.FOCUS).key
            p[K.T_RUNNING] = false
            p[K.T_ENDS] = 0L
            p[K.T_REMAIN] = -1
            ended = phase
        }
        return ended
    }

    val blogNextToken: Flow<String?> = store.data.map { it[K.BLOG_NEXT] }
    val blogSavedAt: Flow<Long> = store.data.map { it[K.BLOG_SAVED] ?: 0L }
    suspend fun setBlogMeta(next: String?, savedAt: Long) = store.edit {
        if (next == null) it.remove(K.BLOG_NEXT) else it[K.BLOG_NEXT] = next
        it[K.BLOG_SAVED] = savedAt
    }

    suspend fun notificationAsked(): Boolean = store.data.first()[K.NOTIF_ASKED] ?: false
    suspend fun setNotificationAsked() = store.edit { it[K.NOTIF_ASKED] = true }

    suspend fun pdfLastPage(assetId: String): Int = store.data.first()[K.pdfPage(assetId)] ?: 0
    suspend fun setPdfLastPage(assetId: String, page: Int) = store.edit { it[K.pdfPage(assetId)] = page }

    /**
     * "Erase all data" (web `wipeAll`): every preference returns to its default,
     * while the onboarding flag stays set exactly like `ubad.onboarding.v1` survives on web.
     */
    suspend fun wipe() = store.edit {
        it.clear()
        it[K.ONBOARDED] = true
    }
}

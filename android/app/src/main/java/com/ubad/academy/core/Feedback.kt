package com.ubad.academy.core

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.ubad.academy.R
import com.ubad.academy.data.local.prefs.SettingsStore
import com.ubad.academy.di.AppScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UI feedback shared app-wide: the web `Sound.play('click')` (respecting the Sound
 * setting) and `FX.confetti()` celebrations (rendered by the app shell).
 */
@Singleton
class Feedback @Inject constructor(
    @ApplicationContext context: Context,
    settings: SettingsStore,
    @AppScope scope: CoroutineScope,
) {
    private val soundOn = settings.settings.map { it.sound }.stateIn(scope, SharingStarted.Eagerly, true)

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        ).build()
    private val clickId = pool.load(context, R.raw.click, 1)

    fun click() {
        if (soundOn.value) pool.play(clickId, 0.35f, 0.35f, 0, 0, 1f)
    }

    private val _celebrations = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val celebrations: SharedFlow<Unit> = _celebrations

    fun celebrate() { _celebrations.tryEmit(Unit) }
}

package com.ubad.academy

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ubad.academy.data.local.prefs.SettingsStore
import com.ubad.academy.domain.model.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class MainUiState(val settings: UserSettings)

@HiltViewModel
class MainViewModel @Inject constructor(settingsStore: SettingsStore) : ViewModel() {

    val uiState: StateFlow<MainUiState?> = settingsStore.settings
        .onEach { applyLocale(it.language.tag) }
        .map { MainUiState(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Per-app language (AppCompat) mirrors the persisted setting; RTL follows the locale. */
    private fun applyLocale(tag: String) {
        val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        if (!current.startsWith(tag)) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        }
    }
}

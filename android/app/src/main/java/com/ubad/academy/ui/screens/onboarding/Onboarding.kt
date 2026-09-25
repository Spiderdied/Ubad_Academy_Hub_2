package com.ubad.academy.ui.screens.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ubad.academy.R
import com.ubad.academy.data.local.db.UbadDatabase
import com.ubad.academy.data.local.prefs.SettingsStore
import com.ubad.academy.domain.model.AppLanguage
import com.ubad.academy.domain.model.LinkKind
import com.ubad.academy.domain.model.ThemeId
import com.ubad.academy.domain.model.UserSettings
import com.ubad.academy.ui.components.ThemePicker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val db: UbadDatabase,
) : ViewModel() {
    private val _show = MutableStateFlow(false)
    val show = _show.asStateFlow()

    init {
        // `shouldShowOnboarding()`: not done yet, default name, and no user content.
        viewModelScope.launch {
            val s = settings.current()
            val empty = db.courses().courses().isEmpty() && db.notes().notes().isEmpty() &&
                db.study().decks().isEmpty() && db.study().links(LinkKind.FORMS.key).isEmpty()
            _show.value = !s.onboarded && s.name == UserSettings.DEFAULT_NAME && empty
        }
    }

    fun setLanguage(l: AppLanguage) = viewModelScope.launch { settings.setLanguage(l) }
    fun setTheme(t: ThemeId) = viewModelScope.launch { settings.setTheme(t) }

    fun finish(name: String) = viewModelScope.launch {
        settings.setName(name.trim().ifEmpty { UserSettings.DEFAULT_NAME })
        settings.setOnboarded()
        _show.value = false
    }

    /** Dismissing without "Start" behaves like closing the web modal: ask again next launch. */
    fun dismiss() { _show.value = false }
}

/** First-run personalization (web `showOnboarding`): language, name, theme — all applied live. */
@Composable
fun OnboardingGate(settings: UserSettings, viewModel: OnboardingViewModel = hiltViewModel()) {
    val show by viewModel.show.collectAsStateWithLifecycle()
    if (!show) return
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = viewModel::dismiss,
        icon = { Image(painterResource(R.drawable.ubad_logo), null, Modifier.size(56.dp)) },
        title = { Text(stringResource(R.string.onboard_title), textAlign = TextAlign.Center) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.onboard_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.onboard_language), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(settings.language == AppLanguage.EN, { viewModel.setLanguage(AppLanguage.EN) }, { Text(stringResource(R.string.lang_en)) })
                    FilterChip(settings.language == AppLanguage.AR, { viewModel.setLanguage(AppLanguage.AR) }, { Text(stringResource(R.string.lang_ar)) })
                }
                OutlinedTextField(
                    name, { name = it.take(40) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.onboard_name)) },
                    placeholder = { Text(stringResource(R.string.onboard_namePh)) },
                )
                Text(stringResource(R.string.onboard_theme), style = MaterialTheme.typography.labelLarge)
                ThemePicker(settings.theme, viewModel::setTheme)
            }
        },
        confirmButton = { Button(onClick = { viewModel.finish(name) }) { Text(stringResource(R.string.onboard_start)) } },
    )
}

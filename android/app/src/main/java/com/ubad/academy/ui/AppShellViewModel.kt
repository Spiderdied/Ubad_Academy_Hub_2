package com.ubad.academy.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ubad.academy.core.Feedback
import com.ubad.academy.data.repository.AppDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** App-wide chrome state: the per-theme custom background and celebration events. */
@HiltViewModel
class AppShellViewModel @Inject constructor(appData: AppDataRepository, private val feedback: Feedback) : ViewModel() {
    val background = appData.background.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val celebrations = feedback.celebrations

    /** Navigation sounds like the web Nav: 'back' when returning, 'move'/'transition' going forward. */
    fun navSound(back: Boolean, deep: Boolean) = if (back) feedback.back() else feedback.move(deep)
}

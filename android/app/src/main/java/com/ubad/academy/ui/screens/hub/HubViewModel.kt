package com.ubad.academy.ui.screens.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ubad.academy.core.Hijri
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class HubViewModel @Inject constructor() : ViewModel() {
    private val _ramadan = MutableStateFlow<Hijri.Ramadan?>(null)
    val ramadan: StateFlow<Hijri.Ramadan?> = _ramadan.asStateFlow()

    init {
        // Up to ~400 calendar conversions → keep it off the main thread.
        viewModelScope.launch(Dispatchers.Default) { _ramadan.value = Hijri.ramadanInfo(LocalDate.now()) }
    }
}

package com.ubad.academy.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ubad.academy.ui.components.UbadScaffold

/** Temporary build-time placeholder while phases are migrated. Removed at the end of migration. */
@Composable
fun PendingScreen(title: String, onBack: () -> Unit) {
    UbadScaffold(title = title, onBack = onBack) { p ->
        Box(Modifier.fillMaxSize().padding(p), contentAlignment = Alignment.Center) { Text(title) }
    }
}

package com.ubad.academy.ui.screens.notes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ubad.academy.R
import com.ubad.academy.core.Dates
import com.ubad.academy.core.currentLocale
import com.ubad.academy.domain.model.Note
import com.ubad.academy.ui.components.EmptyState
import com.ubad.academy.ui.components.LoadingState
import com.ubad.academy.ui.components.UbadScaffold
import com.ubad.academy.ui.navigation.Route
import com.ubad.academy.ui.screens.courses.Load
import com.ubad.academy.ui.screens.dashboard.SmallChip
import com.ubad.academy.ui.theme.UbadThemeExt

@Composable
fun NotesScreen(onNavigate: (Route) -> Unit, onBack: () -> Unit, viewModel: NotesViewModel = hiltViewModel()) {
    val state by viewModel.notes.collectAsStateWithLifecycle()
    val q by viewModel.q.collectAsStateWithLifecycle()
    UbadScaffold(
        title = stringResource(R.string.nav_notes), onBack = onBack,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onNavigate(Route.NoteEditor()) },
                icon = { Icon(Icons.Outlined.Add, null) }, text = { Text(stringResource(R.string.notes_new)) },
            )
        },
    ) { pad ->
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(260.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 4.dp, bottom = pad.calculateBottomPadding() + 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalItemSpacing = 12.dp,
        ) {
            item(span = StaggeredGridItemSpan.FullLine) {
                OutlinedTextField(
                    value = q, onValueChange = viewModel::setQuery, singleLine = true,
                    placeholder = { Text(stringResource(R.string.notes_searchPh)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = {
                        if (q.isNotEmpty()) IconButton(onClick = { viewModel.setQuery("") }) { Icon(Icons.Outlined.Clear, stringResource(R.string.common_close)) }
                    },
                    label = { Text(stringResource(R.string.common_search)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            when (val s = state) {
                Load.Loading -> item(span = StaggeredGridItemSpan.FullLine) { LoadingState(Modifier.padding(top = 48.dp)) }
                is Load.Ready -> if (s.value.isEmpty()) item(span = StaggeredGridItemSpan.FullLine) {
                    EmptyState(
                        Icons.Outlined.Description, if (q.isEmpty()) stringResource(R.string.notes_empty) else stringResource(R.string.search_none, q),
                        hint = if (q.isEmpty()) stringResource(R.string.notes_emptyHint) else null,
                        action = if (q.isEmpty()) stringResource(R.string.notes_new) else null,
                        onAction = { onNavigate(Route.NoteEditor()) },
                    )
                } else items(s.value, key = { it.id }) { n -> NoteCard(n) { onNavigate(Route.NoteEditor(n.id)) } }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteCard(n: Note, onClick: () -> Unit) {
    val locale = currentLocale()
    Card(
        onClick = onClick, shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, UbadThemeExt.colors.line),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(n.title.ifEmpty { stringResource(R.string.notes_untitled) }, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (n.body.isNotEmpty()) Text(
                n.body.take(120), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4, overflow = TextOverflow.Ellipsis,
            )
            Text(
                Dates.dayMonthYear(Dates.fromEpoch(n.updatedAt).toLocalDate(), locale),
                fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline,
            )
            val hasFlags = n.pin || n.images.isNotEmpty() || n.audio.isNotEmpty() || n.tags.isNotEmpty()
            if (hasFlags) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.Center) {
                if (n.pin) SmallChip(stringResource(R.string.notes_pinned), UbadThemeExt.colors.accent(0))
                if (n.images.isNotEmpty()) Icon(Icons.Outlined.Image, stringResource(R.string.notes_images), Modifier.size(18.dp).align(Alignment.CenterVertically))
                if (n.audio.isNotEmpty()) Icon(Icons.Outlined.Mic, stringResource(R.string.notes_audio), Modifier.size(18.dp).align(Alignment.CenterVertically))
                n.tags.take(2).forEach { SmallChip(it) }
            }
        }
    }
}


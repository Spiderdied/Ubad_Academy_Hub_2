package com.ubad.academy.ui.screens.courses

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ubad.academy.R
import com.ubad.academy.core.jsString
import com.ubad.academy.ui.components.CollectMessages
import com.ubad.academy.ui.components.ConfirmDialog
import com.ubad.academy.ui.components.EmptyState
import com.ubad.academy.ui.components.LoadingState
import com.ubad.academy.ui.components.TextInputDialog
import com.ubad.academy.ui.components.UbadScaffold
import com.ubad.academy.ui.navigation.Route
import com.ubad.academy.ui.theme.UbadThemeExt

@Composable
fun CourseDetailScreen(onNavigate: (Route) -> Unit, onBack: () -> Unit, viewModel: CourseDetailViewModel = hiltViewModel()) {
    val state by viewModel.course.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(viewModel.messages.flow, snackbar)
    var editing by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var addingUnit by rememberSaveable { mutableStateOf(false) }

    val loaded = (state as? Load.Ready)?.value
    UbadScaffold(
        title = loaded?.first?.name ?: stringResource(R.string.nav_courses), onBack = onBack, snackbarHostState = snackbar,
        actions = {
            if (loaded != null) {
                IconButton(onClick = { editing = true }) { Icon(Icons.Outlined.Edit, stringResource(R.string.common_edit)) }
                IconButton(onClick = { deleting = true }) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.common_delete)) }
            }
        },
    ) { pad ->
        when (val s = state) {
            Load.Loading -> LoadingState(Modifier.padding(pad))
            is Load.Ready -> {
                val pair = s.value
                if (pair == null) {
                    EmptyState(Icons.AutoMirrored.Outlined.MenuBook, stringResource(R.string.state_not_found), Modifier.padding(pad))
                    return@UbadScaffold
                }
                val (c, index) = pair
                val accent = UbadThemeExt.colors.accent(index)
                val p = c.progress
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 4.dp, bottom = pad.calculateBottomPadding() + 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    item {
                        Card(
                            Modifier.widthIn(max = 840.dp).fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            border = BorderStroke(1.dp, UbadThemeExt.colors.line), shape = MaterialTheme.shapes.large,
                        ) {
                            Column(Modifier.padding(18.dp)) {
                                Text(c.name, style = MaterialTheme.typography.headlineSmall)
                                Text(
                                    listOf(c.code, c.instructor, c.semester).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { "—" },
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    val mono = MaterialTheme.typography.labelLarge
                                    Text("${c.credits.jsString()} ${stringResource(R.string.courses_cr)}", style = mono, fontFamily = FontFamily.Monospace)
                                    Text("${p.done}/${p.total} ${stringResource(R.string.courses_contentLc)}", style = mono, fontFamily = FontFamily.Monospace)
                                    Text("${p.pct}%", style = mono, fontFamily = FontFamily.Monospace)
                                }
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { p.pct / 100f }, color = accent, strokeCap = StrokeCap.Round,
                                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                    item {
                        Row(Modifier.widthIn(max = 840.dp).fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.courses_units), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
                            Button(onClick = { addingUnit = true }) { Icon(Icons.Outlined.Add, null); Text(stringResource(R.string.courses_newUnit)) }
                        }
                    }
                    if (c.units.isEmpty()) item { EmptyState(Icons.Outlined.Layers, stringResource(R.string.courses_noUnits), compact = true) }
                    items(c.units, key = { it.id }) { u ->
                        val up = u.progress
                        ListItem(
                            headlineContent = { Text(u.title) },
                            supportingContent = { Text("${up.done}/${up.total} ${stringResource(R.string.courses_contentLc)}", fontFamily = FontFamily.Monospace) },
                            leadingContent = { Icon(Icons.Outlined.Layers, null, tint = accent) },
                            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth().clip(MaterialTheme.shapes.medium)
                                .clickable(role = Role.Button) { onNavigate(Route.UnitDetail(c.id, u.id)) },
                        )
                    }
                }
            }
        }
    }

    loaded?.first?.let { c ->
        if (editing) CourseDialog(c, onSave = { viewModel.save(c, it); editing = false }, onDismiss = { editing = false })
        if (deleting) ConfirmDialog(
            title = stringResource(R.string.courses_edit), message = stringResource(R.string.common_confirmDelete),
            onConfirm = { viewModel.delete(c, onBack) }, onDismiss = { deleting = false },
        )
    }
    if (addingUnit) TextInputDialog(
        title = stringResource(R.string.courses_newUnit), label = stringResource(R.string.courses_unitName), initial = "", maxLength = 80,
        confirmLabel = stringResource(R.string.common_add),
        onConfirm = { viewModel.addUnit(it); addingUnit = false }, onDismiss = { addingUnit = false },
    )
}


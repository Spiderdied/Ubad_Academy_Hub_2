package com.ubad.academy.ui.screens.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Mosque
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ubad.academy.R
import com.ubad.academy.core.Dates
import com.ubad.academy.core.Web
import com.ubad.academy.core.currentLocale
import com.ubad.academy.core.scheduleTimeLabel
import com.ubad.academy.domain.model.CalendarEvent
import com.ubad.academy.domain.model.Note
import com.ubad.academy.ui.components.CollectMessages
import com.ubad.academy.ui.components.ConfirmDialog
import com.ubad.academy.ui.components.EmptyState
import com.ubad.academy.ui.components.EventDialog
import com.ubad.academy.ui.components.LoadingState
import com.ubad.academy.ui.components.UbadScaffold
import com.ubad.academy.ui.navigation.Route
import com.ubad.academy.ui.theme.UbadThemeExt
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun DashboardScreen(onNavigate: (Route) -> Unit, onBack: (() -> Unit)?, viewModel: DashboardViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(viewModel.messages.flow, snackbar)
    var eventDialog by rememberSaveable { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<TaskRow?>(null) }

    UbadScaffold(title = stringResource(R.string.nav_dashboard), onBack = onBack, snackbarHostState = snackbar) { pad ->
        val s = state ?: return@UbadScaffold LoadingState(Modifier.padding(pad))
        val locale = currentLocale()
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(340.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 4.dp, bottom = pad.calculateBottomPadding() + 24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalItemSpacing = 14.dp,
        ) {
            item(span = StaggeredGridItemSpan.FullLine) {
                Column {
                    Text(Dates.weekdayDayMonth(LocalDate.now(), locale), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline)
                    Text(
                        stringResource(Dates.greetingKey(LocalTime.now().hour), s.name),
                        style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() },
                    )
                }
            }
            item(span = StaggeredGridItemSpan.FullLine) { Tiles(s, onNavigate) }
            item { TasksCard(s, viewModel::addTask, viewModel::toggle, { toDelete = it }) }
            item {
                SectionCard(stringResource(R.string.dash_upcoming), action = {
                    TextButton(onClick = { eventDialog = true }) { Icon(Icons.Outlined.Add, null); Text(stringResource(R.string.dash_newEvent)) }
                }) {
                    if (s.upcoming.isEmpty()) EmptyState(Icons.Outlined.CalendarMonth, stringResource(R.string.dash_noEvents), compact = true)
                    else s.upcoming.forEach { EventRow(it) { onNavigate(Route.Calendar(it.date)) } }
                }
            }
            item {
                SectionCard(stringResource(R.string.dash_recentNotes), action = {
                    TextButton(onClick = { onNavigate(Route.NoteEditor()) }) { Icon(Icons.Outlined.Add, null); Text(stringResource(R.string.dash_newNote)) }
                }) {
                    if (s.recentNotes.isEmpty()) EmptyState(Icons.Outlined.Description, stringResource(R.string.dash_noNotes), compact = true)
                    else s.recentNotes.forEach { NoteRow(it) { onNavigate(Route.NoteEditor(it.id)) } }
                }
            }
            item {
                SectionCard(stringResource(R.string.dash_quick)) {
                    QuickActions(onNavigate) { eventDialog = true }
                }
            }
        }
    }

    if (eventDialog) EventDialog("", Web.today(), "", "", editing = false,
        onSave = { t, d, tm, desc -> viewModel.saveEvent(t, d, tm, desc); eventDialog = false },
        onDismiss = { eventDialog = false })
    toDelete?.let { row ->
        ConfirmDialog(
            title = stringResource(R.string.dash_tasks),
            message = stringResource(if (row.fromSchedule) R.string.schedule_deleteMsg else R.string.common_confirmDelete),
            onConfirm = { viewModel.delete(row) }, onDismiss = { toDelete = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Tiles(s: DashboardState, onNavigate: (Route) -> Unit) {
    val c = UbadThemeExt.colors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 4) {
        val m = Modifier.weight(1f)
        Tile(Icons.Outlined.Mosque, "${s.prayersDone}/5", stringResource(R.string.dash_stPrayers), c.accent(0), m) { onNavigate(Route.Islam) }
        Tile(Icons.AutoMirrored.Outlined.MenuBook, s.courseCount.toString(), stringResource(R.string.dash_stCourses), c.accent(1), m) { onNavigate(Route.Courses) }
        Tile(Icons.Outlined.TaskAlt, s.pendingCount.toString(), stringResource(R.string.dash_stTasks), c.accent(2), m, null)
        Tile(Icons.Outlined.Description, s.noteCount.toString(), stringResource(R.string.dash_stNotes), c.accent(3), m) { onNavigate(Route.Notes) }
    }
}

@Composable
private fun Tile(icon: ImageVector, value: String, label: String, accent: androidx.compose.ui.graphics.Color, modifier: Modifier, onClick: (() -> Unit)?) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    val border = BorderStroke(1.dp, UbadThemeExt.colors.line)
    val content: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.padding(14.dp)) {
            Box(Modifier.size(34.dp).clip(MaterialTheme.shapes.small).background(accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    if (onClick != null) Card(onClick = onClick, modifier = modifier, colors = colors, border = border, content = content)
    else Card(modifier = modifier, colors = colors, border = border, content = content)
}

@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, UbadThemeExt.colors.line),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
                action()
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun TasksCard(s: DashboardState, onAdd: (String) -> Unit, onToggle: (TaskRow) -> Unit, onDelete: (TaskRow) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val submit = { if (text.isNotBlank()) { onAdd(text); text = "" } }
    SectionCard(stringResource(R.string.dash_tasks)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text, onValueChange = { text = it.take(120) },
                placeholder = { Text(stringResource(R.string.dash_addTaskPh)) },
                singleLine = true, modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = submit) { Icon(Icons.Outlined.Add, null); Text(stringResource(R.string.common_add)) }
        }
        Spacer(Modifier.height(8.dp))
        if (s.tasks.isEmpty()) EmptyState(Icons.Outlined.CheckCircle, stringResource(R.string.dash_noTasks), compact = true)
        else s.tasks.forEach { TaskItem(it, s.today, onToggle, onDelete) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskItem(row: TaskRow, today: String, onToggle: (TaskRow) -> Unit, onDelete: (TaskRow) -> Unit) {
    val arabic = currentLocale().language == "ar"
    val locale = currentLocale()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Checkbox(checked = row.done, onCheckedChange = { onToggle(row) })
        Column(Modifier.weight(1f)) {
            Text(
                row.title, style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (row.done) TextDecoration.LineThrough else null,
                color = if (row.done) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (row.fromSchedule) {
                    SmallChip("${scheduleTimeLabel(row.start, arabic)}–${scheduleTimeLabel(row.end, arabic)}")
                    SmallChip(stringResource(R.string.schedule_fromSchedule), UbadThemeExt.colors.ok)
                } else if (row.due.isNotEmpty()) {
                    val overdue = row.due < today
                    SmallChip(if (overdue) stringResource(R.string.dash_overdue) else fmtDue(row.due, today, locale), if (overdue) MaterialTheme.colorScheme.error else null)
                }
            }
        }
        IconButton(onClick = { onDelete(row) }) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.common_delete)) }
    }
}

@Composable
private fun fmtDue(d: String, today: String, locale: java.util.Locale): String = when (d) {
    today -> stringResource(R.string.common_today)
    Web.ymd(LocalDate.now().plusDays(1)) -> stringResource(R.string.common_tomorrow)
    else -> Dates.dayMonth(Web.parseYmd(d), locale)
}

@Composable
fun SmallChip(text: String, color: androidx.compose.ui.graphics.Color? = null) {
    SuggestionChip(
        onClick = {}, enabled = false,
        label = { Text(text, style = MaterialTheme.typography.labelSmall) },
        colors = SuggestionChipDefaults.suggestionChipColors(
            disabledLabelColor = color ?: MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        border = BorderStroke(1.dp, (color ?: MaterialTheme.colorScheme.outline).copy(alpha = 0.4f)),
        modifier = Modifier.height(26.dp),
    )
}

@Composable
private fun EventRow(e: CalendarEvent, onClick: () -> Unit) {
    val locale = currentLocale()
    val d = Web.parseYmd(e.date)
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick, role = Role.Button).padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier.width(48.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)).padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(e.date.substring(8), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(Dates.monthShort(d, locale), style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOf(e.time, e.desc).filter { it.isNotEmpty() }.joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun NoteRow(n: Note, onClick: () -> Unit) {
    val locale = currentLocale()
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick, role = Role.Button).padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Description, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(n.title.ifEmpty { stringResource(R.string.notes_untitled) }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (n.body.isNotEmpty()) Text(n.body.take(60), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        SmallChip(Dates.dayMonth(Dates.fromEpoch(n.updatedAt).toLocalDate(), locale))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickActions(onNavigate: (Route) -> Unit, onNewEvent: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 2) {
        val m = Modifier.weight(1f)
        OutlinedButton(onClick = { onNavigate(Route.NoteEditor()) }, m) { Icon(Icons.Outlined.Description, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.dash_newNote), maxLines = 1) }
        OutlinedButton(onClick = { onNavigate(Route.Islam) }, m) { Icon(Icons.Outlined.Mosque, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.nav_islam), maxLines = 1) }
        OutlinedButton(onClick = onNewEvent, m) { Icon(Icons.Outlined.CalendarMonth, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.dash_newEvent), maxLines = 1) }
        FilledTonalButton(onClick = { onNavigate(Route.Study()) }, m) { Icon(Icons.Outlined.Layers, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.dash_goStudy), maxLines = 1) }
    }
}

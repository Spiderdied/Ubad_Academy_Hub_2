package com.ubad.academy.ui.screens.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ubad.academy.R
import com.ubad.academy.core.Dates
import com.ubad.academy.core.Web
import com.ubad.academy.core.currentLocale
import com.ubad.academy.domain.model.CalendarEvent
import com.ubad.academy.ui.components.CollectMessages
import com.ubad.academy.ui.components.ConfirmDialog
import com.ubad.academy.ui.components.EmptyState
import com.ubad.academy.ui.components.EventDialog
import com.ubad.academy.ui.components.LoadingState
import com.ubad.academy.ui.components.UbadScaffold
import com.ubad.academy.ui.screens.dashboard.SmallChip
import com.ubad.academy.ui.theme.UbadThemeExt
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

@Composable
fun CalendarScreen(onBack: () -> Unit, viewModel: CalendarViewModel = hiltViewModel()) {
    val sel by viewModel.selected.collectAsStateWithLifecycle()
    val monthKey by viewModel.month.collectAsStateWithLifecycle()
    val byDate by viewModel.eventsByDate.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(viewModel.messages.flow, snackbar)

    var adding by rememberSaveable { mutableStateOf(false) }
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }

    UbadScaffold(
        title = stringResource(R.string.nav_calendar), onBack = onBack, snackbarHostState = snackbar,
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text(stringResource(R.string.cal_new)) })
        },
    ) { pad ->
        val map = byDate
        if (map == null) { LoadingState(Modifier.padding(pad)); return@UbadScaffold }
        val dayEvents = map[sel].orEmpty()
        BoxWithConstraints(Modifier.fillMaxSize().padding(pad)) {
            val wide = maxWidth >= 760.dp
            val grid = @Composable { m: Modifier ->
                MonthGrid(YearMonth.parse(monthKey), sel, map, viewModel::select, viewModel::shift, viewModel::goToday, m)
            }
            val side = @Composable { m: Modifier ->
                DayPanel(sel, dayEvents, onEdit = { editId = it.id }, onDelete = { deleteId = it.id }, modifier = m)
            }
            if (wide) Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                grid(Modifier.weight(1.2f).verticalScroll(rememberScrollState()))
                side(Modifier.weight(1f).verticalScroll(rememberScrollState()))
            } else Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                grid(Modifier.widthIn(max = 560.dp))
                Spacer(Modifier.height(16.dp))
                side(Modifier.widthIn(max = 560.dp))
            }
        }

        val all = map.values.flatten()
        val editing = editId?.let { id -> all.firstOrNull { it.id == id } }
        if (adding || editing != null) EventDialog(
            initialTitle = editing?.title.orEmpty(), initialDate = editing?.date ?: sel,
            initialTime = editing?.time.orEmpty(), initialDesc = editing?.desc.orEmpty(), editing = editing != null,
            onSave = { t, d, tm, ds -> viewModel.save(editing, t, d, tm, ds); adding = false; editId = null },
            onDismiss = { adding = false; editId = null },
        )
        deleteId?.let { id ->
            ConfirmDialog(
                title = stringResource(R.string.cal_edit), message = stringResource(R.string.cal_deleteMsg),
                onConfirm = { viewModel.delete(id); deleteId = null }, onDismiss = { deleteId = null },
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    sel: String,
    events: Map<String, List<CalendarEvent>>,
    onSelect: (String) -> Unit,
    onShift: (Long) -> Unit,
    onToday: () -> Unit,
    modifier: Modifier,
) {
    val locale = currentLocale()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val today = Web.today()
    val colors = UbadThemeExt.colors
    Column(
        modifier.pointerInput(rtl) {
            // Web: horizontal swipe changes month only (next = RTL ? swipe left : swipe right).
            var dx = 0f
            detectHorizontalDragGestures(
                onDragStart = { dx = 0f },
                onDragEnd = { if (abs(dx) > 60.dp.toPx()) onShift(if ((if (rtl) dx < 0 else dx > 0)) 1 else -1) },
            ) { _, d -> dx += d }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onShift(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.cal_prev)) }
            Text(
                Dates.monthYear(month.atDay(1), locale), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f).semantics { heading() },
            )
            IconButton(onClick = { onShift(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.cal_next)) }
            OutlinedButton(onClick = onToday) { Text(stringResource(R.string.common_today)) }
        }
        Spacer(Modifier.height(8.dp))
        // Sunday-first like the web grid.
        Row(Modifier.fillMaxWidth()) {
            for (i in 0 until 7) {
                val d = LocalDate.of(2023, 1, 1).plusDays(i.toLong())
                Text(
                    Dates.weekdayShort(d, locale), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        val lead = month.atDay(1).dayOfWeek.let { if (it == DayOfWeek.SUNDAY) 0 else it.value }
        val cells = lead + month.lengthOfMonth()
        val rows = (cells + 6) / 7
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val dayNum = r * 7 + c - lead + 1
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (dayNum in 1..month.lengthOfMonth()) {
                            val date = month.atDay(dayNum)
                            val ds = Web.ymd(date)
                            val n = events[ds]?.size ?: 0
                            val isSel = ds == sel
                            val isToday = ds == today
                            val label = Dates.long(date, locale)
                            Column(
                                Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium)
                                    .background(if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                                    .then(if (isToday) Modifier.border(BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary), MaterialTheme.shapes.medium) else Modifier)
                                    .clickable { onSelect(ds) }
                                    .semantics { contentDescription = if (n > 0) "$label · $n" else label; selected = isSel },
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    dayNum.toString(), style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                )
                                if (n > 0) Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 2.dp)) {
                                    repeat(minOf(3, n)) { i -> Box(Modifier.size(4.dp).clip(CircleShape).background(colors.accent(i))) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayPanel(sel: String, events: List<CalendarEvent>, onEdit: (CalendarEvent) -> Unit, onDelete: (CalendarEvent) -> Unit, modifier: Modifier) {
    val locale = currentLocale()
    Card(
        modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, UbadThemeExt.colors.line),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(Dates.weekdayDayMonth(Web.parseYmd(sel), locale), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(8.dp))
            if (events.isEmpty()) EmptyState(Icons.Outlined.CalendarMonth, stringResource(R.string.cal_none), compact = true)
            events.forEach { e ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(e.title, style = MaterialTheme.typography.bodyLarge)
                        if (e.time.isNotEmpty()) SmallChip(e.time)
                        if (e.desc.isNotEmpty()) Text(e.desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onEdit(e) }) { Icon(Icons.Outlined.Edit, stringResource(R.string.common_edit)) }
                    IconButton(onClick = { onDelete(e) }) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.common_delete)) }
                }
            }
        }
    }
}


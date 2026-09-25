package com.ubad.academy.ui.screens.study

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ubad.academy.R
import com.ubad.academy.core.Web
import com.ubad.academy.core.currentLocale
import com.ubad.academy.core.scheduleTimeLabel
import com.ubad.academy.domain.model.ScheduleEntry
import com.ubad.academy.ui.components.LoadingState
import com.ubad.academy.ui.theme.UbadThemeExt
import java.time.LocalDate
import kotlin.math.roundToInt

/** Web constants: `SCHED_DAY_ORDER` (Sat → Fri), 06:00–23:00, 30-minute slots. */
val SCHED_DAY_ORDER = listOf(6, 0, 1, 2, 3, 4, 5)
private const val START_MIN = 6 * 60
private const val END_MIN = 23 * 60
private const val TOTAL = END_MIN - START_MIN
private const val SLOT = 30
private val MINUTE_H = 1.1.dp
private val COL_W = 104.dp
private val AXIS_W = 64.dp

fun dayLabelRes(d: Int): Int = when (d) {
    6 -> R.string.schedule_daySat
    0 -> R.string.schedule_daySun
    1 -> R.string.schedule_dayMon
    2 -> R.string.schedule_dayTue
    3 -> R.string.schedule_dayWed
    4 -> R.string.schedule_dayThu
    else -> R.string.schedule_dayFri
}

/** JS `Date.getDay()` for a date (0 = Sunday). */
fun jsDay(d: LocalDate): Int = d.dayOfWeek.value % 7

/** What the schedule dialog is opened for: edit an [entry], or add with defaults. */
data class ScheduleDraftRequest(val entry: ScheduleEntry? = null, val day: Int? = null, val start: String? = null)

@Composable
fun ScheduleTab(vm: StudyViewModel, bottom: Dp, onOpen: (ScheduleDraftRequest) -> Unit) {
    val entries by vm.schedule.collectAsStateWithLifecycle()
    val list = entries ?: return LoadingState()
    val arabic = currentLocale().language == "ar"
    val td = Web.today()
    val nowDay = jsDay(LocalDate.now())
    val density = LocalDensity.current
    val colors = UbadThemeExt.colors

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 12.dp, bottom = bottom)) {
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            Column {
                // Header: "Weekly routine" + day heads (tap = add on that day).
                Row {
                    Text(
                        stringResource(R.string.schedule_weekly), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(AXIS_W).padding(end = 4.dp), maxLines = 2,
                    )
                    SCHED_DAY_ORDER.forEach { d ->
                        val today = d == nowDay
                        Column(
                            Modifier.width(COL_W).padding(2.dp).clip(MaterialTheme.shapes.small)
                                .background(if (today) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                                .clickable { onOpen(ScheduleDraftRequest(day = d)) }.padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(stringResource(dayLabelRes(d)), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                            if (today) Text(stringResource(R.string.schedule_today), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Row {
                    // Time axis.
                    Box(Modifier.width(AXIS_W).height(MINUTE_H * TOTAL)) {
                        for (m in START_MIN..END_MIN step SLOT) {
                            Text(
                                scheduleTimeLabel(Web.minToHm(m), arabic), fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.offset(y = MINUTE_H * (m - START_MIN) - 6.dp),
                            )
                        }
                    }
                    SCHED_DAY_ORDER.forEach { d ->
                        val slotPx = with(density) { MINUTE_H.toPx() }
                        Box(
                            Modifier.width(COL_W).height(MINUTE_H * TOTAL).padding(horizontal = 2.dp)
                                .border(0.5.dp, colors.line)
                                .pointerInput(d) {
                                    // Tap on an empty slot: add at that time (rounded to 5 min).
                                    detectTapGestures { p ->
                                        val mins = ((START_MIN + p.y / slotPx) / 5f).roundToInt() * 5
                                        onOpen(ScheduleDraftRequest(day = d, start = Web.minToHm(mins.coerceIn(0, 1439))))
                                    }
                                },
                        ) {
                            for (m in START_MIN + SLOT until END_MIN step SLOT) {
                                HorizontalDivider(Modifier.offset(y = MINUTE_H * (m - START_MIN)), thickness = 0.5.dp, color = colors.line)
                            }
                            list.filter { d in it.days }.forEach { x -> ScheduleBlock(x, d == nowDay && x.doneDates[td] == true, arabic) { onOpen(ScheduleDraftRequest(entry = x)) } }
                        }
                    }
                }
            }
        }
        Text(
            stringResource(R.string.schedule_emptyHint), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun ScheduleBlock(x: ScheduleEntry, done: Boolean, arabic: Boolean, onClick: () -> Unit) {
    val a = Web.hmToMin(x.start) ?: return
    var b = Web.hmToMin(x.end) ?: return
    if (b <= a) b = minOf(END_MIN, a + 60) // keep overnight sessions bounded in the grid (web)
    val top = (a - START_MIN).coerceIn(0, TOTAL)
    val height = maxOf(30, minOf(TOTAL - top, b - a))
    val colors = UbadThemeExt.colors
    val bg = if (done) colors.ok.copy(alpha = 0.22f) else MaterialTheme.colorScheme.primaryContainer
    val time = "${scheduleTimeLabel(x.start, arabic)}–${scheduleTimeLabel(x.end, arabic)}"
    Column(
        Modifier.offset(y = MINUTE_H * top).height(MINUTE_H * height).fillMaxWidth().padding(1.dp)
            .clip(MaterialTheme.shapes.small).background(bg).clickable(onClick = onClick).padding(4.dp)
            .semantics { contentDescription = "${x.title}, $time" + if (done) " ✓" else "" },
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onPrimaryContainer) {
            Text(x.title + if (done) " ✓" else "", style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(time, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** `openScheduleModal(entry, cb, defaults)` */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScheduleDialog(req: ScheduleDraftRequest, onSave: (String, List<Int>, String, String) -> Unit, onDismiss: () -> Unit) {
    val e = req.entry
    val arabic = currentLocale().language == "ar"
    val initialStart = e?.start ?: req.start ?: "18:00"
    // Web default end is 19:00; for a tapped slot we keep the one-hour length instead.
    val initialEnd = e?.end ?: if (req.start != null) Web.minToHm(minOf(1439, (Web.hmToMin(req.start) ?: 1080) + 60)) else "19:00"
    var title by rememberSaveable { mutableStateOf(e?.title.orEmpty()) }
    val days = remember { mutableStateListOf<Int>().apply { addAll(e?.days ?: listOf(req.day ?: jsDay(LocalDate.now()))) } }
    var start by rememberSaveable { mutableStateOf(initialStart) }
    var end by rememberSaveable { mutableStateOf(initialEnd) }
    var autoEnd by rememberSaveable { mutableStateOf(true) }
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (e != null) R.string.schedule_edit else R.string.schedule_add)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it.take(120) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.schedule_sessionTitle)) }, placeholder = { Text(stringResource(R.string.schedule_sessionPh)) })
                Text(stringResource(R.string.schedule_days), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SCHED_DAY_ORDER.forEach { d ->
                        FilterChip(selected = d in days, onClick = { if (d in days) days.remove(d) else days.add(d) }, label = { Text(stringResource(dayLabelRes(d))) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { picking = "start" }, modifier = Modifier.weight(1f)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.schedule_start), style = MaterialTheme.typography.labelSmall)
                            Text(scheduleTimeLabel(start, arabic), fontFamily = FontFamily.Monospace)
                        }
                    }
                    OutlinedButton(onClick = { picking = "end" }, modifier = Modifier.weight(1f)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.schedule_end), style = MaterialTheme.typography.labelSmall)
                            Text(scheduleTimeLabel(end, arabic), fontFamily = FontFamily.Monospace)
                        }
                    }
                }
                error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = Web.hmToMin(start); val b = Web.hmToMin(end)
                error = when {
                    title.isBlank() -> R.string.schedule_needTitle
                    days.isEmpty() -> R.string.schedule_selectDay
                    a == null || b == null -> R.string.toast_error
                    b <= a -> R.string.schedule_endAfter
                    else -> null
                }
                if (error == null) onSave(title, SCHED_DAY_ORDER.filter { it in days }, Web.minToHm(a!!), Web.minToHm(b!!))
            }) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )

    picking?.let { which ->
        TimePickDialog(if (which == "start") start else end, onDismiss = { picking = null }) { v ->
            picking = null
            if (which == "start") {
                // Web: moving the start drags a default one-hour end along until the end is edited.
                if (autoEnd && Web.hmToMin(initialEnd) == ((Web.hmToMin(initialStart) ?: 0) + 60) % 1440) {
                    Web.hmToMin(v)?.let { end = Web.minToHm(minOf(1439, it + 60)) }
                }
                start = v
            } else { end = v; autoEnd = false }
        }
    }
}

/** `openScheduleTimePicker` → Material time picker (12-hour, like the web chips). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickDialog(initial: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    val n = Web.hmToMin(initial) ?: 1080
    val state = rememberTimePickerState(initialHour = n / 60, initialMinute = n % 60, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.schedule_pickTime)) },
        text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state) } },
        confirmButton = { TextButton(onClick = { onDone(Web.minToHm(state.hour * 60 + state.minute)) }) { Text(stringResource(R.string.schedule_done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}


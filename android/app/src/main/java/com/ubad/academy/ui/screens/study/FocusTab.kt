package com.ubad.academy.ui.screens.study

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ubad.academy.R
import com.ubad.academy.domain.model.FocusPhase
import com.ubad.academy.domain.model.FocusSettings
import com.ubad.academy.ui.screens.dashboard.SmallChip
import com.ubad.academy.ui.theme.UbadThemeExt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val FOCUS_PRESETS = listOf(5, 15, 25, 50, 90)
private val BREAK_PRESETS = listOf(5, 10, 15, 20)

/** `fmtMMSS` */
fun mmss(sec: Int): String = "%02d:%02d".format(java.util.Locale.ROOT, sec / 60, sec % 60)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FocusTab(vm: StudyViewModel, bottom: Dp) {
    val t by vm.timerState.collectAsStateWithLifecycle()
    val f by vm.focus.collectAsStateWithLifecycle()
    val timer = t ?: return
    val focus = f ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Re-read permission state when returning from system settings.
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeTick++ }
    val canExact = remember(resumeTick) { vm.timer.canScheduleExact() }
    val notifOk = remember(resumeTick) { vm.timerNotificationsAllowed() }

    // The display ticks from the saved end time; the value is never the source of truth.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(timer.running, timer.endsAt) {
        while (timer.running) {
            now = System.currentTimeMillis()
            if (timer.endsAt <= now) { vm.tickDue(); break }
            delay(250)
        }
    }
    val remaining = vm.timer.remaining(timer, focus, if (timer.running) now else System.currentTimeMillis())
    val full = vm.timer.lengthSec(timer.phase, focus).coerceAtLeast(1)
    val isFocus = timer.phase == FocusPhase.FOCUS

    var askExact by rememberSaveable { mutableStateOf(false) }
    fun maybeAskExact() = scope.launch {
        if (!vm.timer.canScheduleExact() && !vm.exactAlarmAsked()) { vm.setExactAlarmAsked(); askExact = true }
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        resumeTick++
        if (granted) vm.rescheduleIfRunning()
        maybeAskExact()
    }
    // Permissions are requested lazily, only when the user starts a timer.
    fun onStartPressed() {
        vm.startPause(false)
        scope.launch {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notifOk && !vm.notificationAsked()) {
                vm.setNotificationAsked()
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else maybeAskExact()
        }
    }
    fun openExactSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottom),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(
            Modifier.widthIn(max = 640.dp).fillMaxWidth(), shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            border = BorderStroke(1.dp, UbadThemeExt.colors.line),
        ) {
            Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SmallChip(stringResource(if (isFocus) R.string.focus_session else R.string.focus_break), if (isFocus) UbadThemeExt.colors.ok else null)
                    SmallChip("${stringResource(R.string.focus_today)}: ${vm.timer.doneToday(focus)}")
                }
                Spacer(Modifier.height(12.dp))
                FocusRing(remaining, full, isFocus)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { if (timer.running) vm.startPause(true) else onStartPressed() }) {
                        Text(stringResource(if (timer.running) R.string.focus_pause else R.string.focus_start))
                    }
                    OutlinedButton(onClick = vm::reset) { Text(stringResource(R.string.focus_reset)) }
                }
                if (!canExact || !notifOk) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(if (!notifOk) R.string.notif_hint else R.string.exact_alarm_hint),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (notifOk && !canExact) TextButton(onClick = ::openExactSettings) { Text(stringResource(R.string.exact_alarm_allow)) }
                }

                Spacer(Modifier.height(20.dp))
                DurationPicker(
                    title = "${stringResource(R.string.focus_focusLabel)} · ${stringResource(R.string.focus_length)}",
                    presets = FOCUS_PRESETS, value = focus.focusMins, enabled = !timer.running,
                ) { vm.setDuration(FocusPhase.FOCUS, it) }
                Spacer(Modifier.height(18.dp))
                DurationPicker(
                    title = "${stringResource(R.string.focus_breakLabel)} · ${stringResource(R.string.focus_breakLength)}",
                    presets = BREAK_PRESETS, value = focus.breakMins, enabled = !timer.running,
                ) { vm.setDuration(FocusPhase.BREAK, it) }
            }
        }
    }

    if (askExact) AlertDialog(
        onDismissRequest = { askExact = false },
        title = { Text(stringResource(R.string.exact_alarm_title)) },
        text = { Text(stringResource(R.string.exact_alarm_msg)) },
        confirmButton = { TextButton(onClick = { askExact = false; openExactSettings() }) { Text(stringResource(R.string.exact_alarm_allow)) } },
        dismissButton = { TextButton(onClick = { askExact = false }) { Text(stringResource(R.string.exact_alarm_not_now)) } },
    )
}

@Composable
private fun FocusRing(remaining: Int, full: Int, isFocus: Boolean) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val arc = if (isFocus) MaterialTheme.colorScheme.primary else UbadThemeExt.colors.ok
    val frac = (remaining.toFloat() / full).coerceIn(0f, 1f)
    val label = mmss(remaining)
    Box(Modifier.size(216.dp).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            val w = 10.dp.toPx()
            drawArc(track, 0f, 360f, false, style = Stroke(w))
            drawArc(arc, -90f, 360f * frac, false, style = Stroke(w, cap = StrokeCap.Round))
        }
        Text(label, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.displayMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DurationPicker(title: String, presets: List<Int>, value: Int, enabled: Boolean, onPick: (Int) -> Unit) {
    val min = stringResource(R.string.focus_min)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            presets.forEach { m -> FilterChip(selected = value == m, enabled = enabled, onClick = { onPick(m) }, label = { Text("$m $min") }) }
        }
        // Custom length 1–180 (web: clampNum on change).
        var text by remember(value) { mutableStateOf(value.toString()) }
        fun commit() {
            val v = text.toIntOrNull()?.coerceIn(FocusSettings.MIN_LEN, FocusSettings.MAX_LEN) ?: value
            text = v.toString(); if (v != value) onPick(v)
        }
        OutlinedTextField(
            value = text, onValueChange = { v -> text = v.filter(Char::isDigit).take(3) }, enabled = enabled, singleLine = true,
            label = { Text("${stringResource(R.string.focus_custom)} (${stringResource(R.string.focus_customRange)})") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit() }),
            modifier = Modifier.padding(top = 10.dp).width(240.dp).onFocusChanged { if (!it.isFocused) commit() },
        )
    }
}

package com.ubad.academy.ui.screens.islam

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Mosque
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ubad.academy.R
import com.ubad.academy.core.Dates
import com.ubad.academy.core.Hijri
import com.ubad.academy.core.Web
import com.ubad.academy.core.currentLocale
import com.ubad.academy.domain.model.Dhikr
import com.ubad.academy.domain.model.IslamState
import com.ubad.academy.ui.components.CollectMessages
import com.ubad.academy.ui.components.ConfirmDialog
import com.ubad.academy.ui.components.LoadingState
import com.ubad.academy.ui.components.UbadScaffold
import com.ubad.academy.ui.theme.UbadThemeExt

private fun prayerRes(k: String) = when (k) {
    "fajr" -> R.string.islam_p_fajr; "zuhr" -> R.string.islam_p_zuhr; "asr" -> R.string.islam_p_asr
    "maghrib" -> R.string.islam_p_maghrib; else -> R.string.islam_p_isha
}

private fun rawatibRes(k: String) = when (k) {
    "pf" -> R.string.islam_r_pf; "duha" -> R.string.islam_r_duha; "bz" -> R.string.islam_r_bz; "az" -> R.string.islam_r_az
    "am" -> R.string.islam_r_am; "ai" -> R.string.islam_r_ai; "qiyam" -> R.string.islam_r_qiyam; "shaf" -> R.string.islam_r_shaf
    else -> R.string.islam_r_witr
}

private fun builtinDhikrRes(id: String): Int? = when (id) {
    "sub" -> R.string.islam_t_sub; "ham" -> R.string.islam_t_ham; "akb" -> R.string.islam_t_akb
    "ist" -> R.string.islam_t_ist; "saw" -> R.string.islam_t_saw; else -> null
}

private fun isBuiltin(id: String) = id in IslamState.TASBIH_DEFAULTS

/** `tasLabel(item)`: custom text, or the translated built-in. */
@Composable
private fun dhikrLabel(d: Dhikr): String = d.text.ifEmpty { builtinDhikrRes(d.id)?.let { stringResource(it) } ?: d.id }

private val IslamTab.labelRes get() = when (this) {
    IslamTab.PRAYERS -> R.string.islam_tabPrayers; IslamTab.SUNNAH -> R.string.islam_tabSunnah
    IslamTab.FASTING -> R.string.islam_tabFasting; IslamTab.TASBIH -> R.string.islam_tabTasbih
}
private val IslamTab.icon: ImageVector get() = when (this) {
    IslamTab.PRAYERS -> Icons.Outlined.Mosque; IslamTab.SUNNAH -> Icons.Outlined.Spa
    IslamTab.FASTING -> Icons.Outlined.NightsStay; IslamTab.TASBIH -> Icons.Outlined.TouchApp
}

@Composable
fun IslamScreen(viewModel: IslamViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val hijri by viewModel.hijri.collectAsStateWithLifecycle()
    val tabKey by viewModel.tab.collectAsStateWithLifecycle()
    val tab = IslamTab.entries.firstOrNull { it.key == tabKey } ?: IslamTab.PRAYERS
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(viewModel.messages.flow, snackbar)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshCalendar() }

    UbadScaffold(title = stringResource(R.string.nav_islam), onBack = null, snackbarHostState = snackbar) { pad ->
        val isl = s ?: return@UbadScaffold LoadingState(Modifier.padding(pad))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(pad).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val m = Modifier.widthIn(max = 720.dp).fillMaxWidth()
            Hero(hijri, m)
            SingleChoiceSegmentedButtonRow(m) {
                IslamTab.entries.forEachIndexed { i, t ->
                    SegmentedButton(
                        selected = t == tab, onClick = { viewModel.selectTab(t) },
                        shape = SegmentedButtonDefaults.itemShape(i, IslamTab.entries.size),
                        icon = { Icon(t.icon, null, Modifier.size(18.dp)) },
                    ) { Text(stringResource(t.labelRes), maxLines = 1, style = MaterialTheme.typography.labelMedium) }
                }
            }
            when (tab) {
                IslamTab.PRAYERS -> PrayersCard(isl, viewModel, m)
                IslamTab.SUNNAH -> SunnahCard(isl, viewModel, m)
                IslamTab.FASTING -> FastingCard(isl, hijri, viewModel, m)
                IslamTab.TASBIH -> TasbihCard(isl, viewModel, m)
            }
        }
    }
}

@Composable
private fun Hero(h: HijriInfo?, modifier: Modifier) {
    val context = LocalContext.current
    val locale = currentLocale()
    IslamCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.islam_peace), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                if (h != null) {
                    val hij = remember(h.date, locale) { Dates.hijriLong(context, h.date, locale) }
                    Text(hij, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp))
                    Text(Dates.long(h.date, locale), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            h?.ramadan?.let { r ->
                val during = r is Hijri.Ramadan.During
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(start = 12.dp).widthIn(max = 140.dp)) {
                    Text(stringResource(if (during) R.string.islam_ramMubarak else R.string.islam_ramIn), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                    Text(r.days.toString(), style = MaterialTheme.typography.displaySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(if (during) R.string.islam_ramLeft else R.string.islam_ramDays), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun IslamCard(modifier: Modifier, content: @Composable () -> Unit) {
    Card(
        modifier, shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, UbadThemeExt.colors.line),
    ) { Column(Modifier.padding(16.dp)) { content() } }
}

@Composable
private fun CardHeader(title: String, chip: String) {
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
            Text(chip, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
        }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, sub: String? = null, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(checked, role = Role.Checkbox, onValueChange = { onToggle() }).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(horizontal = 8.dp))
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun PrayersCard(isl: IslamState, vm: IslamViewModel, modifier: Modifier) {
    val done = IslamState.PRAYER_KEYS.count { (isl.prayers[it] ?: 0) > 0 }
    IslamCard(modifier) {
        CardHeader(stringResource(R.string.islam_prayers), "$done / 5")
        IslamState.PRAYER_KEYS.forEach { k -> CheckRow(stringResource(prayerRes(k)), (isl.prayers[k] ?: 0) > 0) { vm.togglePrayer(k) } }
        LinearProgressIndicator(progress = { done / 5f }, color = UbadThemeExt.colors.accCyan, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
    }
}

@Composable
private fun SunnahCard(isl: IslamState, vm: IslamViewModel, modifier: Modifier) {
    val keys = IslamState.RAWATIB_KEYS
    val done = keys.count { (isl.rawatib[it] ?: 0) > 0 }
    IslamCard(modifier) {
        CardHeader(stringResource(R.string.islam_rawatib), "$done / ${keys.size}")
        keys.forEach { k ->
            CheckRow(stringResource(rawatibRes(k)), (isl.rawatib[k] ?: 0) != 0, sub = if (k == "duha") stringResource(R.string.islam_duhaHint) else null) { vm.toggleRawatib(k) }
        }
        LinearProgressIndicator(progress = { done.toFloat() / keys.size }, color = UbadThemeExt.colors.accViolet, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
    }
}

@Composable
private fun FastingCard(isl: IslamState, h: HijriInfo?, vm: IslamViewModel, modifier: Modifier) {
    val locale = currentLocale()
    val fastingToday = Web.today() in isl.fasts
    IslamCard(modifier) {
        CardHeader(stringResource(R.string.islam_fasting), "${isl.fasts.size} ${stringResource(R.string.islam_totalFasts)}")
        val label = stringResource(R.string.islam_fastToday)
        Row(
            Modifier.fillMaxWidth().toggleable(fastingToday, role = Role.Switch, onValueChange = { vm.toggleFastToday() }).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                if (h != null && (h.isMonThu || h.isWhite)) {
                    Text(stringResource(if (h.isWhite) R.string.islam_whiteDays else R.string.islam_sunnahDay), style = MaterialTheme.typography.bodySmall, color = UbadThemeExt.colors.ok)
                }
            }
            Switch(checked = fastingToday, onCheckedChange = null)
        }
        Text(stringResource(R.string.islam_upcoming), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
        val up = h?.upcoming.orEmpty()
        if (up.isEmpty()) {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CalendarMonth, null, tint = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.ana_noData), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else up.forEach { u ->
            val name = when (u.kind) {
                Hijri.FastKind.MONDAY -> stringResource(R.string.islam_monday)
                Hijri.FastKind.THURSDAY -> stringResource(R.string.islam_thursday)
                Hijri.FastKind.WHITE -> "${stringResource(R.string.islam_whiteDays)} (${u.hijriDay})"
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Text(Dates.shortChip(u.date, locale), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(name, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TasbihCard(isl: IslamState, vm: IslamViewModel, modifier: Modifier) {
    val t = isl.tasbih
    val items = t.adhkar
    val current = items.firstOrNull { it.id == t.mode } ?: items.firstOrNull()
    val haptics = LocalHapticFeedback.current
    var editing by remember { mutableStateOf<Pair<Boolean, Dhikr?>?>(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }

    IslamCard(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.islam_tasbih), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Button(onClick = { editing = true to null }) { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.islam_tasbihAdd)) }
            Spacer(Modifier.width(6.dp))
            OutlinedButton(onClick = vm::resetCount) { Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.islam_tasbihReset)) }
        }
        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEach { d ->
                val on = d.id == current?.id
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = if (on) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            dhikrLabel(d), style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f).clickable(role = Role.RadioButton) { vm.select(d.id) }.padding(horizontal = 14.dp, vertical = 12.dp),
                        )
                        IconButton(onClick = { editing = true to d }) { Icon(Icons.Outlined.Edit, stringResource(R.string.islam_tasbihEdit)) }
                        if (!isBuiltin(d.id)) IconButton(onClick = { deleting = d.id }) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.islam_tasbihDelete)) }
                    }
                }
            }
        }
        // Big tap target (web `.tas-tap`).
        val tapLabel = current?.let { dhikrLabel(it) }.orEmpty()
        Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
            Surface(
                onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); vm.tap() },
                shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(3.dp, MaterialTheme.colorScheme.primary),
                modifier = Modifier.size(200.dp).semantics { contentDescription = "$tapLabel ${t.count} / ${t.target}" },
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(t.count.toString(), style = MaterialTheme.typography.displayMedium, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("/ ${t.target}", fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                }
            }
        }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            IslamState.TASBIH_TARGETS.forEach { g -> FilterChip(selected = t.target == g, onClick = { vm.setTarget(g) }, label = { Text(g.toString(), fontFamily = FontFamily.Monospace) }) }
        }
        Text(
            "${stringResource(R.string.islam_tasbihTotal)}: ${t.total}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    editing?.let { (_, d) -> DhikrDialog(d, onSave = { vm.saveDhikr(d?.id, it); editing = null }, onDismiss = { editing = null }) }
    deleting?.let { id ->
        ConfirmDialog(
            title = stringResource(R.string.islam_tasbihDelete), message = stringResource(R.string.islam_tasbihDeleteMsg),
            onConfirm = { deleting = null; vm.deleteDhikr(id) }, onDismiss = { deleting = null },
        )
    }
}

/** `openTasModal` — built-ins can be renamed and restored to their default text. */
@Composable
private fun DhikrDialog(d: Dhikr?, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    val builtinText = d?.let { builtinDhikrRes(it.id) }?.let { stringResource(it) }
    var text by rememberSaveable { mutableStateOf(d?.text?.ifEmpty { builtinText.orEmpty() } ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (d == null) R.string.islam_tasbihAdd else R.string.islam_tasbihEdit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(text, { text = it.take(120) }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(stringResource(R.string.islam_tasbihText)) }, placeholder = { Text(stringResource(R.string.islam_tasbihTextPh)) })
                if (d != null && builtinText != null) {
                    OutlinedButton(onClick = { text = builtinText }) {
                        Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.islam_tasbihResetText))
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onSave(text) }) { Text(stringResource(R.string.common_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}


package com.ubad.academy.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.ubad.academy.R
import com.ubad.academy.core.Dates
import com.ubad.academy.core.Web
import com.ubad.academy.core.currentLocale
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Web `confirmModal({title,msg,onOk})`. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = stringResource(R.string.common_delete),
    destructive: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirmLabel, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/** Single text field modal (unit name, deck title, etc.). */
@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = stringResource(R.string.common_save),
    placeholder: String? = null,
    maxLength: Int = 120,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    allowEmpty: Boolean = false,
) {
    var value by rememberSaveable { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val ok = allowEmpty || value.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.take(maxLength) },
                label = { Text(label) },
                placeholder = placeholder?.let { { Text(it) } },
                singleLine = singleLine,
                minLines = if (singleLine) 1 else 3,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = if (singleLine) ImeAction.Done else ImeAction.Default),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = { TextButton(enabled = ok, onClick = { onConfirm(value.trim()) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/** A read-only field that opens the Material date picker; value is YYYY-MM-DD. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, ymd: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, clearable: Boolean = false) {
    var open by rememberSaveable { mutableStateOf(false) }
    val locale = currentLocale()
    val shown = if (Web.isYmd(ymd)) Dates.dayMonthYear(Web.parseYmd(ymd), locale) else ""
    PickerField(label, shown, Icons.Outlined.CalendarMonth, { open = true }, modifier, if (clearable && shown.isNotEmpty()) ({ onChange("") }) else null)
    if (open) {
        val init = if (Web.isYmd(ymd)) Web.parseYmd(ymd) else LocalDate.now()
        val state = rememberDatePickerState(initialSelectedDateMillis = init.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Web.ymd(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())) }
                    open = false
                }) { Text(stringResource(R.string.common_done)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.common_cancel)) } },
        ) { DatePicker(state) }
    }
}

/** A read-only field that opens the Material time picker; value is HH:MM or "" (optional). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(label: String, hm: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, clearable: Boolean = true) {
    var open by rememberSaveable { mutableStateOf(false) }
    PickerField(label, hm, Icons.Outlined.Schedule, { open = true }, modifier, if (clearable && hm.isNotEmpty()) ({ onChange("") }) else null)
    if (open) {
        val m = Web.hmToMin(hm) ?: (9 * 60)
        val state = rememberTimePickerState(initialHour = m / 60, initialMinute = m % 60)
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = { onChange(Web.minToHm(state.hour * 60 + state.minute)); open = false }) {
                    Text(stringResource(R.string.common_done))
                }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.common_cancel)) } },
            text = { TimePicker(state) },
        )
    }
}

@Composable
private fun PickerField(
    label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    onOpen: () -> Unit, modifier: Modifier, onClear: (() -> Unit)?,
) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null) },
        trailingIcon = onClear?.let { { IconButton(onClick = it) { Icon(Icons.Outlined.Close, stringResource(R.string.common_delete)) } } },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }.also { src ->
            LaunchedEffect(src) {
                src.interactions.collect { if (it is androidx.compose.foundation.interaction.PressInteraction.Release) onOpen() }
            }
        },
    )
}

/** `openEventModal(ev, dflt, cb)` — title, date, optional time, description. */
@Composable
fun EventDialog(
    initialTitle: String,
    initialDate: String,
    initialTime: String,
    initialDesc: String,
    editing: Boolean,
    onSave: (title: String, date: String, time: String, desc: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var date by rememberSaveable { mutableStateOf(initialDate.ifEmpty { Web.today() }) }
    var time by rememberSaveable { mutableStateOf(initialTime) }
    var desc by rememberSaveable { mutableStateOf(initialDesc) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (editing) R.string.cal_edit else R.string.cal_new)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it.take(120) }, label = { Text(stringResource(R.string.cal_eventTitle)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateField(stringResource(R.string.cal_date), date, { date = it }, Modifier.weight(1f))
                    TimeField(stringResource(R.string.cal_time) + " (" + stringResource(R.string.common_optional) + ")", time, { time = it }, Modifier.weight(1f))
                }
                OutlinedTextField(desc, { desc = it.take(500) }, label = { Text(stringResource(R.string.cal_desc)) }, minLines = 3, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank() && Web.isYmd(date), onClick = { onSave(title, date, time, desc) }) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

package com.ubad.academy.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ubad.academy.BuildConfig
import com.ubad.academy.R
import com.ubad.academy.data.backup.BackupCodec
import com.ubad.academy.data.backup.BackupSection
import com.ubad.academy.domain.model.AppLanguage
import com.ubad.academy.domain.model.ThemeId
import com.ubad.academy.ui.components.CollectMessages
import com.ubad.academy.ui.components.ConfirmDialog
import com.ubad.academy.ui.components.LoadingState
import com.ubad.academy.ui.components.ThemePicker
import com.ubad.academy.ui.components.UbadScaffold
import com.ubad.academy.ui.components.labelRes
import com.ubad.academy.ui.theme.UbadThemeExt
import com.ubad.academy.ui.theme.swatch

/** Developer Support details shown by the web app (public payment identifiers, not secrets). */
private const val VODAFONE_CASH = "01093557071"
private const val PAYPAL_EMAIL = "abdalla.toaila34@gmail.com"

private fun BackupSection.labelRes() = when (this) {
    BackupSection.USER -> R.string.backup_user; BackupSection.COURSES -> R.string.backup_courses
    BackupSection.NOTES -> R.string.backup_notes; BackupSection.CALENDAR -> R.string.backup_calendar
    BackupSection.STUDY -> R.string.backup_study; BackupSection.ISLAM -> R.string.backup_islam
    BackupSection.SUMMARIES -> R.string.backup_summaries; BackupSection.FORMS -> R.string.backup_forms
    BackupSection.BACKGROUND -> R.string.backup_background
}

private fun BackupSection.emoji() = when (this) {
    BackupSection.USER -> "👤"; BackupSection.COURSES -> "📚"; BackupSection.NOTES -> "📝"; BackupSection.CALENDAR -> "📅"
    BackupSection.STUDY -> "🧠"; BackupSection.ISLAM -> "📿"; BackupSection.SUMMARIES -> "📝"; BackupSection.FORMS -> "📋"
    BackupSection.BACKGROUND -> "🖼️"
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onHome: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val bgs by viewModel.backgrounds.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val pending by viewModel.pendingRestore.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(viewModel.messages.flow, snackbar)
    val clipboard = LocalClipboardManager.current

    var bgTheme by rememberSaveable { mutableStateOf(ThemeId.DARK) }
    val pickBg = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { viewModel.setBackground(bgTheme, it) } }
    var exportSections by remember { mutableStateOf<Set<BackupSection>?>(null) }
    var exportDialog by rememberSaveable { mutableStateOf(false) }
    val createDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val sec = exportSections
        if (uri != null && sec != null) viewModel.export(uri, sec)
        exportSections = null
    }
    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::parseImport) }
    var wiping by rememberSaveable { mutableStateOf(false) }

    UbadScaffold(title = stringResource(R.string.nav_settings), onBack = onBack, snackbarHostState = snackbar) { pad ->
        val st = s ?: return@UbadScaffold LoadingState(Modifier.padding(pad))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(pad).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val m = Modifier.widthIn(max = 720.dp).fillMaxWidth()

            SetGroup(Icons.Outlined.Language, stringResource(R.string.set_language), stringResource(R.string.set_langDesc), m) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(st.language == AppLanguage.EN, { viewModel.setLanguage(AppLanguage.EN) }, { Text(stringResource(R.string.lang_en)) })
                    FilterChip(st.language == AppLanguage.AR, { viewModel.setLanguage(AppLanguage.AR) }, { Text(stringResource(R.string.lang_ar)) })
                }
            }

            SetGroup(Icons.Outlined.Person, stringResource(R.string.set_profile), stringResource(R.string.set_usernameDesc), m) {
                var name by rememberSaveable(st.name) { mutableStateOf(st.name) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(name, { name = it.take(40) }, Modifier.weight(1f), singleLine = true,
                        label = { Text(stringResource(R.string.set_username)) }, placeholder = { Text(stringResource(R.string.set_usernamePh)) })
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { viewModel.saveName(name) }) { Text(stringResource(R.string.common_save)) }
                }
            }

            SetGroup(if (st.theme.isDark) Icons.Outlined.DarkMode else Icons.Outlined.LightMode, stringResource(R.string.set_appearance), stringResource(R.string.set_theme), m) {
                ThemePicker(st.theme, viewModel::setTheme)
            }

            SetGroup(Icons.Outlined.Image, stringResource(R.string.set_bg), stringResource(R.string.set_bgDesc), m) {
                ThemeId.entries.forEachIndexed { i, th ->
                    if (i > 0) HorizontalDivider(color = UbadThemeExt.colors.line)
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(28.dp).background(th.swatch(), CircleShape).border(1.dp, UbadThemeExt.colors.line, CircleShape))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(th.labelRes()), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        OutlinedButton(onClick = {
                            bgTheme = th
                            pickBg.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) { Icon(Icons.Outlined.Upload, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.set_bgUp)) }
                        IconButton(onClick = { viewModel.removeBackground(th) }, enabled = th in bgs) { Icon(Icons.Outlined.Close, stringResource(R.string.set_bgRm)) }
                    }
                }
            }

            Card(m, shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), border = BorderStroke(1.dp, UbadThemeExt.colors.line)) {
                val label = stringResource(R.string.set_sound)
                Row(
                    Modifier.fillMaxWidth().toggleable(st.sound, role = Role.Switch, onValueChange = viewModel::setSound).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GroupHeader(Icons.Outlined.VolumeUp, label, stringResource(R.string.set_soundDesc), Modifier.weight(1f))
                    Switch(checked = st.sound, onCheckedChange = null)
                }
            }

            SetGroup(Icons.Outlined.Download, stringResource(R.string.set_backup), stringResource(R.string.set_backupDesc), m) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { exportDialog = true }) { Icon(Icons.Outlined.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.set_export)) }
                    OutlinedButton(onClick = { openDoc.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }) {
                        Icon(Icons.Outlined.Upload, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.set_import))
                    }
                }
            }

            SetGroup(Icons.Outlined.Favorite, stringResource(R.string.set_support), stringResource(R.string.set_supportDesc), m) {
                SupportRow(stringResource(R.string.set_vodafone), VODAFONE_CASH) { clipboard.setText(AnnotatedString(VODAFONE_CASH)); viewModel.messages.send(R.string.set_copied) }
                HorizontalDivider(color = UbadThemeExt.colors.line)
                SupportRow(stringResource(R.string.set_paypal), PAYPAL_EMAIL) { clipboard.setText(AnnotatedString(PAYPAL_EMAIL)); viewModel.messages.send(R.string.set_copied) }
            }

            SetGroup(Icons.Outlined.WarningAmber, stringResource(R.string.set_danger), stringResource(R.string.set_clearMsg), m, tint = MaterialTheme.colorScheme.error) {
                Button(onClick = { wiping = true }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)) {
                    Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.set_clearAll))
                }
            }

            Card(m, shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), border = BorderStroke(1.dp, UbadThemeExt.colors.line)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.ubad_logo), null, Modifier.size(52.dp))
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(stringResource(R.string.app_name_caps), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.set_aboutBody), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${stringResource(R.string.set_version)} ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            Card(m, shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), border = BorderStroke(1.dp, UbadThemeExt.colors.line)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.rights_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.rights_body), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.rights_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.padding(8.dp))
        }
    }

    if (exportDialog) SectionDialog(
        title = stringResource(R.string.backup_createTitle), desc = stringResource(R.string.backup_createDesc), note = stringResource(R.string.backup_createNote),
        confirm = stringResource(R.string.backup_create), available = BackupSection.entries,
        onConfirm = { sel -> exportDialog = false; exportSections = sel; createDoc.launch(BackupCodec.fileName()) },
        onDismiss = { exportDialog = false }, onNone = { viewModel.messages.send(R.string.backup_none, error = true) },
    )
    pending?.let { p ->
        SectionDialog(
            title = stringResource(R.string.backup_restoreTitle), desc = stringResource(R.string.backup_restoreDesc), note = stringResource(R.string.backup_restoreNote),
            confirm = stringResource(R.string.backup_restore), available = p.available,
            onConfirm = { sel -> viewModel.restore(sel, onHome) }, onDismiss = viewModel::cancelRestore,
            onNone = { viewModel.messages.send(R.string.backup_none, error = true) },
        )
    }
    if (wiping) ConfirmDialog(
        title = stringResource(R.string.set_clearAll), message = stringResource(R.string.set_clearMsg), confirmLabel = stringResource(R.string.set_clearAll),
        onConfirm = { wiping = false; viewModel.wipe(onHome) }, onDismiss = { wiping = false },
    )
    if (busy) Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Card(shape = MaterialTheme.shapes.large) {
            Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(28.dp)); Spacer(Modifier.width(16.dp)); Text(stringResource(R.string.state_loading))
            }
        }
    }
}

@Composable
private fun GroupHeader(icon: ImageVector, title: String, desc: String, modifier: Modifier = Modifier, tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary) {
    Row(modifier, verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = tint, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SetGroup(
    icon: ImageVector, title: String, desc: String, modifier: Modifier,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit,
) {
    Card(modifier, shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), border = BorderStroke(1.dp, UbadThemeExt.colors.line)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GroupHeader(icon, title, desc, tint = tint)
            content()
        }
    }
}

@Composable
private fun SupportRow(label: String, value: String, onCopy: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            // Numbers/emails always read left-to-right (web dir="ltr").
            Text(value, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr))
        }
        OutlinedButton(onClick = onCopy) { Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.set_copy)) }
    }
}

/** Web `backupChecks` modal: select-all + one checkbox per available section. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SectionDialog(
    title: String, desc: String, note: String, confirm: String, available: List<BackupSection>,
    onConfirm: (Set<BackupSection>) -> Unit, onDismiss: () -> Unit, onNone: () -> Unit,
) {
    var selected by remember(available) { mutableStateOf(available.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(desc, style = MaterialTheme.typography.bodyMedium)
                val all = selected.size == available.size
                Row(
                    Modifier.fillMaxWidth().toggleable(all, role = Role.Checkbox) { selected = if (it) available.toSet() else emptySet() },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(all, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.backup_selectAll), fontWeight = FontWeight.SemiBold)
                }
                HorizontalDivider(color = UbadThemeExt.colors.line)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    available.forEach { sec ->
                        val on = sec in selected
                        Row(
                            Modifier.widthIn(min = 150.dp).toggleable(on, role = Role.Checkbox) { selected = if (it) selected + sec else selected - sec }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(on, null); Spacer(Modifier.width(6.dp)); Text("${sec.emoji()} ${stringResource(sec.labelRes())}")
                        }
                    }
                }
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { if (selected.isEmpty()) onNone() else onConfirm(selected) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

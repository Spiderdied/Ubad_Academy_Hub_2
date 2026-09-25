package com.ubad.academy.ui.screens.notes

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.ubad.academy.R
import com.ubad.academy.data.repository.NoteRepository
import com.ubad.academy.domain.model.NoteAttachment
import com.ubad.academy.ui.components.CollectMessages
import com.ubad.academy.ui.components.ConfirmDialog
import com.ubad.academy.ui.components.LoadingState
import com.ubad.academy.ui.components.UbadScaffold
import com.ubad.academy.ui.navigation.Route
import com.ubad.academy.ui.theme.MediaColors
import com.ubad.academy.ui.theme.UbadThemeExt
import com.ubad.academy.ui.screens.viewer.ZoomableImage
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteEditorScreen(onNavigate: (Route) -> Unit, onBack: () -> Unit, viewModel: NoteEditorViewModel = hiltViewModel()) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(viewModel.messages.flow, snackbar)
    var askDiscard by rememberSaveable { mutableStateOf(false) }
    var askDelete by rememberSaveable { mutableStateOf(false) }
    var preview by remember { mutableStateOf<NoteAttachment?>(null) }

    val dirty = draft?.saved == false
    // Natural Back replaces the web's onBeforePop guard.
    BackHandler(enabled = dirty) { askDiscard = true }
    val leave: () -> Unit = { if (dirty) askDiscard = true else onBack() }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) viewModel.pauseAudio() }
        lifecycle.addObserver(obs); onDispose { lifecycle.removeObserver(obs) }
    }

    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        if (uris.isNotEmpty()) viewModel.attach(uris, NoteRepository.Kind.IMAGE)
    }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.attach(uris, NoteRepository.Kind.AUDIO)
    }

    val d = draft
    UbadScaffold(
        title = stringResource(if (d?.exists == true) R.string.nav_notes else R.string.notes_new),
        onBack = leave, snackbarHostState = snackbar,
        actions = {
            if (d != null) {
                IconToggleButton(checked = d.pin, onCheckedChange = { viewModel.togglePin() }) {
                    Icon(
                        if (d.pin) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        stringResource(if (d.pin) R.string.notes_unpin else R.string.notes_pin),
                        tint = if (d.pin) UbadThemeExt.colors.accent(0) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = { viewModel.save(onBack) }, modifier = Modifier.padding(end = 8.dp)) {
                    Icon(Icons.Outlined.Check, null); Text(stringResource(R.string.common_save))
                }
            }
        },
    ) { pad ->
        if (d == null) { LoadingState(Modifier.padding(pad)); return@UbadScaffold }
        Box(Modifier.fillMaxSize().padding(pad).imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 840.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    d.title, viewModel::setTitle, singleLine = true, textStyle = MaterialTheme.typography.titleLarge,
                    placeholder = { Text(stringResource(R.string.notes_titlePh)) }, label = { Text(stringResource(R.string.notes_titlePh)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    d.body, viewModel::setBody, placeholder = { Text(stringResource(R.string.notes_bodyPh)) },
                    label = { Text(stringResource(R.string.notes_bodyPh)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp),
                )
                OutlinedTextField(
                    d.tags, viewModel::setTags, singleLine = true, placeholder = { Text(stringResource(R.string.notes_tagsPh)) },
                    label = { Text(stringResource(R.string.notes_tagsPh)) }, modifier = Modifier.fillMaxWidth(),
                )

                SectionHeader(stringResource(R.string.notes_images)) {
                    OutlinedButton(onClick = { pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Icon(Icons.Outlined.Image, null); Text(stringResource(R.string.notes_attachImage))
                    }
                }
                if (d.images.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val openLabel = stringResource(R.string.lb_open)
                    d.images.forEachIndexed { i, a ->
                        Box(Modifier.size(104.dp)) {
                            AsyncImage(
                                model = viewModel.file(a), contentDescription = a.name, contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium)
                                    .clickable(role = Role.Image, onClickLabel = openLabel) { preview = a },
                            )
                            FilledIconButton(
                                onClick = { viewModel.removeImage(i) },
                                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(28.dp),
                            ) { Icon(Icons.Outlined.Close, stringResource(R.string.common_delete), Modifier.size(16.dp)) }
                        }
                    }
                }

                SectionHeader(stringResource(R.string.notes_audio)) {
                    OutlinedButton(onClick = { pickAudio.launch(arrayOf("audio/*")) }) {
                        Icon(Icons.Outlined.Mic, null); Text(stringResource(R.string.notes_attachAudio))
                    }
                }
                d.audio.forEach { a -> AudioRow(a, viewModel) }

                if (d.exists) OutlinedButton(
                    onClick = { askDelete = true },
                    border = BorderStroke(1.dp, UbadThemeExt.colors.danger),
                ) {
                    Icon(Icons.Outlined.DeleteOutline, null, tint = UbadThemeExt.colors.danger)
                    Text(stringResource(R.string.common_delete), color = UbadThemeExt.colors.danger)
                }
            }
        }
    }

    if (askDiscard) AlertDialog(
        onDismissRequest = { askDiscard = false },
        title = { Text(stringResource(R.string.notes_discard)) },
        text = { Text(stringResource(R.string.notes_discardMsg)) },
        confirmButton = {
            Row {
                TextButton(onClick = { askDiscard = false; viewModel.discard(onBack) }) {
                    Text(stringResource(R.string.notes_discardBtn), color = UbadThemeExt.colors.danger)
                }
                TextButton(onClick = { askDiscard = false; viewModel.save(onBack) }) { Text(stringResource(R.string.common_save)) }
            }
        },
        dismissButton = { TextButton(onClick = { askDiscard = false }) { Text(stringResource(R.string.common_cancel)) } },
    )
    preview?.let { a ->
        // Web `openLightbox`: works for saved and not-yet-saved attachments alike.
        Dialog(onDismissRequest = { preview = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(MediaColors.backdrop)) {
                ZoomableImage(viewModel.file(a), a.name, onTap = {})
                IconButton(onClick = { preview = null }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    Icon(Icons.Outlined.Close, stringResource(R.string.common_close), tint = MediaColors.onBackdrop)
                }
            }
        }
    }
    if (askDelete) ConfirmDialog(
        title = stringResource(R.string.nav_notes), message = stringResource(R.string.notes_deleteMsg),
        onConfirm = { askDelete = false; viewModel.delete(onBack) }, onDismiss = { askDelete = false },
    )
}

@Composable
private fun SectionHeader(title: String, action: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
        action()
    }
}

@Composable
private fun AudioRow(a: NoteAttachment, vm: NoteEditorViewModel) {
    val playingId by vm.playingId.collectAsStateWithLifecycle()
    val isPlaying by vm.isPlaying.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val active = playingId == a.fileId
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, UbadThemeExt.colors.line),
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledIconButton(onClick = { vm.toggleAudio(a) }) {
                if (active && isPlaying) Icon(Icons.Outlined.Pause, stringResource(R.string.focus_pause))
                else Icon(Icons.Outlined.PlayArrow, stringResource(R.string.media_play))
            }
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(a.name, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Slider(value = if (active) progress.first else 0f, onValueChange = { if (active) vm.seek(it) }, enabled = active)
            }
            IconButton(onClick = { vm.removeAudio(a) }) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.common_delete)) }
        }
    }
}


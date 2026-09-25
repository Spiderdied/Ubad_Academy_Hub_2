package com.ubad.academy.ui.screens.courses

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.ubad.academy.R
import com.ubad.academy.core.External
import com.ubad.academy.domain.model.ContentType
import com.ubad.academy.domain.model.CourseContent
import com.ubad.academy.ui.components.CollectMessages
import com.ubad.academy.ui.components.ConfirmDialog
import com.ubad.academy.ui.components.EmptyState
import com.ubad.academy.ui.components.LoadingState
import com.ubad.academy.ui.components.TextInputDialog
import com.ubad.academy.ui.components.UbadScaffold
import com.ubad.academy.ui.navigation.Route
import com.ubad.academy.ui.theme.UbadThemeExt
import kotlinx.coroutines.launch

fun ContentType.labelRes(): Int = when (this) {
    ContentType.TEXT -> R.string.courses_text
    ContentType.IMAGE -> R.string.courses_image
    ContentType.VIDEO -> R.string.courses_video
    ContentType.AUDIO -> R.string.courses_audio
    ContentType.PDF -> R.string.courses_pdf
}

fun ContentType.icon(): ImageVector = when (this) {
    ContentType.TEXT -> Icons.AutoMirrored.Outlined.Notes
    ContentType.IMAGE -> Icons.Outlined.Image
    ContentType.VIDEO -> Icons.Outlined.Movie
    ContentType.AUDIO -> Icons.Outlined.Headphones
    ContentType.PDF -> Icons.Outlined.PictureAsPdf
}

private sealed interface Editor {
    data class Edit(val content: CourseContent) : Editor
    data object New : Editor
}

@Composable
fun UnitScreen(onNavigate: (Route) -> Unit, onBack: () -> Unit, viewModel: UnitViewModel = hiltViewModel()) {
    val state by viewModel.unit.collectAsStateWithLifecycle()
    val chosenTab by viewModel.tab.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(viewModel.messages.flow, snackbar)
    val context = LocalContext.current
    val toolbar = MaterialTheme.colorScheme.surface

    var editorNew by rememberSaveable { mutableStateOf(false) }
    var editorEditId by rememberSaveable { mutableStateOf<String?>(null) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var deletingUnit by rememberSaveable { mutableStateOf(false) }
    var deletingContentId by rememberSaveable { mutableStateOf<String?>(null) }

    // "Download" in the web = save a copy through the system file picker (no storage permission).
    var pendingCopy by remember { mutableStateOf<CourseContent?>(null) }
    val scope = rememberCoroutineScope()
    val saveCopy = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { dest ->
        val src = pendingCopy; pendingCopy = null
        if (dest != null && src != null) scope.launch { viewModel.exportAsset(src.assetId, dest) }
    }

    val loaded = (state as? Load.Ready)?.value
    UbadScaffold(
        title = loaded?.second?.title ?: stringResource(R.string.nav_courses), onBack = onBack, snackbarHostState = snackbar,
        subtitle = loaded?.first?.name,
        actions = {
            if (loaded != null) {
                IconButton(onClick = { renaming = true }) { Icon(Icons.Outlined.Edit, stringResource(R.string.common_edit)) }
                IconButton(onClick = { deletingUnit = true }) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.courses_deleteUnit)) }
            }
        },
        floatingActionButton = {
            if (loaded != null) androidx.compose.material3.ExtendedFloatingActionButton(
                onClick = { editorNew = true }, icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text(stringResource(R.string.courses_addContent)) },
            )
        },
    ) { pad ->
        when (val s = state) {
            Load.Loading -> LoadingState(Modifier.padding(pad))
            is Load.Ready -> {
                val pair = s.value
                if (pair == null) {
                    EmptyState(Icons.Outlined.Layers, stringResource(R.string.state_not_found), Modifier.padding(pad))
                    return@UbadScaffold
                }
                val unit = pair.second
                val counts = ContentType.entries.associateWith { t -> unit.contents.count { it.contentType == t } }
                // Web: remembered tab, else first type with items, else text.
                val active = chosenTab ?: ContentType.entries.firstOrNull { (counts[it] ?: 0) > 0 } ?: ContentType.TEXT
                val group = unit.contents.filter { it.contentType == active }

                Column(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
                    ScrollableTabRow(
                        selectedTabIndex = active.ordinal, edgePadding = 12.dp,
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    ) {
                        ContentType.entries.forEach { t ->
                            Tab(
                                selected = t == active, onClick = { viewModel.selectTab(t) },
                                icon = { Icon(t.icon(), null) },
                                text = { Text("${stringResource(t.labelRes())} · ${counts[t] ?: 0}") },
                            )
                        }
                    }
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = pad.calculateBottomPadding() + 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        item {
                            Text(
                                "${unit.contents.size} ${stringResource(R.string.courses_contentLc)}",
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth(),
                            )
                        }
                        if (group.isEmpty()) item {
                            EmptyState(active.icon(), stringResource(R.string.courses_noContent), hint = stringResource(active.labelRes()))
                        }
                        items(group, key = { it.id }) { x ->
                            ContentCard(
                                x = x,
                                onToggle = { viewModel.toggleDone(x) },
                                onEdit = { editorEditId = x.id },
                                onDelete = { deletingContentId = x.id },
                                body = {
                                    ContentBody(
                                        x, fileFor = viewModel::assetFile,
                                        onImage = { i -> onNavigate(Route.ImageViewer(x.id, i)) },
                                        onYoutube = { External.openYoutube(context, x.url, toolbar) },
                                        onPlay = { onNavigate(Route.MediaPlayer(x.assetId, x.title, video = x.contentType == ContentType.VIDEO)) },
                                        onOpenPdf = { onNavigate(Route.PdfViewer(x.assetId, x.title)) },
                                        onSavePdf = { pendingCopy = x; saveCopy.launch(x.name.ifEmpty { x.title + ".pdf" }) },
                                        onOpenWith = { External.viewFile(context, viewModel.uriFor(x.assetId), x.mime.ifEmpty { "application/pdf" }) },
                                    )
                                },
                            )
                        }
                    }
                }

                val editTarget = editorEditId?.let { id -> unit.contents.firstOrNull { it.id == id } }
                if (editorNew || editTarget != null) ContentEditorDialog(
                    editing = editTarget, initialType = active, saving = saving,
                    onSave = { input -> viewModel.saveContent(editTarget, input) { ok -> if (ok) { editorNew = false; editorEditId = null } } },
                    onDismiss = { editorNew = false; editorEditId = null },
                )
                deletingContentId?.let { id -> unit.contents.firstOrNull { it.id == id } }?.let { x ->
                    ConfirmDialog(
                        title = stringResource(R.string.courses_deleteContent), message = stringResource(R.string.common_confirmDelete),
                        onConfirm = { viewModel.deleteContent(x); deletingContentId = null }, onDismiss = { deletingContentId = null },
                    )
                }
                if (renaming) TextInputDialog(
                    title = stringResource(R.string.common_edit), label = stringResource(R.string.courses_unitName), initial = unit.title, maxLength = 80,
                    onConfirm = { viewModel.rename(it); renaming = false }, onDismiss = { renaming = false },
                )
                if (deletingUnit) ConfirmDialog(
                    title = stringResource(R.string.courses_deleteUnit), message = stringResource(R.string.common_confirmDelete),
                    onConfirm = { deletingUnit = false; viewModel.deleteUnit(unit, onBack) }, onDismiss = { deletingUnit = false },
                )
            }
        }
    }
}

@Composable
private fun ContentCard(
    x: CourseContent,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    body: @Composable () -> Unit,
) {
    val colors = UbadThemeExt.colors
    Card(
        Modifier.widthIn(max = 840.dp).fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, if (x.done) colors.ok.copy(alpha = 0.5f) else colors.line),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) { Icon(x.contentType.icon(), null, tint = MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(x.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val sub = stringResource(x.contentType.labelRes()) + when {
                        x.name.isNotEmpty() -> " · " + x.name
                        x.isYoutube -> " · YouTube"
                        else -> ""
                    }
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val doneLabel = stringResource(R.string.courses_contentDone)
                Checkbox(checked = x.done, onCheckedChange = { onToggle() }, modifier = Modifier.semantics { contentDescription = doneLabel })
                IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, stringResource(R.string.common_edit)) }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.common_delete)) }
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.alpha(if (x.done) 0.75f else 1f)) { body() }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContentBody(
    x: CourseContent,
    fileFor: (String) -> java.io.File,
    onImage: (Int) -> Unit,
    onYoutube: () -> Unit,
    onPlay: () -> Unit,
    onOpenPdf: () -> Unit,
    onSavePdf: () -> Unit,
    onOpenWith: () -> Unit,
) {
    val missing = @Composable { Text(stringResource(R.string.toast_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    when {
        x.contentType == ContentType.TEXT -> SelectionContainer {
            Text(x.text, style = MaterialTheme.typography.bodyLarge)
        }
        x.isYoutube -> {
            if (com.ubad.academy.core.Web.youtubeVideoId(x.url) == null) Text(stringResource(R.string.courses_badFile), color = MaterialTheme.colorScheme.error)
            else FilledTonalButton(onClick = onYoutube) { Icon(Icons.Outlined.OpenInNew, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.courses_open) + " · YouTube") }
        }
        x.contentType == ContentType.IMAGE -> {
            val refs = x.assets.map { it.id }.ifEmpty { listOfNotNull(x.assetId.ifEmpty { null }) }.filter { fileFor(it).exists() }
            if (refs.isEmpty()) missing() else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val openLabel = stringResource(R.string.lb_open)
                refs.forEachIndexed { i, id ->
                    Box(
                        Modifier.width(150.dp).aspectRatio(1f).clip(MaterialTheme.shapes.medium)
                            .clickable(role = Role.Image, onClickLabel = openLabel) { onImage(i) },
                    ) {
                        AsyncImage(model = fileFor(id), contentDescription = "${x.title} ${i + 1}", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        Text(
                            "${i + 1}/${refs.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)).padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
        x.contentType == ContentType.VIDEO || x.contentType == ContentType.AUDIO -> {
            if (x.assetId.isEmpty() || !fileFor(x.assetId).exists()) missing()
            else Button(onClick = onPlay) { Icon(Icons.Outlined.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.media_play)) }
        }
        x.contentType == ContentType.PDF -> {
            if (x.assetId.isEmpty() || !fileFor(x.assetId).exists()) missing()
            else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenPdf) { Icon(Icons.Outlined.Visibility, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.courses_openPdf)) }
                OutlinedButton(onClick = onSavePdf) { Icon(Icons.Outlined.SaveAlt, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.courses_downloadPdf)) }
                OutlinedButton(onClick = onOpenWith) { Icon(Icons.Outlined.OpenInNew, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.action_open_with)) }
            }
        }
    }
}

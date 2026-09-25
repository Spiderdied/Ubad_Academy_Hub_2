package com.ubad.academy.ui.screens.courses

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.ubad.academy.R
import com.ubad.academy.data.repository.CourseRepository
import com.ubad.academy.domain.model.ContentType
import com.ubad.academy.domain.model.CourseContent

/** `openCourseContentModal(unit, content, onSaved)` — native pickers instead of `<input type=file>`. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContentEditorDialog(
    editing: CourseContent?,
    initialType: ContentType,
    saving: Boolean,
    onSave: (CourseRepository.ContentInput) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var title by rememberSaveable { mutableStateOf(editing?.title.orEmpty()) }
    var type by rememberSaveable { mutableStateOf(editing?.contentType ?: initialType) }
    var text by rememberSaveable { mutableStateOf(if (editing?.contentType == ContentType.TEXT) editing.text else "") }
    var source by rememberSaveable {
        mutableStateOf(if (editing?.contentType == ContentType.VIDEO) editing.source.ifEmpty { if (editing.url.isNotEmpty()) "youtube" else "local" } else "local")
    }
    var ytUrl by rememberSaveable { mutableStateOf(if (editing?.contentType == ContentType.VIDEO) editing.url else "") }
    var file by rememberSaveable { mutableStateOf<Uri?>(null) }
    var fileName by rememberSaveable { mutableStateOf("") }
    val images = rememberSaveable(saver = uriListSaver) { mutableStateListOf() }

    fun nameOf(uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull().orEmpty()

    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { file = uri; fileName = nameOf(uri) }
    }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { file = uri; fileName = nameOf(uri) }
    }
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { uris ->
        if (uris.isNotEmpty()) { images.clear(); images.addAll(uris) }
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.92f),
        title = { Text(stringResource(if (editing != null) R.string.courses_editContent else R.string.courses_addContent)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(title, { title = it.take(120) }, label = { Text(stringResource(R.string.courses_contentTitle)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.courses_contentType), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ContentType.entries.forEach { t ->
                        FilterChip(
                            selected = type == t, onClick = { type = t; file = null; fileName = ""; if (t != ContentType.IMAGE) images.clear() },
                            label = { Text(stringResource(t.labelRes())) }, leadingIcon = { Icon(t.icon(), null) },
                        )
                    }
                }
                when (type) {
                    ContentType.TEXT -> OutlinedTextField(
                        text, { text = it.take(50_000) }, label = { Text(stringResource(R.string.courses_content)) },
                        placeholder = { Text(stringResource(R.string.courses_textPh)) }, minLines = 8, modifier = Modifier.fillMaxWidth(),
                    )
                    ContentType.VIDEO -> {
                        Text(stringResource(R.string.courses_videoSource), style = MaterialTheme.typography.labelLarge)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            listOf("local" to R.string.courses_videoLocal, "youtube" to R.string.courses_videoYouTube).forEachIndexed { i, (k, label) ->
                                SegmentedButton(source == k, { source = k }, SegmentedButtonDefaults.itemShape(i, 2)) { Text(stringResource(label)) }
                            }
                        }
                        if (source == "youtube") OutlinedTextField(
                            ytUrl, { ytUrl = it.take(500) }, label = { Text(stringResource(R.string.courses_videoUrl)) },
                            placeholder = { Text(stringResource(R.string.courses_videoUrlPh)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        ) else FilePickRow(
                            fileName.ifEmpty { if (editing?.contentType == ContentType.VIDEO) editing.name else "" },
                        ) { pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }
                    }
                    ContentType.IMAGE -> {
                        val old = if (editing?.contentType == ContentType.IMAGE) editing.assets.size.takeIf { it > 0 } ?: (if (editing.assetId.isNotEmpty()) 1 else 0) else 0
                        val count = images.size.takeIf { it > 0 } ?: old
                        FilePickRow(if (count > 0) "$count ${stringResource(R.string.courses_imagesSelected)}" else "") {
                            pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    }
                    ContentType.AUDIO, ContentType.PDF -> FilePickRow(
                        fileName.ifEmpty { if (editing?.contentType == type) editing.name else "" },
                    ) { openDoc.launch(arrayOf(if (type == ContentType.PDF) "application/pdf" else "audio/*")) }
                }
                if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = !saving && title.isNotBlank(), onClick = {
                onSave(CourseRepository.ContentInput(title, type, text, source, ytUrl, file, images.toList()))
            }) { Text(stringResource(R.string.courses_saveContent)) }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun FilePickRow(current: String, onPick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(onClick = onPick, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.AttachFile, null); Text(stringResource(R.string.courses_chooseFile))
        }
        if (current.isNotEmpty()) Text(current, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val uriListSaver = androidx.compose.runtime.saveable.listSaver<androidx.compose.runtime.snapshots.SnapshotStateList<Uri>, String>(
    save = { l -> l.map { it.toString() } },
    restore = { l -> mutableStateListOf<Uri>().apply { addAll(l.map(Uri::parse)) } },
)

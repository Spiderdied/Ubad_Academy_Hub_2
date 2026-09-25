package com.ubad.academy.ui.screens.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.ubad.academy.R
import com.ubad.academy.core.External
import com.ubad.academy.data.local.files.FileStore
import com.ubad.academy.data.repository.CourseRepository
import com.ubad.academy.data.repository.NoteRepository
import com.ubad.academy.ui.components.EmptyState
import com.ubad.academy.ui.components.LoadingState
import com.ubad.academy.ui.theme.MediaColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject

data class GalleryImage(val file: File, val name: String, val mime: String)

sealed interface GallerySource {
    data class CourseContent(val contentId: String) : GallerySource
    data class Note(val noteId: String) : GallerySource
}

@HiltViewModel
class ImageViewerViewModel @Inject constructor(
    private val courses: CourseRepository,
    private val notes: NoteRepository,
    private val files: FileStore,
) : ViewModel() {
    suspend fun load(source: GallerySource): List<GalleryImage> = when (source) {
        is GallerySource.CourseContent -> {
            val c = courses.courses.first().asSequence().flatMap { it.contents }.firstOrNull { it.id == source.contentId }
            when {
                c == null -> emptyList()
                c.assets.isNotEmpty() -> c.assets.map { GalleryImage(courses.assetFile(it.id), it.name.ifEmpty { c.title }, it.mime) }
                c.assetId.isNotEmpty() -> listOf(GalleryImage(courses.assetFile(c.assetId), c.name.ifEmpty { c.title }, c.mime))
                else -> emptyList()
            }.filter { it.file.exists() }
        }
        is GallerySource.Note -> notes.note(source.noteId)?.images.orEmpty()
            .map { GalleryImage(notes.file(it), it.name, it.mime) }.filter { it.file.exists() }
    }

    fun uriFor(f: File) = files.uriFor(f)
}

/** Native replacement for the web lightbox gallery (`openLightboxGallery`). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewerScreen(source: GallerySource, startIndex: Int, onBack: () -> Unit, viewModel: ImageViewerViewModel = hiltViewModel()) {
    val images by produceState<List<GalleryImage>?>(null, source) { value = viewModel.load(source) }
    var chrome by rememberSaveable { mutableStateOf(true) }
    val context = LocalContext.current

    Box(Modifier.fillMaxSize().background(MediaColors.backdrop)) {
        val list = images
        when {
            list == null -> LoadingState()
            list.isEmpty() -> EmptyState(Icons.Outlined.Image, stringResource(R.string.toast_error))
            else -> {
                val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, list.size - 1)) { list.size }
                HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { i ->
                    ZoomableImage(
                        model = list[i].file,
                        contentDescription = stringResource(R.string.image_desc, i + 1, list.size) + " · " + list[i].name,
                        onTap = { chrome = !chrome },
                    )
                }
                if (chrome) TopAppBar(
                    title = {
                        Text(
                            "${pager.currentPage + 1}/${list.size} · ${list[pager.currentPage].name}",
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) } },
                    actions = {
                        IconButton(onClick = {
                            val img = list[pager.currentPage]
                            External.shareFile(context, viewModel.uriFor(img.file), img.mime.ifEmpty { "image/*" }, img.name)
                        }) { Icon(Icons.Outlined.Share, stringResource(R.string.action_share)) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MediaColors.scrim, titleContentColor = MediaColors.onBackdrop,
                        navigationIconContentColor = MediaColors.onBackdrop, actionIconContentColor = MediaColors.onBackdrop,
                    ),
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                )
            }
        }
    }
}

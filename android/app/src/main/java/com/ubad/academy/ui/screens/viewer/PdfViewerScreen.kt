package com.ubad.academy.ui.screens.viewer

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material.icons.outlined.ZoomOutMap
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ubad.academy.R
import com.ubad.academy.core.pdf.PdfSession
import com.ubad.academy.ui.components.EmptyState
import com.ubad.academy.ui.components.ImmersiveMode
import com.ubad.academy.ui.components.LoadingState
import com.ubad.academy.ui.components.TextInputDialog
import com.ubad.academy.ui.theme.MediaColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 5f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(onBack: () -> Unit, viewModel: PdfViewerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var chromeInFullscreen by remember { mutableStateOf(false) }
    val showChrome = !fullscreen || chromeInFullscreen
    ImmersiveMode(fullscreen)

    val ready = state as? PdfViewerViewModel.State.Ready
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = ready?.initialPage ?: 0)
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    var goTo by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val current by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.firstOrNull { it.offset <= center && it.offset + it.size >= center }?.index
                ?: listState.firstVisibleItemIndex
        }
    }
    LaunchedEffect(current, ready) { if (ready != null) viewModel.onPageVisible(current) }
    // First open: jump to the remembered page once the document is ready.
    var jumped by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(ready) {
        if (ready != null && !jumped) { jumped = true; if (ready.initialPage > 0) listState.scrollToItem(ready.initialPage) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        topBar = {
            if (showChrome) TopAppBar(
                title = { Text(viewModel.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) } },
                actions = {
                    if (ready != null) {
                        TextButton(onClick = { goTo = true }) {
                            Text(stringResource(R.string.pdf_page_of, current + 1, ready.session.pageCount), style = MaterialTheme.typography.labelLarge)
                        }
                        IconButton(onClick = { fullscreen = !fullscreen; chromeInFullscreen = false }) {
                            if (fullscreen) Icon(Icons.Outlined.FullscreenExit, stringResource(R.string.pdf_exit_fullscreen))
                            else Icon(Icons.Outlined.Fullscreen, stringResource(R.string.pdf_fullscreen))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        },
        bottomBar = {
            if (showChrome && ready != null) BottomAppBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                val count = ready.session.pageCount
                IconButton(enabled = current > 0, onClick = { scope.launch { listState.animateScrollToItem(current - 1) } }) {
                    Icon(Icons.Outlined.KeyboardArrowUp, stringResource(R.string.courses_pdfPrev))
                }
                if (count > 1) Slider(
                    value = current.toFloat(), valueRange = 0f..(count - 1).toFloat(),
                    onValueChange = { v -> scope.launch { listState.scrollToItem(v.roundToInt()) } },
                    modifier = Modifier.weight(1f),
                ) else Box(Modifier.weight(1f))
                IconButton(enabled = current < count - 1, onClick = { scope.launch { listState.animateScrollToItem(current + 1) } }) {
                    Icon(Icons.Outlined.KeyboardArrowDown, stringResource(R.string.courses_pdfNext))
                }
                IconButton(enabled = zoom > MIN_ZOOM, onClick = { zoom = (zoom / 1.25f).coerceAtLeast(MIN_ZOOM) }) {
                    Icon(Icons.Outlined.ZoomOut, stringResource(R.string.courses_pdfZoomOut))
                }
                IconButton(enabled = zoom != 1f, onClick = { zoom = 1f }) { Icon(Icons.Outlined.ZoomOutMap, stringResource(R.string.courses_pdfFit)) }
                IconButton(enabled = zoom < MAX_ZOOM, onClick = { zoom = (zoom * 1.25f).coerceAtMost(MAX_ZOOM) }) {
                    Icon(Icons.Outlined.ZoomIn, stringResource(R.string.courses_pdfZoomIn))
                }
            }
        },
    ) { pad ->
        when (val s = state) {
            PdfViewerViewModel.State.Loading -> LoadingState(Modifier.padding(pad), stringResource(R.string.courses_pdfLoading))
            PdfViewerViewModel.State.Missing, PdfViewerViewModel.State.Broken ->
                EmptyState(Icons.Outlined.PictureAsPdf, stringResource(R.string.courses_pdfError), Modifier.padding(pad))
            PdfViewerViewModel.State.Protected ->
                EmptyState(Icons.Outlined.PictureAsPdf, stringResource(R.string.pdf_password), Modifier.padding(pad))
            is PdfViewerViewModel.State.Ready -> PdfPages(
                session = s.session, listState = listState, zoom = zoom, onZoom = { zoom = it },
                contentPadding = pad,
                onTap = { if (fullscreen) chromeInFullscreen = !chromeInFullscreen },
            )
        }
    }

    if (goTo && ready != null) TextInputDialog(
        title = stringResource(R.string.pdf_go_to_page), label = stringResource(R.string.courses_pdfPage),
        initial = (current + 1).toString(), keyboardType = KeyboardType.Number, maxLength = 6,
        confirmLabel = stringResource(R.string.courses_open),
        onConfirm = { v ->
            goTo = false
            v.trim().toIntOrNull()?.let { n -> scope.launch { listState.scrollToItem((n - 1).coerceIn(0, ready.session.pageCount - 1)) } }
        },
        onDismiss = { goTo = false },
    )
}

/**
 * Continuous vertical document. Zoom widens the column (pages re-rasterise at a coarser
 * resolution bucket), with horizontal scrolling for the overflow; pinch keeps the focal
 * point under the fingers and double-tap toggles 1× / 2.5×.
 */
@Composable
private fun PdfPages(
    session: PdfSession,
    listState: androidx.compose.foundation.lazy.LazyListState,
    zoom: Float,
    onZoom: (Float) -> Unit,
    contentPadding: PaddingValues,
    onTap: () -> Unit,
) {
    // Pages are documents: keep their geometry LTR even in the Arabic UI so zoom maths hold.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(contentPadding).clipToBounds()) {
            val density = LocalDensity.current
            val viewportW = constraints.maxWidth.toFloat()
            val hScroll = rememberScrollState()
            val scope = rememberCoroutineScope()
            val zoomNow by rememberUpdatedState(zoom)
            val pageGap = with(density) { 8.dp.toPx() }

            fun applyZoom(target: Float, cx: Float, cy: Float) {
                val old = zoomNow
                val new = target.coerceIn(MIN_ZOOM, MAX_ZOOM)
                if (new == old) return
                val f = new / old
                val first = listState.firstVisibleItemIndex
                val offset = listState.firstVisibleItemScrollOffset
                val newOffset = ((offset + cy) * f - cy - pageGap * (f - 1)).coerceAtLeast(0f)
                val newH = ((hScroll.value + cx) * f - cx).coerceAtLeast(0f)
                onZoom(new)
                scope.launch {
                    listState.scrollToItem(first, newOffset.roundToInt())
                    withFrameNanos { }
                    hScroll.scrollTo(newH.roundToInt())
                }
            }

            // Render resolution follows zoom in coarse steps so pinching doesn't thrash.
            val bucket = when {
                zoom < 1.4f -> 1f
                zoom < 2.2f -> 2f
                zoom < 3.5f -> 3f
                else -> 4f
            }
            val renderW = (viewportW * bucket).roundToInt().coerceAtMost(maxOf(PdfSession.MAX_WIDTH, viewportW.roundToInt()))
            val pageWpx = viewportW * zoom

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxHeight()
                    .horizontalScroll(hScroll, enabled = zoom > MIN_ZOOM)
                    .width(with(density) { pageWpx.toDp() })
                    .pointerInput(viewportW) {
                        // Two-finger pinch (Initial pass, so the list doesn't also scroll).
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.changes.count { it.pressed } >= 2) {
                                    val z = event.calculateZoom()
                                    val c = event.calculateCentroid(useCurrent = true)
                                    if (z != 1f) applyZoom(zoomNow * z, c.x - hScroll.value, c.y)
                                    event.changes.forEach { it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    .pointerInput(viewportW) {
                        detectTapGestures(
                            onTap = { onTap() },
                            onDoubleTap = { p -> applyZoom(if (zoomNow > 1.05f) 1f else 2.5f, p.x - hScroll.value, p.y) },
                        )
                    },
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(session.pageCount, key = { it }) { i ->
                    PdfPage(session, i, pageWpx, renderW)
                }
            }
        }
    }
}

@Composable
private fun PdfPage(session: PdfSession, index: Int, widthPx: Float, renderW: Int) {
    val density = LocalDensity.current
    var size by remember(index) { mutableStateOf(session.cachedSize(index) ?: session.cachedSize(0) ?: PdfSession.DEFAULT_SIZE) }
    var bitmap by remember(index) { mutableStateOf<Bitmap?>(session.cached(index, renderW)) }
    LaunchedEffect(index, renderW) {
        session.render(index, renderW)?.let { bitmap = it }
        session.cachedSize(index)?.let { size = it }
    }
    val heightPx = widthPx * size.height / size.width.coerceAtLeast(1)
    val desc = stringResource(R.string.pdf_page_desc, index + 1, session.pageCount)
    Box(
        Modifier.size(with(density) { widthPx.toDp() }, with(density) { heightPx.toDp() }).background(MediaColors.paper),
        contentAlignment = Alignment.Center,
    ) {
        val b = bitmap
        if (b != null) Image(b.asImageBitmap(), desc, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        else CircularProgressIndicator(Modifier.size(28.dp), color = MediaColors.pageSpinner, strokeWidth = 2.dp)
    }
}

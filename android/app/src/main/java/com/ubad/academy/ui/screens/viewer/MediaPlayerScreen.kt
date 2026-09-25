package com.ubad.academy.ui.screens.viewer

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.toRoute
import com.ubad.academy.R
import com.ubad.academy.data.local.files.FileStore
import com.ubad.academy.ui.components.EmptyState
import com.ubad.academy.ui.components.ImmersiveMode
import com.ubad.academy.ui.navigation.Route
import com.ubad.academy.ui.theme.MediaColors
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Local audio/video via Media3. The player lives in the ViewModel so rotation doesn't
 * restart playback; it pauses when the app goes to the background (no background
 * service — same as the web page) and is released when the screen is closed.
 */
@HiltViewModel
class MediaPlayerViewModel @Inject constructor(
    handle: SavedStateHandle,
    files: FileStore,
    @ApplicationContext context: Context,
) : ViewModel() {
    private val route = handle.toRoute<Route.MediaPlayer>()
    val title = route.title
    val isVideo = route.video
    private val file = files.courseFile(route.assetId)

    private val _error = MutableStateFlow(!file.exists())
    val error = _error.asStateFlow()

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(if (isVideo) C.AUDIO_CONTENT_TYPE_MOVIE else C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build().apply {
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) { _error.value = true }
            })
            if (file.exists()) {
                setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file)))
                prepare()
                playWhenReady = true
            }
        }

    override fun onCleared() = player.release()
}

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaPlayerScreen(onBack: () -> Unit, viewModel: MediaPlayerViewModel = hiltViewModel()) {
    val error by viewModel.error.collectAsStateWithLifecycle()
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    ImmersiveMode(fullscreen)

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) viewModel.player.pause() }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    Scaffold(
        containerColor = if (viewModel.isVideo) MediaColors.backdrop else MaterialTheme.colorScheme.background,
        topBar = {
            if (!fullscreen) TopAppBar(
                title = { Text(viewModel.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) } },
                colors = if (viewModel.isVideo) TopAppBarDefaults.topAppBarColors(
                    containerColor = MediaColors.backdrop, titleContentColor = MediaColors.onBackdrop,
                    navigationIconContentColor = MediaColors.onBackdrop,
                ) else TopAppBarDefaults.topAppBarColors(),
            )
        },
    ) { pad ->
        if (error) {
            EmptyState(if (viewModel.isVideo) Icons.Outlined.Movie else Icons.Outlined.Headphones, stringResource(R.string.media_error), Modifier.padding(pad))
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(pad), horizontalAlignment = Alignment.CenterHorizontally) {
            if (!viewModel.isVideo) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Headphones, null, Modifier.size(120.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = viewModel.player
                        keepScreenOn = true
                        setShowNextButton(false)
                        setShowPreviousButton(false)
                        if (viewModel.isVideo) {
                            setFullscreenButtonClickListener { fullscreen = it }
                        } else {
                            // Audio: keep the transport controls permanently visible.
                            controllerShowTimeoutMs = 0
                            controllerHideOnTouch = false
                            useArtwork = false
                            showController()
                        }
                    }
                },
                update = { it.player = viewModel.player },
                onRelease = { it.player = null },
                modifier = if (viewModel.isVideo) Modifier.fillMaxSize().background(MediaColors.backdrop)
                else Modifier.fillMaxWidth().height(160.dp),
            )
        }
    }
}

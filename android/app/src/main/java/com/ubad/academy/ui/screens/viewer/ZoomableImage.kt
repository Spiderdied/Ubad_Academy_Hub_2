package com.ubad.academy.ui.screens.viewer

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import coil3.compose.AsyncImage

/**
 * Pinch / double-tap zoom with panning. While not zoomed, single-finger drags are left
 * unconsumed so the surrounding pager can swipe between images.
 */
@Composable
fun ZoomableImage(model: Any, contentDescription: String?, onTap: () -> Unit, modifier: Modifier = Modifier) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }

    fun clamp(o: Offset, s: Float): Offset {
        val maxX = (box.width * (s - 1f)) / 2f
        val maxY = (box.height * (s - 1f)) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { box = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val multi = event.changes.count { it.pressed } >= 2
                        if (multi || scale > 1f) {
                            val z = event.calculateZoom()
                            val pan = event.calculatePan()
                            val newScale = (scale * z).coerceIn(1f, 5f)
                            offset = clamp(offset + pan, newScale)
                            scale = newScale
                            if (scale <= 1f) offset = Offset.Zero
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { p ->
                        if (scale > 1f) { scale = 1f; offset = Offset.Zero } else {
                            scale = 2.5f
                            val center = Offset(box.width / 2f, box.height / 2f)
                            offset = clamp((center - p) * (scale - 1f), scale)
                        }
                    },
                )
            }
            .graphicsLayer {
                scaleX = scale; scaleY = scale
                translationX = offset.x; translationY = offset.y
            },
    )
}

private fun androidx.compose.ui.input.pointer.PointerInputChange.positionChanged() = position != previousPosition

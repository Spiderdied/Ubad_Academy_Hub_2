package com.ubad.academy.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ubad.academy.ui.theme.UbadThemeExt
import java.io.File

/**
 * The ambient environment from the web (`.bg`, `.bg-glow-a/b`, `.bg-grid`, `.bg-user`):
 * two soft brand glows, a faint dot grid, and the optional per-theme user image.
 * Pure drawing — no recomposition while scrolling.
 */
@Composable
fun UbadBackground(
    userBackground: File?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = UbadThemeExt.colors
    val bg = MaterialTheme.colorScheme.background
    val dot = c.line.copy(alpha = c.line.alpha * 0.7f)
    Box(modifier.fillMaxSize().background(bg)) {
        if (userBackground != null) {
            AsyncImage(
                model = userBackground,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(if (c.isDark) 0.18f else 0.10f),
            )
        }
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val m = maxOf(w, h)
            drawCircle(
                Brush.radialGradient(listOf(c.glowA, Color.Transparent), center = Offset(w * 0.05f, -h * 0.05f), radius = m * 0.62f),
                radius = m * 0.62f, center = Offset(w * 0.05f, -h * 0.05f),
            )
            drawCircle(
                Brush.radialGradient(listOf(c.glowB, Color.Transparent), center = Offset(w * 0.95f, h * 1.05f), radius = m * 0.55f),
                radius = m * 0.55f, center = Offset(w * 0.95f, h * 1.05f),
            )
            val step = 34.dp.toPx()
            val r = 1.dp.toPx()
            val gridAlpha = if (userBackground != null) 0.55f else 1f
            var y = step / 2
            while (y < h * 0.75f) {
                var x = step / 2
                val fade = (1f - y / (h * 0.75f)).coerceIn(0f, 1f)
                while (x < w) {
                    drawCircle(dot.copy(alpha = dot.alpha * fade * gridAlpha), r, Offset(x, y))
                    x += step
                }
                y += step
            }
        }
        content()
    }
}


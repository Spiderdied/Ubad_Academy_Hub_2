package com.ubad.academy.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import com.ubad.academy.ui.theme.UbadThemeExt
import kotlinx.coroutines.flow.Flow
import kotlin.math.sin
import kotlin.random.Random

/** Native `FX.confetti()` — a short, non-interactive burst drawn above the UI. Respects "remove animations". */
@Composable
fun ConfettiOverlay(triggers: Flow<Unit>) {
    val context = LocalContext.current
    val reduced = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    var burst by remember { mutableIntStateOf(0) }
    LaunchedEffect(triggers) { triggers.collect { if (!reduced) burst++ } }
    if (burst == 0) return
    val colors = UbadThemeExt.colors
    val palette = listOf(colors.accCyan, colors.accBlue, colors.accViolet, colors.ok, colors.danger)
    val progress = remember(burst) { Animatable(0f) }
    val pieces = remember(burst) {
        val r = Random(burst)
        List(70) { Piece(r.nextFloat(), -r.nextFloat() * 0.3f, 0.6f + r.nextFloat() * 0.8f, r.nextFloat() * 360f, r.nextFloat() * 6f, r.nextInt(5)) }
    }
    LaunchedEffect(burst) { progress.animateTo(1f, tween(1800, easing = LinearEasing)) }
    if (progress.value >= 1f) return
    Canvas(Modifier.fillMaxSize()) {
        val p = progress.value
        pieces.forEach { pc ->
            val y = (pc.y0 + p * pc.speed * 1.3f) * size.height
            val x = (pc.x + sin((p * 6 + pc.wobble).toDouble()).toFloat() * 0.03f) * size.width
            rotate(pc.rot + p * 540f, Offset(x, y)) {
                drawRect(palette[pc.color].copy(alpha = 1f - p * 0.6f), Offset(x - 5f, y - 8f), Size(10f, 16f))
            }
        }
    }
}

private data class Piece(val x: Float, val y0: Float, val speed: Float, val rot: Float, val wobble: Float, val color: Int)

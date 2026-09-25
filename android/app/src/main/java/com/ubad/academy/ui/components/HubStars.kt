package com.ubad.academy.ui.components

import android.app.ActivityManager
import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ubad.academy.ui.theme.UbadThemeExt
import kotlin.math.sin
import kotlin.random.Random

private class Star(var x: Float, var y: Float, val vy: Float, val r: Float, val ph: Float, val sp: Float, val c: Int)

/**
 * Native port of the hub's `FX.starsStart()`: 26 slowly rising, twinkling dots.
 * Skipped with "remove animations" on or on low-RAM devices (the web checks reduced motion / weak hardware).
 * Frames stop automatically while the app is in the background.
 */
@Composable
fun HubStars(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val enabled = remember {
        val reduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        val weak = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice
        !reduced && !weak
    }
    if (!enabled) return
    val colors = UbadThemeExt.colors
    val palette = listOf(colors.accCyan, colors.accBlue, colors.accViolet)
    val amplitude = if (colors.isDark) 0.8f else 0.32f
    val stars = remember {
        List(26) {
            Star(Random.nextFloat(), Random.nextFloat(), -(0.05f + Random.nextFloat() * 0.11f),
                0.8f + Random.nextFloat() * 1.4f, Random.nextFloat() * 6.28f, 0.4f + Random.nextFloat() * 0.8f, Random.nextInt(3))
        }
    }
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) withFrameNanos { now ->
            // The web advances 0.016 per animation frame; normalise to 60 fps.
            val frames = if (last == 0L) 1f else ((now - last) / 16_666_667f).coerceIn(0f, 4f)
            last = now
            t += 0.016f * frames
        }
    }
    Canvas(modifier) {
        val px = 1.dp.toPx()
        val h = size.height
        val w = size.width
        val time = t
        stars.forEach { s ->
            // Positions are fractions of the canvas; velocities are CSS px per frame at 60 fps.
            // Rising past the top (-4px) re-enters at the bottom (h+4px), like the web loop.
            val span = h + 8 * px
            val raw = s.y * h + s.vy * px * (time / 0.016f) + 4 * px
            val y = ((raw % span) + span) % span - 4 * px
            val a = amplitude * (0.35f + 0.65f * (sin(time * s.sp + s.ph) * 0.5f + 0.5f))
            drawCircle(palette[s.c].copy(alpha = a), s.r * px, Offset(s.x * w, y))
        }
    }
}

package com.ubad.academy.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ubad.academy.domain.model.ThemeId

private val LocalUbadColors = staticCompositionLocalOf { themeSpec(ThemeId.DARK).extra }

/** Radii from style.css: --r-lg 22px, --r-md 16px, chips/buttons ~12px. */
val UbadShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val base = Typography()

/** System sans (Noto Arabic on Android) like the web's system font stack; all sizes in sp. */
val UbadTypography = base.copy(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

/** Tabular figures for counters/timers/chips (`.mono` in the web CSS). */
val MonoStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)

@Composable
fun UbadTheme(theme: ThemeId = ThemeId.DARK, content: @Composable () -> Unit) {
    val spec = themeSpec(theme)
    CompositionLocalProvider(LocalUbadColors provides spec.extra) {
        MaterialTheme(
            colorScheme = spec.scheme,
            typography = UbadTypography,
            shapes = UbadShapes,
            content = content,
        )
    }
}

object UbadThemeExt {
    val colors: UbadColors
        @Composable @ReadOnlyComposable get() = LocalUbadColors.current
}

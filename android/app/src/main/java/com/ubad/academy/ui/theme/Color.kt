package com.ubad.academy.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.ubad.academy.domain.model.ThemeId

/**
 * Brand colours not covered by Material roles. Values come from the CSS custom
 * properties in style.css (`--acc-c`, `--acc-b`, `--acc-v`, `--ok`, glows…).
 */
@Immutable
data class UbadColors(
    val accCyan: Color,
    val accBlue: Color,
    val accViolet: Color,
    val ok: Color,
    val danger: Color,
    val glowA: Color,
    val glowB: Color,
    val line: Color,
    val isDark: Boolean,
) {
    val brand: Brush get() = Brush.linearGradient(listOf(accCyan, accBlue, accViolet))
    /** Course / hub-card accent rotation (`courseAccent` in app.js). */
    fun accent(index: Int): Color = when (Math.floorMod(index, 3)) { 0 -> accCyan; 1 -> accBlue; else -> accViolet }
}

internal data class ThemeSpec(val scheme: ColorScheme, val extra: UbadColors)

internal fun themeSpec(id: ThemeId): ThemeSpec = when (id) {
    ThemeId.DARK -> ThemeSpec(
        darkColorScheme(
            primary = Color(0xFF8B5CF6), onPrimary = Color.White,
            primaryContainer = Color(0xFF2A2152), onPrimaryContainer = Color(0xFFD9CBFF),
            secondary = Color(0xFF3B82F6), onSecondary = Color.White,
            secondaryContainer = Color(0xFF1B2A55), onSecondaryContainer = Color(0xFFCFE0FF),
            tertiary = Color(0xFF22D3EE), onTertiary = Color(0xFF00252E),
            tertiaryContainer = Color(0xFF0E3440), onTertiaryContainer = Color(0xFFB6F2FF),
            background = Color(0xFF050816), onBackground = Color(0xFFE6E9FF),
            surface = Color(0xFF070B1D), onSurface = Color(0xFFE6E9FF),
            surfaceVariant = Color(0xFF141C3A), onSurfaceVariant = Color(0xFFA9B2D6),
            surfaceContainerLowest = Color(0xFF04060F), surfaceContainerLow = Color(0xFF0A1024),
            surfaceContainer = Color(0xFF0E152E), surfaceContainerHigh = Color(0xFF131B38),
            surfaceContainerHighest = Color(0xFF192344), surfaceBright = Color(0xFF1E2A50),
            outline = Color(0xFF7C86A6), outlineVariant = Color(0xFF232C4F),
            error = Color(0xFFF87171), onError = Color(0xFF3B0A0A),
            errorContainer = Color(0xFF3A1620), onErrorContainer = Color(0xFFFCA5A5),
            inverseSurface = Color(0xFFE6E9FF), inverseOnSurface = Color(0xFF0A1024),
            scrim = Color(0xFF02040C),
        ),
        UbadColors(Color(0xFF22D3EE), Color(0xFF3B82F6), Color(0xFF8B5CF6), Color(0xFF34D399), Color(0xFFF87171),
            Color(0x298B5CF6), Color(0x1C22D3EE), Color(0x248CA0FF), isDark = true),
    )
    ThemeId.OLED -> ThemeSpec(
        darkColorScheme(
            primary = Color(0xFF8B5CF6), onPrimary = Color.White,
            primaryContainer = Color(0xFF221A45), onPrimaryContainer = Color(0xFFD9CBFF),
            secondary = Color(0xFF3B82F6), onSecondary = Color.White,
            secondaryContainer = Color(0xFF14204A), onSecondaryContainer = Color(0xFFCFE0FF),
            tertiary = Color(0xFF22D3EE), onTertiary = Color(0xFF00252E),
            tertiaryContainer = Color(0xFF0A2A33), onTertiaryContainer = Color(0xFFB6F2FF),
            background = Color(0xFF000000), onBackground = Color(0xFFE6E9FF),
            surface = Color(0xFF000000), onSurface = Color(0xFFE6E9FF),
            surfaceVariant = Color(0xFF0D1020), onSurfaceVariant = Color(0xFFA9B2D6),
            surfaceContainerLowest = Color(0xFF000000), surfaceContainerLow = Color(0xFF06070E),
            surfaceContainer = Color(0xFF0A0C16), surfaceContainerHigh = Color(0xFF0E111F),
            surfaceContainerHighest = Color(0xFF141828), surfaceBright = Color(0xFF1A1F33),
            outline = Color(0xFF7C86A6), outlineVariant = Color(0xFF1C2240),
            error = Color(0xFFF87171), onError = Color(0xFF3B0A0A),
            errorContainer = Color(0xFF2E0F16), onErrorContainer = Color(0xFFFCA5A5),
            inverseSurface = Color(0xFFE6E9FF), inverseOnSurface = Color(0xFF000000),
            scrim = Color(0xFF000000),
        ),
        UbadColors(Color(0xFF22D3EE), Color(0xFF3B82F6), Color(0xFF8B5CF6), Color(0xFF34D399), Color(0xFFF87171),
            Color(0x1F8B5CF6), Color(0x1422D3EE), Color(0x298CA0FF), isDark = true),
    )
    ThemeId.LIGHT -> ThemeSpec(
        lightColorScheme(
            primary = Color(0xFF7C3AED), onPrimary = Color.White,
            primaryContainer = Color(0xFFEDE4FD), onPrimaryContainer = Color(0xFF6D28D9),
            secondary = Color(0xFF2563EB), onSecondary = Color.White,
            secondaryContainer = Color(0xFFDDE7FD), onSecondaryContainer = Color(0xFF1E40AF),
            tertiary = Color(0xFF0891B2), onTertiary = Color.White,
            tertiaryContainer = Color(0xFFD3F1F8), onTertiaryContainer = Color(0xFF0E5567),
            background = Color(0xFFEDF1FB), onBackground = Color(0xFF141A33),
            surface = Color(0xFFF7F9FF), onSurface = Color(0xFF141A33),
            surfaceVariant = Color(0xFFE3E9F8), onSurfaceVariant = Color(0xFF414B72),
            surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF7F9FF),
            surfaceContainer = Color(0xFFF1F4FD), surfaceContainerHigh = Color(0xFFEAEFFB),
            surfaceContainerHighest = Color(0xFFE3E9F8), surfaceBright = Color(0xFFFFFFFF),
            outline = Color(0xFF6C7594), outlineVariant = Color(0xFFD2D9EC),
            error = Color(0xFFDC2626), onError = Color.White,
            errorContainer = Color(0xFFFDE3E3), onErrorContainer = Color(0xFF991B1B),
            inverseSurface = Color(0xFF141A33), inverseOnSurface = Color(0xFFEDF1FB),
        ),
        UbadColors(Color(0xFF0891B2), Color(0xFF2563EB), Color(0xFF7C3AED), Color(0xFF059669), Color(0xFFDC2626),
            Color(0x147C3AED), Color(0x120891B2), Color(0x26263678), isDark = false),
    )
    ThemeId.PAPER -> ThemeSpec(
        lightColorScheme(
            primary = Color(0xFFB45309), onPrimary = Color.White,
            primaryContainer = Color(0xFFF3DFC6), onPrimaryContainer = Color(0xFF8A4406),
            secondary = Color(0xFF0F766E), onSecondary = Color.White,
            secondaryContainer = Color(0xFFD5ECE6), onSecondaryContainer = Color(0xFF0B4F4A),
            tertiary = Color(0xFF9F1239), onTertiary = Color.White,
            tertiaryContainer = Color(0xFFF6DADF), onTertiaryContainer = Color(0xFF6E0D28),
            background = Color(0xFFF3EBDD), onBackground = Color(0xFF2C2317),
            surface = Color(0xFFFBF6EA), onSurface = Color(0xFF2C2317),
            surfaceVariant = Color(0xFFEDE2CF), onSurfaceVariant = Color(0xFF5C4F3A),
            surfaceContainerLowest = Color(0xFFFFFCF5), surfaceContainerLow = Color(0xFFFBF6EA),
            surfaceContainer = Color(0xFFF7F0E2), surfaceContainerHigh = Color(0xFFF2E9D8),
            surfaceContainerHighest = Color(0xFFEDE2CF), surfaceBright = Color(0xFFFFFCF5),
            outline = Color(0xFF8C7E67), outlineVariant = Color(0xFFDDCFB8),
            error = Color(0xFFB91C1C), onError = Color.White,
            errorContainer = Color(0xFFF8DCD5), onErrorContainer = Color(0xFF7F1D1D),
            inverseSurface = Color(0xFF2C2317), inverseOnSurface = Color(0xFFF3EBDD),
        ),
        UbadColors(Color(0xFF0F766E), Color(0xFFB45309), Color(0xFF9F1239), Color(0xFF15803D), Color(0xFFB91C1C),
            Color(0x1AB45309), Color(0x170F766E), Color(0x337A5C32), isDark = false),
    )
    ThemeId.SAGE -> ThemeSpec(
        lightColorScheme(
            primary = Color(0xFF047857), onPrimary = Color.White,
            primaryContainer = Color(0xFFD4EBDD), onPrimaryContainer = Color(0xFF046C50),
            secondary = Color(0xFF0D9488), onSecondary = Color.White,
            secondaryContainer = Color(0xFFD2EEEA), onSecondaryContainer = Color(0xFF0A5B54),
            tertiary = Color(0xFF4338CA), onTertiary = Color.White,
            tertiaryContainer = Color(0xFFE0DEF8), onTertiaryContainer = Color(0xFF2E2690),
            background = Color(0xFFECF3ED), onBackground = Color(0xFF1C2B22),
            surface = Color(0xFFF7FBF7), onSurface = Color(0xFF1C2B22),
            surfaceVariant = Color(0xFFE2EDE5), onSurfaceVariant = Color(0xFF43604F),
            surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF7FBF7),
            surfaceContainer = Color(0xFFF0F7F1), surfaceContainerHigh = Color(0xFFE9F2EB),
            surfaceContainerHighest = Color(0xFFE2EDE5), surfaceBright = Color(0xFFFFFFFF),
            outline = Color(0xFF6F8679), outlineVariant = Color(0xFFCFE0D4),
            error = Color(0xFFB91C1C), onError = Color.White,
            errorContainer = Color(0xFFF8DCDC), onErrorContainer = Color(0xFF7F1D1D),
            inverseSurface = Color(0xFF1C2B22), inverseOnSurface = Color(0xFFECF3ED),
        ),
        UbadColors(Color(0xFF0D9488), Color(0xFF047857), Color(0xFF4338CA), Color(0xFF15803D), Color(0xFFB91C1C),
            Color(0x144338CA), Color(0x1A0D9488), Color(0x2E1E5A3C), isDark = false),
    )
    ThemeId.ROSE -> ThemeSpec(
        lightColorScheme(
            primary = Color(0xFFBE185D), onPrimary = Color.White,
            primaryContainer = Color(0xFFF7D6E3), onPrimaryContainer = Color(0xFFA31553),
            secondary = Color(0xFF9333EA), onSecondary = Color.White,
            secondaryContainer = Color(0xFFEDDCFB), onSecondaryContainer = Color(0xFF6B21A8),
            tertiary = Color(0xFFDB2777), onTertiary = Color.White,
            tertiaryContainer = Color(0xFFFBDDEB), onTertiaryContainer = Color(0xFF9D174D),
            background = Color(0xFFF8EDF1), onBackground = Color(0xFF33202A),
            surface = Color(0xFFFDF5F8), onSurface = Color(0xFF33202A),
            surfaceVariant = Color(0xFFF2E4EA), onSurfaceVariant = Color(0xFF6C4A59),
            surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFDF5F8),
            surfaceContainer = Color(0xFFFAEFF3), surfaceContainerHigh = Color(0xFFF6E9EE),
            surfaceContainerHighest = Color(0xFFF2E4EA), surfaceBright = Color(0xFFFFFFFF),
            outline = Color(0xFF9A7C89), outlineVariant = Color(0xFFEBD3DD),
            error = Color(0xFFB91C1C), onError = Color.White,
            errorContainer = Color(0xFFF8DCDC), onErrorContainer = Color(0xFF7F1D1D),
            inverseSurface = Color(0xFF33202A), inverseOnSurface = Color(0xFFF8EDF1),
        ),
        UbadColors(Color(0xFFDB2777), Color(0xFF9333EA), Color(0xFFBE185D), Color(0xFF15803D), Color(0xFFB91C1C),
            Color(0x149333EA), Color(0x17DB2777), Color(0x2E964664), isDark = false),
    )
}

/** Swatch shown in theme pickers (`themeDots` in app.js onboarding). */
fun ThemeId.swatch(): Color = when (this) {
    ThemeId.DARK -> Color(0xFF0A1024)
    ThemeId.OLED -> Color(0xFF000000)
    ThemeId.LIGHT -> Color(0xFFF7F9FF)
    ThemeId.PAPER -> Color(0xFFFBF6EC)
    ThemeId.SAGE -> Color(0xFFF7FBF7)
    ThemeId.ROSE -> Color(0xFFFDF5F8)
}

/** Fixed colours for media surfaces (photo/video backdrops, PDF paper) — same in every theme. */
object MediaColors {
    val backdrop = Color(0xFF000000)
    val onBackdrop = Color(0xFFFFFFFF)
    val scrim = Color(0x8C000000)
    val paper = Color(0xFFFFFFFF)
    val pageSpinner = Color(0xFF9E9E9E)
}

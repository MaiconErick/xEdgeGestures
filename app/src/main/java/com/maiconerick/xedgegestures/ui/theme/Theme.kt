package com.maiconerick.xedgegestures.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Neon accents ported from xPlayTimeTracker's palette. */
enum class NeonAccent(val color: Color, val dim: Color, val label: String) {
    GREEN(Color(0xFF39FF14), Color(0xFF1C7A0A), "Neon Green"),
    CYAN(Color(0xFF00E5FF), Color(0xFF0088A3), "Cyan"),
    MAGENTA(Color(0xFFFF2CD6), Color(0xFF9C1183), "Magenta"),
    ORANGE(Color(0xFFFF6A00), Color(0xFF9E4200), "Amber"),
    VIOLET(Color(0xFF9D5CFF), Color(0xFF5B2F9E), "Violet"),
    RED(Color(0xFFFF1744), Color(0xFF9E0F2A), "Crimson");

    companion object {
        fun from(name: String?): NeonAccent =
            entries.firstOrNull { it.name == name } ?: CYAN
    }
}

private val Black = Color(0xFF000000)
private val Surface1 = Color(0xFF0B0B0D)
private val Surface2 = Color(0xFF141418)
private val OnSurface = Color(0xFFEDEDED)
private val OnSurfaceDim = Color(0xFF9A9AA2)

val LocalNeon = staticCompositionLocalOf { NeonAccent.CYAN }

@Composable
fun XEdgeGesturesTheme(
    accent: NeonAccent = NeonAccent.CYAN,
    content: @Composable () -> Unit,
) {
    val scheme = darkColorScheme(
        primary = accent.color,
        onPrimary = Black,
        primaryContainer = accent.dim,
        onPrimaryContainer = Color.White,
        secondary = accent.color,
        onSecondary = Black,
        tertiary = accent.color,
        background = Black,
        onBackground = OnSurface,
        surface = Black,
        onSurface = OnSurface,
        surfaceVariant = Surface2,
        onSurfaceVariant = OnSurfaceDim,
        surfaceContainer = Surface1,
        surfaceContainerHigh = Surface2,
        surfaceContainerHighest = Surface2,
        outline = Color(0xFF2A2A30),
        outlineVariant = Color(0xFF1C1C22),
        error = Color(0xFFFF5252),
    )
    CompositionLocalProvider(LocalNeon provides accent) {
        MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
    }
}

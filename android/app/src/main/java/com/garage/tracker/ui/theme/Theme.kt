package com.garage.tracker.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/** Every colour the screens use, in one place so light and dark stay in step. */
data class AppColors(
    val cream: Color, val surface: Color, val ink: Color, val inkSoft: Color, val primary: Color,
    val mintBg: Color, val mintFg: Color,
    val amberBg: Color, val amberFg: Color,
    val coralBg: Color, val coralFg: Color,
    val neutralBg: Color, val skyBg: Color, val skyFg: Color,
)

val LightColors = AppColors(
    cream = Color(0xFFF8F9FE), surface = Color(0xFFFFFFFF), ink = Color(0xFF1F2430), inkSoft = Color(0xFF656C79),
    primary = Color(0xFF5B7CFA),
    mintBg = Color(0xFFDDF5E7), mintFg = Color(0xFF1A7B48),
    amberBg = Color(0xFFFFF0CC), amberFg = Color(0xFF946219),
    coralBg = Color(0xFFFFE0DC), coralFg = Color(0xFFAD423F),
    neutralBg = Color(0xFFEEF0FA), skyBg = Color(0xFFDCEEFF), skyFg = Color(0xFF2A6AAE),
)

val DarkColors = AppColors(
    cream = Color(0xFF12141C), surface = Color(0xFF1C1F2A), ink = Color(0xFFE8EAF2), inkSoft = Color(0xFF9AA0B4),
    primary = Color(0xFF8EA6FF),
    mintBg = Color(0xFF1E3A2D), mintFg = Color(0xFF6FD39B),
    amberBg = Color(0xFF3F3220), amberFg = Color(0xFFF2B65A),
    coralBg = Color(0xFF402423), coralFg = Color(0xFFFF8A80),
    neutralBg = Color(0xFF2A2E3B), skyBg = Color(0xFF1E3347), skyFg = Color(0xFF7CC4FF),
)

val LocalAppColors = staticCompositionLocalOf { LightColors }

// Short names used across the screens; they resolve to the current theme.
val Cream: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.cream
val Surface: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.surface
val Ink: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.ink
val InkSoft: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.inkSoft
val Primary: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.primary
val MintBg: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.mintBg
val MintFg: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.mintFg
val AmberBg: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.amberBg
val AmberFg: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.amberFg
val CoralBg: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.coralBg
val CoralFg: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.coralFg
val NeutralBg: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.neutralBg
val SkyBg: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.skyBg
val SkyFg: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.skyFg

val ChartColors = listOf(Color(0xFF7C9CFF), Color(0xFFFFB38A), Color(0xFF8FD6B3), Color(0xFFC9A8F5), Color(0xFFF5A3C7), Color(0xFF9FB8C9))

/** The brand blue, for places that aren't composable. */
val BrandBlue = Color(0xFF5B7CFA)

private fun scheme(c: AppColors, dark: Boolean) =
    if (dark) darkColorScheme(
        primary = c.primary, onPrimary = Color(0xFF0B1020),
        background = c.cream, onBackground = c.ink,
        surface = c.surface, onSurface = c.ink,
        surfaceVariant = c.neutralBg, onSurfaceVariant = c.inkSoft,
        outline = Color(0xFF3A3F50),
        surfaceContainerLowest = c.cream, surfaceContainerLow = Color(0xFF181B25), surfaceContainer = c.surface,
        surfaceContainerHigh = Color(0xFF242836), surfaceContainerHighest = c.neutralBg,
        secondaryContainer = Color(0xFF2A3350), onSecondaryContainer = c.ink,
    ) else lightColorScheme(
        primary = c.primary, onPrimary = Color.White,
        background = c.cream, onBackground = c.ink,
        surface = c.surface, onSurface = c.ink,
        surfaceVariant = c.neutralBg, onSurfaceVariant = c.inkSoft,
        outline = Color(0xFFD5D9EA),
    )

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

/** [mode] is "system", "light" or "dark". */
@Composable
fun GarageTheme(mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val colors = if (dark) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.cream.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
        }
    }
    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(colorScheme = scheme(colors, dark), shapes = AppShapes, content = content)
    }
}

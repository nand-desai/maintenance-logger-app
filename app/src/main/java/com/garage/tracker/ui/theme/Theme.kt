package com.garage.tracker.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Cream = Color(0xFFF8F9FE)
val Surface = Color(0xFFFFFFFF)
val Ink = Color(0xFF1F2430)
val InkSoft = Color(0xFF6B7280)
val Primary = Color(0xFF5B7CFA)

// Soft pastel status colours (container / content)
val MintBg = Color(0xFFDDF5E7); val MintFg = Color(0xFF1E8E53)
val AmberBg = Color(0xFFFFF0CC); val AmberFg = Color(0xFFB7791F)
val CoralBg = Color(0xFFFFE0DC); val CoralFg = Color(0xFFD9534F)

val ChartColors = listOf(Color(0xFF7C9CFF), Color(0xFFFFB38A), Color(0xFF8FD6B3), Color(0xFFC9A8F5))

private val Light = lightColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    background = Cream,
    onBackground = Ink,
    surface = Surface,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEEF0FA),
    onSurfaceVariant = InkSoft,
    outline = Color(0xFFD5D9EA),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

/** Always light: the app intentionally has no dark theme. */
@Composable
fun GarageTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Light, shapes = AppShapes, content = content)
}

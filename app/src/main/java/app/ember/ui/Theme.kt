package app.ember.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object EmberColors {
    val Bg = Color(0xFF231312)
    val BgDeep = Color(0xFF1A0D0C)
    val Surface = Color(0xFF33201E)
    val Field = Color(0xFF3A2523)
    val Line = Color(0xFF45302D)
    val Outline = Color(0xFF5A3B37)
    val Red = Color(0xFFB23F3B)
    val RedSoft = Color(0xFFE58A84)
    val Text = Color(0xFFF3E9E6)
    val TextSoft = Color(0xFFE2D1CD)
    val Muted = Color(0xFFC9A9A4)
    val Dim = Color(0xFFB8A19D)
}

@Composable
fun EmberTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = EmberColors.Red,
        onPrimary = Color.White,
        secondary = EmberColors.RedSoft,
        onSecondary = EmberColors.Bg,
        background = EmberColors.Bg,
        onBackground = EmberColors.Text,
        surface = EmberColors.Bg,
        onSurface = EmberColors.Text,
        surfaceVariant = EmberColors.Field,
        onSurfaceVariant = EmberColors.Muted,
        surfaceContainer = EmberColors.Surface,
        surfaceContainerHigh = EmberColors.Surface,
        surfaceContainerHighest = EmberColors.Field,
        surfaceContainerLow = EmberColors.Surface,
        outline = EmberColors.Outline,
        outlineVariant = EmberColors.Line,
        error = Color(0xFFFF8A80),
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

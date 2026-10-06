package io.github.zbowling.lightdeck.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Amber = Color(0xFFFFC107)

// A dark theme reads best on a panel floating over passthrough or a virtual room.
private val colors = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF241A00),
    secondary = Color(0xFFD6C4A0),
    background = Color(0xFF121212),
    surface = Color(0xFF1C1B1A),
    surfaceVariant = Color(0xFF2D2A26),
)

@Composable
fun LightDeckTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}

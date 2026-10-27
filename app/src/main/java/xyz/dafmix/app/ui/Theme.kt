package xyz.dafmix.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Red = Color(0xFFE50914)
val Muted = Color(0xFF9AA0A6)
val Bg = Color(0xFF141414)
val Panel = Color(0xFF1F1F1F)
val Panel2 = Color(0xFF212125)
val Green = Color(0xFF46D369)

private val Dark = darkColorScheme(
    primary = Red,
    onPrimary = Color.White,
    background = Bg,
    onBackground = Color.White,
    surface = Panel,
    onSurface = Color.White,
    surfaceVariant = Panel2,
    onSurfaceVariant = Muted,
    secondary = Muted,
    outline = Color(0xFF3A3A40),
)

@Composable
fun DafmixTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Dark, content = content)
}

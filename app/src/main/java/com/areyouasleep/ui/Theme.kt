package com.areyouasleep.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF0B0F14)
val Surface1 = Color(0xFF141A21)
val Surface2 = Color(0xFF1E2732)
val Accent = Color(0xFF9FE870)
val AccentDim = Color(0xFF6EE7B7)
val TextPrimary = Color(0xFFE6EDF3)
val TextSecondary = Color(0xFF8B98A5)
val Danger = Color(0xFFFF7A72)
val WarnBg = Color(0xFF2A1E12)

private val Scheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF0B2409),
    secondary = AccentDim,
    onSecondary = Color(0xFF06251A),
    background = Bg,
    onBackground = TextPrimary,
    surface = Surface1,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextSecondary,
    error = Danger,
    onError = Color(0xFF2B0B09),
)

@Composable
fun SleepTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}

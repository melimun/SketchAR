package com.tracecam.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Cream = Color(0xFFF7F0DC)
val Sage = Color(0xFFB4C299)
val Orchid = Color(0xFFC274B5)

/** Fixed brand palette; intentionally ignores dynamic (wallpaper) colour and dark mode. */
private val SketchColorScheme = lightColorScheme(
    primary = Orchid,
    onPrimary = Color.Black,
    secondaryContainer = Sage,
    onSecondaryContainer = Color.Black,
    background = Cream,
    onBackground = Color.Black,
    surface = Cream,
    onSurface = Color.Black,
    onSurfaceVariant = Color.Black,
)

@Composable
fun SketchARTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SketchColorScheme, content = content)
}

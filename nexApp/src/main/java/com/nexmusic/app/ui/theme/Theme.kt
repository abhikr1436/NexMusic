package com.nexmusic.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Apple Music's system palette: one accent, grouped greys, hairline separators. */
data class NexPalette(
    val background: Color,
    val grouped: Color,
    val elevated: Color,
    val fill: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val separator: Color,
    val isDark: Boolean,
)

val Accent = Color(0xFFFA2D48)

/** iOS switch green. */
val IosGreen = Color(0xFF34C759)

private val Dark =
    NexPalette(
        background = Color.Black,
        grouped = Color(0xFF1C1C1E),
        elevated = Color(0xFF2C2C2E),
        fill = Color(0x3D787880),
        label = Color.White,
        secondaryLabel = Color(0x99EBEBF5),
        tertiaryLabel = Color(0x4DEBEBF5),
        separator = Color(0xFF38383A),
        isDark = true,
    )

private val Light =
    NexPalette(
        background = Color.White,
        grouped = Color(0xFFF2F2F7),
        elevated = Color.White,
        fill = Color(0x1F787880),
        label = Color.Black,
        secondaryLabel = Color(0x993C3C43),
        tertiaryLabel = Color(0x4D3C3C43),
        separator = Color(0xFFC6C6C8),
        isDark = false,
    )

val LocalNex = staticCompositionLocalOf { Dark }

/** iOS text styles, in sp. Colour comes from LocalContentColor unless a call site sets one. */
object NexType {
    val LargeTitle = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, lineHeight = 41.sp, letterSpacing = 0.2.sp)
    val Title1 = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp)
    val Title2 = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp)
    val Title3 = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 25.sp)
    val Headline = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp)
    val Body = TextStyle(fontSize = 17.sp, lineHeight = 22.sp)
    val Callout = TextStyle(fontSize = 16.sp, lineHeight = 21.sp)
    val Subhead = TextStyle(fontSize = 15.sp, lineHeight = 20.sp)
    val Footnote = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
    val Caption = TextStyle(fontSize = 12.sp, lineHeight = 16.sp)
    val Tab = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium)
}

@Composable
fun NexTheme(content: @Composable () -> Unit) {
    val palette = if (isSystemInDarkTheme()) Dark else Light
    val scheme =
        if (palette.isDark) {
            darkColorScheme(primary = Accent, background = palette.background, surface = palette.background, onBackground = palette.label, onSurface = palette.label)
        } else {
            lightColorScheme(primary = Accent, background = palette.background, surface = palette.background, onBackground = palette.label, onSurface = palette.label)
        }
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalNex provides palette, LocalContentColor provides palette.label, content = content)
    }
}

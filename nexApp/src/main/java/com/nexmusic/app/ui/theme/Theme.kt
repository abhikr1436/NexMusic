package com.nexmusic.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
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

/** The accent colours on offer in Appearance; the first is Apple Music's red and the default. */
val Accents: List<Pair<String, Color>> =
    listOf(
        "Red" to Color(0xFFFA2D48),
        "Pink" to Color(0xFFFF2D87),
        "Orange" to Color(0xFFFF9500),
        "Green" to Color(0xFF30C35A),
        "Teal" to Color(0xFF30B0C7),
        "Blue" to Color(0xFF0A84FF),
        "Indigo" to Color(0xFF5E5CE6),
        "Purple" to Color(0xFFBF5AF2),
    )

private val accentState = mutableStateOf(Accents.first().second)

/** The user's accent colour. Snapshot state, so every screen reading it recolours the moment it changes. */
val Accent: Color get() = accentState.value

fun setAccentColor(color: Color) {
    accentState.value = color
}

/** Background themes: a top-to-bottom gradient for each of dark and light; one colour is a plain fill. */
enum class Backdrop(
    val label: String,
    private val dark: List<Color>,
    private val light: List<Color>,
) {
    Classic("Classic", listOf(Color.Black), listOf(Color.White)),
    Glow("Accent Glow", emptyList(), emptyList()),
    Midnight("Midnight", listOf(Color(0xFF101A3D), Color(0xFF05070F)), listOf(Color(0xFFDDE4FF), Color(0xFFF7F8FF))),
    Sunset("Sunset", listOf(Color(0xFF45152A), Color(0xFF140812)), listOf(Color(0xFFFFDCCB), Color(0xFFFFF6F0))),
    Ocean("Ocean", listOf(Color(0xFF07304A), Color(0xFF03121C)), listOf(Color(0xFFD3EEFF), Color(0xFFF4FAFF))),
    Forest("Forest", listOf(Color(0xFF123222), Color(0xFF06120B)), listOf(Color(0xFFD9F2E2), Color(0xFFF5FBF7))),
    ;

    fun colors(
        dark: Boolean,
        accent: Color,
    ): List<Color> =
        when {
            // The accent itself, mixed into the top of the page.
            this == Glow -> (if (dark) Color.Black else Color.White).let { base -> listOf(lerp(base, accent, if (dark) 0.32f else 0.2f), base, base) }
            dark -> this.dark
            else -> light
        }

    fun brush(
        dark: Boolean,
        accent: Color,
    ): Brush = colors(dark, accent).let { if (it.size == 1) SolidColor(it[0]) else Brush.verticalGradient(it) }
}

/** The page background, for the few places that paint it (the app root, under the NavHost). */
val LocalBackdrop = staticCompositionLocalOf<Brush> { SolidColor(Color.Black) }

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
fun NexTheme(
    dark: Boolean = isSystemInDarkTheme(),
    backdrop: Backdrop = Backdrop.Classic,
    content: @Composable () -> Unit,
) {
    val accent = Accent
    // background is the top of the gradient: what the status-bar fade and solid fills match.
    val palette = (if (dark) Dark else Light).copy(background = backdrop.colors(dark, accent).first())
    val scheme =
        if (palette.isDark) {
            darkColorScheme(primary = accent, background = palette.background, surface = palette.background, onBackground = palette.label, onSurface = palette.label)
        } else {
            lightColorScheme(primary = accent, background = palette.background, surface = palette.background, onBackground = palette.label, onSurface = palette.label)
        }
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalNex provides palette,
            LocalContentColor provides palette.label,
            LocalBackdrop provides backdrop.brush(dark, accent),
            content = content,
        )
    }
}

package com.hoursgram.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

class HgPalette(
    val name: String,
    val dark: Boolean,
    val accent: Color,
    val background: Color,
    val surface: Color,
    val text: Color,
    val subtext: Color,
    val bubbleIn: Color,
    val bubbleOut: Color,
)

// The colors are the ones of the PC client's themes.
val Palettes = listOf(
    HgPalette(
        "HorusGram", true,
        accent = Color(0xFF8993D8), background = Color(0xFF2E303C), surface = Color(0xFF3C3E4B),
        text = Color(0xFFE6E7EC), subtext = Color(0xFFB1B2B7),
        bubbleIn = Color(0xFF3C3E4B), bubbleOut = Color(0xFF5A63A8),
    ),
    HgPalette(
        "macOS, тёмная", true,
        accent = Color(0xFF2EA6FF), background = Color(0xFF18222D), surface = Color(0xFF1E2C3B),
        text = Color(0xFFFFFFFF), subtext = Color(0xFFB1C3D5),
        bubbleIn = Color(0xFF1E2C3B), bubbleOut = Color(0xFF2B5278),
    ),
    HgPalette(
        "macOS, светлая", false,
        accent = Color(0xFF2481CC), background = Color(0xFFFFFFFF), surface = Color(0xFFF1F1F1),
        text = Color(0xFF000000), subtext = Color(0xFF999999),
        bubbleIn = Color(0xFFF1F1F1), bubbleOut = Color(0xFFEFFDDE),
    ),
)

val LocalPalette = androidx.compose.runtime.staticCompositionLocalOf { Palettes[0] }

@Composable
fun HgTheme(palette: HgPalette, content: @Composable () -> Unit) {
    val scheme = if (palette.dark) {
        darkColorScheme(
            primary = palette.accent, onPrimary = Color.White,
            background = palette.background, onBackground = palette.text,
            surface = palette.background, onSurface = palette.text,
            surfaceVariant = palette.surface, onSurfaceVariant = palette.subtext,
        )
    } else {
        lightColorScheme(
            primary = palette.accent, onPrimary = Color.White,
            background = palette.background, onBackground = palette.text,
            surface = palette.background, onSurface = palette.text,
            surfaceVariant = palette.surface, onSurfaceVariant = palette.subtext,
        )
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

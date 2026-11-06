package io.github.rin.passkey.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/** Brand blue, shared with the launcher icon. */
private val Blue = Color(0xFF1E6BF0)
private val BlueDark = Color(0xFF7AA7FF)

@Composable
fun PasskeyTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    // White cards on a cool grey page in light mode, raised greys on near-black in dark mode.
    val colors = if (darkTheme) darkColorScheme(
        primary = BlueDark,
        primaryVariant = BlueDark,
        surface = Color(0xFF0C0D10),
        background = Color(0xFF0C0D10),
        surfaceContainer = Color(0xFF1B1C21),
        tertiaryContainer = Color(0xFF1D2A43),
        onTertiaryContainer = BlueDark,
    ) else lightColorScheme(
        primary = Blue,
        primaryVariant = Blue,
        surface = Color(0xFFF4F5F8),
        background = Color(0xFFF4F5F8),
        surfaceContainer = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFE8F0FE),
        onTertiaryContainer = Blue,
    )
    MiuixTheme(colors = colors, content = content)
}

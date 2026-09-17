package io.github.rin.passkey.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

@Composable
fun PasskeyTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) darkColorScheme(
        primary = Color(0xFF80ADFF),
        primaryVariant = Color(0xFF80ADFF),
        surface = Color(0xFF101114),
        background = Color(0xFF101114),
        surfaceContainer = Color(0xFF1E2027),
        tertiaryContainer = Color(0xFF202F4A),
        onTertiaryContainer = Color(0xFF80ADFF),
    ) else lightColorScheme(
        primary = Color(0xFF0065E9),
        primaryVariant = Color(0xFF0065E9),
        surface = Color(0xFFF8F8FC),
        background = Color(0xFFF8F8FC),
        surfaceContainer = Color(0xFFF0F0F8),
        tertiaryContainer = Color(0xFFE1E9FC),
        onTertiaryContainer = Color(0xFF0065E9),
    )
    MiuixTheme(colors = colors, content = content)
}

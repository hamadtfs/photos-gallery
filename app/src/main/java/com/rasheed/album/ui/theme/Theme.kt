package com.rasheed.album.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.rasheed.album.data.ThemeMode

val Ink = Color(0xFF1C1C1E)
val Muted = Color(0xFF8E8E93)
val Hairline = Color(0xFFE6E6EA)
val Blue = Color(0xFF3D6BFF)
val Heart = Color(0xFFFF4D6A)
val Danger = Color(0xFFE23B4A)
val Placeholder = Color(0xFFEEEFF3)
val Banner = Color(0xFFF3F6FF)

data class AlbumColors(
    val ink: Color,
    val muted: Color,
    val hairline: Color,
    val background: Color,
    val banner: Color,
    val placeholder: Color,
)

private val LightColors = AlbumColors(
    ink = Ink,
    muted = Muted,
    hairline = Hairline,
    background = Color.White,
    banner = Banner,
    placeholder = Placeholder,
)

private val DarkColors = AlbumColors(
    ink = Color(0xFFF2F2F7),
    muted = Color(0xFF9A9AA1),
    hairline = Color(0xFF2C2C2E),
    background = Color(0xFF111113),
    banner = Color(0xFF1C2438),
    placeholder = Color(0xFF2A2A2E),
)

val LocalAlbumColors = staticCompositionLocalOf { LightColors }

private val LightScheme = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    background = Color.White,
    surface = Color.White,
    onBackground = Ink,
    onSurface = Ink,
)

private val DarkScheme = darkColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    background = DarkColors.background,
    surface = DarkColors.background,
    onBackground = DarkColors.ink,
    onSurface = DarkColors.ink,
)

@Composable
fun AlbumTheme(mode: ThemeMode = ThemeMode.System, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.System -> isSystemInDarkTheme()
    }
    val colors = if (dark) DarkColors else LightColors
    CompositionLocalProvider(LocalAlbumColors provides colors) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            content = content,
        )
    }
}

package com.jarves.mh.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val PocketOrange = Color(0xFF00E5FF) // Sana Electric Cyan accent
val PocketBlue = Color(0xFF8B5CF6)   // Sana Neon Violet
val PocketGreen = Color(0xFF00F59B)  // Sana Matrix Emerald
val PocketBackground = Color(0xFF07090E) // Deep Space Obsidian
val PocketSurface = Color(0xFF0E131F)    // Cyber Surface
val PocketSurfaceVariant = Color(0xFF161E30) // Elevated Glass Surface
val PocketOutline = Color(0xFF243048)    // Sleek Border

private val DarkColors = darkColorScheme(
    primary = PocketOrange,
    onPrimary = Color(0xFF002026),
    primaryContainer = Color(0xFF004D5A),
    onPrimaryContainer = Color(0xFFB8F5FF),
    secondary = PocketBlue,
    onSecondary = Color(0xFF250059),
    tertiary = PocketGreen,
    onTertiary = Color(0xFF003822),
    background = PocketBackground,
    onBackground = Color(0xFFEDF2F7),
    surface = PocketSurface,
    onSurface = Color(0xFFEDF2F7),
    surfaceVariant = PocketSurfaceVariant,
    onSurfaceVariant = Color(0xFFA0AEC0),
    outline = PocketOutline,
    outlineVariant = Color(0xFF2D3748),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFD85A20),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE0D2),
    onPrimaryContainer = Color(0xFF451A08),
    secondary = Color(0xFF3366CC),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF1B8A5A),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF6F8FA),
    onBackground = Color(0xFF1F2328),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F2328),
    surfaceVariant = Color(0xFFEAEFF5),
    onSurfaceVariant = Color(0xFF57606A),
    outline = Color(0xFFD0D7DE),
    outlineVariant = Color(0xFFD8DEE4),
)

enum class AppThemeMode { SYSTEM, DARK, LIGHT }

@Composable
fun PocketTheme(themeMode: AppThemeMode = AppThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val isDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !isDark
            insetsController.isAppearanceLightNavigationBars = !isDark
        }
    }

    MaterialTheme(
        colorScheme = if (isDark) DarkColors else LightColors,
        content = content,
    )
}

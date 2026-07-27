package com.example.pixelcolor.ui.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun PixelColorTheme(
    themeIndex: Int = 0,
    content: @Composable () -> Unit
) {
    val colors = AllThemes.getOrElse(themeIndex) { DarkTheme }

    val colorScheme = if (colors.isDark) {
        darkColorScheme(
            primary = colors.gold,
            onPrimary = colors.bg,
            primaryContainer = colors.accent,
            onPrimaryContainer = colors.white,
            secondary = colors.accent,
            onSecondary = colors.white,
            secondaryContainer = colors.surface,
            onSecondaryContainer = colors.onBg,
            tertiary = colors.gold,
            onTertiary = colors.bg,
            background = colors.bg,
            onBackground = colors.onBg,
            surface = colors.surface,
            onSurface = colors.onBg,
            surfaceVariant = colors.surfaceLight,
            onSurfaceVariant = colors.muted,
            outline = colors.accent,
            outlineVariant = colors.gridLine,
            error = colors.danger,
            onError = colors.white,
            errorContainer = colors.danger.copy(alpha = 0.2f),
            onErrorContainer = colors.danger
        )
    } else {
        lightColorScheme(
            primary = colors.gold,
            onPrimary = colors.white,
            primaryContainer = colors.accent,
            onPrimaryContainer = colors.white,
            secondary = colors.accent,
            onSecondary = colors.white,
            secondaryContainer = colors.surface,
            onSecondaryContainer = colors.onBg,
            tertiary = colors.gold,
            onTertiary = colors.white,
            background = colors.bg,
            onBackground = colors.onBg,
            surface = colors.surface,
            onSurface = colors.onBg,
            surfaceVariant = colors.surfaceLight,
            onSurfaceVariant = colors.muted,
            outline = colors.accent,
            outlineVariant = colors.gridLine,
            error = colors.danger,
            onError = colors.white,
            errorContainer = colors.danger.copy(alpha = 0.2f),
            onErrorContainer = colors.danger
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.bg.toArgb()
            window.navigationBarColor = colors.bg.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !colors.isDark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !colors.isDark
        }
    }

    CompositionLocalProvider(LocalAppTheme provides colors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PixelTypography,
            content = content
        )
    }
}

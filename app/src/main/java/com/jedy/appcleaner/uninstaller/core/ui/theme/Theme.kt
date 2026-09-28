package com.jedy.appcleaner.uninstaller.core.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.jedy.appcleaner.uninstaller.data.prefs.ThemeMode

private fun schemeFor(c: AppColors) = if (c.isDark) {
    darkColorScheme(
        primary = c.teal, onPrimary = c.onTeal,
        primaryContainer = c.tealSurface, onPrimaryContainer = c.teal,
        secondary = c.textPrimary, onSecondary = c.background,
        tertiary = c.premiumGold, onTertiary = c.background,
        background = c.background, onBackground = c.textPrimary,
        surface = c.background, onSurface = c.textPrimary,
        surfaceVariant = c.surface, onSurfaceVariant = c.textSecondary,
        surfaceContainerLowest = c.background, surfaceContainerLow = c.surface,
        surfaceContainer = c.surface, surfaceContainerHigh = c.surface, surfaceContainerHighest = c.surfaceMuted,
        outline = c.border, outlineVariant = c.border,
        error = c.removeRed, onError = c.onRemoveRed,
        errorContainer = c.removeRedSurface, onErrorContainer = c.removeRed,
    )
} else {
    lightColorScheme(
        primary = c.teal, onPrimary = c.onTeal,
        primaryContainer = c.tealSurface, onPrimaryContainer = c.teal,
        secondary = c.textPrimary, onSecondary = c.background,
        tertiary = c.premiumGold, onTertiary = c.textPrimary,
        background = c.background, onBackground = c.textPrimary,
        surface = c.background, onSurface = c.textPrimary,
        surfaceVariant = c.surface, onSurfaceVariant = c.textSecondary,
        surfaceContainerLowest = c.background, surfaceContainerLow = c.surface,
        surfaceContainer = c.surface, surfaceContainerHigh = c.background, surfaceContainerHighest = c.surfaceMuted,
        outline = c.border, outlineVariant = c.border,
        error = c.removeRed, onError = c.onRemoveRed,
        errorContainer = c.removeRedSurface, onErrorContainer = c.removeRed,
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(Dimens.controlRadius),
    medium = RoundedCornerShape(Dimens.cardRadius),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * @param themeMode PRD §1: follows the system theme unless overridden in Settings.
 * @param layoutDirection PRD §1: strictly LTR, RTL only for Arabic and Hebrew. Pinned explicitly
 * so a device in, say, Farsi does not mirror an English UI.
 */
@Composable
fun AppCleanerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (dark) DarkAppColors else LightAppColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(
        LocalLayoutDirection provides layoutDirection,
        LocalAppColors provides colors,
    ) {
        MaterialTheme(
            colorScheme = schemeFor(colors),
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

/** `AppTheme.colors.teal`, `AppTheme.colors.removeRed`, ... */
object AppTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable get() = LocalAppColors.current
}

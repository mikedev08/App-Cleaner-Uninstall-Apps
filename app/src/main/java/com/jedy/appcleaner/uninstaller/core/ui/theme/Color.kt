package com.jedy.appcleaner.uninstaller.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * PRD §1 palette. Teal is the brand; red means "this removes something" and nothing else; gold
 * marks premium and nothing else. Read these through [AppTheme.colors] so light and dark resolve.
 */
@Immutable
data class AppColors(
    val background: Color,
    val surface: Color,
    val surfaceMuted: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val teal: Color,
    val onTeal: Color,
    val tealSurface: Color,
    val removeRed: Color,
    val onRemoveRed: Color,
    val removeRedSurface: Color,
    val premiumGold: Color,
    val premiumGoldSurface: Color,
    /** Storage-bar segments. */
    val storageApps: Color,
    val storageOther: Color,
    val storageFree: Color,
    val isDark: Boolean,
)

val LightAppColors = AppColors(
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF5F7F8),
    surfaceMuted = Color(0xFFEEF1F3),
    border = Color(0xFFE3E7EA),
    textPrimary = Color(0xFF111827),
    textSecondary = Color(0xFF6B7280),
    teal = Color(0xFF0F9D8A),
    onTeal = Color(0xFFFFFFFF),
    tealSurface = Color(0xFFE3F5F2),
    removeRed = Color(0xFFE5484D),
    onRemoveRed = Color(0xFFFFFFFF),
    removeRedSurface = Color(0xFFFDECEC),
    premiumGold = Color(0xFFF5B301),
    premiumGoldSurface = Color(0xFFFFF5D6),
    storageApps = Color(0xFF0F9D8A),
    storageOther = Color(0xFF9FB3BA),
    storageFree = Color(0xFFE3E7EA),
    isDark = false,
)

val DarkAppColors = AppColors(
    background = Color(0xFF0F1417),
    surface = Color(0xFF182125),
    surfaceMuted = Color(0xFF1F2A2F),
    border = Color(0xFF2A363C),
    textPrimary = Color(0xFFF3F4F6),
    textSecondary = Color(0xFF9CA3AF),
    teal = Color(0xFF2DD4BF),
    onTeal = Color(0xFF062A25),
    tealSurface = Color(0xFF123A35),
    removeRed = Color(0xFFF2555A),
    onRemoveRed = Color(0xFFFFFFFF),
    removeRedSurface = Color(0xFF3A1D1F),
    premiumGold = Color(0xFFFFC53D),
    premiumGoldSurface = Color(0xFF3A3012),
    storageApps = Color(0xFF2DD4BF),
    storageOther = Color(0xFF5B7078),
    storageFree = Color(0xFF2A363C),
    isDark = true,
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }

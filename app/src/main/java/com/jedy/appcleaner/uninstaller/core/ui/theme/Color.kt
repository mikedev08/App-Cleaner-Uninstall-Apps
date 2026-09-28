package com.jedy.appcleaner.uninstaller.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Design system v2 (Sept 2026 redesign). Read through [AppTheme.colors] so light/dark resolve.
 *
 * - [accent] "Spring green" #22C55E is the brand: CTAs, the gauge sweep, selection. It is a *fill*
 *   colour; for green text or icons on a plain background use [accentText], which has contrast.
 * - Severity is the psychology layer: [danger] (red) and [warning] (amber) tell the user where the
 *   space is going — space-hog apps, a nearly-full phone, long-unused apps. They must only ever be
 *   driven by the user's real numbers (see [Severity]); never decorative, never fake.
 * - [removeRed] stays reserved for the Uninstall action itself.
 * - [premiumGold] marks premium and nothing else.
 */
@Immutable
data class AppColors(
    val background: Color,
    val surface: Color,
    val surfaceMuted: Color,
    val surfaceElevated: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val onAccent: Color,
    val accentText: Color,
    val accentSurface: Color,
    val danger: Color,
    val dangerSurface: Color,
    val warning: Color,
    val warningSurface: Color,
    val removeRed: Color,
    val onRemoveRed: Color,
    val removeRedSurface: Color,
    val premiumGold: Color,
    val onPremiumGold: Color,
    val premiumGoldSurface: Color,
    val gaugeTrack: Color,
    /** Storage-bar segments. */
    val storageApps: Color,
    val storageOther: Color,
    val storageFree: Color,
    val isDark: Boolean,
)

val LightAppColors = AppColors(
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF4F7F5),
    surfaceMuted = Color(0xFFEAF0EC),
    surfaceElevated = Color(0xFFFFFFFF),
    border = Color(0xFFE3E9E5),
    textPrimary = Color(0xFF0F1714),
    textSecondary = Color(0xFF5E6B67),
    textMuted = Color(0xFF94A09C),
    accent = Color(0xFF22C55E),
    onAccent = Color(0xFF052E16),
    accentText = Color(0xFF15803D),
    accentSurface = Color(0xFFDCFCE7),
    danger = Color(0xFFEF4444),
    dangerSurface = Color(0xFFFEE2E2),
    warning = Color(0xFFF59E0B),
    warningSurface = Color(0xFFFEF3C7),
    removeRed = Color(0xFFEF4444),
    onRemoveRed = Color(0xFFFFFFFF),
    removeRedSurface = Color(0xFFFEE2E2),
    premiumGold = Color(0xFFF5B301),
    onPremiumGold = Color(0xFF3A2A00),
    premiumGoldSurface = Color(0xFFFEF3C7),
    gaugeTrack = Color(0xFFE6ECE8),
    storageApps = Color(0xFF22C55E),
    storageOther = Color(0xFF94A3B8),
    storageFree = Color(0xFFE6ECE8),
    isDark = false,
)

val DarkAppColors = AppColors(
    background = Color(0xFF0B0F0E),
    surface = Color(0xFF151B19),
    surfaceMuted = Color(0xFF1C2421),
    surfaceElevated = Color(0xFF1A211F),
    border = Color(0xFF26302C),
    textPrimary = Color(0xFFF2F5F4),
    textSecondary = Color(0xFF9AA7A3),
    textMuted = Color(0xFF66736F),
    accent = Color(0xFF22C55E),
    onAccent = Color(0xFF052E16),
    accentText = Color(0xFF4ADE80),
    accentSurface = Color(0xFF12301E),
    danger = Color(0xFFF87171),
    dangerSurface = Color(0xFF3B1618),
    warning = Color(0xFFFBBF24),
    warningSurface = Color(0xFF3A2E0F),
    removeRed = Color(0xFFF87171),
    onRemoveRed = Color(0xFF2A0707),
    removeRedSurface = Color(0xFF3B1618),
    premiumGold = Color(0xFFFBBF24),
    onPremiumGold = Color(0xFF2A1F00),
    premiumGoldSurface = Color(0xFF3A2E0F),
    gaugeTrack = Color(0xFF1F2926),
    storageApps = Color(0xFF22C55E),
    storageOther = Color(0xFF64748B),
    storageFree = Color(0xFF1F2926),
    isDark = true,
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }

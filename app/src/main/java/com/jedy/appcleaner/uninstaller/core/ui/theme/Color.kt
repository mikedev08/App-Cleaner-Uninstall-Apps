package com.jedy.appcleaner.uninstaller.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Design system v2.1 (design review, Sept 2026). Read through [AppTheme.colors] so light/dark
 * resolve. Colour rules (design-review §3.3). Every text/background pair below is at least 4.5:1:
 *
 * - **Green** is the brand, primary actions and anything positive, including "you can free X".
 *   [accent] is a fill; green *text* is always [accentText] (alias [positive]).
 * - **Red** ([removeRed], [danger]) is for destructive actions only: Uninstall, Remove, a failed
 *   removal. Never for sizes, stats, categories or good news.
 * - **Gold** is Premium. [premiumGold] fills exactly one strong spot (the upsell card). Everywhere
 *   else use the quiet pair [premiumGoldText] on [premiumGoldSurface].
 * - **Amber** ([warning]) is never a text colour. It fills indicators (the gauge, [sizeBarLarge])
 *   and tints [warningSurface]; text on that surface is [onWarningSurface].
 * - **Sizes** are shown in [textPrimary]; only the bar (or a "Large" chip) carries colour.
 * - Cards are [surface] with a 1dp [border] so they separate from [background] in both themes.
 */
@Immutable
data class AppColors(
    val background: Color,
    /** Cards and list rows. One step darker than [background], outlined with [border]. */
    val surface: Color,
    /** Tracks, tonal buttons, segmented-control tracks: one step darker than [surface]. */
    val surfaceMuted: Color,
    /** Sheets and dialogs. */
    val surfaceElevated: Color,
    /** 1dp card/row outline and hairline dividers. */
    val border: Color,
    val textPrimary: Color,
    /** ≥4.5:1 on [background], [surface] and [accentSurface]. */
    val textSecondary: Color,
    /** ≥4.5:1 on [background] and [surface]. Captions, timestamps, footers. Not on tinted surfaces. */
    val textMuted: Color,
    /** Brand green. **Fill only, never text** (2.1:1 on white). For green text use [accentText]. */
    val accent: Color,
    val onAccent: Color,
    /** Green text and icons on [background], [surface] or [accentSurface]. */
    val accentText: Color,
    val accentSurface: Color,
    /** Destructive-state icons (a failed removal). Not for sizes, stats or categories. */
    val danger: Color,
    val dangerSurface: Color,
    /** Text and icons on [dangerSurface]. */
    val onDangerSurface: Color,
    /** Amber indicator fill (gauge at 75%+). **Never a text colour.** */
    val warning: Color,
    val warningSurface: Color,
    /** Text and icons on [warningSurface]: a dark amber in light mode, a pale one in dark mode. */
    val onWarningSurface: Color,
    /** The destructive button fill; [onRemoveRed] on it is ≥4.5:1. */
    val removeRed: Color,
    val onRemoveRed: Color,
    val removeRedSurface: Color,
    /** Strong gold fill: the single upsell spot only (see `ProBadge(strong = true)`). */
    val premiumGold: Color,
    val onPremiumGold: Color,
    /** The quiet premium surface (PRO pill, compact premium rows). */
    val premiumGoldSurface: Color,
    /** Gold text/icons on [premiumGoldSurface] (≥4.5:1). */
    val premiumGoldText: Color,
    val gaugeTrack: Color,
    /** The size-bar fill for an app that `LargeApps.isLarge`. Normal bars are [accent]. */
    val sizeBarLarge: Color,
    /** Storage-bar segments. */
    val storageApps: Color,
    val storageOther: Color,
    val storageFree: Color,
    val isDark: Boolean,
) {
    /** Positive numbers and good news ("You can free up 1.1 GB"). Same as [accentText]. */
    val positive: Color get() = accentText
}

val LightAppColors = AppColors(
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFEEF2EF),
    surfaceMuted = Color(0xFFE3E9E5),
    surfaceElevated = Color(0xFFFFFFFF),
    border = Color(0xFFD8E0DB),
    textPrimary = Color(0xFF0F1714),
    textSecondary = Color(0xFF5E6B67), // 4.9:1 on surface
    textMuted = Color(0xFF63706B), // 5.2:1 on white, 4.6:1 on surface
    accent = Color(0xFF22C55E),
    onAccent = Color(0xFF052E16),
    accentText = Color(0xFF15773A), // 5.0:1 on surface, 5.1:1 on accentSurface
    accentSurface = Color(0xFFDCFCE7),
    danger = Color(0xFFDC2626),
    dangerSurface = Color(0xFFFEE2E2),
    onDangerSurface = Color(0xFFB91C1C), // 5.3:1
    warning = Color(0xFFF59E0B),
    warningSurface = Color(0xFFFEF3C7),
    onWarningSurface = Color(0xFF78350F), // 8.2:1
    removeRed = Color(0xFFDC2626), // white on it: 4.8:1
    onRemoveRed = Color(0xFFFFFFFF),
    removeRedSurface = Color(0xFFFEE2E2),
    premiumGold = Color(0xFFF5B301),
    onPremiumGold = Color(0xFF3A2A00),
    premiumGoldSurface = Color(0xFFFDF3D7),
    premiumGoldText = Color(0xFF8A5A00), // 5.4:1
    gaugeTrack = Color(0xFFDDE4E0),
    sizeBarLarge = Color(0xFFF59E0B),
    storageApps = Color(0xFF22C55E),
    storageOther = Color(0xFF94A3B8),
    storageFree = Color(0xFFDDE4E0),
    isDark = false,
)

val DarkAppColors = AppColors(
    background = Color(0xFF0B0F0E),
    surface = Color(0xFF1A2220),
    surfaceMuted = Color(0xFF232D2A),
    surfaceElevated = Color(0xFF1F2826),
    border = Color(0xFF2A3531),
    textPrimary = Color(0xFFF2F5F4),
    textSecondary = Color(0xFF9AA7A3), // 6.5:1 on surface
    textMuted = Color(0xFF838F8B), // 4.8:1 on surface, 5.8:1 on background
    accent = Color(0xFF22C55E),
    onAccent = Color(0xFF052E16),
    accentText = Color(0xFF4ADE80),
    accentSurface = Color(0xFF12301E),
    danger = Color(0xFFF87171),
    dangerSurface = Color(0xFF401A1F),
    onDangerSurface = Color(0xFFFCA5A5), // 7.9:1
    warning = Color(0xFFF59E0B),
    warningSurface = Color(0xFF33280E),
    onWarningSurface = Color(0xFFFDE68A), // 11.6:1
    removeRed = Color(0xFFF87171),
    onRemoveRed = Color(0xFF2A0707), // 6.7:1
    removeRedSurface = Color(0xFF401A1F),
    premiumGold = Color(0xFFFBBF24),
    onPremiumGold = Color(0xFF2A1F00),
    premiumGoldSurface = Color(0xFF2E2710),
    premiumGoldText = Color(0xFFF5C542), // 9.2:1
    gaugeTrack = Color(0xFF26302C),
    sizeBarLarge = Color(0xFFF59E0B),
    storageApps = Color(0xFF22C55E),
    storageOther = Color(0xFF64748B),
    storageFree = Color(0xFF26302C),
    isDark = true,
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }

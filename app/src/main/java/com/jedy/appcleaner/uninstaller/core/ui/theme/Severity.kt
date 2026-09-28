package com.jedy.appcleaner.uninstaller.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.jedy.appcleaner.uninstaller.core.format.DAY_MILLIS

/**
 * The urgency layer for **indicators**: the storage gauge fill and tinted chips/tiles. Every level
 * is computed from real numbers (Play's Deceptive Behavior policy bans fake alarms).
 *
 * Design review §3.3: severity never colours text. Sizes and numbers are [AppColors.textPrimary];
 * good news is [AppColors.positive]. Whether an app is "Large" is `LargeApps.isLarge` (core/model),
 * not a severity level, and it is drawn with one colour ([AppColors.sizeBarLarge]).
 */
enum class Severity { OK, WARNING, DANGER }

object SeverityRules {
    /** Storage gauge: amber from 75% full, red from 90%. */
    fun storage(usedFraction: Float): Severity = when {
        usedFraction >= 0.90f -> Severity.DANGER
        usedFraction >= 0.75f -> Severity.WARNING
        else -> Severity.OK
    }

    /**
     * Legacy three-step size ramp (red ≥ 1 GB or 2% of the phone, amber ≥ 250 MB or 0.5%). It
     * disagrees with the Large count, which is how Home showed "0 Space hogs" above five 400 MB
     * apps (design review §2A).
     */
    @Deprecated(
        "One definition of Large: use LargeApps.isLarge(bestKnownBytes) and colour only the bar.",
        ReplaceWith("LargeApps.isLarge(bytes)", "com.jedy.appcleaner.uninstaller.core.model.LargeApps"),
    )
    fun appSize(bytes: Long, deviceTotalBytes: Long): Severity {
        val share = if (deviceTotalBytes > 0) bytes.toDouble() / deviceTotalBytes else 0.0
        return when {
            bytes >= 1_000_000_000L || share >= 0.02 -> Severity.DANGER
            bytes >= 250_000_000L || share >= 0.005 -> Severity.WARNING
            else -> Severity.OK
        }
    }

    /** Not opened: red from 90 days, amber from 30. Null lastUsed = no record in the window. */
    fun idle(lastUsedAt: Long?, now: Long): Severity {
        val days = if (lastUsedAt == null) Long.MAX_VALUE else (now - lastUsedAt) / DAY_MILLIS
        return when {
            days >= 90 -> Severity.DANGER
            days >= 30 -> Severity.WARNING
            else -> Severity.OK
        }
    }
}

/**
 * Indicator **fill** (gauge arc, dots, bars). Never use it as a text colour: amber text is 2:1.
 * For text or icons on [surface] use [contentColor].
 */
val Severity.color: Color
    @Composable @ReadOnlyComposable get() = when (this) {
        Severity.OK -> AppTheme.colors.accent
        Severity.WARNING -> AppTheme.colors.warning
        Severity.DANGER -> AppTheme.colors.danger
    }

/** The tinted background of a chip or icon tile at this level. */
val Severity.surface: Color
    @Composable @ReadOnlyComposable get() = when (this) {
        Severity.OK -> AppTheme.colors.accentSurface
        Severity.WARNING -> AppTheme.colors.warningSurface
        Severity.DANGER -> AppTheme.colors.dangerSurface
    }

/** Text and icons drawn on [surface] (≥4.5:1 in both themes). */
val Severity.contentColor: Color
    @Composable @ReadOnlyComposable get() = when (this) {
        Severity.OK -> AppTheme.colors.accentText
        Severity.WARNING -> AppTheme.colors.onWarningSurface
        Severity.DANGER -> AppTheme.colors.onDangerSurface
    }

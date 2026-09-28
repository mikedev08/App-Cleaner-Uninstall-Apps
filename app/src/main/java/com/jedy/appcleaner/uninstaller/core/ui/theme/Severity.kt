package com.jedy.appcleaner.uninstaller.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.jedy.appcleaner.uninstaller.core.format.DAY_MILLIS

/**
 * The urgency layer (redesign brief: "use red to show which apps take the most space").
 * Every level is computed from real numbers, so the red a user sees is always true — Play's
 * Deceptive Behavior policy bans fake alarms, and honest urgency is also what converts.
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
     * An app's footprint relative to the phone: red when one app takes ≥ 2% of total storage or
     * ≥ 1 GB ("space hog"), amber from 0.5% or 250 MB.
     */
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

val Severity.color: Color
    @Composable @ReadOnlyComposable get() = when (this) {
        Severity.OK -> AppTheme.colors.accent
        Severity.WARNING -> AppTheme.colors.warning
        Severity.DANGER -> AppTheme.colors.danger
    }

val Severity.surface: Color
    @Composable @ReadOnlyComposable get() = when (this) {
        Severity.OK -> AppTheme.colors.accentSurface
        Severity.WARNING -> AppTheme.colors.warningSurface
        Severity.DANGER -> AppTheme.colors.dangerSurface
    }

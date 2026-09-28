package com.jedy.appcleaner.uninstaller.data.usage

import com.jedy.appcleaner.uninstaller.core.model.InstalledApp
import com.jedy.appcleaner.uninstaller.core.model.UnusedApp
import kotlinx.coroutines.flow.StateFlow

/**
 * CONTRACT (frozen). Last-used data from UsageStatsManager (PRD Feature 3). Computed for anyone
 * who granted access — the entitlement only decides whether rows render in clear.
 */
interface UsageInsights {
    /** package -> last used epoch millis. Absent key = no record in the ~2-year window. Empty without access. */
    val lastUsed: StateFlow<Map<String, Long>>

    /** Start of the retention window, for "Not opened since at least <date>". */
    val windowStart: StateFlow<Long>

    suspend fun refresh()

    /**
     * The unused rule: unused at [thresholdDays] when not used AND not installed within the
     * threshold, excluding default launcher/keyboard/SMS/dialer, accessibility services,
     * notification listeners and device admins. Sorted least recently used first.
     */
    fun unusedApps(apps: List<InstalledApp>, thresholdDays: Int, now: Long = System.currentTimeMillis()): List<UnusedApp>

    /** Packages that must never be suggested as unused, and that warrant a warning chip when selected. */
    fun alwaysRunningPackages(): Set<String>
}

package com.jedy.appcleaner.uninstaller.data.scan

import com.jedy.appcleaner.uninstaller.core.model.DeviceStorage
import kotlinx.coroutines.flow.StateFlow

/**
 * CONTRACT (frozen). The "Scan my phone" moment (redesign): one pass over the real inventory,
 * usage and storage data that produces the numbers the Home dashboard, the Scan result and the
 * paywall headline all quote — so every screen tells the same, true story.
 */
interface CleanupScan {
    /** Last completed scan (kept for the session and persisted cheaply); null before the first. */
    val result: StateFlow<ScanResult?>

    /** Live progress while [run] is working; null when idle. */
    val progress: StateFlow<ScanProgress?>

    /** Runs a full scan (inventory refresh, usage + storage refresh) and publishes [result]. */
    suspend fun run(): ScanResult
}

enum class ScanStep { APPS, USAGE, STORAGE, DONE }

data class ScanProgress(val step: ScanStep, val fraction: Float)

/**
 * All byte counts are real measurements (StorageStatsManager totals when Usage Access is granted,
 * APK sizes otherwise — see [sizesAreEstimates]).
 */
data class ScanResult(
    val scannedAt: Long,
    val storage: DeviceStorage,
    val appCount: Int,
    val appsBytes: Long,
    /** Unused at the user's threshold (needs Usage Access; 0 without it). */
    val unusedCount: Int,
    val unusedBytes: Long,
    val thresholdDays: Int,
    /** Apps flagged "space hog" by SeverityRules.appSize (DANGER). */
    val largeCount: Int,
    val largeBytes: Long,
    /** Total cache across apps (needs Usage Access). */
    val cacheBytes: Long,
    val cacheAppCount: Int,
    /** Headline: unused apps + cache — what the user could realistically reclaim. */
    val reclaimableBytes: Long,
    val hasUsageAccess: Boolean,
    val sizesAreEstimates: Boolean,
)

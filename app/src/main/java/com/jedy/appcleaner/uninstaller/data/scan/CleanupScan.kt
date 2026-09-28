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
 * All byte counts are real measurements: each app's best-known size (`InstalledApp.bestKnownBytes`:
 * the StorageStatsManager app + data + cache total when Usage Access is granted, the APK size
 * otherwise — see [sizesAreEstimates]).
 *
 * **The three categories and the headline** (design review §2A, "the numbers contradict each
 * other"). Home's category rows and the Scan result show the same three rows, and the headline is
 * exactly their sum, so every app and every byte is counted once:
 *
 * | Row    | Count / bytes shown                   | What it is                                           |
 * |--------|---------------------------------------|------------------------------------------------------|
 * | Unused | [unusedCount] / [unusedBytes]         | not opened in [thresholdDays] (needs Usage Access)   |
 * | Large  | [largeOnlyCount] / [largeOnlyBytes]   | `LargeApps.isLarge` apps that are *not* also Unused  |
 * | Cache  | [keptCacheAppCount] / [keptCacheBytes]| cache of the apps in neither row above               |
 *
 * [reclaimableBytes] = unusedBytes + largeOnlyBytes + keptCacheBytes. An unused or large app's
 * size already contains its own cache, which is why the Cache row only adds the rest.
 *
 * [largeCount] / [largeBytes] and [cacheBytes] / [cacheAppCount] are the whole categories — the
 * same sets the Apps "Large" and "Cache" filters list — for "N more are also unused" style notes.
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
    /** Every app `LargeApps.isLarge` calls Large, unused or not (the Apps "Large" filter). */
    val largeCount: Int,
    val largeBytes: Long,
    /** The Large apps that are not also Unused: what the Large row adds to the headline. */
    val largeOnlyCount: Int,
    val largeOnlyBytes: Long,
    /** Total cache across installed apps (needs Usage Access). */
    val cacheBytes: Long,
    val cacheAppCount: Int,
    /** Cache of the apps that are neither Unused nor Large: what the Cache row adds. */
    val keptCacheBytes: Long,
    val keptCacheAppCount: Int,
    /** Headline: unusedBytes + largeOnlyBytes + keptCacheBytes, the sum of the three rows. */
    val reclaimableBytes: Long,
    val hasUsageAccess: Boolean,
    val sizesAreEstimates: Boolean,
) {
    /** Unused + Large apps: what "Review N apps" selects. */
    val reviewAppCount: Int get() = unusedCount + largeOnlyCount

    /** What removing [reviewAppCount] apps frees (the headline minus the kept apps' cache). */
    val reviewAppBytes: Long get() = unusedBytes + largeOnlyBytes

    /** Large apps also counted under Unused (shown as "N more" / "counted in Unused"). */
    val largeAlsoUnusedCount: Int get() = largeCount - largeOnlyCount
}

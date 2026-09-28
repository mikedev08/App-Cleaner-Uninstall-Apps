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
 * **The three categories** (design review §2A, "the numbers contradict each other"). Home's
 * category rows, the Scan result rows and the Apps filters show the *whole* category, so each
 * number is the same on every screen:
 *
 * | Row    | Count / bytes shown             | What it is (the same set the Apps filter lists)     |
 * |--------|---------------------------------|-----------------------------------------------------|
 * | Unused | [unusedCount] / [unusedBytes]   | not opened in [thresholdDays] (needs Usage Access)  |
 * | Large  | [largeCount] / [largeBytes]     | every `LargeApps.isLarge` app, unused or not        |
 * | Cache  | [cacheAppCount] / [cacheBytes]  | every app holding any cache (needs Usage Access)    |
 *
 * The rows overlap: an app can be both unused and large ([overlapCount], which the screens explain
 * in one line), and an unused or large app's size already contains its own cache.
 *
 * **The headline** counts every app and every byte once:
 * [reclaimableBytes] = [reviewAppBytes] (the sizes of the Unused ∪ Large apps) +
 * [otherCacheBytes] (the cache of the apps in neither).
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
    /** Every installed app with cache > 0, and their total cache (the Apps "Cache" filter). */
    val cacheAppCount: Int,
    val cacheBytes: Long,
    /** Apps that are both Unused and Large: in both rows, counted once in the headline. */
    val overlapCount: Int,
    /** The Unused ∪ Large apps: what "Review N apps" selects, each app once. */
    val reviewAppCount: Int,
    val reviewAppBytes: Long,
    /** Cache of the apps that are neither Unused nor Large: what the headline adds for cache. */
    val otherCacheBytes: Long,
    /** Headline: reviewAppBytes + otherCacheBytes. */
    val reclaimableBytes: Long,
    val hasUsageAccess: Boolean,
    val sizesAreEstimates: Boolean,
)

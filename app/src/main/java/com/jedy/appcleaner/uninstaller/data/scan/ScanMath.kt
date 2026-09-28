package com.jedy.appcleaner.uninstaller.data.scan

import com.jedy.appcleaner.uninstaller.core.model.AppSize
import com.jedy.appcleaner.uninstaller.core.model.DeviceStorage
import com.jedy.appcleaner.uninstaller.core.model.InstalledApp
import com.jedy.appcleaner.uninstaller.core.model.LargeApps
import com.jedy.appcleaner.uninstaller.core.model.UnusedApp
import com.jedy.appcleaner.uninstaller.core.model.bestKnownBytes

/**
 * The arithmetic behind a [ScanResult], kept pure so it is unit-tested without Android. Every
 * number the Scan result, the Home dashboard and the paywall quote comes out of here, which is
 * what keeps the "you can free up X" promise honest (Play's Deceptive Behavior policy).
 *
 * One size source and one Large rule for every screen: an app's size is its best-known size
 * ([sizeOf]) and it is Large when [LargeApps.isLarge] says so — the same call the Apps "Large"
 * filter makes. The rows show whole categories and may overlap; the headline counts each app
 * once (see [ScanResult]).
 */
object ScanMath {

    /** The measured app + data + cache total when StorageStatsManager has it, else the APK bytes. */
    fun sizeOf(app: InstalledApp, sizes: Map<String, AppSize>): Long = app.bestKnownBytes(sizes)

    fun compute(
        apps: List<InstalledApp>,
        unused: List<UnusedApp>,
        sizes: Map<String, AppSize>,
        storage: DeviceStorage,
        thresholdDays: Int,
        hasUsageAccess: Boolean,
        now: Long,
    ): ScanResult {
        val bytesByPackage = apps.associate { it.packageName to sizeOf(it, sizes) }
        // Unused apps must still be installed: the list and the inventory can be a refresh apart.
        val unusedPackages = unused.mapTo(HashSet()) { it.app.packageName }.filterTo(HashSet()) { it in bytesByPackage }
        val largePackages = bytesByPackage.filterValues(LargeApps::isLarge).keys
        // Unused ∪ Large, each app once: the apps the headline and "Review N apps" count.
        val reviewPackages = unusedPackages + largePackages
        // Only installed apps: the size cache can briefly outlive an uninstall.
        val cacheByPackage = apps.mapNotNull { app -> sizes[app.packageName]?.let { app.packageName to it.cacheBytes } }
            .filter { it.second > 0 }
        // A reviewed app's size already includes its cache; the headline only adds everyone else's.
        val otherCacheBytes = cacheByPackage.filter { (pkg, _) -> pkg !in reviewPackages }.sumOf { it.second }
        val reviewAppBytes = reviewPackages.sumOf { bytesByPackage.getValue(it) }
        return ScanResult(
            scannedAt = now,
            storage = storage,
            appCount = apps.size,
            appsBytes = bytesByPackage.values.sum(),
            unusedCount = unusedPackages.size,
            unusedBytes = unusedPackages.sumOf { bytesByPackage.getValue(it) },
            thresholdDays = thresholdDays,
            largeCount = largePackages.size,
            largeBytes = largePackages.sumOf { bytesByPackage.getValue(it) },
            cacheAppCount = cacheByPackage.size,
            cacheBytes = cacheByPackage.sumOf { it.second },
            overlapCount = unusedPackages.count { it in largePackages },
            reviewAppCount = reviewPackages.size,
            reviewAppBytes = reviewAppBytes,
            otherCacheBytes = otherCacheBytes,
            reclaimableBytes = reviewAppBytes + otherCacheBytes,
            hasUsageAccess = hasUsageAccess,
            sizesAreEstimates = apps.any { it.packageName !in sizes },
        )
    }

    /** Every Large app (the Large row and filter): what free "Review N big apps" selects. */
    fun largePackages(apps: List<InstalledApp>, sizes: Map<String, AppSize>): List<String> =
        apps.filter { LargeApps.isLarge(it, sizes) }.map { it.packageName }

    /** Unused ∪ Large, each once, unused first: what premium "Review N apps" selects. */
    fun reviewPackages(apps: List<InstalledApp>, sizes: Map<String, AppSize>, unused: Set<String>): List<String> {
        val installed = apps.mapTo(HashSet()) { it.packageName }
        return (unused.filter { it in installed } + largePackages(apps, sizes)).distinct()
    }

    /** Share of the phone in use right now, 0..1 (0 when the volume could not be read). */
    fun usedFraction(storage: DeviceStorage): Float =
        if (storage.totalBytes <= 0) 0f else (storage.usedBytes.toDouble() / storage.totalBytes).toFloat().coerceIn(0f, 1f)

    /** Share that would be in use once [ScanResult.reclaimableBytes] is freed, 0..1. */
    fun usedFractionAfter(result: ScanResult): Float {
        val total = result.storage.totalBytes
        if (total <= 0) return 0f
        val after = (result.storage.usedBytes - result.reclaimableBytes).coerceAtLeast(0)
        return (after.toDouble() / total).toFloat().coerceIn(0f, 1f)
    }
}

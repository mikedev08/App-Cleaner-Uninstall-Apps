package com.jedy.appcleaner.uninstaller.data.scan

import com.jedy.appcleaner.uninstaller.core.model.AppSize
import com.jedy.appcleaner.uninstaller.core.model.DeviceStorage
import com.jedy.appcleaner.uninstaller.core.model.InstalledApp
import com.jedy.appcleaner.uninstaller.core.model.UnusedApp
import com.jedy.appcleaner.uninstaller.core.ui.theme.Severity
import com.jedy.appcleaner.uninstaller.core.ui.theme.SeverityRules

/**
 * The arithmetic behind a [ScanResult], kept pure so it is unit-tested without Android. Every
 * number the Scan result, the Home dashboard and the paywall quote comes out of here, which is
 * what keeps the "you can free up X" promise honest (Play's Deceptive Behavior policy).
 */
object ScanMath {

    /** The measured app + data + cache total when StorageStatsManager has it, else the APK bytes. */
    fun sizeOf(app: InstalledApp, sizes: Map<String, AppSize>): Long =
        sizes[app.packageName]?.totalBytes ?: app.apkBytes

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
        val unusedBytes = unusedPackages.sumOf { bytesByPackage.getValue(it) }
        val large = bytesByPackage.values.filter { SeverityRules.appSize(it, storage.totalBytes) == Severity.DANGER }
        // Only installed apps: the size cache can briefly outlive an uninstall.
        val cacheByPackage = apps.mapNotNull { app -> sizes[app.packageName]?.let { app.packageName to it.cacheBytes } }
        val cacheBytes = cacheByPackage.sumOf { it.second }
        // An unused app's size already includes its cache. Counting that cache twice would inflate
        // the headline, so only the cache of the apps the user keeps is added on top.
        val keptCache = cacheByPackage.filter { it.first !in unusedPackages }.sumOf { it.second }
        return ScanResult(
            scannedAt = now,
            storage = storage,
            appCount = apps.size,
            appsBytes = bytesByPackage.values.sum(),
            unusedCount = unusedPackages.size,
            unusedBytes = unusedBytes,
            thresholdDays = thresholdDays,
            largeCount = large.size,
            largeBytes = large.sum(),
            cacheBytes = cacheBytes,
            cacheAppCount = cacheByPackage.count { it.second > 0 },
            reclaimableBytes = unusedBytes + keptCache,
            hasUsageAccess = hasUsageAccess,
            sizesAreEstimates = apps.any { it.packageName !in sizes },
        )
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

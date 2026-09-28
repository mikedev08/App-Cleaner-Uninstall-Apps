package com.jedy.appcleaner.uninstaller.data.scan

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jedy.appcleaner.uninstaller.core.model.DeviceStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.scanDataStore: DataStore<Preferences> by preferencesDataStore(name = "appcleaner_last_scan")

/**
 * The last scan, persisted so Home can show "You can free up 3.4 GB" on a cold start
 * instead of a blank dashboard. Its own tiny DataStore: [AppPreferences] is frozen, and a scan is
 * a cache (losing it only means Home falls back to live numbers), not user settings.
 */
@Singleton
class ScanResultStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private object Keys {
        val version = intPreferencesKey("version")
        val scannedAt = longPreferencesKey("scanned_at")
        val totalBytes = longPreferencesKey("total_bytes")
        val freeBytes = longPreferencesKey("free_bytes")
        val appCount = intPreferencesKey("app_count")
        val appsBytes = longPreferencesKey("apps_bytes")
        val unusedCount = intPreferencesKey("unused_count")
        val unusedBytes = longPreferencesKey("unused_bytes")
        val thresholdDays = intPreferencesKey("threshold_days")
        val largeCount = intPreferencesKey("large_count")
        val largeBytes = longPreferencesKey("large_bytes")
        val cacheBytes = longPreferencesKey("cache_bytes")
        val cacheAppCount = intPreferencesKey("cache_app_count")
        val overlapCount = intPreferencesKey("overlap_count")
        val reviewAppCount = intPreferencesKey("review_app_count")
        val reviewAppBytes = longPreferencesKey("review_app_bytes")
        val otherCacheBytes = longPreferencesKey("other_cache_bytes")
        val reclaimableBytes = longPreferencesKey("reclaimable_bytes")
        val hasUsageAccess = booleanPreferencesKey("has_usage_access")
        val sizesAreEstimates = booleanPreferencesKey("sizes_are_estimates")
        val packages = stringSetPreferencesKey("packages")
    }

    /** Null when nothing was saved yet, or it was saved by an incompatible version. */
    suspend fun load(): ScanResult? = runCatching {
        val p = context.scanDataStore.data.first()
        if (p[Keys.version] != VERSION) return@runCatching null
        ScanResult(
            scannedAt = p[Keys.scannedAt] ?: return@runCatching null,
            storage = DeviceStorage(totalBytes = p[Keys.totalBytes] ?: 0, freeBytes = p[Keys.freeBytes] ?: 0),
            appCount = p[Keys.appCount] ?: 0,
            appsBytes = p[Keys.appsBytes] ?: 0,
            unusedCount = p[Keys.unusedCount] ?: 0,
            unusedBytes = p[Keys.unusedBytes] ?: 0,
            thresholdDays = p[Keys.thresholdDays] ?: 0,
            largeCount = p[Keys.largeCount] ?: 0,
            largeBytes = p[Keys.largeBytes] ?: 0,
            cacheAppCount = p[Keys.cacheAppCount] ?: 0,
            cacheBytes = p[Keys.cacheBytes] ?: 0,
            overlapCount = p[Keys.overlapCount] ?: 0,
            reviewAppCount = p[Keys.reviewAppCount] ?: 0,
            reviewAppBytes = p[Keys.reviewAppBytes] ?: 0,
            otherCacheBytes = p[Keys.otherCacheBytes] ?: 0,
            reclaimableBytes = p[Keys.reclaimableBytes] ?: 0,
            hasUsageAccess = p[Keys.hasUsageAccess] ?: false,
            sizesAreEstimates = p[Keys.sizesAreEstimates] ?: true,
        )
    }.getOrNull()

    /** The installed set the saved result describes, so a change while we were closed is noticed. */
    suspend fun loadPackages(): Set<String>? = runCatching { context.scanDataStore.data.first()[Keys.packages] }.getOrNull()

    suspend fun save(result: ScanResult, packages: Set<String>) {
        runCatching {
            context.scanDataStore.edit { p ->
                p[Keys.version] = VERSION
                p[Keys.scannedAt] = result.scannedAt
                p[Keys.totalBytes] = result.storage.totalBytes
                p[Keys.freeBytes] = result.storage.freeBytes
                p[Keys.appCount] = result.appCount
                p[Keys.appsBytes] = result.appsBytes
                p[Keys.unusedCount] = result.unusedCount
                p[Keys.unusedBytes] = result.unusedBytes
                p[Keys.thresholdDays] = result.thresholdDays
                p[Keys.largeCount] = result.largeCount
                p[Keys.largeBytes] = result.largeBytes
                p[Keys.cacheBytes] = result.cacheBytes
                p[Keys.cacheAppCount] = result.cacheAppCount
                p[Keys.overlapCount] = result.overlapCount
                p[Keys.reviewAppCount] = result.reviewAppCount
                p[Keys.reviewAppBytes] = result.reviewAppBytes
                p[Keys.otherCacheBytes] = result.otherCacheBytes
                p[Keys.reclaimableBytes] = result.reclaimableBytes
                p[Keys.hasUsageAccess] = result.hasUsageAccess
                p[Keys.sizesAreEstimates] = result.sizesAreEstimates
                p[Keys.packages] = packages
            }
        }
    }

    private companion object {
        /**
         * 3: whole-category rows (they may overlap) and a headline over Unused ∪ Large. v2 saved
         * disjoint rows ("N more apps"), v1 other maths; both are dropped and rescanned.
         */
        const val VERSION = 3
    }
}

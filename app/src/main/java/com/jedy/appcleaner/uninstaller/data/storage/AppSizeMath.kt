package com.jedy.appcleaner.uninstaller.data.storage

import com.jedy.appcleaner.uninstaller.core.format.DAY_MILLIS
import com.jedy.appcleaner.uninstaller.core.model.AppSize

/**
 * Turns raw `StorageStats` numbers into the app / data / cache split the UI shows (PRD Feature 3),
 * and decides when a cached measurement is too old to present. Pure Kotlin for unit tests.
 */
object AppSizeMath {

    /** PRD Feature 3: a cached size is stale after 24h. */
    const val STALE_AFTER_MILLIS = DAY_MILLIS

    /**
     * `StorageStats.getDataBytes()` already *includes* the cache, and `getCacheBytes()` already
     * includes the external cache when shared storage lives on the same volume. Adding them up
     * naively would double-count and disagree with system Settings, which is the number users
     * compare us against (PRD Feature 1). So: cache is the larger of cacheBytes and (API 31+)
     * externalCacheBytes — the latter only matters on OEMs that leave it out — and data is what
     * remains of dataBytes once the cache is taken out. [AppSize.totalBytes] then equals
     * Settings' "Total".
     */
    fun fromStats(
        appBytes: Long,
        dataBytes: Long,
        cacheBytes: Long,
        externalCacheBytes: Long,
        measuredAt: Long,
    ): AppSize {
        val cache = maxOf(cacheBytes, externalCacheBytes).coerceAtLeast(0)
        return AppSize(
            appBytes = appBytes.coerceAtLeast(0),
            dataBytes = (dataBytes - cache).coerceAtLeast(0),
            cacheBytes = cache,
            measuredAt = measuredAt,
        )
    }

    /**
     * Re-measure when never measured, older than 24h, measured "in the future" (the clock moved
     * back, so the age is unknowable), or the package was updated since (ACTION_PACKAGE_REPLACED
     * surfaces as a new lastUpdateTime in the inventory).
     */
    fun isStale(measuredAt: Long?, measuredForUpdate: Long?, packageUpdatedAt: Long, now: Long): Boolean {
        if (measuredAt == null || measuredForUpdate == null) return true
        if (measuredForUpdate != packageUpdatedAt) return true
        if (measuredAt > now) return true
        return now - measuredAt >= STALE_AFTER_MILLIS
    }
}

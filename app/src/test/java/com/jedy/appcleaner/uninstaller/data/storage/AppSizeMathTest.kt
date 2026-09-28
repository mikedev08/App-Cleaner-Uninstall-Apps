package com.jedy.appcleaner.uninstaller.data.storage

import com.jedy.appcleaner.uninstaller.core.format.DAY_MILLIS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSizeMathTest {

    @Test
    fun `data excludes the cache it already contains so the total matches Settings`() {
        // StorageStats: dataBytes (300) includes cacheBytes (100).
        val size = AppSizeMath.fromStats(appBytes = 500, dataBytes = 300, cacheBytes = 100, externalCacheBytes = 40, measuredAt = 1)
        assertEquals(500, size.appBytes)
        assertEquals(200, size.dataBytes)
        assertEquals(100, size.cacheBytes)
        assertEquals(800, size.totalBytes)
    }

    @Test
    fun `external cache counts when the platform left it out of cacheBytes`() {
        val size = AppSizeMath.fromStats(appBytes = 10, dataBytes = 100, cacheBytes = 0, externalCacheBytes = 30, measuredAt = 1)
        assertEquals(30, size.cacheBytes)
        assertEquals(70, size.dataBytes)
    }

    @Test
    fun `negative or inconsistent numbers never go below zero`() {
        val size = AppSizeMath.fromStats(appBytes = -1, dataBytes = 10, cacheBytes = 50, externalCacheBytes = 0, measuredAt = 1)
        assertEquals(0, size.appBytes)
        assertEquals(0, size.dataBytes)
        assertEquals(50, size.cacheBytes)
    }

    @Test
    fun `staleness rules`() {
        val now = 100 * DAY_MILLIS
        assertTrue("never measured", AppSizeMath.isStale(null, null, packageUpdatedAt = 5, now = now))
        assertFalse("fresh", AppSizeMath.isStale(now - 1_000, 5, packageUpdatedAt = 5, now = now))
        assertTrue("24h old", AppSizeMath.isStale(now - DAY_MILLIS, 5, packageUpdatedAt = 5, now = now))
        assertTrue("updated since", AppSizeMath.isStale(now - 1_000, 5, packageUpdatedAt = 6, now = now))
        assertTrue("measured in the future", AppSizeMath.isStale(now + 1_000, 5, packageUpdatedAt = 5, now = now))
    }
}

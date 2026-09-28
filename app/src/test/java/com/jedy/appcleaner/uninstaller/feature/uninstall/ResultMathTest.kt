package com.jedy.appcleaner.uninstaller.feature.uninstall

import com.jedy.appcleaner.uninstaller.core.model.AppSize
import com.jedy.appcleaner.uninstaller.core.model.DeviceStorage
import com.jedy.appcleaner.uninstaller.core.model.InstalledApp
import com.jedy.appcleaner.uninstaller.core.model.UnusedApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultMathTest {

    private val gb = 1_000_000_000L

    @Test
    fun `before is current used plus freed, after is current used`() {
        // 128 GB phone, 40 GB free now => 88 GB used; the batch freed 2 GB.
        val drop = ResultMath.storageDrop(DeviceStorage(totalBytes = 128 * gb, freeBytes = 40 * gb), freedBytes = 2 * gb)!!
        assertEquals(90f / 128f, drop.beforeFraction, 1e-6f)
        assertEquals(88f / 128f, drop.afterFraction, 1e-6f)
        assertEquals(69, drop.afterPercent)
    }

    @Test
    fun `before never exceeds a full phone`() {
        val drop = ResultMath.storageDrop(DeviceStorage(totalBytes = 64 * gb, freeBytes = 1 * gb), freedBytes = 5 * gb)!!
        assertEquals(1f, drop.beforeFraction, 0f)
        assertEquals(63f / 64f, drop.afterFraction, 1e-6f)
    }

    @Test
    fun `nothing freed means no drop`() {
        val drop = ResultMath.storageDrop(DeviceStorage(totalBytes = 100 * gb, freeBytes = 32 * gb), freedBytes = 0)!!
        assertEquals(drop.beforeFraction, drop.afterFraction, 0f)
        assertEquals(68, drop.afterPercent)
    }

    @Test
    fun `negative freed is treated as zero`() {
        val drop = ResultMath.storageDrop(DeviceStorage(totalBytes = 100 * gb, freeBytes = 50 * gb), freedBytes = -3 * gb)!!
        assertEquals(0.5f, drop.beforeFraction, 1e-6f)
    }

    @Test
    fun `an unreadable volume shows no gauge instead of a made-up one`() {
        assertNull(ResultMath.storageDrop(DeviceStorage(totalBytes = 0, freeBytes = 0), freedBytes = gb))
    }

    @Test
    fun `percent rounds and clamps`() {
        assertEquals(68, ResultMath.percent(0.678f))
        assertEquals(0, ResultMath.percent(-0.2f))
        assertEquals(100, ResultMath.percent(1.4f))
    }

    @Test
    fun `teaser uses measured sizes when available and is exact`() {
        val unused = listOf(unused("a", apk = 10), unused("b", apk = 20))
        val sizes = mapOf("a" to size(300), "b" to size(700))
        val teaser = ResultMath.unusedTeaser(unused, sizes, thresholdDays = 30)
        assertEquals(2, teaser.count)
        assertEquals(1_000L, teaser.bytes)
        assertFalse(teaser.isEstimate)
        assertEquals(30, teaser.thresholdDays)
    }

    @Test
    fun `teaser falls back to apk size and says about`() {
        val unused = listOf(unused("a", apk = 10), unused("b", apk = 20))
        val partial = ResultMath.unusedTeaser(unused, mapOf("a" to size(300)), thresholdDays = 60)
        assertEquals(320L, partial.bytes)
        assertTrue(partial.isEstimate)

        val free = ResultMath.unusedTeaser(unused, fullSizes = null, thresholdDays = 60)
        assertEquals(30L, free.bytes)
        assertTrue(free.isEstimate)
    }

    @Test
    fun `no unused apps means a hidden teaser`() {
        val teaser = ResultMath.unusedTeaser(emptyList(), emptyMap(), thresholdDays = 90)
        assertEquals(0, teaser.count)
        assertEquals(0L, teaser.bytes)
    }

    private fun size(total: Long) = AppSize(appBytes = total, dataBytes = 0, cacheBytes = 0, measuredAt = 0)

    private fun unused(pkg: String, apk: Long) = UnusedApp(
        app = InstalledApp(
            packageName = pkg,
            label = pkg,
            versionName = null,
            firstInstallTime = 0,
            lastUpdateTime = 0,
            installerPackage = null,
            apkBytes = apk,
            storageUuid = null,
            uid = 0,
        ),
        lastUsedAt = null,
    )
}

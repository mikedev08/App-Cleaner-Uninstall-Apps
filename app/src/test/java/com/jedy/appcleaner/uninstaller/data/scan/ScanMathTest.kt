package com.jedy.appcleaner.uninstaller.data.scan

import com.jedy.appcleaner.uninstaller.core.model.AppSize
import com.jedy.appcleaner.uninstaller.core.model.DeviceStorage
import com.jedy.appcleaner.uninstaller.core.model.InstalledApp
import com.jedy.appcleaner.uninstaller.core.model.UnusedApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanMathTest {

    private val gb = 1_000_000_000L
    private val mb = 1_000_000L
    private val device = DeviceStorage(totalBytes = 128 * gb, freeBytes = 28 * gb)

    private val game = app("com.game", apk = 1_500 * mb)
    private val chat = app("com.chat", apk = 200 * mb)
    private val notes = app("com.notes", apk = 20 * mb)
    private val apps = listOf(game, chat, notes)

    @Test
    fun `measured totals win over apk bytes and estimates are flagged`() {
        val sizes = mapOf(chat.packageName to size(app = 200 * mb, data = 900 * mb, cache = 300 * mb))
        val result = compute(sizes = sizes)
        assertEquals(1_500 * mb + 1_400 * mb + 20 * mb, result.appsBytes)
        assertTrue(result.sizesAreEstimates)

        val all = apps.associate { it.packageName to size(app = it.apkBytes, data = 0, cache = 0) }
        assertFalse(compute(sizes = all).sizesAreEstimates)
    }

    @Test
    fun `space hogs are exactly the apps SeverityRules calls DANGER`() {
        val sizes = mapOf(chat.packageName to size(app = 200 * mb, data = 900 * mb, cache = 0))
        val result = compute(sizes = sizes)
        // game (1.5 GB apk) and chat (1.1 GB measured) are ≥ 1 GB; notes is not.
        assertEquals(2, result.largeCount)
        assertEquals(2_600 * mb, result.largeBytes)
    }

    @Test
    fun `reclaimable is unused size plus the cache of kept apps, never double counted`() {
        val sizes = mapOf(
            game.packageName to size(app = 1_500 * mb, data = 0, cache = 100 * mb),
            chat.packageName to size(app = 200 * mb, data = 0, cache = 300 * mb),
        )
        val result = compute(sizes = sizes, unused = listOf(game))
        assertEquals(1, result.unusedCount)
        assertEquals(1_600 * mb, result.unusedBytes)
        assertEquals(400 * mb, result.cacheBytes)
        assertEquals(2, result.cacheAppCount)
        // game's own 100 MB cache is already inside its 1.6 GB.
        assertEquals(1_600 * mb + 300 * mb, result.reclaimableBytes)
    }

    @Test
    fun `unused apps that are no longer installed do not count`() {
        val gone = app("com.gone", apk = 5 * gb)
        val result = compute(unused = listOf(notes, gone))
        assertEquals(1, result.unusedCount)
        assertEquals(20 * mb, result.unusedBytes)
    }

    @Test
    fun `cache of uninstalled apps is ignored`() {
        val sizes = mapOf("com.gone" to size(app = 0, data = 0, cache = 9 * gb))
        assertEquals(0L, compute(sizes = sizes).cacheBytes)
    }

    @Test
    fun `before and after fractions come from the real numbers`() {
        val sizes = mapOf(game.packageName to size(app = 1_500 * mb, data = 0, cache = 0))
        val result = compute(sizes = sizes, unused = listOf(game))
        assertEquals(100f / 128f, ScanMath.usedFraction(device), 0.0001f)
        assertEquals((100f - 1.5f) / 128f, ScanMath.usedFractionAfter(result), 0.0001f)
        assertEquals(0f, ScanMath.usedFraction(DeviceStorage(0, 0)))
    }

    private fun compute(
        sizes: Map<String, AppSize> = emptyMap(),
        unused: List<InstalledApp> = emptyList(),
    ) = ScanMath.compute(
        apps = apps,
        unused = unused.map { UnusedApp(it, lastUsedAt = null) },
        sizes = sizes,
        storage = device,
        thresholdDays = 60,
        hasUsageAccess = true,
        now = 42,
    )

    private fun size(app: Long, data: Long, cache: Long) = AppSize(app, data, cache, measuredAt = 0)

    private fun app(pkg: String, apk: Long) = InstalledApp(
        packageName = pkg, label = pkg, versionName = "1", firstInstallTime = 1, lastUpdateTime = 1,
        installerPackage = null, apkBytes = apk, storageUuid = null, uid = 10_000,
    )
}

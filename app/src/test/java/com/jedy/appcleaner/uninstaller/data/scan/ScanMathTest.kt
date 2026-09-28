package com.jedy.appcleaner.uninstaller.data.scan

import com.jedy.appcleaner.uninstaller.core.model.AppSize
import com.jedy.appcleaner.uninstaller.core.model.DeviceStorage
import com.jedy.appcleaner.uninstaller.core.model.InstalledApp
import com.jedy.appcleaner.uninstaller.core.model.LargeApps
import com.jedy.appcleaner.uninstaller.core.model.UnusedApp
import com.jedy.appcleaner.uninstaller.core.model.bestKnownBytes
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
    fun `size source is the shared best-known size`() {
        val sizes = mapOf(chat.packageName to size(app = 200 * mb, data = 50 * mb, cache = 0))
        assertEquals(chat.bestKnownBytes(sizes), ScanMath.sizeOf(chat, sizes))
        assertEquals(game.apkBytes, ScanMath.sizeOf(game, sizes))
    }

    @Test
    fun `large apps are exactly the ones LargeApps calls large, by best-known size`() {
        // chat's APK (200 MB) is under the line; measured with data it is 260 MB, which is over.
        val sizes = mapOf(chat.packageName to size(app = 200 * mb, data = 60 * mb, cache = 0))
        val result = compute(sizes = sizes)
        val expected = apps.filter { LargeApps.isLarge(it, sizes) }
        assertEquals(listOf(game, chat), expected)
        assertEquals(2, result.largeCount)
        assertEquals(1_500 * mb + 260 * mb, result.largeBytes)
        // Nothing is unused, so the Large row is the whole category.
        assertEquals(2, result.largeOnlyCount)
        assertEquals(result.largeBytes, result.largeOnlyBytes)
    }

    @Test
    fun `the 250 MB line is inclusive and share of disk plays no part`() {
        val edge = app("com.edge", apk = LargeApps.THRESHOLD_BYTES)
        val under = app("com.under", apk = LargeApps.THRESHOLD_BYTES - 1)
        val result = ScanMath.compute(
            apps = listOf(edge, under), unused = emptyList(), sizes = emptyMap(),
            // A tiny phone: the old share-of-disk rule would have called both "space hogs".
            storage = DeviceStorage(totalBytes = 4 * gb, freeBytes = 1 * gb),
            thresholdDays = 60, hasUsageAccess = false, now = 0,
        )
        assertEquals(1, result.largeCount)
        assertEquals(LargeApps.THRESHOLD_BYTES, result.largeBytes)
    }

    @Test
    fun `an app that is unused and large is counted once, under Unused`() {
        val result = compute(unused = listOf(game))
        assertEquals(1, result.largeCount)
        assertEquals(0, result.largeOnlyCount)
        assertEquals(0L, result.largeOnlyBytes)
        assertEquals(1, result.largeAlsoUnusedCount)
        assertEquals(1_500 * mb, result.reclaimableBytes)
    }

    @Test
    fun `headline is the sum of the three rows with no double counting`() {
        val bigChat = app("com.bigchat", apk = 400 * mb)
        val all = listOf(game, bigChat, chat, notes)
        val sizes = mapOf(
            game.packageName to size(app = 1_500 * mb, data = 0, cache = 100 * mb),
            bigChat.packageName to size(app = 400 * mb, data = 0, cache = 50 * mb),
            chat.packageName to size(app = 200 * mb, data = 0, cache = 30 * mb),
            notes.packageName to size(app = 20 * mb, data = 0, cache = 5 * mb),
        )
        val result = ScanMath.compute(
            apps = all,
            unused = listOf(UnusedApp(game, lastUsedAt = null)),
            sizes = sizes, storage = device, thresholdDays = 60, hasUsageAccess = true, now = 42,
        )
        // Unused row: game (1.6 GB, its own cache inside).
        assertEquals(1, result.unusedCount)
        assertEquals(1_600 * mb, result.unusedBytes)
        // Large row: bigChat only (game is already under Unused).
        assertEquals(2, result.largeCount)
        assertEquals(1, result.largeOnlyCount)
        assertEquals(450 * mb, result.largeOnlyBytes)
        // Cache row: only the apps in neither row above (chat + notes).
        assertEquals(185 * mb, result.cacheBytes)
        assertEquals(4, result.cacheAppCount)
        assertEquals(35 * mb, result.keptCacheBytes)
        assertEquals(2, result.keptCacheAppCount)
        // Headline = the rows shown.
        assertEquals(result.unusedBytes + result.largeOnlyBytes + result.keptCacheBytes, result.reclaimableBytes)
        assertEquals(1_600 * mb + 450 * mb + 35 * mb, result.reclaimableBytes)
        // "Review N apps" is the Unused + Large rows.
        assertEquals(2, result.reviewAppCount)
        assertEquals(2_050 * mb, result.reviewAppBytes)
    }

    @Test
    fun `largeOnlyPackages matches the Large row`() {
        val sizes = mapOf(chat.packageName to size(app = 200 * mb, data = 60 * mb, cache = 0))
        assertEquals(listOf(chat.packageName), ScanMath.largeOnlyPackages(apps, sizes, unused = setOf(game.packageName)))
        assertEquals(compute(sizes = sizes, unused = listOf(game)).largeOnlyCount, 1)
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

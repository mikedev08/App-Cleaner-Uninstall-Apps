package com.jedy.appcleaner.uninstaller.feature.home

import com.jedy.appcleaner.uninstaller.core.model.AppSize
import com.jedy.appcleaner.uninstaller.core.model.DeviceStorage
import com.jedy.appcleaner.uninstaller.core.model.HomeTab
import com.jedy.appcleaner.uninstaller.core.model.InstalledApp
import com.jedy.appcleaner.uninstaller.core.model.UnusedApp
import com.jedy.appcleaner.uninstaller.data.scan.ScanMath
import com.jedy.appcleaner.uninstaller.data.scan.ScanResult
import com.jedy.appcleaner.uninstaller.feature.scan.ScanCta
import com.jedy.appcleaner.uninstaller.feature.scan.ScanCtas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCategoriesTest {

    private val mb = 1_000_000L
    private val game = app("com.game", apk = 1_500 * mb)
    private val video = app("com.video", apk = 400 * mb)
    private val notes = app("com.notes", apk = 20 * mb)
    private val sizes = mapOf(
        game.packageName to AppSize(1_500 * mb, 0, 100 * mb, 0),
        video.packageName to AppSize(400 * mb, 0, 50 * mb, 0),
        notes.packageName to AppSize(20 * mb, 0, 5 * mb, 0),
    )

    @Test
    fun `rows are Unused, Large, Cache, each its whole category`() {
        val result = scan(unused = listOf(game))
        val rows = HomeCategories.rows(result, isPremium = true)
        assertEquals(listOf(Category.UNUSED, Category.LARGE, Category.CACHE), rows.map { it.category })
        assertEquals(CategoryValue.Value(1, 1_600 * mb), rows[0].value)
        // Large: every large app (game and video), the same count the Apps "Large" filter shows.
        assertEquals(CategoryValue.Value(2, 2_050 * mb), rows[1].value)
        assertEquals(CategoryValue.Value(result.largeCount, result.largeBytes), rows[1].value)
        // Cache: every app holding cache.
        assertEquals(CategoryValue.Value(3, 155 * mb), rows[2].value)
        assertTrue(rows.none { it.locked })
    }

    @Test
    fun `headline counts every app once and the overlap gets one line`() {
        val result = scan(unused = listOf(game))
        // game (unused and large) + video (large) + notes' cache.
        assertEquals(1_600 * mb + 450 * mb + 5 * mb, result.reclaimableBytes)
        assertEquals(1, HomeCategories.overlapCount(result))
        assertEquals(0, HomeCategories.overlapCount(scan()))
        assertEquals(0, HomeCategories.overlapCount(null))
    }

    @Test
    fun `only Unused is Pro`() {
        assertEquals(setOf(Category.UNUSED), HomeCategories.proOnly)
        val rows = HomeCategories.rows(scan(unused = listOf(game)), isPremium = false)
        assertEquals(listOf(true, false, false), rows.map { it.locked })
    }

    @Test
    fun `zero rows are quiet, not a chevron to an empty list`() {
        val rows = HomeCategories.rows(scan(apps = listOf(notes), unused = listOf(notes)), isPremium = false)
        assertEquals(CategoryValue.Empty, rows[1].value)
        assertFalse(rows[1].locked)
        val noCache = ScanMath.compute(
            apps = listOf(notes), unused = emptyList(), sizes = mapOf(notes.packageName to AppSize(20 * mb, 0, 0, 0)),
            storage = DeviceStorage(128_000 * mb, 28_000 * mb), thresholdDays = 60, hasUsageAccess = true, now = 0,
        )
        assertEquals(CategoryValue.Empty, HomeCategories.rows(noCache, isPremium = true)[2].value)
    }

    @Test
    fun `without usage access unused and cache ask for it and large counts by APK size`() {
        val rows = HomeCategories.rows(scan(hasAccess = false), isPremium = false)
        assertEquals(CategoryValue.NeedsAccess, rows[0].value)
        assertEquals(CategoryValue.Value(2, 1_900 * mb), rows[1].value)
        assertFalse(rows[1].locked)
        assertEquals(CategoryValue.NeedsAccess, rows[2].value)
    }

    @Test
    fun `loading keeps all three rows`() {
        assertTrue(HomeCategories.rows(null, isPremium = true).all { it.value == CategoryValue.Loading })
    }

    @Test
    fun `review goes to the first filter with something in it this user can open`() {
        val result = scan(unused = listOf(game))
        assertEquals(HomeTab.UNUSED, HomeCategories.reviewTab(result, isPremium = true))
        assertEquals(HomeTab.LARGE, HomeCategories.reviewTab(scan(), isPremium = true))
        assertEquals(HomeTab.LARGE, HomeCategories.reviewTab(result, isPremium = false))
        val cacheOnly = scan(apps = listOf(notes))
        assertEquals(HomeTab.CACHE, HomeCategories.reviewTab(cacheOnly, isPremium = false))
    }

    @Test
    fun `scan button says what it frees and never quotes a Pro number to free users`() {
        val result = scan(unused = listOf(game))
        // Premium: Unused ∪ Large, each once (game + video).
        assertEquals(ScanCta.ReviewApps(2, 2_050 * mb, HomeTab.ALL), ScanCtas.primary(result, isPremium = true))
        // Free: every big app, and a quiet Pro line for the unused one.
        assertEquals(ScanCta.ReviewLarge(2, 2_050 * mb), ScanCtas.primary(result, isPremium = false))
        assertTrue(ScanCtas.showProHint(result, isPremium = false))
        assertFalse(ScanCtas.showProHint(result, isPremium = true))
        assertFalse(ScanCtas.showProHint(scan(), isPremium = false))
        // Only unused apps and no cache: the free button is the visible lock, with no number.
        val onlyUnused = ScanMath.compute(
            apps = listOf(notes), unused = listOf(UnusedApp(notes, lastUsedAt = null)),
            sizes = mapOf(notes.packageName to AppSize(20 * mb, 0, 0, 0)),
            storage = DeviceStorage(128_000 * mb, 28_000 * mb), thresholdDays = 60, hasUsageAccess = true, now = 0,
        )
        assertEquals(ScanCta.Unlock, ScanCtas.primary(onlyUnused, isPremium = false))
        assertFalse(ScanCtas.showProHint(onlyUnused, isPremium = false))
        assertEquals(ScanCta.ReviewApps(1, 20 * mb, HomeTab.UNUSED), ScanCtas.primary(onlyUnused, isPremium = true))
        // Free, cache only: cache is free too.
        assertEquals(ScanCta.ReviewCache(5 * mb), ScanCtas.primary(scan(apps = listOf(notes)), isPremium = false))
        assertEquals(ScanCta.AllowAccess, ScanCtas.primary(scan(hasAccess = false), isPremium = true))
    }

    private fun scan(
        apps: List<InstalledApp> = listOf(game, video, notes),
        unused: List<InstalledApp> = emptyList(),
        hasAccess: Boolean = true,
    ): ScanResult = ScanMath.compute(
        apps = apps,
        unused = if (hasAccess) unused.map { UnusedApp(it, lastUsedAt = null) } else emptyList(),
        sizes = if (hasAccess) sizes else emptyMap(),
        storage = DeviceStorage(totalBytes = 128_000 * mb, freeBytes = 28_000 * mb),
        thresholdDays = 60,
        hasUsageAccess = hasAccess,
        now = 0,
    )

    private fun app(pkg: String, apk: Long) = InstalledApp(
        packageName = pkg, label = pkg, versionName = "1", firstInstallTime = 1, lastUpdateTime = 1,
        installerPackage = null, apkBytes = apk, storageUuid = null, uid = 10_000,
    )
}

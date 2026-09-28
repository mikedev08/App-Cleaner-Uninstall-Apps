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
    fun `rows are Unused, Large, Cache and add up to the headline`() {
        val result = scan(unused = listOf(game))
        val rows = HomeCategories.rows(result, isPremium = true)
        assertEquals(listOf(Category.UNUSED, Category.LARGE, Category.CACHE), rows.map { it.category })
        val values = rows.map { it.value as CategoryValue.Value }
        assertEquals(result.reclaimableBytes, values.sumOf { it.bytes })
        // Large: game is counted under Unused, so video reads "1 more app".
        assertEquals(CategoryValue.Value(1, 450 * mb, more = true), values[1])
        assertEquals(CategoryValue.Value(1, 5 * mb, more = true), values[2])
        assertTrue(rows.none { it.locked })
    }

    @Test
    fun `free users see one lock per row that has something in it`() {
        val rows = HomeCategories.rows(scan(unused = listOf(game)), isPremium = false)
        assertTrue(rows.all { it.locked })
    }

    @Test
    fun `zero rows are quiet, not a chevron to an empty list`() {
        val rows = HomeCategories.rows(scan(apps = listOf(notes), unused = listOf(notes)), isPremium = false)
        assertEquals(CategoryValue.Empty(countedAbove = false), rows[1].value)
        // notes' cache is inside its unused size: counted above, nothing to add.
        assertEquals(CategoryValue.Empty(countedAbove = true), rows[2].value)
        assertFalse(rows[1].locked)
    }

    @Test
    fun `without usage access unused and cache ask for it and large still counts`() {
        val rows = HomeCategories.rows(scan(hasAccess = false), isPremium = true)
        assertEquals(CategoryValue.NeedsAccess, rows[0].value)
        assertEquals(CategoryValue.Value(2, 1_900 * mb, more = false), rows[1].value)
        assertEquals(CategoryValue.NeedsAccess, rows[2].value)
    }

    @Test
    fun `loading keeps all three rows`() {
        assertTrue(HomeCategories.rows(null, isPremium = true).all { it.value == CategoryValue.Loading })
    }

    @Test
    fun `review goes to the first filter with something in it, or All for free users`() {
        val result = scan(unused = listOf(game))
        assertEquals(HomeTab.UNUSED, HomeCategories.reviewTab(result, isPremium = true))
        assertEquals(HomeTab.LARGE, HomeCategories.reviewTab(scan(), isPremium = true))
        assertEquals(HomeTab.ALL, HomeCategories.reviewTab(result, isPremium = false))
    }

    @Test
    fun `scan button says what it frees and never quotes a Pro number to free users`() {
        val result = scan(unused = listOf(game))
        assertEquals(ScanCta.ReviewApps(2, 2_050 * mb, HomeTab.ALL), ScanCtas.primary(result, isPremium = true))
        assertEquals(ScanCta.RemoveLarge(1, 450 * mb), ScanCtas.primary(result, isPremium = false))
        // Only unused apps to free: the free button is the visible lock, with no number.
        val onlyUnused = scan(apps = listOf(game, notes), unused = listOf(game, notes))
        assertEquals(ScanCta.Unlock, ScanCtas.primary(onlyUnused, isPremium = false))
        assertEquals(ScanCta.ReviewApps(2, 1_625 * mb, HomeTab.UNUSED), ScanCtas.primary(onlyUnused, isPremium = true))
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

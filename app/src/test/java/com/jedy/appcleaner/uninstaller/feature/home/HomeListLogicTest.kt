package com.jedy.appcleaner.uninstaller.feature.home

import com.jedy.appcleaner.uninstaller.core.model.AppSize
import com.jedy.appcleaner.uninstaller.core.model.DeviceStorage
import com.jedy.appcleaner.uninstaller.core.model.InstalledApp
import com.jedy.appcleaner.uninstaller.core.model.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeListLogicTest {

    private val maps = app("com.google.android.apps.maps", "Maps", apk = 300, installed = 30, updated = 5)
    private val chess = app("org.chess.free", "chess", apk = 50, installed = 10, updated = 50)
    private val bank = app("com.bank.mobile", "Bank", apk = 120, installed = 20, updated = 40)
    private val all = listOf(maps, chess, bank)

    @Test
    fun `search matches label and package, ignoring case and padding`() {
        assertTrue(HomeListLogic.matches(maps, "  MAP "))
        assertTrue(HomeListLogic.matches(maps, "google.android"))
        assertFalse(HomeListLogic.matches(chess, "maps"))
        assertTrue(HomeListLogic.matches(chess, ""))
    }

    @Test
    fun `size sort is largest first`() {
        assertEquals(listOf(maps, bank, chess), sorted(SortOrder.SIZE))
    }

    @Test
    fun `name sort is case-insensitive A to Z`() {
        assertEquals(listOf(bank, chess, maps), sorted(SortOrder.NAME))
    }

    @Test
    fun `install date sort is oldest first`() {
        assertEquals(listOf(chess, bank, maps), sorted(SortOrder.INSTALL_DATE))
    }

    @Test
    fun `last updated sort is most recent first`() {
        assertEquals(listOf(chess, bank, maps), sorted(SortOrder.LAST_UPDATED))
    }

    @Test
    fun `last used sort puts never-used first, then least recent`() {
        val lastUsed = mapOf(maps.packageName to 500L, bank.packageName to 100L)
        val rows = all.map { HomeAppRow(it, it.apkBytes, lastUsed[it.packageName]) }
        val result = HomeListLogic.sort(rows, SortOrder.LAST_USED, String.CASE_INSENSITIVE_ORDER).map { it.app }
        assertEquals(listOf(chess, bank, maps), result)
    }

    @Test
    fun `equal keys fall back to label so rows never jump`() {
        val a = app("x.a", "Alpha", apk = 10)
        val b = app("x.b", "beta", apk = 10)
        val rows = listOf(b, a).map { HomeAppRow(it, it.apkBytes, null) }
        assertEquals(listOf(a, b), HomeListLogic.sort(rows, SortOrder.SIZE, String.CASE_INSENSITIVE_ORDER).map { it.app })
    }

    @Test
    fun `premium sort falls back to size when not premium`() {
        assertEquals(SortOrder.SIZE, HomeListLogic.effectiveSort(SortOrder.LAST_USED, isPremium = false))
        assertEquals(SortOrder.LAST_USED, HomeListLogic.effectiveSort(SortOrder.LAST_USED, isPremium = true))
        assertEquals(SortOrder.NAME, HomeListLogic.effectiveSort(SortOrder.NAME, isPremium = false))
    }

    @Test
    fun `display size uses the measured total only when asked and known`() {
        val sizes = mapOf(maps.packageName to AppSize(appBytes = 300, dataBytes = 700, cacheBytes = 0, measuredAt = 0))
        assertEquals(1_000L, HomeListLogic.displayBytes(maps, sizes, useTotal = true))
        assertEquals(300L, HomeListLogic.displayBytes(maps, sizes, useTotal = false))
        assertEquals(50L, HomeListLogic.displayBytes(chess, sizes, useTotal = true))
    }

    @Test
    fun `apps total is apk bytes for free users and measured totals for premium`() {
        val sizes = mapOf(maps.packageName to AppSize(appBytes = 300, dataBytes = 700, cacheBytes = 0, measuredAt = 0))
        assertEquals(470L, HomeListLogic.appsBytes(all, sizes, useMeasured = false))
        // Unmeasured apps still count their APK bytes.
        assertEquals(1_170L, HomeListLogic.appsBytes(all, sizes, useMeasured = true))
    }

    @Test
    fun `storage summary keeps apps within used and derives other`() {
        val summary = HomeListLogic.storageSummary(DeviceStorage(totalBytes = 1_000, freeBytes = 400), appsBytes = 900)
        assertEquals(600L, summary.usedBytes)
        assertEquals(600L, summary.appsBytes)
        assertEquals(0L, summary.otherBytes)
        assertEquals(60, summary.usedPercent)

        val normal = HomeListLogic.storageSummary(DeviceStorage(totalBytes = 1_000, freeBytes = 400), appsBytes = 200)
        assertEquals(400L, normal.otherBytes)
    }

    @Test
    fun `unknown device storage is reported as such`() {
        val summary = HomeListLogic.storageSummary(DeviceStorage(totalBytes = 0, freeBytes = 0), appsBytes = 10)
        assertFalse(summary.isKnown)
        assertEquals(0, summary.usedPercent)
    }

    @Test
    fun `hidden by search counts selected apps the query filters out`() {
        val byPackage = all.associateBy { it.packageName }
        val selected = setOf(maps.packageName, chess.packageName, "com.not.in.inventory")
        assertEquals(1, HomeListLogic.hiddenBySearch(selected, byPackage, "maps"))
        assertEquals(0, HomeListLogic.hiddenBySearch(selected, byPackage, " "))
    }

    private fun sorted(order: SortOrder): List<InstalledApp> =
        HomeListLogic.sort(all.map { HomeAppRow(it, it.apkBytes, null) }, order, String.CASE_INSENSITIVE_ORDER).map { it.app }

    private fun app(pkg: String, label: String, apk: Long, installed: Long = 1, updated: Long = 1) = InstalledApp(
        packageName = pkg,
        label = label,
        versionName = "1.0",
        firstInstallTime = installed,
        lastUpdateTime = updated,
        installerPackage = null,
        apkBytes = apk,
        storageUuid = null,
        uid = 10_000,
    )
}

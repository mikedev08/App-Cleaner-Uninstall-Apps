package com.jedy.appcleaner.uninstaller.feature.apps

import com.jedy.appcleaner.uninstaller.core.model.AppSize
import com.jedy.appcleaner.uninstaller.core.format.DAY_MILLIS
import com.jedy.appcleaner.uninstaller.core.model.InstalledApp
import com.jedy.appcleaner.uninstaller.core.model.SortOrder
import com.jedy.appcleaner.uninstaller.core.ui.theme.Severity
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppsListLogicTest {

    private val maps = app("com.google.android.apps.maps", "Maps", apk = 300, installed = 30, updated = 5)
    private val chess = app("org.chess.free", "chess", apk = 50, installed = 10, updated = 50)
    private val bank = app("com.bank.mobile", "Bank", apk = 120, installed = 20, updated = 40)
    private val all = listOf(maps, chess, bank)

    @Test
    fun `search matches label and package, ignoring case and padding`() {
        assertTrue(AppsListLogic.matches(maps, "  MAP "))
        assertTrue(AppsListLogic.matches(maps, "google.android"))
        assertFalse(AppsListLogic.matches(chess, "maps"))
        assertTrue(AppsListLogic.matches(chess, ""))
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
        val rows = all.map { AppsRow(it, it.apkBytes, lastUsed[it.packageName]) }
        val result = AppsListLogic.sort(rows, SortOrder.LAST_USED, String.CASE_INSENSITIVE_ORDER).map { it.app }
        assertEquals(listOf(chess, bank, maps), result)
    }

    @Test
    fun `equal keys fall back to label so rows never jump`() {
        val a = app("x.a", "Alpha", apk = 10)
        val b = app("x.b", "beta", apk = 10)
        val rows = listOf(b, a).map { AppsRow(it, it.apkBytes, null) }
        assertEquals(listOf(a, b), AppsListLogic.sort(rows, SortOrder.SIZE, String.CASE_INSENSITIVE_ORDER).map { it.app })
    }

    @Test
    fun `premium sort falls back to size when not premium`() {
        assertEquals(SortOrder.SIZE, AppsListLogic.effectiveSort(SortOrder.LAST_USED, isPremium = false))
        assertEquals(SortOrder.LAST_USED, AppsListLogic.effectiveSort(SortOrder.LAST_USED, isPremium = true))
        assertEquals(SortOrder.NAME, AppsListLogic.effectiveSort(SortOrder.NAME, isPremium = false))
    }

    @Test
    fun `display size uses the measured total only when asked and known`() {
        val sizes = mapOf(maps.packageName to AppSize(appBytes = 300, dataBytes = 700, cacheBytes = 0, measuredAt = 0))
        assertEquals(1_000L, AppsListLogic.displayBytes(maps, sizes, useTotal = true))
        assertEquals(300L, AppsListLogic.displayBytes(maps, sizes, useTotal = false))
        assertEquals(50L, AppsListLogic.displayBytes(chess, sizes, useTotal = true))
    }

    @Test
    fun `hidden by search counts selected apps the query filters out`() {
        val byPackage = all.associateBy { it.packageName }
        val selected = setOf(maps.packageName, chess.packageName, "com.not.in.inventory")
        assertEquals(1, AppsListLogic.hiddenBySearch(selected, byPackage, "maps"))
        assertEquals(0, AppsListLogic.hiddenBySearch(selected, byPackage, " "))
    }

    @Test
    fun `size sort splits red and amber apps from the rest`() {
        val rows = listOf(
            AppsRow(maps, 300, null, severity = Severity.DANGER),
            AppsRow(bank, 120, null, severity = Severity.WARNING),
            AppsRow(chess, 50, null),
        )
        val sections = AppsListLogic.sections(rows, SortOrder.SIZE)!!
        assertEquals(listOf(maps, bank), sections.hogs.map { it.app })
        assertEquals(listOf(chess), sections.rest.map { it.app })
        assertEquals(420L, sections.hogBytes)
        assertNull(AppsListLogic.sections(rows, SortOrder.NAME))
        assertNull(AppsListLogic.sections(listOf(AppsRow(chess, 50, null)), SortOrder.SIZE))
    }

    @Test
    fun `idle chip follows real usage and never predates the install`() {
        val now = 1_000 * DAY_MILLIS
        // Opened 100 days ago: red, 3 months.
        assertEquals(IdleChip(Severity.DANGER, 3), AppsListLogic.idleChip(now - 100 * DAY_MILLIS, 1, 1, now))
        // Opened 40 days ago: amber.
        assertEquals(Severity.WARNING, AppsListLogic.idleChip(now - 40 * DAY_MILLIS, 1, 1, now)?.severity)
        // Opened last week: no chip.
        assertNull(AppsListLogic.idleChip(now - 7 * DAY_MILLIS, 1, 1, now))
        // No record, but installed 5 days ago: not idle.
        assertNull(AppsListLogic.idleChip(null, now - 5 * DAY_MILLIS, 1, now))
        // No record in a window that started 400 days ago: over a year.
        assertEquals(true, AppsListLogic.idleChip(null, 1, now - 400 * DAY_MILLIS, now)?.overAYear)
    }

    @Test
    fun `size fraction is relative to the biggest app`() {
        assertEquals(0.5f, AppsListLogic.sizeFraction(50, 100))
        assertEquals(0f, AppsListLogic.sizeFraction(50, 0))
    }

    private fun sorted(order: SortOrder): List<InstalledApp> =
        AppsListLogic.sort(all.map { AppsRow(it, it.apkBytes, null) }, order, String.CASE_INSENSITIVE_ORDER).map { it.app }

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

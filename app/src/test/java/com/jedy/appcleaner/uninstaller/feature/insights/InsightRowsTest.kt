package com.jedy.appcleaner.uninstaller.feature.insights

import com.jedy.appcleaner.uninstaller.core.format.DAY_MILLIS
import com.jedy.appcleaner.uninstaller.core.model.AppSize
import com.jedy.appcleaner.uninstaller.core.model.InstalledApp
import com.jedy.appcleaner.uninstaller.core.model.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class InsightRowsTest {

    private fun app(pkg: String, label: String, installed: Long = 0, updated: Long = 0) = InstalledApp(
        packageName = pkg, label = label, versionName = null, firstInstallTime = installed, lastUpdateTime = updated,
        installerPackage = null, apkBytes = 1, storageUuid = null, uid = 0,
    )

    private fun size(total: Long) = AppSize(appBytes = total, dataBytes = 0, cacheBytes = 0, measuredAt = 0)

    private val rows = listOf(
        LargeRow(app("com.b", "banana", installed = 2, updated = 20), size(300), lastUsedAt = 50),
        LargeRow(app("com.a", "Apple", installed = 1, updated = 30), size(100), lastUsedAt = null),
        LargeRow(app("com.c", "cherry", installed = 3, updated = 10), null, lastUsedAt = 10),
    )

    @Test
    fun `size sorts largest first with unmeasured last`() {
        assertEquals(listOf("com.b", "com.a", "com.c"), InsightSort.sort(rows, SortOrder.SIZE).map { it.app.packageName })
    }

    @Test
    fun `name sorts case-insensitively`() {
        assertEquals(listOf("Apple", "banana", "cherry"), InsightSort.sort(rows, SortOrder.NAME).map { it.app.label })
    }

    @Test
    fun `install date oldest first, last updated newest first, last used least recent first`() {
        assertEquals(listOf("com.a", "com.b", "com.c"), InsightSort.sort(rows, SortOrder.INSTALL_DATE).map { it.app.packageName })
        assertEquals(listOf("com.a", "com.b", "com.c"), InsightSort.sort(rows, SortOrder.LAST_UPDATED).map { it.app.packageName })
        assertEquals(listOf("com.a", "com.c", "com.b"), InsightSort.sort(rows, SortOrder.LAST_USED).map { it.app.packageName })
    }

    @Test
    fun `search matches label and package name, ignoring case and padding`() {
        assertEquals(listOf("com.b"), InsightSort.filter(rows, " BAN ").map { it.app.packageName })
        assertEquals(listOf("com.c"), InsightSort.filter(rows, "com.c").map { it.app.packageName })
        assertEquals(3, InsightSort.filter(rows, "  ").size)
    }

    @Test
    fun `age picks the unit a person would use`() {
        val now = 1_000 * DAY_MILLIS
        assertEquals(Age(0, AgeUnit.DAYS), Age.between(now, now))
        assertEquals(Age(45, AgeUnit.DAYS), Age.between(now - 45 * DAY_MILLIS, now))
        assertEquals(Age(4, AgeUnit.MONTHS), Age.between(now - 125 * DAY_MILLIS, now))
        assertEquals(Age(2, AgeUnit.YEARS), Age.between(now - 800 * DAY_MILLIS, now))
        assertEquals(Age(0, AgeUnit.DAYS), Age.between(now + DAY_MILLIS, now))
    }
}

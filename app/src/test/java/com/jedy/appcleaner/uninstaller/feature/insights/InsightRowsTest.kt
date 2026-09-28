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
    fun `size sorts largest first, unmeasured apps by their APK size`() {
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

    private fun cached(total: Long, cache: Long) =
        AppSize(appBytes = total - cache, dataBytes = 0, cacheBytes = cache, measuredAt = 0)

    private val cacheRows = listOf(
        LargeRow(app("com.a", "Apple"), cached(total = 900_000_000, cache = 5_000_000), lastUsedAt = null),
        LargeRow(app("com.b", "banana"), cached(total = 400_000_000, cache = 300_000_000), lastUsedAt = null),
        LargeRow(app("com.c", "cherry"), null, lastUsedAt = null),
        LargeRow(app("com.d", "date"), cached(total = 100_000_000, cache = 40_000_000), lastUsedAt = null),
    )

    @Test
    fun `cache filter lists every measured app with cache, biggest first, and totals exactly those`() {
        val cached = LargeInsights.cacheRows(cacheRows)
        assertEquals(listOf("com.b", "com.d", "com.a"), cached.map { it.app.packageName })
        assertEquals(345_000_000L, LargeInsights.cacheBytes(cached))
        assertEquals(emptyList<LargeRow>(), LargeInsights.cacheRows(listOf(LargeRow(app("com.z", "zero"), cached(10, 0), null))))
    }

    @Test
    fun `large filter uses the one Large rule on the best-known size`() {
        val big = app("com.big", "big").copy(apkBytes = 260_000_000)
        val rows = listOf(
            // Measured total counts: 400 MB is Large.
            LargeRow(app("com.m", "measured"), cached(total = 400_000_000, cache = 0), null),
            // Measured total under the line: not Large, whatever the APK says.
            LargeRow(big, cached(total = 249_999_999, cache = 0), null),
            // Unmeasured: judged by its APK size.
            LargeRow(big.copy(packageName = "com.apk"), null, null),
            LargeRow(app("com.small", "small"), null, null),
        )
        assertEquals(listOf("com.m", "com.apk"), LargeInsights.largeRows(rows).map { it.app.packageName })
        assertEquals(260_000_000L, rows[2].bytes)
    }

    @Test
    fun `breakdown sums every measured app and is null when nothing is measured`() {
        val breakdown = LargeInsights.breakdown(cacheRows)!!
        assertEquals(1_400_000_000L, breakdown.totalBytes)
        assertEquals(345_000_000L, breakdown.cacheBytes)
        assertEquals(null, LargeInsights.breakdown(listOf(cacheRows[2])))
    }
}

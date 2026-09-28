package com.jedy.appcleaner.uninstaller.data.usage

import com.jedy.appcleaner.uninstaller.core.format.DAY_MILLIS
import com.jedy.appcleaner.uninstaller.core.model.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnusedRuleTest {

    private val now = 1_800_000_000_000L

    private fun app(pkg: String, installedDaysAgo: Int, label: String = pkg) = InstalledApp(
        packageName = pkg,
        label = label,
        versionName = "1.0",
        firstInstallTime = now - installedDaysAgo * DAY_MILLIS,
        lastUpdateTime = now - installedDaysAgo * DAY_MILLIS,
        installerPackage = InstalledApp.PLAY_STORE_PACKAGE,
        apkBytes = 10_000_000,
        storageUuid = null,
        uid = 10_000,
    )

    private fun daysAgo(days: Int) = now - days * DAY_MILLIS

    @Test
    fun `app not used within the threshold and installed long ago is unused`() {
        val result = UnusedRule.find(listOf(app("a", 400)), mapOf("a" to daysAgo(61)), emptySet(), 60, now)
        assertEquals(listOf("a"), result.map { it.app.packageName })
        assertEquals(daysAgo(61), result.single().lastUsedAt)
    }

    @Test
    fun `app used within the threshold is not unused`() {
        val result = UnusedRule.find(listOf(app("a", 400)), mapOf("a" to daysAgo(59)), emptySet(), 60, now)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `exactly at the threshold counts as unused`() {
        val result = UnusedRule.find(listOf(app("a", 60)), mapOf("a" to daysAgo(60)), emptySet(), 60, now)
        assertEquals(1, result.size)
    }

    @Test
    fun `recently installed app is never unused even without any usage record`() {
        val result = UnusedRule.find(listOf(app("new", 7)), emptyMap(), emptySet(), 60, now)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `no usage record means unused with a null last-used time`() {
        val result = UnusedRule.find(listOf(app("a", 400)), emptyMap(), emptySet(), 30, now)
        assertNull(result.single().lastUsedAt)
    }

    @Test
    fun `always-running packages are excluded`() {
        val apps = listOf(app("keyboard", 400), app("game", 400))
        val result = UnusedRule.find(apps, emptyMap(), setOf("keyboard"), 30, now)
        assertEquals(listOf("game"), result.map { it.app.packageName })
    }

    @Test
    fun `a last-used time in the future is treated as used today`() {
        val result = UnusedRule.find(listOf(app("a", 400)), mapOf("a" to now + 40 * DAY_MILLIS), emptySet(), 30, now)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `an install time in the future is treated as just installed`() {
        val future = app("a", 0).copy(firstInstallTime = now + DAY_MILLIS)
        assertTrue(UnusedRule.find(listOf(future), emptyMap(), emptySet(), 30, now).isEmpty())
    }

    @Test
    fun `threshold changes the answer from the same timestamps`() {
        val apps = listOf(app("a", 400))
        val lastUsed = mapOf("a" to daysAgo(45))
        assertEquals(1, UnusedRule.find(apps, lastUsed, emptySet(), 30, now).size)
        assertEquals(0, UnusedRule.find(apps, lastUsed, emptySet(), 60, now).size)
        assertEquals(0, UnusedRule.find(apps, lastUsed, emptySet(), 90, now).size)
    }

    @Test
    fun `sorted least recently used first with no-record apps leading`() {
        val apps = listOf(app("recent", 400), app("never", 400, label = "Zed"), app("old", 400), app("never2", 400, label = "Alpha"))
        val lastUsed = mapOf("recent" to daysAgo(100), "old" to daysAgo(300))
        val result = UnusedRule.find(apps, lastUsed, emptySet(), 60, now)
        assertEquals(listOf("never2", "never", "old", "recent"), result.map { it.app.packageName })
    }

    @Test
    fun `reduce keeps the latest of lastTimeUsed and lastTimeVisible across buckets`() {
        val records = listOf(
            UsageRecord("a", lastTimeUsed = daysAgo(200), lastTimeVisible = daysAgo(150), bucketStart = daysAgo(700)),
            UsageRecord("a", lastTimeUsed = daysAgo(90), lastTimeVisible = 0, bucketStart = daysAgo(300)),
            UsageRecord("b", lastTimeUsed = 0, lastTimeVisible = daysAgo(10), bucketStart = daysAgo(300)),
        )
        val snapshot = UnusedRule.reduce(records, now)
        assertEquals(daysAgo(90), snapshot.lastUsed["a"])
        assertEquals(daysAgo(10), snapshot.lastUsed["b"])
        assertEquals(daysAgo(700), snapshot.windowStart)
    }

    @Test
    fun `reduce clamps future timestamps to now and ignores empty records`() {
        val records = listOf(
            UsageRecord("future", lastTimeUsed = now + 5 * DAY_MILLIS, lastTimeVisible = 0, bucketStart = daysAgo(10)),
            UsageRecord("empty", lastTimeUsed = 0, lastTimeVisible = 0, bucketStart = daysAgo(10)),
        )
        val snapshot = UnusedRule.reduce(records, now)
        assertEquals(now, snapshot.lastUsed["future"])
        assertTrue("empty" !in snapshot.lastUsed)
    }

    @Test
    fun `window start falls back to the two-year retention when there are no buckets`() {
        assertEquals(now - UnusedRule.RETENTION_MILLIS, UnusedRule.reduce(emptyList(), now).windowStart)
    }

    @Test
    fun `component lists parse to package names`() {
        val value = "com.a.keyboard/.Service:com.b.helper/com.b.helper.Access: :"
        assertEquals(setOf("com.a.keyboard", "com.b.helper"), UnusedRule.packagesFromComponentList(value))
        assertTrue(UnusedRule.packagesFromComponentList(null).isEmpty())
    }
}

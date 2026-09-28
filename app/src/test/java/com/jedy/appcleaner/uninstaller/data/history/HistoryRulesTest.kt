package com.jedy.appcleaner.uninstaller.data.history

import com.jedy.appcleaner.uninstaller.core.format.DAY_MILLIS
import com.jedy.appcleaner.uninstaller.data.local.UninstallHistoryEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class HistoryRulesTest {

    private val zone = ZoneOffset.UTC
    private val now = LocalDateTime.of(2026, 9, 28, 9, 30).toInstant(zone).toEpochMilli()

    @Test
    fun `groups by local calendar day, newest first`() {
        val todayEarly = at(2026, 9, 28, 0, 5)
        val yesterdayLate = at(2026, 9, 27, 23, 55)
        val older = at(2026, 9, 1, 12, 0)
        val days = HistoryRules.groupByDay(
            listOf(entry(1, older), entry(2, todayEarly), entry(3, yesterdayLate), entry(4, at(2026, 9, 28, 8, 0))),
            now = now,
            zone = zone,
        )
        assertEquals(listOf(HistoryDay.Kind.TODAY, HistoryDay.Kind.YESTERDAY, HistoryDay.Kind.OTHER), days.map { it.kind })
        assertEquals(listOf(4L, 2L), days[0].entries.map { it.id })
        assertEquals(listOf(3L), days[1].entries.map { it.id })
        assertEquals(java.time.LocalDate.of(2026, 9, 1), days[2].date)
    }

    @Test
    fun `reinstall buckets follow the analytics spec`() {
        assertEquals("0", HistoryRules.daysSinceRemovedBucket(now - 60_000, now))
        assertEquals("1-7", HistoryRules.daysSinceRemovedBucket(now - DAY_MILLIS, now))
        assertEquals("1-7", HistoryRules.daysSinceRemovedBucket(now - 7 * DAY_MILLIS, now))
        assertEquals("8-30", HistoryRules.daysSinceRemovedBucket(now - 8 * DAY_MILLIS, now))
        assertEquals("8-30", HistoryRules.daysSinceRemovedBucket(now - 30 * DAY_MILLIS, now))
        assertEquals(">30", HistoryRules.daysSinceRemovedBucket(now - 31 * DAY_MILLIS, now))
        // A clock moved backwards never produces a negative bucket.
        assertEquals("0", HistoryRules.daysSinceRemovedBucket(now + DAY_MILLIS, now))
    }

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int) =
        LocalDateTime.of(y, m, d, h, min).toInstant(zone).toEpochMilli()

    private fun entry(id: Long, removedAt: Long) = UninstallHistoryEntity(
        id = id,
        packageName = "pkg.$id",
        label = "App $id",
        iconPath = null,
        bytes = 1,
        bytesIsEstimate = false,
        installerPackage = null,
        versionName = null,
        removedAt = removedAt,
    )
}

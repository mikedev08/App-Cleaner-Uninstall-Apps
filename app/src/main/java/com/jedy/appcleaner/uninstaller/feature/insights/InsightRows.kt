package com.jedy.appcleaner.uninstaller.feature.insights

import com.jedy.appcleaner.uninstaller.core.format.DAY_MILLIS
import com.jedy.appcleaner.uninstaller.core.model.AppSize
import com.jedy.appcleaner.uninstaller.core.model.InstalledApp
import com.jedy.appcleaner.uninstaller.core.model.SortOrder
import com.jedy.appcleaner.uninstaller.core.ui.theme.Severity
import com.jedy.appcleaner.uninstaller.core.ui.theme.SeverityRules

/** What both premium lists need in order to sort and filter a row. */
interface InsightRow {
    val app: InstalledApp

    /** Bytes the Size sort ranks by; null sorts last ("Size unavailable"). */
    val sortBytes: Long?

    /** Last used; null = no record in the window, which sorts as the least recent. */
    val lastUsedAt: Long?
}

/** One Unused-tab row. [notOpenedSince] is the "Not opened since at least …" date for [lastUsedAt] == null. */
data class UnusedRow(
    override val app: InstalledApp,
    override val lastUsedAt: Long?,
    val notOpenedSince: Long,
    val size: AppSize?,
) : InsightRow {
    /** Full footprint where measured, APK size otherwise — the best number we honestly have. */
    val bytes: Long get() = size?.totalBytes ?: app.apkBytes
    override val sortBytes: Long get() = bytes
}

/** One Large-tab row. [size] null = not measured (volume unavailable, or still measuring). */
data class LargeRow(
    override val app: InstalledApp,
    val size: AppSize?,
    override val lastUsedAt: Long?,
) : InsightRow {
    override val sortBytes: Long? get() = size?.totalBytes
}

/**
 * Home's sort menu writes one order per tab (PRD §4 Screen 4); the tab bodies apply it. Same
 * directions as the All tab: size largest first, name A–Z, install date oldest first, last
 * updated newest first, last used least recent first.
 */
object InsightSort {
    fun <T : InsightRow> sort(rows: List<T>, order: SortOrder): List<T> = when (order) {
        SortOrder.SIZE -> rows.sortedWith(
            compareByDescending<T> { it.sortBytes ?: Long.MIN_VALUE }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.app.label }
        )
        SortOrder.NAME -> rows.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.app.label })
        SortOrder.INSTALL_DATE -> rows.sortedBy { it.app.firstInstallTime }
        SortOrder.LAST_UPDATED -> rows.sortedByDescending { it.app.lastUpdateTime }
        SortOrder.LAST_USED -> rows.sortedWith(
            compareBy<T, Long?>(nullsFirst()) { it.lastUsedAt }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.app.label }
        )
    }

    /** Home's inline search: label and package name, case-insensitive (PRD §4 Screen 4). */
    fun <T : InsightRow> filter(rows: List<T>, query: String): List<T> {
        val q = query.trim()
        if (q.isEmpty()) return rows
        return rows.filter { it.app.label.contains(q, ignoreCase = true) || it.app.packageName.contains(q, ignoreCase = true) }
    }
}

/**
 * One row of the blurred premium preview. Only the size travels with it: the preview draws each
 * row's real size bar and severity colour (so it is visibly *their* data) but never a readable
 * name or number.
 */
data class PreviewRow(val packageName: String, val bytes: Long)

/** The Large tab's own "Total / Cache" toggle; Home's sort order still applies under [TOTAL]. */
enum class LargeSort { TOTAL, CACHE }

/** Cache held by the apps where clearing it is worth a trip to App info. */
data class CacheSummary(val bytes: Long, val apps: Int)

/** Pure Large-tab maths behind the summary card, the cache callout and the Cache sort. */
object LargeInsights {
    /**
     * Below 10 MB an app's cache is not worth opening App info for. Counting only apps above it
     * keeps "Cache: 640 MB in 12 apps" exact — the bytes are precisely those 12 apps' cache —
     * instead of "in 86 apps", which is every app and tells the user nothing.
     */
    const val NOTABLE_CACHE_BYTES = 10_000_000L

    fun cacheSummary(rows: List<LargeRow>): CacheSummary {
        val notable = rows.mapNotNull { it.size?.cacheBytes }.filter { it >= NOTABLE_CACHE_BYTES }
        return CacheSummary(notable.sum(), notable.size)
    }

    /** App / data / cache summed over every measured app; null when nothing is measured. */
    fun breakdown(rows: List<LargeRow>): AppSize? {
        val sizes = rows.mapNotNull { it.size }
        if (sizes.isEmpty()) return null
        return AppSize(
            appBytes = sizes.sumOf { it.appBytes },
            dataBytes = sizes.sumOf { it.dataBytes },
            cacheBytes = sizes.sumOf { it.cacheBytes },
            measuredAt = sizes.maxOf { it.measuredAt },
        )
    }

    /** Biggest cache first; unmeasured apps last; ties by name so the order is stable. */
    fun sortByCache(rows: List<LargeRow>): List<LargeRow> = rows.sortedWith(
        compareByDescending<LargeRow> { it.size?.cacheBytes ?: Long.MIN_VALUE }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.app.label }
    )
}

/** How loud the headline numbers are. Pure, and only ever derived from the user's real numbers. */
object InsightSeverity {
    /**
     * The Unused headline takes the worse of two honest signals: how much space the idle apps
     * hold (SeverityRules.appSize) and how long "idle" is (SeverityRules.idle at the threshold).
     * 1.3 GB unused for 60 days is red; 120 MB unused for 30 days stays amber.
     */
    fun unused(totalBytes: Long, deviceTotalBytes: Long, thresholdDays: Int, now: Long): Severity = maxOf(
        SeverityRules.appSize(totalBytes, deviceTotalBytes),
        SeverityRules.idle(now - thresholdDays * DAY_MILLIS, now),
    )

    /** "1.2 GB cache" earns a chip only when the cache alone would count as a big app. */
    fun cache(cacheBytes: Long, deviceTotalBytes: Long): Severity = SeverityRules.appSize(cacheBytes, deviceTotalBytes)
}

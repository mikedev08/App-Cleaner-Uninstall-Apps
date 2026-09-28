package com.jedy.appcleaner.uninstaller.feature.insights

import com.jedy.appcleaner.uninstaller.core.model.AppSize
import com.jedy.appcleaner.uninstaller.core.model.InstalledApp
import com.jedy.appcleaner.uninstaller.core.model.LargeApps
import com.jedy.appcleaner.uninstaller.core.model.SortOrder
import com.jedy.appcleaner.uninstaller.core.model.bestKnownBytes

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

/**
 * One Large- or Cache-filter row. [size] null = not measured (volume unavailable, or still
 * measuring); [bytes] then falls back to the APK size, exactly as `LargeApps` judges it.
 */
data class LargeRow(
    override val app: InstalledApp,
    val size: AppSize?,
    override val lastUsedAt: Long?,
) : InsightRow {
    /** Best-known size: measured app + data + cache, else APK. */
    val bytes: Long get() = app.bestKnownBytes(size)
    override val sortBytes: Long get() = bytes
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
 * row's real size bar (so it is visibly *their* data) but never a readable name or number.
 */
data class PreviewRow(val packageName: String, val bytes: Long)

/** Pure maths behind the Large and Cache filters and their summary cards. */
object LargeInsights {
    /**
     * The Large filter: exactly the apps `LargeApps.isLarge` counts on Home and Scan (best-known
     * size ≥ 250 MB), so the three numbers always agree.
     */
    fun largeRows(rows: List<LargeRow>): List<LargeRow> = rows.filter { LargeApps.isLarge(it.bytes) }

    /**
     * The Cache filter (it replaced the Large tab's Total / Cache toggle): every measured app that
     * holds any cache, biggest cache first, ties by name so the order is stable.
     */
    fun cacheRows(rows: List<LargeRow>): List<LargeRow> = rows
        .filter { (it.size?.cacheBytes ?: 0L) > 0L }
        .sortedWith(compareByDescending<LargeRow> { it.size!!.cacheBytes }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.app.label })

    fun cacheBytes(rows: List<LargeRow>): Long = rows.sumOf { it.size?.cacheBytes ?: 0L }

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

}

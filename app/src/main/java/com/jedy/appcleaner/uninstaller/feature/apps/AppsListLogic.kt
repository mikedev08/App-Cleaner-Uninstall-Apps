package com.jedy.appcleaner.uninstaller.feature.apps

import com.jedy.appcleaner.uninstaller.core.format.DAY_MILLIS
import com.jedy.appcleaner.uninstaller.core.model.AppSize
import com.jedy.appcleaner.uninstaller.core.model.HomeTab
import com.jedy.appcleaner.uninstaller.core.model.InstalledApp
import com.jedy.appcleaner.uninstaller.core.model.SortOrder
import com.jedy.appcleaner.uninstaller.core.ui.theme.Severity
import com.jedy.appcleaner.uninstaller.core.ui.theme.SeverityRules
import java.text.Collator

/** One All-tab row: the app plus the size this user is entitled to see for it. */
data class AppsRow(
    val app: InstalledApp,
    /** Total footprint for premium users on "Total" display with a measured size, else APK bytes. */
    val sizeBytes: Long,
    /** Last use (premium sort / meta only); null when unknown or not entitled. */
    val lastUsedAt: Long?,
    /** `LargeApps.isLarge(bestKnownBytes)`: the one Large rule Home and Scan count with. */
    val large: Boolean = false,
    /** Measured total when known, else APK: what [large] and the Large section total use. */
    val bestKnownBytes: Long = sizeBytes,
    /** 0..1 of the biggest app in the list, for the size bar. */
    val sizeFraction: Float = 0f,
    /** Premium idle chip ("Not opened in 3 mo"); null when not entitled or recently used. */
    val idle: IdleChip? = null,
) {
    val packageName: String get() = app.packageName
}

/** How long an app has sat unopened, already bucketed for its chip. */
data class IdleChip(val severity: Severity, val months: Int) {
    /** Past a year the months stop being useful; the chip says "over a year". */
    val overAYear: Boolean get() = months >= 12
}

/**
 * The size-sorted list splits into "Large" and "Everything else". [largeBytes] sums the
 * best-known sizes, so "Large · 9 apps · 3.1 GB" is the same figure Home and Scan show.
 */
data class AppsSections(val large: List<AppsRow>, val rest: List<AppsRow>) {
    val largeBytes: Long get() = large.sumOf { it.bestKnownBytes }
}

/**
 * Search, sort, size and chip rules for the Apps list. PRD Feature 1: sort and search are pure
 * in-memory work over the snapshot (300 apps sort in well under 16ms), so they never show a
 * spinner — which is also what makes them testable without Android.
 */
internal object AppsListLogic {

    /** PRD §4 Screen 4: search matches label and package name, case-insensitively. */
    fun matches(app: InstalledApp, query: String): Boolean {
        val needle = query.trim()
        if (needle.isEmpty()) return true
        return app.label.contains(needle, ignoreCase = true) || app.packageName.contains(needle, ignoreCase = true)
    }

    /**
     * Settings "Show app size as": Total only means something for premium users whose size has
     * been measured; everyone else sees APK bytes (the free-tier size everywhere, Feature 1).
     */
    fun displayBytes(app: InstalledApp, sizes: Map<String, AppSize>, useTotal: Boolean): Long =
        if (useTotal) sizes[app.packageName]?.totalBytes ?: app.apkBytes else app.apkBytes

    /** A persisted premium sort survives a lapsed subscription on disk but not on screen. */
    fun effectiveSort(order: SortOrder, isPremium: Boolean): SortOrder =
        if (order.isPremium && !isPremium) SortOrder.defaultFor(HomeTab.ALL) else order

    /**
     * PRD §4 Screen 4 sort menu. Ties always fall back to the label, then the package, so rows
     * never jump around between equal keys.
     */
    fun sort(rows: List<AppsRow>, order: SortOrder, collator: Comparator<String> = defaultCollator()): List<AppsRow> {
        val byLabel = compareBy<AppsRow, String>(collator) { it.app.label }.thenBy { it.packageName }
        val comparator: Comparator<AppsRow> = when (order) {
            SortOrder.SIZE -> compareByDescending<AppsRow> { it.sizeBytes }.then(byLabel)
            SortOrder.NAME -> byLabel
            SortOrder.INSTALL_DATE -> compareBy<AppsRow> { it.app.firstInstallTime }.then(byLabel)
            SortOrder.LAST_UPDATED -> compareByDescending<AppsRow> { it.app.lastUpdateTime }.then(byLabel)
            // Least recent first; no record in the retention window counts as the least recent.
            SortOrder.LAST_USED -> compareBy<AppsRow> { it.lastUsedAt ?: Long.MIN_VALUE }.then(byLabel)
        }
        return rows.sortedWith(comparator)
    }

    /** Size bar length: relative to the biggest app, so the top row always fills the bar. */
    fun sizeFraction(bytes: Long, largest: Long): Float =
        if (largest <= 0) 0f else (bytes.toDouble() / largest).toFloat().coerceIn(0f, 1f)

    /**
     * The idle chip, only from real usage data. No record inside the retention window means
     * "not opened since at least the window start" — never since before the app was installed,
     * so a fresh install is not called idle.
     */
    fun idleChip(lastUsedAt: Long?, firstInstallTime: Long, windowStart: Long, now: Long): IdleChip? {
        val since = lastUsedAt ?: maxOf(firstInstallTime, windowStart)
        if (since <= 0) return null
        val severity = SeverityRules.idle(since, now)
        if (severity == Severity.OK) return null
        val months = (((now - since).coerceAtLeast(0) / DAY_MILLIS) / DAYS_PER_MONTH).toInt().coerceAtLeast(1)
        return IdleChip(severity, months)
    }

    /**
     * "Large" (`LargeApps.isLarge`) above "Everything else". Only for the size sort, where the
     * split follows the order; other sorts would scatter the groups.
     */
    fun sections(rows: List<AppsRow>, order: SortOrder): AppsSections? {
        if (order != SortOrder.SIZE) return null
        val (large, rest) = rows.partition { it.large }
        if (large.isEmpty()) return null
        return AppsSections(large, rest)
    }

    /**
     * Preinstalled apps report the Unix epoch (or near it) as their install time, which printed
     * as "Installed Jan 1970". Anything before Android existed is treated as unknown.
     */
    fun isKnownDate(millis: Long): Boolean = millis >= KNOWN_DATE_FLOOR_MILLIS

    /** 1 Jan 2008 UTC, before the first Android phone shipped. */
    const val KNOWN_DATE_FLOOR_MILLIS = 1_199_145_600_000L

    /**
     * PRD §6 item 21: selected apps the current search hides. Counted over the whole inventory,
     * not the visible tab, because selection is one global set.
     */
    fun hiddenBySearch(selected: Set<String>, apps: Map<String, InstalledApp>, query: String): Int {
        if (query.isBlank()) return 0
        return selected.count { pkg -> apps[pkg]?.let { !matches(it, query) } ?: false }
    }

    private const val DAYS_PER_MONTH = 30

    private fun defaultCollator(): Comparator<String> =
        Collator.getInstance().apply { strength = Collator.PRIMARY }.let { collator -> Comparator { a, b -> collator.compare(a, b) } }
}

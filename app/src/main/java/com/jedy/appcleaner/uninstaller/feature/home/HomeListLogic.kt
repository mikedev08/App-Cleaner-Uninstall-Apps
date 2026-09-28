package com.jedy.appcleaner.uninstaller.feature.home

import com.jedy.appcleaner.uninstaller.core.model.AppSize
import com.jedy.appcleaner.uninstaller.core.model.DeviceStorage
import com.jedy.appcleaner.uninstaller.core.model.HomeTab
import com.jedy.appcleaner.uninstaller.core.model.InstalledApp
import com.jedy.appcleaner.uninstaller.core.model.SortOrder
import java.text.Collator

/** One All-tab row: the app plus the size this user is entitled to see for it. */
data class HomeAppRow(
    val app: InstalledApp,
    /** Total footprint for premium users on "Total" display with a measured size, else APK bytes. */
    val sizeBytes: Long,
    /** Last use (premium sort / meta only); null when unknown or not entitled. */
    val lastUsedAt: Long?,
) {
    val packageName: String get() = app.packageName
}

/** Numbers behind the Home storage card (PRD §4 Screen 4, Feature 1 "Free storage overview"). */
data class StorageSummary(
    val totalBytes: Long,
    val usedBytes: Long,
    val freeBytes: Long,
    /** Never more than [usedBytes], so the Apps and Other segments always add up to "used". */
    val appsBytes: Long,
) {
    val otherBytes: Long get() = (usedBytes - appsBytes).coerceAtLeast(0)
    val isKnown: Boolean get() = totalBytes > 0
    val usedPercent: Int get() = if (totalBytes <= 0) 0 else ((usedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
}

/**
 * Search, sort and size rules for Home. PRD Feature 1: sort and search are pure in-memory work
 * over the snapshot (300 apps sort in well under 16ms), so they never show a spinner — which is
 * also what makes them testable without Android.
 */
internal object HomeListLogic {

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
    fun sort(rows: List<HomeAppRow>, order: SortOrder, collator: Comparator<String> = defaultCollator()): List<HomeAppRow> {
        val byLabel = compareBy<HomeAppRow, String>(collator) { it.app.label }.thenBy { it.packageName }
        val comparator: Comparator<HomeAppRow> = when (order) {
            SortOrder.SIZE -> compareByDescending<HomeAppRow> { it.sizeBytes }.then(byLabel)
            SortOrder.NAME -> byLabel
            SortOrder.INSTALL_DATE -> compareBy<HomeAppRow> { it.app.firstInstallTime }.then(byLabel)
            SortOrder.LAST_UPDATED -> compareByDescending<HomeAppRow> { it.app.lastUpdateTime }.then(byLabel)
            // Least recent first; no record in the retention window counts as the least recent.
            SortOrder.LAST_USED -> compareBy<HomeAppRow> { it.lastUsedAt ?: Long.MIN_VALUE }.then(byLabel)
        }
        return rows.sortedWith(comparator)
    }

    /**
     * PRD Feature 1: "Apps" is the sum of apk_bytes for free users, and the full app + data +
     * cache total once Usage Access is granted and premium is active. An app not measured yet
     * contributes its APK bytes rather than nothing.
     */
    fun appsBytes(apps: List<InstalledApp>, sizes: Map<String, AppSize>, useMeasured: Boolean): Long =
        apps.sumOf { app -> if (useMeasured) sizes[app.packageName]?.totalBytes ?: app.apkBytes else app.apkBytes }

    fun storageSummary(device: DeviceStorage, appsBytes: Long): StorageSummary = StorageSummary(
        totalBytes = device.totalBytes.coerceAtLeast(0),
        usedBytes = device.usedBytes,
        freeBytes = device.freeBytes.coerceAtLeast(0),
        appsBytes = appsBytes.coerceIn(0, device.usedBytes),
    )

    /**
     * PRD §6 item 21: selected apps the current search hides. Counted over the whole inventory,
     * not the visible tab, because selection is one global set.
     */
    fun hiddenBySearch(selected: Set<String>, apps: Map<String, InstalledApp>, query: String): Int {
        if (query.isBlank()) return 0
        return selected.count { pkg -> apps[pkg]?.let { !matches(it, query) } ?: false }
    }

    private fun defaultCollator(): Comparator<String> =
        Collator.getInstance().apply { strength = Collator.PRIMARY }.let { collator -> Comparator { a, b -> collator.compare(a, b) } }
}

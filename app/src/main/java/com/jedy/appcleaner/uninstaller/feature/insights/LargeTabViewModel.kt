package com.jedy.appcleaner.uninstaller.feature.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jedy.appcleaner.uninstaller.core.model.AppSize
import com.jedy.appcleaner.uninstaller.core.model.HomeTab
import com.jedy.appcleaner.uninstaller.core.model.InstalledApp
import com.jedy.appcleaner.uninstaller.core.model.SortOrder
import com.jedy.appcleaner.uninstaller.core.selection.SelectionStore
import com.jedy.appcleaner.uninstaller.data.billing.Premium
import com.jedy.appcleaner.uninstaller.data.inventory.AppInventory
import com.jedy.appcleaner.uninstaller.data.prefs.AppPreferences
import com.jedy.appcleaner.uninstaller.data.storage.StorageBreakdown
import com.jedy.appcleaner.uninstaller.data.usage.InsightsSync
import com.jedy.appcleaner.uninstaller.data.usage.UsageAccess
import com.jedy.appcleaner.uninstaller.data.usage.UsageInsights
import com.jedy.appcleaner.uninstaller.di.DefaultDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * PRD §4 Screen 6. One shape serves both size filters, Large and Cache; for Cache the "bytes" are
 * cache bytes. Both are free (only Unused is Pro): there is no locked state, only the access one.
 */
sealed interface LargeContent {
    data object Loading : LargeContent

    /** Cache only: cache sizes need Usage Access, so the filter asks for it (never the paywall). */
    data object NoAccess : LargeContent

    /** Cache only: access granted but nothing could be measured (e.g. every volume unavailable). */
    data object Unavailable : LargeContent

    /**
     * @param totalBytes what the listed apps hold (Large: best-known sizes; Cache: cache bytes).
     * @param breakdown app / data / cache summed over the listed, measured apps (Large only).
     * @param needsAccess Large without Usage Access: the rows are APK sizes, and a prompt above
     *   them offers the full sizes. The count is still exactly Home's and Scan's.
     */
    data class Listed(
        val rows: List<LargeRow>,
        val totalBytes: Long,
        val measuring: Boolean,
        val breakdown: AppSize?,
        val deviceTotalBytes: Long,
        val needsAccess: Boolean = false,
    ) : LargeContent
}

/**
 * The Large and Cache filters (PRD §4 Screen 6, Feature 3; design review §2.6). Large lists
 * exactly the apps `LargeApps.isLarge` counts on Home and Scan (best-known sizes, so APK sizes
 * without Usage Access); Cache lists apps by cache size, the same set Home's and Scan's Cache row
 * count. Both are free for every user. Both filter bodies share this one ViewModel.
 */
@HiltViewModel
class LargeTabViewModel @Inject constructor(
    private val access: UsageAccess,
    insights: UsageInsights,
    private val storage: StorageBreakdown,
    sync: InsightsSync,
    inventory: AppInventory,
    premium: Premium,
    preferences: AppPreferences,
    private val selection: SelectionStore,
    @param:DefaultDispatcher dispatcher: CoroutineDispatcher,
) : ViewModel() {

    val selected: StateFlow<Set<String>> = selection.selected

    /** The phone's capacity does not change while we run: read it once, off the main thread. */
    private val deviceTotalBytes by lazy { storage.deviceStorage().totalBytes }

    private val settings = combine(
        access.isGranted,
        premium.isPremium,
        preferences.sortOrder(HomeTab.LARGE),
        inventory.isInitialLoading,
    ) { granted, isPremium, order, loading -> Settings(granted, isPremium, order, loading) }

    private val progress = combine(sync.sizesMeasured, sync.sizesRefreshing) { measured, refreshing ->
        Progress(measured, refreshing)
    }

    private val snapshot = combine(
        settings,
        progress,
        inventory.apps,
        storage.sizes,
        insights.lastUsed,
    ) { s, p, apps, sizes, lastUsed -> Snapshot(s, p, apps, sizes, lastUsed) }

    val uiState: StateFlow<LargeContent> = snapshot
        .map { buildLarge(it) }
        .flowOn(dispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), LargeContent.Loading)

    val cacheState: StateFlow<LargeContent> = snapshot
        .map { buildCache(it) }
        .flowOn(dispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), LargeContent.Loading)

    /** Still reading: the inventory, or (with access) an empty size cache before its first pass. */
    private fun isLoading(snap: Snapshot): Boolean {
        val (s, p, _, sizes) = snap
        if (s.inventoryLoading) return true
        // Room-cached sizes render at once; an empty cache waits for the first measuring pass.
        return s.granted && sizes.isEmpty() && (!p.measured || p.refreshing)
    }

    private fun rowsOf(snap: Snapshot): List<LargeRow> =
        snap.apps.map { LargeRow(it, snap.sizes[it.packageName], snap.lastUsed[it.packageName]) }

    private fun buildLarge(snap: Snapshot): LargeContent {
        if (isLoading(snap)) return LargeContent.Loading
        val large = LargeInsights.largeRows(rowsOf(snap))
        // A premium sort ("last used") left on disk after Pro ended falls back to size.
        val order = snap.settings.order.takeUnless { it.isPremium && !snap.settings.isPremium } ?: SortOrder.SIZE
        return LargeContent.Listed(
            rows = InsightSort.sort(large, order),
            totalBytes = large.sumOf { it.bytes },
            measuring = snap.settings.granted && snap.progress.refreshing,
            breakdown = LargeInsights.breakdown(large),
            deviceTotalBytes = deviceTotalBytes,
            needsAccess = !snap.settings.granted,
        )
    }

    private fun buildCache(snap: Snapshot): LargeContent {
        if (!snap.settings.granted) return LargeContent.NoAccess
        if (isLoading(snap)) return LargeContent.Loading
        val all = rowsOf(snap)
        if (all.none { it.size != null }) return LargeContent.Unavailable
        val cached = LargeInsights.cacheRows(all)
        return LargeContent.Listed(
            rows = cached,
            totalBytes = LargeInsights.cacheBytes(cached),
            measuring = snap.progress.refreshing,
            breakdown = null,
            deviceTotalBytes = deviceTotalBytes,
        )
    }

    fun onToggle(packageName: String) = selection.toggle(packageName)

    fun onSelectAll(packages: List<String>, select: Boolean) {
        if (select) selection.select(packages) else selection.deselect(packages)
    }

    fun onResume() = access.recheck()

    private data class Settings(
        val granted: Boolean,
        val isPremium: Boolean,
        val order: SortOrder,
        val inventoryLoading: Boolean,
    )

    private data class Progress(val measured: Boolean, val refreshing: Boolean)

    private data class Snapshot(
        val settings: Settings,
        val progress: Progress,
        val apps: List<InstalledApp>,
        val sizes: Map<String, AppSize>,
        val lastUsed: Map<String, Long>,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

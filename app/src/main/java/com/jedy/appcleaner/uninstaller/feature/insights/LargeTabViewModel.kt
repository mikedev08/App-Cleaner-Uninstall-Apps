package com.jedy.appcleaner.uninstaller.feature.insights

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jedy.appcleaner.uninstaller.core.analytics.Analytics
import com.jedy.appcleaner.uninstaller.core.analytics.AnalyticsEvent
import com.jedy.appcleaner.uninstaller.core.format.bytesBucket
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

/** PRD §4 Screen 6: the same three access states as the Unused tab. */
sealed interface LargeContent {
    data object Loading : LargeContent
    data object NoAccess : LargeContent

    /** Access granted but nothing could be measured (e.g. every volume unavailable). */
    data object Unavailable : LargeContent

    /**
     * Free user: "Your 10 largest apps use 18.7 GB", rows redacted. [deviceTotalBytes] turns the
     * number into a share of the phone ("That's 15% of your storage") — a real ratio, not a scare.
     */
    data class Locked(
        val topCount: Int,
        val topBytes: Long,
        val preview: List<PreviewRow>,
        val deviceTotalBytes: Long,
    ) : LargeContent

    /**
     * @param breakdown app / data / cache summed over every measured app, for the summary legend.
     * @param cache the cache callout ("Cache: 640 MB in 12 apps").
     * @param sort the tab's own Total / Cache toggle, already applied to [rows].
     */
    data class Unlocked(
        val rows: List<LargeRow>,
        val totalBytes: Long,
        val measuring: Boolean,
        val breakdown: AppSize?,
        val cache: CacheSummary,
        val deviceTotalBytes: Long,
        val sort: LargeSort,
    ) : LargeContent
}

/**
 * Large tab (PRD §4 Screen 6, Feature 3): apps ranked by full footprint (app + data + cache).
 * Sizes are measured for every user who granted access; premium only unlocks the rows.
 *
 * The Total / Cache toggle lives in [SavedStateHandle] rather than AppPreferences: it is a way
 * of looking at the list right now ("which apps hoard cache?"), not a setting worth persisting.
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
    private val analytics: Analytics,
    private val savedState: SavedStateHandle,
    @param:DefaultDispatcher dispatcher: CoroutineDispatcher,
) : ViewModel() {

    val selected: StateFlow<Set<String>> = selection.selected

    /** The phone's capacity does not change while we run: read it once, off the main thread. */
    private val deviceTotalBytes by lazy { storage.deviceStorage().totalBytes }

    private val sort = savedState.getStateFlow(KEY_SORT, LargeSort.TOTAL.name)
        .map { name -> LargeSort.entries.firstOrNull { it.name == name } ?: LargeSort.TOTAL }

    private val settings = combine(
        access.isGranted,
        premium.isPremium,
        preferences.sortOrder(HomeTab.LARGE),
        inventory.isInitialLoading,
        sort,
    ) { granted, isPremium, order, loading, sort -> Settings(granted, isPremium, order, loading, sort) }

    private val progress = combine(sync.sizesMeasured, sync.sizesRefreshing) { measured, refreshing ->
        Progress(measured, refreshing)
    }

    val uiState: StateFlow<LargeContent> = combine(
        settings,
        progress,
        inventory.apps,
        storage.sizes,
        insights.lastUsed,
    ) { s, p, apps, sizes, lastUsed -> build(s, p, apps, sizes, lastUsed) }
        .flowOn(dispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), LargeContent.Loading)

    private fun build(
        s: Settings,
        p: Progress,
        apps: List<InstalledApp>,
        sizes: Map<String, AppSize>,
        lastUsed: Map<String, Long>,
    ): LargeContent {
        if (!s.granted) return LargeContent.NoAccess
        if (s.inventoryLoading) return LargeContent.Loading
        // Room-cached sizes render at once; an empty cache waits for the first measuring pass.
        if (sizes.isEmpty() && (!p.measured || p.refreshing)) return LargeContent.Loading

        val rows = apps.map { LargeRow(it, sizes[it.packageName], lastUsed[it.packageName]) }
        val ranked = rows.filter { it.size != null }.sortedByDescending { it.size!!.totalBytes }
        if (ranked.isEmpty()) return LargeContent.Unavailable
        return if (s.isPremium) {
            val sorted = when (s.sort) {
                LargeSort.TOTAL -> InsightSort.sort(rows, s.order)
                LargeSort.CACHE -> LargeInsights.sortByCache(rows)
            }
            LargeContent.Unlocked(
                rows = sorted,
                totalBytes = ranked.sumOf { it.size!!.totalBytes },
                measuring = p.refreshing,
                breakdown = LargeInsights.breakdown(rows),
                cache = LargeInsights.cacheSummary(rows),
                deviceTotalBytes = deviceTotalBytes,
                sort = s.sort,
            )
        } else {
            val top = ranked.take(TEASER_TOP)
            LargeContent.Locked(
                topCount = top.size,
                topBytes = top.sumOf { it.size!!.totalBytes },
                preview = top.take(PREVIEW_ROWS).map { PreviewRow(it.app.packageName, it.size!!.totalBytes) },
                deviceTotalBytes = deviceTotalBytes,
            )
        }
    }

    fun onSortSelected(sort: LargeSort) {
        savedState[KEY_SORT] = sort.name
    }

    fun onToggle(packageName: String) = selection.toggle(packageName)

    fun onSelectAll(packages: List<String>, select: Boolean) {
        if (select) selection.select(packages) else selection.deselect(packages)
    }

    fun onResume() = access.recheck()

    /** PRD §9 premium_teaser_viewed, once per entry into the blurred state. */
    fun onTeaserViewed() {
        val content = uiState.value as? LargeContent.Locked ?: return
        analytics.log(AnalyticsEvent.PremiumTeaserViewed("large", content.topCount, bytesBucket(content.topBytes)))
    }

    private data class Settings(
        val granted: Boolean,
        val isPremium: Boolean,
        val order: SortOrder,
        val inventoryLoading: Boolean,
        val sort: LargeSort,
    )

    private data class Progress(val measured: Boolean, val refreshing: Boolean)

    private companion object {
        const val TEASER_TOP = 10
        const val PREVIEW_ROWS = 5
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val KEY_SORT = "large_sort"
    }
}

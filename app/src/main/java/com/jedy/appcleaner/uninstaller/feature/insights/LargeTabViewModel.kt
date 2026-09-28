package com.jedy.appcleaner.uninstaller.feature.insights

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

/**
 * PRD §4 Screen 6: the same three access states as the Unused tab. One shape serves both size
 * filters, Large and Cache; for Cache the "bytes" are cache bytes.
 */
sealed interface LargeContent {
    data object Loading : LargeContent
    data object NoAccess : LargeContent

    /** Access granted but nothing could be measured (e.g. every volume unavailable). */
    data object Unavailable : LargeContent

    /**
     * Free user: the real count and bytes in clear ("9 apps · 3.1 GB"), rows redacted.
     * [deviceTotalBytes] turns the number into a share of the phone ("That's 2% of your storage").
     */
    data class Locked(
        val count: Int,
        val bytes: Long,
        val preview: List<PreviewRow>,
        val deviceTotalBytes: Long,
    ) : LargeContent

    /**
     * @param totalBytes what the listed apps hold (Large: best-known sizes; Cache: cache bytes).
     * @param breakdown app / data / cache summed over the listed, measured apps (Large only).
     */
    data class Unlocked(
        val rows: List<LargeRow>,
        val totalBytes: Long,
        val measuring: Boolean,
        val breakdown: AppSize?,
        val deviceTotalBytes: Long,
    ) : LargeContent
}

/**
 * The Large and Cache filters (PRD §4 Screen 6, Feature 3; design review §2.6). Large lists
 * exactly the apps `LargeApps.isLarge` counts on Home and Scan; Cache lists apps by cache size
 * (it replaced the old Total / Cache toggle). Sizes are measured for every user who granted
 * access; premium only unlocks the rows. Both filter bodies share this one ViewModel.
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

    /** The access / loading gates both filters share; null = go on and build the rows. */
    private fun gate(snap: Snapshot): LargeContent? {
        val (s, p, _, sizes) = snap
        if (!s.granted) return LargeContent.NoAccess
        if (s.inventoryLoading) return LargeContent.Loading
        // Room-cached sizes render at once; an empty cache waits for the first measuring pass.
        if (sizes.isEmpty() && (!p.measured || p.refreshing)) return LargeContent.Loading
        return null
    }

    private fun rowsOf(snap: Snapshot): List<LargeRow> =
        snap.apps.map { LargeRow(it, snap.sizes[it.packageName], snap.lastUsed[it.packageName]) }

    private fun buildLarge(snap: Snapshot): LargeContent {
        gate(snap)?.let { return it }
        val all = rowsOf(snap)
        if (all.none { it.size != null }) return LargeContent.Unavailable
        val large = LargeInsights.largeRows(all)
        val total = large.sumOf { it.bytes }
        return if (snap.settings.isPremium) {
            LargeContent.Unlocked(
                rows = InsightSort.sort(large, snap.settings.order),
                totalBytes = total,
                measuring = snap.progress.refreshing,
                breakdown = LargeInsights.breakdown(large),
                deviceTotalBytes = deviceTotalBytes,
            )
        } else {
            LargeContent.Locked(
                count = large.size,
                bytes = total,
                preview = large.sortedByDescending { it.bytes }.take(PREVIEW_ROWS).map { PreviewRow(it.app.packageName, it.bytes) },
                deviceTotalBytes = deviceTotalBytes,
            )
        }
    }

    private fun buildCache(snap: Snapshot): LargeContent {
        gate(snap)?.let { return it }
        val all = rowsOf(snap)
        if (all.none { it.size != null }) return LargeContent.Unavailable
        val cached = LargeInsights.cacheRows(all)
        val total = LargeInsights.cacheBytes(cached)
        return if (snap.settings.isPremium) {
            LargeContent.Unlocked(
                rows = cached,
                totalBytes = total,
                measuring = snap.progress.refreshing,
                breakdown = null,
                deviceTotalBytes = deviceTotalBytes,
            )
        } else {
            LargeContent.Locked(
                count = cached.size,
                bytes = total,
                preview = cached.take(PREVIEW_ROWS).map { PreviewRow(it.app.packageName, it.size!!.cacheBytes) },
                deviceTotalBytes = deviceTotalBytes,
            )
        }
    }

    fun onToggle(packageName: String) = selection.toggle(packageName)

    fun onSelectAll(packages: List<String>, select: Boolean) {
        if (select) selection.select(packages) else selection.deselect(packages)
    }

    fun onResume() = access.recheck()

    /** PRD §9 premium_teaser_viewed, once per entry into the blurred state. */
    fun onTeaserViewed() {
        val content = uiState.value as? LargeContent.Locked ?: return
        analytics.log(AnalyticsEvent.PremiumTeaserViewed("large", content.count, bytesBucket(content.bytes)))
    }

    fun onCacheTeaserViewed() {
        val content = cacheState.value as? LargeContent.Locked ?: return
        analytics.log(AnalyticsEvent.PremiumTeaserViewed("cache", content.count, bytesBucket(content.bytes)))
    }

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
        const val PREVIEW_ROWS = 5
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

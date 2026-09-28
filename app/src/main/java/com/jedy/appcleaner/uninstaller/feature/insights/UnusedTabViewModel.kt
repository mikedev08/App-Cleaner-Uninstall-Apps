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
import com.jedy.appcleaner.uninstaller.core.model.UNUSED_THRESHOLD_DAYS
import com.jedy.appcleaner.uninstaller.core.selection.SelectionStore
import com.jedy.appcleaner.uninstaller.data.billing.Premium
import com.jedy.appcleaner.uninstaller.data.inventory.AppInventory
import com.jedy.appcleaner.uninstaller.data.prefs.AppPreferences
import com.jedy.appcleaner.uninstaller.data.storage.StorageBreakdown
import com.jedy.appcleaner.uninstaller.data.usage.RecentSetup
import com.jedy.appcleaner.uninstaller.data.usage.UsageAccess
import com.jedy.appcleaner.uninstaller.data.usage.UsageInsights
import com.jedy.appcleaner.uninstaller.di.DefaultDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UnusedUiState(
    val thresholdDays: Int = UNUSED_THRESHOLD_DAYS,
    val content: UnusedContent = UnusedContent.Loading,
)

/** PRD §4 Screen 5, states in order: (a) [NoAccess], (b) [Locked], (c) [Unlocked]. */
sealed interface UnusedContent {
    data object Loading : UnusedContent
    data object NoAccess : UnusedContent

    /**
     * Free user with access: the real count and bytes in clear, rows redacted. [deviceTotalBytes]
     * lets the UI colour the number through `SeverityRules`, so it is red only when the space
     * really is large for this phone.
     */
    data class Locked(
        val count: Int,
        val totalBytes: Long,
        val preview: List<PreviewRow>,
        val deviceTotalBytes: Long,
        val now: Long,
        /** Nothing found and the phone looks just set up ([RecentSetup]): explain the empty list. */
        val recentlySetUp: Boolean = false,
    ) : UnusedContent

    /** [now] is the instant the rule ran, so the idle chips agree with the rule that picked the rows. */
    data class Unlocked(
        val rows: List<UnusedRow>,
        val totalBytes: Long,
        val deviceTotalBytes: Long,
        val now: Long,
        val recentlySetUp: Boolean = false,
    ) : UnusedContent
}

/**
 * Unused tab (PRD §4 Screen 5, Feature 3). The rule runs for every user who granted access —
 * the entitlement only decides whether rows render in clear (PRD §0 decision 3). The threshold is
 * the fixed [UNUSED_THRESHOLD_DAYS].
 */
@HiltViewModel
class UnusedTabViewModel @Inject constructor(
    private val access: UsageAccess,
    private val insights: UsageInsights,
    private val storage: StorageBreakdown,
    inventory: AppInventory,
    premium: Premium,
    private val preferences: AppPreferences,
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
        preferences.sortOrder(HomeTab.UNUSED),
        inventory.isInitialLoading,
    ) { granted, isPremium, order, loading -> Settings(granted, isPremium, UNUSED_THRESHOLD_DAYS, order, loading) }

    val uiState: StateFlow<UnusedUiState> = combine(
        settings,
        inventory.apps,
        insights.lastUsed,
        insights.windowStart,
        storage.sizes,
    ) { s, apps, _, windowStart, sizes -> UnusedUiState(s.threshold, build(s, apps, windowStart, sizes)) }
        .flowOn(dispatcher)
        .stateIn(viewModelScope, SharingStarted.Eagerly, UnusedUiState())

    init {
        // PRD §9 unused_apps_found: whenever the count changes.
        viewModelScope.launch {
            uiState
                .mapNotNull { state ->
                    when (val content = state.content) {
                        is UnusedContent.Locked -> content.count to state.thresholdDays
                        is UnusedContent.Unlocked -> content.rows.size to state.thresholdDays
                        else -> null
                    }
                }
                .distinctUntilChanged()
                .collect { (count, days) -> analytics.log(AnalyticsEvent.UnusedAppsFound(count, days)) }
        }
    }

    private fun build(s: Settings, apps: List<InstalledApp>, windowStart: Long, sizes: Map<String, AppSize>): UnusedContent {
        if (!s.granted) return UnusedContent.NoAccess
        // windowStart is 0 until the first read of usage data after the grant.
        if (windowStart == 0L || s.inventoryLoading) return UnusedContent.Loading
        val now = System.currentTimeMillis()
        val rows = insights.unusedApps(apps, s.threshold, now).map { unused ->
            UnusedRow(
                app = unused.app,
                lastUsedAt = unused.lastUsedAt,
                // "Not opened since at least <date>": never earlier than the install itself.
                notOpenedSince = maxOf(windowStart, unused.app.firstInstallTime),
                size = sizes[unused.app.packageName],
            )
        }
        val total = rows.sumOf { it.bytes }
        // PRD §6 "Apps restored to a new phone": only worth working out when there is nothing to show.
        val recentlySetUp = rows.isEmpty() && RecentSetup.looksRecentlySetUp(apps, now, s.threshold)
        return if (s.isPremium) {
            UnusedContent.Unlocked(InsightSort.sort(rows, s.order), total, deviceTotalBytes, now, recentlySetUp)
        } else {
            // The biggest offenders go in the preview, so its bars are visibly long and red.
            val preview = rows.sortedByDescending { it.bytes }.take(PREVIEW_ROWS).map { PreviewRow(it.app.packageName, it.bytes) }
            UnusedContent.Locked(rows.size, total, preview, deviceTotalBytes, now, recentlySetUp)
        }
    }

    fun onToggle(packageName: String) = selection.toggle(packageName)

    fun onSelectAll(packages: List<String>, select: Boolean) {
        if (select) selection.select(packages) else selection.deselect(packages)
    }

    /** PRD §6 item 12: a revoked grant must show on the next resume. */
    fun onResume() = access.recheck()

    /** PRD §9 premium_teaser_viewed, once per entry into the blurred state. */
    fun onTeaserViewed() {
        val content = uiState.value.content as? UnusedContent.Locked ?: return
        analytics.log(AnalyticsEvent.PremiumTeaserViewed("unused", content.count, bytesBucket(content.totalBytes)))
    }

    private data class Settings(
        val granted: Boolean,
        val isPremium: Boolean,
        val threshold: Int,
        val order: SortOrder,
        val inventoryLoading: Boolean,
    )

    private companion object {
        const val PREVIEW_ROWS = 5
    }
}

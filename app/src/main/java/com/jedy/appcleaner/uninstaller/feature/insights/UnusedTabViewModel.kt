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
    val thresholdDays: Int = AppPreferences.DEFAULT_UNUSED_DAYS,
    val content: UnusedContent = UnusedContent.Loading,
)

/** PRD §4 Screen 5, states in order: (a) [NoAccess], (b) [Locked], (c) [Unlocked]. */
sealed interface UnusedContent {
    data object Loading : UnusedContent
    data object NoAccess : UnusedContent

    /** Free user with access: the real count in clear, rows redacted. */
    data class Locked(val count: Int, val totalBytes: Long, val previewPackages: List<String>) : UnusedContent

    data class Unlocked(val rows: List<UnusedRow>, val totalBytes: Long) : UnusedContent
}

/**
 * Unused tab (PRD §4 Screen 5, Feature 3). The rule runs for every user who granted access —
 * the entitlement only decides whether rows render in clear (PRD §0 decision 3). Switching the
 * threshold recomputes from the cached timestamps, so the chips answer instantly.
 */
@HiltViewModel
class UnusedTabViewModel @Inject constructor(
    private val access: UsageAccess,
    private val insights: UsageInsights,
    storage: StorageBreakdown,
    inventory: AppInventory,
    premium: Premium,
    private val preferences: AppPreferences,
    private val selection: SelectionStore,
    private val analytics: Analytics,
    @param:DefaultDispatcher dispatcher: CoroutineDispatcher,
) : ViewModel() {

    val selected: StateFlow<Set<String>> = selection.selected

    private val settings = combine(
        access.isGranted,
        premium.isPremium,
        preferences.unusedThresholdDays,
        preferences.sortOrder(HomeTab.UNUSED),
        inventory.isInitialLoading,
    ) { granted, isPremium, threshold, order, loading -> Settings(granted, isPremium, threshold, order, loading) }

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
        // PRD §9 unused_apps_found: whenever the count or the threshold changes.
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
        val rows = insights.unusedApps(apps, s.threshold, System.currentTimeMillis()).map { unused ->
            UnusedRow(
                app = unused.app,
                lastUsedAt = unused.lastUsedAt,
                // "Not opened since at least <date>": never earlier than the install itself.
                notOpenedSince = maxOf(windowStart, unused.app.firstInstallTime),
                size = sizes[unused.app.packageName],
            )
        }
        val total = rows.sumOf { it.bytes }
        return if (s.isPremium) {
            UnusedContent.Unlocked(InsightSort.sort(rows, s.order), total)
        } else {
            UnusedContent.Locked(rows.size, total, rows.take(PREVIEW_ROWS).map { it.app.packageName })
        }
    }

    fun onThresholdSelected(days: Int) {
        viewModelScope.launch { preferences.setUnusedThresholdDays(days) }
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

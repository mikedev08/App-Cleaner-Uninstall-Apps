package com.jedy.appcleaner.uninstaller.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jedy.appcleaner.uninstaller.core.analytics.Analytics
import com.jedy.appcleaner.uninstaller.core.analytics.AnalyticsEvent
import com.jedy.appcleaner.uninstaller.core.model.AppSize
import com.jedy.appcleaner.uninstaller.core.model.DeviceStorage
import com.jedy.appcleaner.uninstaller.core.model.HomeTab
import com.jedy.appcleaner.uninstaller.core.model.InstalledApp
import com.jedy.appcleaner.uninstaller.core.model.UNUSED_THRESHOLD_DAYS
import com.jedy.appcleaner.uninstaller.core.selection.SelectionStore
import com.jedy.appcleaner.uninstaller.core.ui.theme.Severity
import com.jedy.appcleaner.uninstaller.core.ui.theme.SeverityRules
import com.jedy.appcleaner.uninstaller.data.billing.Premium
import com.jedy.appcleaner.uninstaller.data.inventory.AppInventory
import com.jedy.appcleaner.uninstaller.data.scan.CleanupScan
import com.jedy.appcleaner.uninstaller.data.scan.ScanMath
import com.jedy.appcleaner.uninstaller.data.scan.ScanResult
import com.jedy.appcleaner.uninstaller.data.storage.StorageBreakdown
import com.jedy.appcleaner.uninstaller.data.usage.UsageAccess
import com.jedy.appcleaner.uninstaller.data.usage.UsageInsights
import com.jedy.appcleaner.uninstaller.di.DefaultDispatcher
import com.jedy.appcleaner.uninstaller.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Everything the Home dashboard renders. Every figure is a real measurement (see [ScanMath]). */
data class HomeUiState(
    /** Null until the first off-main-thread read. Total 0 = the volume could not be read. */
    val storage: DeviceStorage? = null,
    /** The last completed scan (this session or persisted); null before the first one. */
    val lastScan: ScanResult? = null,
    /**
     * The category numbers: [lastScan] when there is one (and it is not a quick scan the user has
     * since outgrown by granting Usage Access), else the same maths over live data, so a brand-new
     * user still sees their real large apps before scanning.
     */
    val summary: ScanResult? = null,
    /** Unused · Large · Cache, from [summary] (placeholders while it loads). */
    val categories: List<CategoryRow> = HomeCategories.rows(null, isPremium = false),
    val isPremium: Boolean = false,
    val hasUsageAccess: Boolean = false,
    val showPremiumEnded: Boolean = false,
    val isInventoryLoading: Boolean = true,
) {
    val usedFraction: Float get() = storage?.let(ScanMath::usedFraction) ?: 0f
    val storageSeverity: Severity get() = if (storage == null || storage.totalBytes <= 0) Severity.OK else SeverityRules.storage(usedFraction)

    /** The hero's "You can free up X": the numbers under it, once there has been a scan. */
    val heroResult: ScanResult? get() = if (lastScan == null) null else summary ?: lastScan

    /** Where the hero's "Review" goes. */
    val reviewTab: HomeTab get() = heroResult?.let { HomeCategories.reviewTab(it, isPremium) } ?: HomeTab.ALL
}

/**
 * The Home dashboard (design review §2.6): the storage gauge, one state-driven scan card and the
 * Unused · Large · Cache rows. The list itself lives on Apps and lifetime totals on History; this
 * ViewModel only reads and summarises.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val inventory: AppInventory,
    private val storage: StorageBreakdown,
    private val usageAccess: UsageAccess,
    private val usageInsights: UsageInsights,
    private val premium: Premium,
    private val scan: CleanupScan,
    private val selection: SelectionStore,
    private val analytics: Analytics,
    private val session: HomeSession,
    private val premiumLapse: PremiumLapse,
    @param:IoDispatcher private val io: CoroutineDispatcher,
    @param:DefaultDispatcher private val default: CoroutineDispatcher,
) : ViewModel() {

    private val deviceStorage = MutableStateFlow<DeviceStorage?>(null)

    /** Reminder deep link / Result screen: Home forwards the request to the Apps screen. */
    val requestedTab = selection.requestedTab

    private data class Live(
        val apps: List<InstalledApp>,
        val loading: Boolean,
        val sizes: Map<String, AppSize>,
        val lastUsed: Map<String, Long>,
    )
    private data class Entitlement(val isPremium: Boolean, val hasAccess: Boolean, val premiumEnded: Boolean)

    val uiState: StateFlow<HomeUiState> = combine(
        combine(
            inventory.apps,
            inventory.isInitialLoading,
            storage.sizes,
            usageInsights.lastUsed,
            ::Live,
        ),
        combine(premium.isPremium, usageAccess.isGranted, premiumLapse.showBanner, ::Entitlement),
        deviceStorage,
        scan.result,
    ) { live, entitlement, device, lastScan ->
        build(live, entitlement, device, lastScan)
    }.flowOn(default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        // Free space moves whenever an app comes or goes.
        viewModelScope.launch { inventory.apps.collect { refreshDeviceStorage() } }
        logHomeViewedOnce()
    }

    private fun build(
        live: Live,
        entitlement: Entitlement,
        device: DeviceStorage?,
        lastScan: ScanResult?,
    ): HomeUiState {
        // A quick scan cannot know what is unused; once access is granted, live numbers are better.
        val scanned = lastScan?.takeIf { it.hasUsageAccess || !entitlement.hasAccess }
        val summary = scanned ?: device?.takeIf { live.apps.isNotEmpty() }?.let {
            ScanMath.compute(
                apps = live.apps,
                // lastUsed feeds unusedApps(); an empty map means it has not loaded yet.
                unused = if (entitlement.hasAccess && live.lastUsed.isNotEmpty()) usageInsights.unusedApps(live.apps, UNUSED_THRESHOLD_DAYS) else emptyList(),
                sizes = live.sizes,
                storage = it,
                thresholdDays = UNUSED_THRESHOLD_DAYS,
                hasUsageAccess = entitlement.hasAccess,
                now = System.currentTimeMillis(),
            )
        }
        return HomeUiState(
            storage = device,
            lastScan = lastScan,
            summary = summary,
            categories = HomeCategories.rows(summary, entitlement.isPremium),
            isPremium = entitlement.isPremium,
            hasUsageAccess = entitlement.hasAccess,
            showPremiumEnded = entitlement.premiumEnded,
            isInventoryLoading = live.loading && live.apps.isEmpty(),
        )
    }

    /** PRD §6 item 12: Usage Access is re-read on every resume; free space may have moved too. */
    fun onResume() {
        usageAccess.recheck()
        refreshDeviceStorage()
    }

    fun onDismissPremiumEnded() = premiumLapse.dismiss()

    fun consumeTabRequest() = selection.consumeTabRequest()

    private fun refreshDeviceStorage() {
        viewModelScope.launch {
            deviceStorage.value = withContext(io) {
                runCatching { storage.deviceStorage() }.getOrDefault(DeviceStorage(totalBytes = 0, freeBytes = 0))
            }
        }
    }

    /** PRD §9 `home_viewed`, once per session, after the numbers it reports are real. */
    private fun logHomeViewedOnce() {
        viewModelScope.launch {
            val ready = combine(uiState, premiumLapse.settled, ::Pair)
                .first { (state, settled) -> settled && !state.isInventoryLoading && state.storage != null }
                .first
            if (!session.claimHomeViewed()) return@launch
            analytics.log(
                AnalyticsEvent.HomeViewed(
                    appCount = inventory.apps.value.size,
                    storageUsedPct = (ready.usedFraction * 100).toInt().coerceIn(0, 100),
                    hasUsageAccess = ready.hasUsageAccess,
                    isPremium = ready.isPremium,
                ),
            )
        }
    }
}

package com.jedy.appcleaner.uninstaller.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jedy.appcleaner.uninstaller.core.analytics.Analytics
import com.jedy.appcleaner.uninstaller.core.analytics.AnalyticsEvent
import com.jedy.appcleaner.uninstaller.core.model.AppSize
import com.jedy.appcleaner.uninstaller.core.model.DeviceStorage
import com.jedy.appcleaner.uninstaller.core.model.InstalledApp
import com.jedy.appcleaner.uninstaller.core.selection.SelectionStore
import com.jedy.appcleaner.uninstaller.core.ui.theme.Severity
import com.jedy.appcleaner.uninstaller.core.ui.theme.SeverityRules
import com.jedy.appcleaner.uninstaller.data.billing.Premium
import com.jedy.appcleaner.uninstaller.data.history.HistoryRepository
import com.jedy.appcleaner.uninstaller.data.inventory.AppInventory
import com.jedy.appcleaner.uninstaller.data.prefs.AppPreferences
import com.jedy.appcleaner.uninstaller.data.prefs.SizeDisplay
import com.jedy.appcleaner.uninstaller.data.scan.CleanupScan
import com.jedy.appcleaner.uninstaller.data.scan.ScanMath
import com.jedy.appcleaner.uninstaller.data.scan.ScanResult
import com.jedy.appcleaner.uninstaller.data.storage.StorageBreakdown
import com.jedy.appcleaner.uninstaller.data.usage.UsageAccess
import com.jedy.appcleaner.uninstaller.data.usage.UsageInsights
import com.jedy.appcleaner.uninstaller.di.DefaultDispatcher
import com.jedy.appcleaner.uninstaller.di.IoDispatcher
import com.jedy.appcleaner.uninstaller.feature.apps.AppsListLogic
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** One "Biggest apps" row on the dashboard. */
data class BiggestApp(
    val app: InstalledApp,
    val bytes: Long,
    val severity: Severity,
    val sizeFraction: Float,
)

/** Uninstall History totals for the "Freed so far" tile. */
data class FreedSoFar(val count: Int, val bytes: Long)

/** Everything the Home dashboard renders. Every figure is a real measurement (see [ScanMath]). */
data class HomeUiState(
    /** Null until the first off-main-thread read. Total 0 = the volume could not be read. */
    val storage: DeviceStorage? = null,
    /** The last completed scan (this session or persisted); null before the first one. */
    val lastScan: ScanResult? = null,
    /**
     * The tile numbers: [lastScan] when there is one, else the same maths over live data, so a
     * brand-new user still sees their real space hogs before scanning.
     */
    val summary: ScanResult? = null,
    val biggest: List<BiggestApp> = emptyList(),
    val freed: FreedSoFar = FreedSoFar(0, 0),
    val isPremium: Boolean = false,
    val hasUsageAccess: Boolean = false,
    val showPremiumEnded: Boolean = false,
    val isInventoryLoading: Boolean = true,
) {
    val usedFraction: Float get() = storage?.let(ScanMath::usedFraction) ?: 0f
    val storageSeverity: Severity get() = if (storage == null || storage.totalBytes <= 0) Severity.OK else SeverityRules.storage(usedFraction)
}

/**
 * The Home dashboard (redesign): storage hero, the scan entry, four stat tiles and the biggest
 * apps. The list itself moved to Apps; this ViewModel only reads and summarises.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val inventory: AppInventory,
    private val storage: StorageBreakdown,
    private val usageAccess: UsageAccess,
    private val usageInsights: UsageInsights,
    private val premium: Premium,
    private val preferences: AppPreferences,
    private val scan: CleanupScan,
    private val selection: SelectionStore,
    history: HistoryRepository,
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
        val threshold: Int,
    )
    private data class Entitlement(val isPremium: Boolean, val hasAccess: Boolean, val sizeDisplay: SizeDisplay, val premiumEnded: Boolean)

    private val freed = history.entries.map { rows -> FreedSoFar(rows.size, rows.sumOf { it.bytes }) }

    val uiState: StateFlow<HomeUiState> = combine(
        combine(
            inventory.apps,
            inventory.isInitialLoading,
            storage.sizes,
            usageInsights.lastUsed,
            preferences.unusedThresholdDays,
            ::Live,
        ),
        combine(premium.isPremium, usageAccess.isGranted, preferences.sizeDisplay, premiumLapse.showBanner, ::Entitlement),
        deviceStorage,
        scan.result,
        freed,
    ) { live, entitlement, device, lastScan, freedSoFar ->
        build(live, entitlement, device, lastScan, freedSoFar)
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
        freedSoFar: FreedSoFar,
    ): HomeUiState {
        val total = device?.totalBytes ?: 0L
        val summary = lastScan ?: device?.takeIf { live.apps.isNotEmpty() }?.let {
            ScanMath.compute(
                apps = live.apps,
                // lastUsed feeds unusedApps(); an empty map means it has not loaded yet.
                unused = if (entitlement.hasAccess && live.lastUsed.isNotEmpty()) usageInsights.unusedApps(live.apps, live.threshold) else emptyList(),
                sizes = live.sizes,
                storage = it,
                thresholdDays = live.threshold,
                hasUsageAccess = entitlement.hasAccess,
                now = System.currentTimeMillis(),
            )
        }
        // Same sizes as the Apps list, so a row reads the same on both screens.
        val useTotal = entitlement.isPremium && entitlement.sizeDisplay == SizeDisplay.TOTAL
        val top = live.apps
            .map { it to AppsListLogic.displayBytes(it, live.sizes, useTotal) }
            .sortedByDescending { it.second }
            .take(BIGGEST_COUNT)
        val largest = top.firstOrNull()?.second ?: 0L
        return HomeUiState(
            storage = device,
            lastScan = lastScan,
            summary = summary,
            biggest = top.map { (app, bytes) ->
                BiggestApp(app, bytes, SeverityRules.appSize(bytes, total), AppsListLogic.sizeFraction(bytes, largest))
            },
            freed = freedSoFar,
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

    private companion object {
        const val BIGGEST_COUNT = 5
    }
}

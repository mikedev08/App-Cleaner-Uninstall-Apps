package com.jedy.appcleaner.uninstaller.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jedy.appcleaner.uninstaller.core.analytics.Analytics
import com.jedy.appcleaner.uninstaller.core.analytics.AnalyticsEvent
import com.jedy.appcleaner.uninstaller.core.model.AppSize
import com.jedy.appcleaner.uninstaller.core.model.DeviceStorage
import com.jedy.appcleaner.uninstaller.core.model.HomeTab
import com.jedy.appcleaner.uninstaller.core.model.InstalledApp
import com.jedy.appcleaner.uninstaller.core.model.SortOrder
import com.jedy.appcleaner.uninstaller.core.selection.SelectionStore
import com.jedy.appcleaner.uninstaller.data.billing.Premium
import com.jedy.appcleaner.uninstaller.data.inventory.AppInventory
import com.jedy.appcleaner.uninstaller.data.inventory.InventoryHealth
import com.jedy.appcleaner.uninstaller.data.prefs.AppPreferences
import com.jedy.appcleaner.uninstaller.data.prefs.SizeDisplay
import com.jedy.appcleaner.uninstaller.data.storage.StorageBreakdown
import com.jedy.appcleaner.uninstaller.data.usage.UsageAccess
import com.jedy.appcleaner.uninstaller.data.usage.UsageInsights
import com.jedy.appcleaner.uninstaller.di.DefaultDispatcher
import com.jedy.appcleaner.uninstaller.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Everything the Home screen renders (PRD §4 Screen 4). */
data class HomeUiState(
    val tab: HomeTab = HomeTab.ALL,
    val query: String = "",
    val isSearchOpen: Boolean = false,
    /** First-ever scan with an empty cache: shimmer rows. */
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    /** All tab rows after search and sort. */
    val rows: List<HomeAppRow> = emptyList(),
    val totalAppCount: Int = 0,
    val sortOrder: SortOrder = SortOrder.SIZE,
    val storage: StorageSummary? = null,
    val selected: Set<String> = emptySet(),
    val selectedBytes: Long = 0,
    val hiddenSelectedCount: Int = 0,
    val allVisibleSelected: Boolean = false,
    val isPremium: Boolean = false,
    val hasUsageAccess: Boolean = false,
    val showPremiumEnded: Boolean = false,
    val showInventoryIncomplete: Boolean = false,
) {
    val selectedCount: Int get() = selected.size
}

/** Screen 7, the App Details sheet. Premium-only fields are already gated. */
data class AppDetailsUi(
    val app: InstalledApp,
    val isPremium: Boolean,
    val hasUsageAccess: Boolean,
    /** Null = no record inside the usage window (see [usageWindowStart]) or not entitled. */
    val lastUsedAt: Long?,
    val usageWindowStart: Long,
    /** App / Data / Cache from StorageStatsManager; null until measured or when unavailable. */
    val size: AppSize?,
    val isMeasuring: Boolean,
)

/** An Uninstall Confirm Sheet request: from the Selection Bar or from one app's details. */
data class UninstallRequest(val packages: List<String>, val sourceTab: HomeTab)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val inventory: AppInventory,
    inventoryHealth: InventoryHealth,
    private val storage: StorageBreakdown,
    private val usageAccess: UsageAccess,
    private val usageInsights: UsageInsights,
    private val premium: Premium,
    private val preferences: AppPreferences,
    private val selection: SelectionStore,
    private val analytics: Analytics,
    private val session: HomeSession,
    @param:IoDispatcher private val io: CoroutineDispatcher,
    @param:DefaultDispatcher private val default: CoroutineDispatcher,
) : ViewModel() {

    private val tab = MutableStateFlow(HomeTab.ALL)
    private val query = MutableStateFlow("")
    private val searchOpen = MutableStateFlow(false)
    private val refreshing = MutableStateFlow(false)
    private val deviceStorage = MutableStateFlow<DeviceStorage?>(null)

    /**
     * Billing reports `false` until RevenueCat's cached CustomerInfo arrives, which would flash
     * the premium-ended banner at every paying user on cold start. The banner (and the
     * `is_premium` analytics param) wait until premium was seen or [PREMIUM_SETTLE_MS] elapsed.
     */
    private val premiumSettled = MutableStateFlow(false)

    private data class DetailsSelection(
        val packageName: String,
        /** Used when the package isn't in the inventory list (e.g. still being scanned). */
        val snapshot: InstalledApp?,
        val measured: AppSize? = null,
        val isMeasuring: Boolean = false,
    )

    private val detailsSelection = MutableStateFlow<DetailsSelection?>(null)
    private var measureJob: Job? = null

    private val _uninstallRequest = MutableStateFlow<UninstallRequest?>(null)
    val uninstallRequest: StateFlow<UninstallRequest?> = _uninstallRequest

    private data class ViewFilters(val tab: HomeTab, val query: String, val searchOpen: Boolean, val refreshing: Boolean)
    private data class Entitlement(val isPremium: Boolean, val hasAccess: Boolean, val sizeDisplay: SizeDisplay, val sort: SortOrder)
    private data class Sources(
        val apps: List<InstalledApp>,
        val loading: Boolean,
        val sizes: Map<String, AppSize>,
        val lastUsed: Map<String, Long>,
        val selected: Set<String>,
    )
    private data class Notices(val premiumEnded: Boolean, val incomplete: Boolean, val device: DeviceStorage?)

    private val premiumEnded = combine(
        premium.isPremium,
        preferences.wasPremium,
        preferences.premiumEndedBannerSeen,
        premiumSettled,
    ) { isPremium, wasPremium, seen, settled -> settled && wasPremium && !isPremium && !seen }

    val uiState: StateFlow<HomeUiState> = combine(
        combine(tab, query, searchOpen, refreshing, ::ViewFilters),
        combine(
            premium.isPremium,
            usageAccess.isGranted,
            preferences.sizeDisplay,
            preferences.sortOrder(HomeTab.ALL),
            ::Entitlement,
        ),
        combine(
            inventory.apps,
            inventory.isInitialLoading,
            storage.sizes,
            usageInsights.lastUsed,
            selection.selected,
            ::Sources,
        ),
        combine(premiumEnded, inventoryHealth.isIncomplete, deviceStorage, ::Notices),
        ::buildState,
    ).flowOn(default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    val details: StateFlow<AppDetailsUi?> = combine(
        detailsSelection,
        inventory.apps,
        combine(premium.isPremium, usageAccess.isGranted, ::Pair),
        combine(storage.sizes, usageInsights.lastUsed, usageInsights.windowStart, ::Triple),
    ) { selectionState, apps, (isPremium, hasAccess), (sizes, lastUsed, windowStart) ->
        val chosen = selectionState ?: return@combine null
        val app = apps.firstOrNull { it.packageName == chosen.packageName } ?: chosen.snapshot ?: return@combine null
        AppDetailsUi(
            app = app,
            isPremium = isPremium,
            hasUsageAccess = hasAccess,
            lastUsedAt = if (isPremium) lastUsed[app.packageName]?.let(::notInFuture) else null,
            usageWindowStart = windowStart,
            size = if (isPremium) chosen.measured ?: sizes[app.packageName] else null,
            isMeasuring = chosen.isMeasuring,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // PRD §6 item 21: selection survives every view change, but never outlives the app.
        viewModelScope.launch {
            combine(inventory.apps, inventory.isInitialLoading, ::Pair).collect { (apps, loading) ->
                // An empty list before the first scan is "unknown", not "nothing installed" —
                // retaining against it would wipe a reminder's pre-selection on cold start.
                if (!loading && apps.isNotEmpty()) selection.retainOnly(apps.mapTo(HashSet(apps.size)) { it.packageName })
            }
        }
        // Free space moves whenever an app comes or goes.
        viewModelScope.launch { inventory.apps.collect { refreshDeviceStorage() } }
        // A details sheet for an app removed meanwhile (elsewhere, or by our own queue) closes.
        viewModelScope.launch {
            inventory.apps.collect { apps ->
                val open = detailsSelection.value?.packageName ?: return@collect
                if (apps.none { it.packageName == open } && withContext(io) { !inventory.isInstalled(open) }) closeDetails()
            }
        }
        // Reminder deep link / Result screen "See unused apps": jump to the requested tab once.
        viewModelScope.launch {
            selection.requestedTab.filterNotNull().collect { requested ->
                tab.value = requested
                selection.consumeTabRequest()
            }
        }
        // PRD §6 item 18: remember that this user once had premium, so a lapse can be explained.
        viewModelScope.launch {
            premium.isPremium.collect { isPremium ->
                if (!isPremium) return@collect
                premiumSettled.value = true
                if (!preferences.wasPremium.first()) preferences.setWasPremium(true)
                // A renewed subscriber gets the one-time explanation again if it lapses again.
                if (preferences.premiumEndedBannerSeen.first()) preferences.setPremiumEndedBannerSeen(false)
            }
        }
        viewModelScope.launch {
            delay(PREMIUM_SETTLE_MS)
            premiumSettled.value = true
        }
        logHomeViewedOnce()
    }

    private fun buildState(filters: ViewFilters, entitlement: Entitlement, sources: Sources, notices: Notices): HomeUiState {
        val useTotal = entitlement.isPremium && entitlement.sizeDisplay == SizeDisplay.TOTAL
        val allRows = sources.apps.map { app ->
            HomeAppRow(
                app = app,
                sizeBytes = HomeListLogic.displayBytes(app, sources.sizes, useTotal),
                lastUsedAt = if (entitlement.isPremium) sources.lastUsed[app.packageName]?.let(::notInFuture) else null,
            )
        }
        val sort = HomeListLogic.effectiveSort(entitlement.sort, entitlement.isPremium)
        val visible = HomeListLogic.sort(allRows.filter { HomeListLogic.matches(it.app, filters.query) }, sort)
        val rowsByPackage = allRows.associateBy { it.packageName }
        val appsByPackage = sources.apps.associateBy { it.packageName }
        val storageSummary = notices.device?.let { device ->
            val useMeasured = entitlement.isPremium && entitlement.hasAccess
            HomeListLogic.storageSummary(device, HomeListLogic.appsBytes(sources.apps, sources.sizes, useMeasured))
        }
        return HomeUiState(
            tab = filters.tab,
            query = filters.query,
            isSearchOpen = filters.searchOpen,
            isInitialLoading = sources.loading && sources.apps.isEmpty(),
            isRefreshing = filters.refreshing,
            rows = visible,
            totalAppCount = sources.apps.size,
            sortOrder = sort,
            storage = storageSummary,
            selected = sources.selected,
            selectedBytes = sources.selected.sumOf { rowsByPackage[it]?.sizeBytes ?: 0L },
            hiddenSelectedCount = HomeListLogic.hiddenBySearch(sources.selected, appsByPackage, filters.query),
            allVisibleSelected = visible.isNotEmpty() && visible.all { it.packageName in sources.selected },
            isPremium = entitlement.isPremium,
            hasUsageAccess = entitlement.hasAccess,
            showPremiumEnded = notices.premiumEnded,
            showInventoryIncomplete = notices.incomplete,
        )
    }

    /** PRD §6 item 12: Usage Access is re-read on every resume; free space may have moved too. */
    fun onResume() {
        usageAccess.recheck()
        refreshDeviceStorage()
    }

    fun onTabSelected(selected: HomeTab) {
        tab.value = selected
    }

    fun onSearchOpened() {
        searchOpen.value = true
    }

    /** Closing search clears the filter; the selection it may have hidden is untouched. */
    fun onSearchClosed() {
        searchOpen.value = false
        query.value = ""
    }

    fun onQueryChanged(value: String) {
        query.value = value
    }

    /** Callers gate premium sorts (paywall) before getting here. */
    fun onSortSelected(order: SortOrder) {
        viewModelScope.launch { preferences.setSortOrder(HomeTab.ALL, order) }
    }

    /** PRD Feature 1: pull-to-refresh forces a full rescan. */
    fun refresh() {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            try {
                inventory.refresh()
                refreshDeviceStorage()
            } finally {
                refreshing.value = false
            }
        }
    }

    fun onToggleSelected(packageName: String) {
        val adding = packageName !in selection.selected.value
        selection.toggle(packageName)
        if (adding) logFirstSelection(VIA_CHECKBOX)
    }

    /** "Select all" acts on the visible (searched) rows only — never on apps the user can't see. */
    fun onSelectAllVisible() {
        val visible = uiState.value.rows.map { it.packageName }
        if (visible.isEmpty()) return
        selection.select(visible)
        logFirstSelection(VIA_SELECT_ALL)
    }

    fun onDeselectAllVisible() {
        selection.deselect(uiState.value.rows.map { it.packageName })
    }

    fun onClearSelection() = selection.clear()

    fun onDismissPremiumEnded() {
        viewModelScope.launch { preferences.setPremiumEndedBannerSeen(true) }
    }

    /**
     * Screen 7. Premium users with Usage Access get a fresh StorageStatsManager measurement; the
     * cached size (if any) shows meanwhile so the sheet never opens blank.
     */
    fun openDetails(packageName: String) {
        measureJob?.cancel()
        val listed = inventory.apps.value.firstOrNull { it.packageName == packageName }
        val canMeasure = premium.isPremium.value && usageAccess.isGranted.value
        detailsSelection.value = DetailsSelection(packageName, snapshot = listed, isMeasuring = canMeasure)
        if (listed == null) {
            viewModelScope.launch {
                val found = inventory.find(packageName)
                if (detailsSelection.value?.packageName != packageName) return@launch
                if (found == null) closeDetails() else detailsSelection.update { it?.copy(snapshot = found) }
            }
        }
        if (canMeasure) {
            measureJob = viewModelScope.launch {
                val size = runCatching { storage.measure(packageName) }.getOrNull()
                detailsSelection.update { current ->
                    if (current?.packageName == packageName) current.copy(measured = size, isMeasuring = false) else current
                }
            }
        }
    }

    fun closeDetails() {
        measureJob?.cancel()
        detailsSelection.value = null
    }

    /** Selection Bar "Uninstall": the full selection, including apps the search hides (§6 item 21). */
    fun requestUninstallSelection() {
        val selected = selection.selected.value
        if (selected.isEmpty()) return
        val inDisplayOrder = uiState.value.rows.map { it.packageName }.filter { it in selected }
        _uninstallRequest.value = UninstallRequest(
            packages = inDisplayOrder + (selected - inDisplayOrder.toSet()),
            sourceTab = tab.value,
        )
    }

    /** Details sheet "Uninstall": that one app, regardless of the selection. */
    fun requestUninstallOne(packageName: String) {
        closeDetails()
        _uninstallRequest.value = UninstallRequest(listOf(packageName), tab.value)
    }

    fun dismissUninstallRequest() {
        _uninstallRequest.value = null
    }

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
            val ready = combine(uiState, premiumSettled, ::Pair)
                .first { (state, settled) -> settled && !state.isInitialLoading && state.storage != null }
                .first
            if (!session.claimHomeViewed()) return@launch
            analytics.log(
                AnalyticsEvent.HomeViewed(
                    appCount = ready.totalAppCount,
                    storageUsedPct = ready.storage?.usedPercent ?: 0,
                    hasUsageAccess = ready.hasUsageAccess,
                    isPremium = ready.isPremium,
                ),
            )
        }
    }

    /** PRD §9 `app_selected`: sampled to the first selection per session. */
    private fun logFirstSelection(via: String) {
        if (!session.claimFirstSelection()) return
        analytics.log(AnalyticsEvent.AppSelected(tab = tab.value.name.lowercase(), via = via))
    }

    /** PRD §6 item 14: a last use "in the future" (clock moved back) reads as used today. */
    private fun notInFuture(timestamp: Long): Long = timestamp.coerceAtMost(System.currentTimeMillis())

    private companion object {
        const val PREMIUM_SETTLE_MS = 2_500L
        const val VIA_CHECKBOX = "checkbox"
        const val VIA_SELECT_ALL = "select_all"
    }
}

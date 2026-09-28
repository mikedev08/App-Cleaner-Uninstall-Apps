package com.jedy.appcleaner.uninstaller.feature.apps

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jedy.appcleaner.uninstaller.core.analytics.Analytics
import com.jedy.appcleaner.uninstaller.core.analytics.AnalyticsEvent
import com.jedy.appcleaner.uninstaller.core.model.AppSize
import com.jedy.appcleaner.uninstaller.core.model.HomeTab
import com.jedy.appcleaner.uninstaller.core.model.InstalledApp
import com.jedy.appcleaner.uninstaller.core.model.SortOrder
import com.jedy.appcleaner.uninstaller.core.selection.SelectionStore
import com.jedy.appcleaner.uninstaller.core.model.LargeApps
import com.jedy.appcleaner.uninstaller.core.model.bestKnownBytes
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
import com.jedy.appcleaner.uninstaller.feature.home.HomeSession
import com.jedy.appcleaner.uninstaller.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Everything the Apps screen renders (PRD §4 Screen 4, the list half of the old Home). */
data class AppsUiState(
    val tab: HomeTab = HomeTab.ALL,
    val query: String = "",
    val isSearchOpen: Boolean = false,
    /** First-ever scan with an empty cache: shimmer rows. */
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    /** All tab rows after search and sort. */
    val rows: List<AppsRow> = emptyList(),
    /** Size sort only: "Large" / "Everything else". */
    val sections: AppsSections? = null,
    val totalAppCount: Int = 0,
    /** What the whole library takes, in the sizes this user sees (header summary). */
    val totalBytes: Long = 0,
    val sortOrder: SortOrder = SortOrder.SIZE,
    val selected: Set<String> = emptySet(),
    val selectedBytes: Long = 0,
    val hiddenSelectedCount: Int = 0,
    val allVisibleSelected: Boolean = false,
    val isPremium: Boolean = false,
    val hasUsageAccess: Boolean = false,
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
    /** The size headline this user is entitled to (measured total for premium, else APK). */
    val displayBytes: Long,
    /** `LargeApps.isLarge` on the best-known size: the same rule as the Large filter and Home. */
    val isLarge: Boolean,
)

/** An Uninstall Confirm Sheet request: from the Selection Bar or from one app's details. */
data class UninstallRequest(val packages: List<String>, val sourceTab: HomeTab)

@HiltViewModel
class AppsViewModel @Inject constructor(
    savedState: SavedStateHandle,
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

    /** Seeded from the route (`apps/{tab}`), so the first frame is already on the right tab. */
    private val tab = MutableStateFlow(
        savedState.get<String>(Routes.ARG_TAB)?.let { runCatching { HomeTab.valueOf(it) }.getOrNull() } ?: HomeTab.ALL,
    )
    private val query = MutableStateFlow("")
    private val searchOpen = MutableStateFlow(false)
    private val refreshing = MutableStateFlow(false)

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
    private data class Notices(val incomplete: Boolean, val windowStart: Long)

    val uiState: StateFlow<AppsUiState> = combine(
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
        combine(inventoryHealth.isIncomplete, usageInsights.windowStart, ::Notices),
        ::buildState,
    ).flowOn(default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppsUiState(tab = tab.value))

    val details: StateFlow<AppDetailsUi?> = combine(
        detailsSelection,
        inventory.apps,
        combine(premium.isPremium, usageAccess.isGranted, preferences.sizeDisplay, ::Triple),
        combine(storage.sizes, usageInsights.lastUsed, usageInsights.windowStart, ::Triple),
    ) { selectionState, apps, (isPremium, hasAccess, sizeDisplay), (sizes, lastUsed, windowStart) ->
        val chosen = selectionState ?: return@combine null
        val app = apps.firstOrNull { it.packageName == chosen.packageName } ?: chosen.snapshot ?: return@combine null
        val size = if (isPremium) chosen.measured ?: sizes[app.packageName] else null
        val bytes = if (isPremium && hasAccess && size != null) size.totalBytes
        else AppsListLogic.displayBytes(app, sizes, useTotal = isPremium && sizeDisplay == SizeDisplay.TOTAL)
        AppDetailsUi(
            app = app,
            isPremium = isPremium,
            hasUsageAccess = hasAccess,
            lastUsedAt = if (isPremium) lastUsed[app.packageName]?.let(::notInFuture) else null,
            usageWindowStart = windowStart,
            size = size,
            isMeasuring = chosen.isMeasuring,
            displayBytes = bytes,
            isLarge = LargeApps.isLarge(app.bestKnownBytes(chosen.measured ?: sizes[app.packageName])),
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
        // A details sheet for an app removed meanwhile (elsewhere, or by our own queue) closes.
        viewModelScope.launch {
            inventory.apps.collect { apps ->
                val open = detailsSelection.value?.packageName ?: return@collect
                if (apps.none { it.packageName == open } && withContext(io) { !inventory.isInstalled(open) }) closeDetails()
            }
        }
        // Reminder deep link / Result screen "See unused apps" while Apps is open: switch once.
        viewModelScope.launch {
            selection.requestedTab.filterNotNull().collect { requested ->
                tab.value = requested
                selection.consumeTabRequest()
            }
        }
    }

    private fun buildState(filters: ViewFilters, entitlement: Entitlement, sources: Sources, notices: Notices): AppsUiState {
        val useTotal = entitlement.isPremium && entitlement.sizeDisplay == SizeDisplay.TOTAL
        val now = System.currentTimeMillis()
        // Idle chips need real usage data: premium, access granted, and the map actually loaded.
        val showIdle = entitlement.isPremium && entitlement.hasAccess && sources.lastUsed.isNotEmpty()
        val bytes = sources.apps.associate { it.packageName to AppsListLogic.displayBytes(it, sources.sizes, useTotal) }
        val largest = bytes.values.maxOrNull() ?: 0L
        val allRows = sources.apps.map { app ->
            val size = bytes.getValue(app.packageName)
            val best = app.bestKnownBytes(sources.sizes)
            val lastUsed = sources.lastUsed[app.packageName]?.let(::notInFuture)
            AppsRow(
                app = app,
                sizeBytes = size,
                lastUsedAt = if (entitlement.isPremium) lastUsed else null,
                large = LargeApps.isLarge(best),
                bestKnownBytes = best,
                sizeFraction = AppsListLogic.sizeFraction(size, largest),
                idle = if (showIdle) AppsListLogic.idleChip(lastUsed, app.firstInstallTime, notices.windowStart, now) else null,
            )
        }
        val sort = AppsListLogic.effectiveSort(entitlement.sort, entitlement.isPremium)
        val visible = AppsListLogic.sort(allRows.filter { AppsListLogic.matches(it.app, filters.query) }, sort)
        val rowsByPackage = allRows.associateBy { it.packageName }
        val appsByPackage = sources.apps.associateBy { it.packageName }
        return AppsUiState(
            tab = filters.tab,
            query = filters.query,
            isSearchOpen = filters.searchOpen,
            isInitialLoading = sources.loading && sources.apps.isEmpty(),
            isRefreshing = filters.refreshing,
            rows = visible,
            sections = AppsListLogic.sections(visible, sort),
            totalAppCount = sources.apps.size,
            totalBytes = bytes.values.sum(),
            sortOrder = sort,
            selected = sources.selected,
            selectedBytes = sources.selected.sumOf { rowsByPackage[it]?.sizeBytes ?: 0L },
            hiddenSelectedCount = AppsListLogic.hiddenBySearch(sources.selected, appsByPackage, filters.query),
            allVisibleSelected = visible.isNotEmpty() && visible.all { it.packageName in sources.selected },
            isPremium = entitlement.isPremium,
            hasUsageAccess = entitlement.hasAccess,
            showInventoryIncomplete = notices.incomplete,
        )
    }

    /** PRD §6 item 12: Usage Access is re-read on every resume. */
    fun onResume() {
        usageAccess.recheck()
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

    /** PRD §9 `app_selected`: sampled to the first selection per session. */
    private fun logFirstSelection(via: String) {
        if (!session.claimFirstSelection()) return
        analytics.log(AnalyticsEvent.AppSelected(tab = tab.value.name.lowercase(), via = via))
    }

    /** PRD §6 item 14: a last use "in the future" (clock moved back) reads as used today. */
    private fun notInFuture(timestamp: Long): Long = timestamp.coerceAtMost(System.currentTimeMillis())

    private companion object {
        const val VIA_CHECKBOX = "checkbox"
        const val VIA_SELECT_ALL = "select_all"
    }
}

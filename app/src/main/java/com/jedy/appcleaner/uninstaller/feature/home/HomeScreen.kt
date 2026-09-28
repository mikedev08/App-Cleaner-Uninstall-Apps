package com.jedy.appcleaner.uninstaller.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jedy.appcleaner.uninstaller.core.model.HomeTab
import com.jedy.appcleaner.uninstaller.core.model.PaywallSource
import com.jedy.appcleaner.uninstaller.core.model.UsageAccessTrigger
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens
import com.jedy.appcleaner.uninstaller.feature.insights.LargeTab
import com.jedy.appcleaner.uninstaller.feature.insights.UnusedTab
import com.jedy.appcleaner.uninstaller.feature.uninstall.ResumeUninstallBanner
import com.jedy.appcleaner.uninstaller.feature.uninstall.UninstallConfirmSheet

/**
 * CONTRACT (frozen signature). PRD §4 Screen 4 — the core screen — embedding the Unused and Large
 * tabs (Screens 5–6, built by Insights) and hosting the App Details sheet (Screen 7).
 *
 * Layout, top to bottom: app bar (title / inline search, sort, history, settings) · resume and
 * premium-ended banners · storage card · tabs · tab body · sliding Selection Bar. The NavHost
 * applies no insets, so this screen handles status bar, navigation bar, cutout and IME itself.
 */
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenPaywall: (PaywallSource) -> Unit,
    onOpenUsageAccess: (UsageAccessTrigger) -> Unit,
    onBatchStarted: (batchId: Long) -> Unit,
    onOpenScan: () -> Unit = {},
    onOpenApps: (com.jedy.appcleaner.uninstaller.core.model.HomeTab) -> Unit = {},
) {
    val viewModel: HomeViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val details by viewModel.details.collectAsStateWithLifecycle()
    val uninstallRequest by viewModel.uninstallRequest.collectAsStateWithLifecycle()
    // Hoisted so the All list keeps its scroll position across tab switches.
    val allListState = rememberLazyListState()

    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }
    BackHandler(enabled = state.isSearchOpen) { viewModel.onSearchClosed() }

    val onSeeFullBreakdown = {
        when {
            !state.hasUsageAccess -> onOpenUsageAccess(UsageAccessTrigger.STORAGE_CARD)
            !state.isPremium -> onOpenPaywall(PaywallSource.LARGE_TAB)
            else -> viewModel.onTabSelected(HomeTab.LARGE)
        }
    }

    Scaffold(
        containerColor = AppTheme.colors.background,
        // safeDrawing includes the IME, so the list stays reachable while typing a search.
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            HomeTopBar(
                isSearchOpen = state.isSearchOpen,
                onQueryChanged = viewModel::onQueryChanged,
                onOpenSearch = viewModel::onSearchOpened,
                onCloseSearch = viewModel::onSearchClosed,
                showSort = state.tab == HomeTab.ALL,
                sortOrder = state.sortOrder,
                isPremium = state.isPremium,
                onSortSelected = { order ->
                    if (order.isPremium && !state.isPremium) onOpenPaywall(PaywallSource.SORT_MENU)
                    else viewModel.onSortSelected(order)
                },
                onOpenHistory = onOpenHistory,
                onOpenSettings = onOpenSettings,
            )
        },
        bottomBar = {
            SelectionBar(
                count = state.selectedCount,
                bytes = state.selectedBytes,
                hiddenCount = state.hiddenSelectedCount,
                onClear = viewModel::onClearSelection,
                onUninstall = viewModel::requestUninstallSelection,
            )
        },
    ) { padding ->
        val layoutDirection = LocalLayoutDirection.current
        val bottomPadding = PaddingValues(bottom = padding.calculateBottomPadding())
        Column(
            Modifier
                .fillMaxSize()
                .padding(
                    start = padding.calculateStartPadding(layoutDirection),
                    top = padding.calculateTopPadding(),
                    end = padding.calculateEndPadding(layoutDirection),
                ),
        ) {
            ResumeUninstallBanner(
                onContinue = onBatchStarted,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.gutter),
            )
            AnimatedVisibility(
                visible = state.showPremiumEnded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                PremiumEndedBanner(
                    onRenew = {
                        viewModel.onDismissPremiumEnded()
                        onOpenPaywall(PaywallSource.SETTINGS)
                    },
                    onDismiss = viewModel::onDismissPremiumEnded,
                    modifier = Modifier.padding(horizontal = Dimens.gutter, vertical = 4.dp),
                )
            }
            // Searching is about the list; the card steps aside to give the results room.
            AnimatedVisibility(
                visible = !state.isSearchOpen,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                StorageCard(
                    summary = state.storage,
                    isPremium = state.isPremium,
                    hasUsageAccess = state.hasUsageAccess,
                    onSeeFullBreakdown = onSeeFullBreakdown,
                    modifier = Modifier.padding(horizontal = Dimens.gutter, vertical = 8.dp),
                )
            }
            HomeTabRow(
                selected = state.tab,
                isPremium = state.isPremium,
                onSelected = viewModel::onTabSelected,
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (state.tab) {
                    HomeTab.ALL -> AllAppsTab(
                        state = state,
                        listState = allListState,
                        contentPadding = bottomPadding,
                        onRefresh = viewModel::refresh,
                        onToggleSelected = viewModel::onToggleSelected,
                        onOpenDetails = viewModel::openDetails,
                        onSelectAll = viewModel::onSelectAllVisible,
                        onDeselectAll = viewModel::onDeselectAllVisible,
                    )
                    HomeTab.UNUSED -> UnusedTab(
                        searchQuery = state.query,
                        onRequestAccess = { onOpenUsageAccess(UsageAccessTrigger.UNUSED_TAB) },
                        onUnlock = { onOpenPaywall(PaywallSource.UNUSED_TAB) },
                        onOpenDetails = viewModel::openDetails,
                        modifier = Modifier.fillMaxSize().padding(bottomPadding),
                    )
                    HomeTab.LARGE -> LargeTab(
                        searchQuery = state.query,
                        onRequestAccess = { onOpenUsageAccess(UsageAccessTrigger.LARGE_TAB) },
                        onUnlock = { onOpenPaywall(PaywallSource.LARGE_TAB) },
                        onOpenDetails = viewModel::openDetails,
                        modifier = Modifier.fillMaxSize().padding(bottomPadding),
                    )
                }
            }
        }
    }

    details?.let { current ->
        AppDetailsSheet(
            details = current,
            onDismiss = viewModel::closeDetails,
            onUnlockSizes = { onOpenPaywall(PaywallSource.DETAILS_SHEET) },
            onRequestUsageAccess = { onOpenUsageAccess(UsageAccessTrigger.LARGE_TAB) },
            onUninstall = { viewModel.requestUninstallOne(current.app.packageName) },
        )
    }

    uninstallRequest?.let { request ->
        UninstallConfirmSheet(
            packages = request.packages,
            sourceTab = request.sourceTab,
            onDismiss = viewModel::dismissUninstallRequest,
            onStarted = { batchId ->
                viewModel.dismissUninstallRequest()
                onBatchStarted(batchId)
            },
        )
    }
}

package com.jedy.appcleaner.uninstaller.feature.apps

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.format.formatBytes
import com.jedy.appcleaner.uninstaller.core.model.HomeTab
import com.jedy.appcleaner.uninstaller.core.model.PaywallSource
import com.jedy.appcleaner.uninstaller.core.model.UsageAccessTrigger
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens
import com.jedy.appcleaner.uninstaller.feature.home.PremiumEndedBanner
import com.jedy.appcleaner.uninstaller.feature.insights.LargeTab
import com.jedy.appcleaner.uninstaller.feature.insights.UnusedTab
import com.jedy.appcleaner.uninstaller.feature.uninstall.UninstallConfirmSheet

/**
 * CONTRACT (frozen signature). The app list that used to be Home (PRD §4 Screen 4), embedding
 * the Unused and Large tabs (Screens 5–6, built by Insights) and hosting the App Details sheet
 * (Screen 7).
 *
 * Top to bottom: back / search / sort · large "Apps" title · premium-ended banner · pill tabs ·
 * tab body · the floating Selection Bar. The NavHost applies no insets, so this screen handles
 * status bar, navigation bar, cutout and IME itself.
 */
@Composable
fun AppsScreen(
    initialTab: HomeTab,
    onBack: () -> Unit,
    onOpenPaywall: (PaywallSource) -> Unit,
    onOpenUsageAccess: (UsageAccessTrigger) -> Unit,
    onBatchStarted: (batchId: Long) -> Unit,
) {
    // The ViewModel seeds its tab from the same route argument, so [initialTab] needs no effect.
    val viewModel: AppsViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val details by viewModel.details.collectAsStateWithLifecycle()
    val uninstallRequest by viewModel.uninstallRequest.collectAsStateWithLifecycle()
    // Hoisted so the All list keeps its scroll position across tab switches.
    val allListState = rememberLazyListState()
    val context = LocalContext.current

    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }
    BackHandler(enabled = state.isSearchOpen) { viewModel.onSearchClosed() }

    // Room for the floating bar under the last row, plus the navigation bar / keyboard.
    val safeBottom = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
    val bottomPadding = PaddingValues(bottom = safeBottom + if (state.selectedCount > 0) SelectionBarReserve else 16.dp)

    Box(
        Modifier
            .fillMaxSize()
            .background(AppTheme.colors.background),
    ) {
        Column(Modifier.fillMaxSize()) {
            AppsHeader(
                summary = if (state.totalAppCount > 0) {
                    stringResource(
                        R.string.apps_summary,
                        pluralStringResource(R.plurals.home_list_count, state.totalAppCount, state.totalAppCount),
                        formatBytes(context, state.totalBytes),
                    )
                } else {
                    null
                },
                isSearchOpen = state.isSearchOpen,
                onBack = onBack,
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
            )
            Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
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
                        modifier = Modifier.padding(horizontal = Dimens.gutter, vertical = 8.dp),
                    )
                }
                AppsTabs(
                    selected = state.tab,
                    isPremium = state.isPremium,
                    onSelected = viewModel::onTabSelected,
                    modifier = Modifier.padding(horizontal = Dimens.gutter, vertical = 12.dp),
                )
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            ) {
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
                    // TODO(apps agent): CACHE gets its own list; it shows the Large tab until then.
                    HomeTab.LARGE, HomeTab.CACHE -> LargeTab(
                        searchQuery = state.query,
                        onRequestAccess = { onOpenUsageAccess(UsageAccessTrigger.LARGE_TAB) },
                        onUnlock = { onOpenPaywall(PaywallSource.LARGE_TAB) },
                        onOpenDetails = viewModel::openDetails,
                        modifier = Modifier.fillMaxSize().padding(bottomPadding),
                    )
                }
            }
        }
        SelectionBar(
            count = state.selectedCount,
            bytes = state.selectedBytes,
            hiddenCount = state.hiddenSelectedCount,
            onClear = viewModel::onClearSelection,
            onUninstall = viewModel::requestUninstallSelection,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
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

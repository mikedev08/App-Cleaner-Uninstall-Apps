package com.jedy.appcleaner.uninstaller.feature.apps

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import com.jedy.appcleaner.uninstaller.feature.insights.CacheTab
import com.jedy.appcleaner.uninstaller.feature.insights.LargeTab
import com.jedy.appcleaner.uninstaller.feature.insights.UnusedTab
import com.jedy.appcleaner.uninstaller.feature.uninstall.UninstallConfirmSheet

/**
 * CONTRACT (frozen signature). The app list that used to be Home (PRD §4 Screen 4), embedding
 * the Unused and Large tabs (Screens 5–6, built by Insights) and hosting the App Details sheet
 * (Screen 7).
 *
 * Top to bottom: the shared top bar (back / search / sort) · the large "Apps" title, which
 * scrolls away with the list · the pinned All / Unused / Large / Cache filters · tab body · the
 * floating Selection Bar. The premium-lapse notice lives on Home only (design review §2.6). The
 * NavHost applies no insets, so this screen handles status bar, navigation bar, cutout and IME.
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
    val bottomPadding = PaddingValues(bottom = safeBottom + if (state.selectedCount > 0) SelectionBarReserve else Dimens.bottomContentGap)

    val header = rememberCollapsingHeaderState()
    val summary = if (state.totalAppCount > 0) {
        stringResource(
            R.string.apps_summary,
            pluralStringResource(R.plurals.home_list_count, state.totalAppCount, state.totalAppCount),
            formatBytes(context, state.totalBytes),
        )
    } else {
        null
    }
    // derivedStateOf: the offset changes every scroll frame, the screen only cares past 60%.
    val titleCollapsed by remember(header) { derivedStateOf { header.collapsedFraction > 0.6f } }
    val collapsed = state.isSearchOpen || titleCollapsed
    // Opening or closing search swaps the header's content; start it fully shown again.
    LaunchedEffect(state.isSearchOpen) { header.reset() }

    Box(
        Modifier
            .fillMaxSize()
            .background(AppTheme.colors.background),
    ) {
        Column(Modifier.fillMaxSize()) {
            AppsTopBar(
                isSearchOpen = state.isSearchOpen,
                titleVisible = collapsed,
                // Content scrolls under the pinned filters, not the bar: the hairline goes there.
                scrolled = false,
                onBack = onBack,
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
            val horizontalInsets = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            CollapsingHeaderLayout(
                state = header,
                modifier = Modifier.weight(1f).fillMaxWidth().then(horizontalInsets).clipToBounds(),
                // Only the title block scrolls away; while searching there is none.
                header = { if (!state.isSearchOpen) AppsTitle(summary) },
                pinned = {
                    Column(Modifier.fillMaxWidth().background(AppTheme.colors.background)) {
                        if (state.isSearchOpen) {
                            SearchPill(
                                onQueryChanged = viewModel::onQueryChanged,
                                modifier = Modifier.padding(start = Dimens.gutter, end = Dimens.gutter, top = Dimens.space8),
                            )
                        }
                        AppsFilters(
                            selected = state.tab,
                            isPremium = state.isPremium,
                            onSelected = viewModel::onTabSelected,
                            // The chips scroll edge to edge; the 20dp gutter is inside the row.
                            modifier = Modifier.padding(vertical = Dimens.space12),
                        )
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(Dimens.hairline)
                                .background(if (collapsed) AppTheme.colors.border else Color.Transparent),
                        )
                    }
                },
                content = {
                    val tabModifier = Modifier.fillMaxSize().nestedScroll(header.connection)
                    when (state.tab) {
                        HomeTab.ALL -> AllAppsTab(
                            state = state,
                            listState = allListState,
                            contentPadding = bottomPadding,
                            headerConnection = header.connection,
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
                            modifier = tabModifier,
                            contentPadding = bottomPadding,
                        )
                        HomeTab.LARGE -> LargeTab(
                            searchQuery = state.query,
                            onRequestAccess = { onOpenUsageAccess(UsageAccessTrigger.LARGE_TAB) },
                            onOpenDetails = viewModel::openDetails,
                            modifier = tabModifier,
                            contentPadding = bottomPadding,
                        )
                        HomeTab.CACHE -> CacheTab(
                            searchQuery = state.query,
                            onRequestAccess = { onOpenUsageAccess(UsageAccessTrigger.CACHE_TAB) },
                            onOpenDetails = viewModel::openDetails,
                            modifier = tabModifier,
                            contentPadding = bottomPadding,
                        )
                    }
                },
            )
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
            onRequestUsageAccess = { onOpenUsageAccess(UsageAccessTrigger.DETAILS_SHEET) },
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

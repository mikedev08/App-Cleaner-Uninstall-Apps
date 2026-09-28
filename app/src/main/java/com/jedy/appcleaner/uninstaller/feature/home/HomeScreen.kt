package com.jedy.appcleaner.uninstaller.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.model.HomeTab
import com.jedy.appcleaner.uninstaller.core.model.PaywallSource
import com.jedy.appcleaner.uninstaller.core.model.UsageAccessTrigger
import com.jedy.appcleaner.uninstaller.core.ui.component.SectionHeader
import com.jedy.appcleaner.uninstaller.core.ui.component.listContentPadding
import com.jedy.appcleaner.uninstaller.core.ui.component.rememberIsScrolled
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens
import com.jedy.appcleaner.uninstaller.feature.uninstall.ResumeUninstallBanner

/**
 * CONTRACT (frozen signature). The Home dashboard (design review §2.6: status + one action):
 * the shared top bar · the storage gauge · one state-driven hero card ("Scan my phone", or
 * "You can free up X" → Review) · the Unused · Large · Cache rows, each opening that Apps filter
 * ([onOpenApps]). Lifetime totals live in History, the full list on Apps.
 *
 * The premium-lapse line sits at the bottom so its late arrival never pushes the layout, and every
 * block keeps its final height while loading. The NavHost applies no insets: the top bar pads the
 * status bar, the list pads the navigation bar (+16dp) and horizontal cutouts.
 */
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenPaywall: (PaywallSource) -> Unit,
    onOpenUsageAccess: (UsageAccessTrigger) -> Unit,
    onBatchStarted: (batchId: Long) -> Unit,
    onOpenScan: () -> Unit = {},
    onOpenApps: (HomeTab) -> Unit = {},
) {
    val viewModel: HomeViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val requestedTab by viewModel.requestedTab.collectAsStateWithLifecycle()
    val latestOnOpenApps by rememberUpdatedState(onOpenApps)
    val listState = rememberLazyListState()

    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }
    // Reminder deep link: the tab it asks for lives on Apps now, so Home forwards it once.
    LaunchedEffect(requestedTab) {
        val tab = requestedTab ?: return@LaunchedEffect
        viewModel.consumeTabRequest()
        latestOnOpenApps(tab)
    }

    Column(Modifier.fillMaxSize().background(AppTheme.colors.background)) {
        HomeTopBar(onOpenHistory = onOpenHistory, onOpenSettings = onOpenSettings, scrolled = rememberIsScrolled(listState))
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            contentPadding = listContentPadding(top = Dimens.space8),
        ) {
            item(key = "resume", contentType = "banner") {
                ResumeUninstallBanner(onContinue = onBatchStarted, modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.gutter))
            }
            item(key = "gauge", contentType = "gauge") {
                StorageHero(
                    storage = state.storage,
                    usedFraction = state.usedFraction,
                    severity = state.storageSeverity,
                    modifier = Modifier.padding(horizontal = Dimens.gutter),
                )
            }
            item(key = "hero", contentType = "hero") {
                AnimatedContent(
                    targetState = state.heroResult,
                    contentKey = { it != null },
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "heroCard",
                    modifier = Modifier.padding(start = Dimens.gutter, end = Dimens.gutter, top = Dimens.space24),
                ) { result ->
                    HomeHeroCard(
                        result = result,
                        onScan = onOpenScan,
                        onReview = { onOpenApps(state.reviewTab) },
                    )
                }
            }
            item(key = "categories_title", contentType = "header") {
                SectionHeader(
                    title = stringResource(R.string.home_stats_title),
                    modifier = Modifier.padding(
                        start = Dimens.gutter,
                        end = Dimens.gutter,
                        top = Dimens.sectionGap,
                        bottom = Dimens.headingToContent,
                    ),
                )
            }
            item(key = "categories", contentType = "categories") {
                CategoryCard(
                    rows = state.categories,
                    onOpen = { onOpenApps(it.tab) },
                    onAllowAccess = { onOpenUsageAccess(UsageAccessTrigger.STORAGE_CARD) },
                    modifier = Modifier.padding(horizontal = Dimens.gutter),
                )
            }
            // Last on the screen: arriving ~2.5 s after launch, it pushes nothing down.
            item(key = "premium_ended", contentType = "banner") {
                AnimatedVisibility(visible = state.showPremiumEnded, enter = fadeIn(), exit = fadeOut()) {
                    PremiumEndedBanner(
                        onRenew = {
                            viewModel.onDismissPremiumEnded()
                            onOpenPaywall(PaywallSource.SETTINGS)
                        },
                        onDismiss = viewModel::onDismissPremiumEnded,
                        modifier = Modifier.padding(start = Dimens.gutter, end = Dimens.gutter, top = Dimens.cardGap),
                    )
                }
            }
        }
    }
}

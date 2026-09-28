package com.jedy.appcleaner.uninstaller.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.format.formatBytes
import com.jedy.appcleaner.uninstaller.core.model.HomeTab
import com.jedy.appcleaner.uninstaller.core.model.LargeApps
import com.jedy.appcleaner.uninstaller.core.model.PaywallSource
import com.jedy.appcleaner.uninstaller.core.model.UsageAccessTrigger
import com.jedy.appcleaner.uninstaller.core.ui.component.AppRow
import com.jedy.appcleaner.uninstaller.core.ui.component.SecondaryButton
import com.jedy.appcleaner.uninstaller.core.ui.component.SectionHeader
import com.jedy.appcleaner.uninstaller.core.ui.component.SeverityChip
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens
import com.jedy.appcleaner.uninstaller.core.ui.theme.Severity
import com.jedy.appcleaner.uninstaller.feature.uninstall.ResumeUninstallBanner

/**
 * CONTRACT (frozen signature). The Home dashboard (redesign): wordmark bar · resume and
 * premium-ended banners · the storage hero · "Scan my phone" or the Last scan card · a 2×2 of
 * real numbers · the biggest apps. The list itself lives on Apps now ([onOpenApps]).
 *
 * The urgency here is honest by construction: the gauge colour, the headline and every red
 * figure are computed by `SeverityRules` from the user's own measurements. The NavHost applies
 * no insets, so the screen handles status bar, navigation bar and cutout itself.
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
    val requestedTab by viewModel.requestedTab.collectAsStateWithLifecycle()
    val latestOnOpenApps by rememberUpdatedState(onOpenApps)
    val context = LocalContext.current
    val sharePercent = rememberPercentFormat(fractionDigits = 1)

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

    val bottom = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
    val allowAccess = { onOpenUsageAccess(UsageAccessTrigger.STORAGE_CARD) }

    Column(
        Modifier
            .fillMaxSize()
            .background(AppTheme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        HomeTopBar(onOpenHistory = onOpenHistory, onOpenSettings = onOpenSettings)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottom + Dimens.gutterLarge),
        ) {
            item(key = "banners", contentType = "banner") {
                Column(Modifier.padding(horizontal = Dimens.gutter)) {
                    ResumeUninstallBanner(onContinue = onBatchStarted, modifier = Modifier.fillMaxWidth())
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
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
            }
            item(key = "hero", contentType = "hero") {
                StorageHero(
                    storage = state.storage,
                    usedFraction = state.usedFraction,
                    severity = state.storageSeverity,
                    modifier = Modifier.padding(horizontal = Dimens.gutter, vertical = 12.dp),
                )
            }
            item(key = "scan", contentType = "scan") {
                AnimatedContent(
                    targetState = state.lastScan,
                    contentKey = { it != null },
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "scanEntry",
                    modifier = Modifier.padding(horizontal = Dimens.gutter, vertical = 12.dp),
                ) { last ->
                    if (last == null) ScanCallToAction(onOpenScan = onOpenScan)
                    else LastScanCard(result = last, onOpenScan = onOpenScan)
                }
            }
            item(key = "stats_title", contentType = "header") {
                SectionHeader(
                    title = stringResource(R.string.home_stats_title),
                    modifier = Modifier.padding(start = Dimens.gutter, end = Dimens.gutter, top = 20.dp, bottom = 12.dp),
                )
            }
            item(key = "stats", contentType = "stats") {
                StatGrid(
                    summary = state.summary,
                    freed = state.freed,
                    isPremium = state.isPremium,
                    hasUsageAccess = state.hasUsageAccess,
                    onOpenUnused = { onOpenApps(HomeTab.UNUSED) },
                    onOpenHogs = { onOpenApps(HomeTab.ALL) },
                    onOpenCache = { onOpenApps(HomeTab.LARGE) },
                    onOpenHistory = onOpenHistory,
                    onAllowAccess = allowAccess,
                    modifier = Modifier.padding(horizontal = Dimens.gutter),
                )
            }
            if (state.biggest.isNotEmpty()) {
                item(key = "biggest_title", contentType = "header") {
                    SectionHeader(
                        title = stringResource(R.string.home_biggest_title),
                        subtitle = stringResource(R.string.home_biggest_subtitle),
                        action = stringResource(R.string.home_see_all_short),
                        onAction = { onOpenApps(HomeTab.ALL) },
                        modifier = Modifier.padding(start = Dimens.gutter, end = 8.dp, top = 28.dp, bottom = 8.dp),
                    )
                }
                items(state.biggest, key = { "big_" + it.app.packageName }, contentType = { "app" }) { big ->
                    AppRow(
                        packageName = big.app.packageName,
                        label = big.app.label,
                        meta = state.storage?.totalBytes?.takeIf { it > 0 }
                            ?.let { total -> stringResource(R.string.home_biggest_share, sharePercent(big.bytes.toFloat() / total)) }
                            .orEmpty(),
                        onClick = { onOpenApps(HomeTab.ALL) },
                        trailingText = formatBytes(context, big.bytes),
                        large = LargeApps.isLarge(big.bytes),
                        sizeFraction = big.sizeFraction,
                        chips = if (big.severity == Severity.DANGER) {
                            { SeverityChip(stringResource(R.string.apps_chip_space_hog), Severity.DANGER, icon = Icons.Rounded.LocalFireDepartment) }
                        } else {
                            null
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
                item(key = "see_all", contentType = "action") {
                    SecondaryButton(
                        text = stringResource(R.string.home_see_all),
                        onClick = { onOpenApps(HomeTab.ALL) },
                        icon = Icons.AutoMirrored.Rounded.ArrowForward,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.gutter).padding(top = 12.dp),
                    )
                }
            }
            item(key = "end", contentType = "spacer") { Spacer(Modifier.height(8.dp)) }
        }
    }
}

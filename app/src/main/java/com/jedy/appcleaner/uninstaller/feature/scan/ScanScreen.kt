package com.jedy.appcleaner.uninstaller.feature.scan

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.format.formatBytes
import com.jedy.appcleaner.uninstaller.core.model.HomeTab
import com.jedy.appcleaner.uninstaller.core.model.PaywallSource
import com.jedy.appcleaner.uninstaller.core.model.UsageAccessTrigger
import com.jedy.appcleaner.uninstaller.core.ui.component.AppCard
import com.jedy.appcleaner.uninstaller.core.ui.component.IconBadge
import com.jedy.appcleaner.uninstaller.core.ui.component.PrimaryButton
import com.jedy.appcleaner.uninstaller.core.ui.component.SecondaryButton
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens
import com.jedy.appcleaner.uninstaller.core.ui.theme.Severity
import com.jedy.appcleaner.uninstaller.data.scan.ScanResult

/**
 * CONTRACT (frozen signature). The "Scan my phone" flow (redesign): an optional Usage Access
 * explanation → the staged scanning ring → "You can free up X" with buckets and the CTA.
 *
 * Every figure on the result is a [ScanResult] field computed from the user's own data; the
 * scanning animation only paces real work, it never makes a number up. Handles insets itself.
 */
@Composable
fun ScanScreen(
    onClose: () -> Unit,
    onOpenApps: (HomeTab) -> Unit,
    onOpenPaywall: (PaywallSource) -> Unit,
    onOpenUsageAccess: (UsageAccessTrigger) -> Unit,
) {
    val viewModel: ScanViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }
    val allowAccess = { onOpenUsageAccess(UsageAccessTrigger.SCAN) }

    Column(
        Modifier
            .fillMaxSize()
            .background(AppTheme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.scan_close), tint = AppTheme.colors.textPrimary)
            }
        }
        AnimatedContent(
            targetState = state.phase,
            contentKey = { it::class },
            transitionSpec = { (fadeIn() + slideInVertically { it / 12 }) togetherWith fadeOut() },
            label = "scanPhase",
            modifier = Modifier.weight(1f),
        ) { phase ->
            when (phase) {
                ScanPhase.Intro -> PhaseLayout(
                    actions = {
                        PrimaryButton(stringResource(R.string.scan_intro_allow), onClick = allowAccess, icon = Icons.Rounded.Lock, modifier = Modifier.fillMaxWidth())
                        SecondaryButton(stringResource(R.string.scan_intro_quick), onClick = viewModel::onQuickScan, modifier = Modifier.fillMaxWidth())
                    },
                ) { IntroContent() }
                is ScanPhase.Scanning -> PhaseLayout(actions = null) { ScanProgressContent(phase) }
                is ScanPhase.Result -> PhaseLayout(
                    actions = { ResultActions(phase.result, state.isPremium, viewModel, onOpenApps, onOpenPaywall, allowAccess) },
                ) {
                    ScanResultContent(
                        result = phase.result,
                        isPremium = state.isPremium,
                        onOpenUnused = { if (state.isPremium) onOpenApps(HomeTab.UNUSED) else onOpenPaywall(PaywallSource.SCAN_RESULT) },
                        onOpenHogs = { onOpenApps(HomeTab.ALL) },
                        onOpenCache = { if (state.isPremium) onOpenApps(HomeTab.LARGE) else onOpenPaywall(PaywallSource.SCAN_RESULT) },
                        onAllowAccess = allowAccess,
                    )
                }
                ScanPhase.Failed -> PhaseLayout(
                    actions = { PrimaryButton(stringResource(R.string.scan_error_retry), onClick = viewModel::onRetry, icon = Icons.Rounded.Refresh, modifier = Modifier.fillMaxWidth()) },
                ) { FailedContent() }
            }
        }
    }
}

/** Scrollable content with the actions pinned above the navigation bar. */
@Composable
private fun PhaseLayout(actions: (@Composable () -> Unit)?, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.gutter, vertical = 8.dp),
        ) { content() }
        if (actions != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                    .padding(horizontal = Dimens.gutter, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) { actions() }
        }
    }
}

/**
 * Result CTAs. With a real amount to free: "Free up 3.4 GB" — premium pre-selects the unused
 * apps and opens them, free users see the paywall. Without Usage Access the honest next step is
 * the full scan, so that becomes the primary action.
 */
@Composable
private fun ResultActions(
    result: ScanResult,
    isPremium: Boolean,
    viewModel: ScanViewModel,
    onOpenApps: (HomeTab) -> Unit,
    onOpenPaywall: (PaywallSource) -> Unit,
    onAllowAccess: () -> Unit,
) {
    val context = LocalContext.current
    when {
        !result.hasUsageAccess -> PrimaryButton(
            stringResource(R.string.scan_cta_allow_full),
            onClick = onAllowAccess,
            icon = Icons.Rounded.Radar,
            modifier = Modifier.fillMaxWidth(),
        )
        result.reclaimableBytes > 0 -> PrimaryButton(
            stringResource(R.string.scan_cta_free_up, formatBytes(context, result.reclaimableBytes)),
            onClick = {
                if (isPremium) {
                    viewModel.preselectUnused(result)
                    onOpenApps(HomeTab.UNUSED)
                } else {
                    onOpenPaywall(PaywallSource.SCAN_RESULT)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    SecondaryButton(stringResource(R.string.scan_cta_review), onClick = { onOpenApps(HomeTab.ALL) }, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun IntroContent() {
    val colors = AppTheme.colors
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(12.dp))
        IconBadge(icon = Icons.Rounded.Radar, size = 88.dp)
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.scan_intro_access_title),
            style = MaterialTheme.typography.headlineLarge,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.scan_intro_access_body),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                IntroPoint(Icons.Rounded.HourglassBottom, stringResource(R.string.scan_intro_point_unused), Severity.DANGER)
                IntroPoint(Icons.Rounded.LocalFireDepartment, stringResource(R.string.scan_intro_point_hogs), Severity.WARNING)
                IntroPoint(Icons.Rounded.Layers, stringResource(R.string.scan_intro_point_cache), Severity.OK)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.scan_intro_quick_hint),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun IntroPoint(icon: ImageVector, text: String, severity: Severity) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon = icon, severity = severity)
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, color = AppTheme.colors.textPrimary)
    }
}

@Composable
private fun FailedContent() {
    val colors = AppTheme.colors
    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.scan_error_title), style = MaterialTheme.typography.headlineSmall, color = colors.textPrimary, textAlign = TextAlign.Center)
        Text(stringResource(R.string.scan_error_body), style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary, textAlign = TextAlign.Center)
    }
}

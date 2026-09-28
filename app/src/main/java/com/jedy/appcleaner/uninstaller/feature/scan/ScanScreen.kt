package com.jedy.appcleaner.uninstaller.feature.scan

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
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
import com.jedy.appcleaner.uninstaller.core.ui.component.AppTopBar
import com.jedy.appcleaner.uninstaller.core.ui.component.IconBadge
import com.jedy.appcleaner.uninstaller.core.ui.component.LockIcon
import com.jedy.appcleaner.uninstaller.core.ui.component.PrimaryButton
import com.jedy.appcleaner.uninstaller.core.ui.component.TopBarAction
import com.jedy.appcleaner.uninstaller.core.ui.component.bottomContentPadding
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens
import com.jedy.appcleaner.uninstaller.data.scan.ScanResult
import com.jedy.appcleaner.uninstaller.feature.home.TextAction

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

    Column(Modifier.fillMaxSize().background(AppTheme.colors.background)) {
        AppTopBar(
            title = null,
            actions = { TopBarAction(Icons.Outlined.Close, stringResource(R.string.scan_close), onClose) },
        )
        AnimatedContent(
            targetState = state.phase,
            contentKey = { it::class },
            transitionSpec = { (fadeIn() + slideInVertically { it / 12 }) togetherWith fadeOut() },
            label = "scanPhase",
            modifier = Modifier
                .weight(1f)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        ) { phase ->
            when (phase) {
                ScanPhase.Intro -> PhaseLayout(
                    actions = {
                        PrimaryButton(stringResource(R.string.scan_intro_allow), onClick = allowAccess, icon = Icons.Outlined.Lock, modifier = Modifier.fillMaxWidth())
                        TextAction(stringResource(R.string.scan_intro_quick), onClick = viewModel::onQuickScan, modifier = Modifier.fillMaxWidth())
                    },
                ) { IntroContent() }
                is ScanPhase.Scanning -> PhaseLayout(actions = null, centered = true) { ScanProgressContent(phase) }
                is ScanPhase.Result -> PhaseLayout(
                    actions = { ResultActions(phase.result, state.isPremium, viewModel, onOpenApps, onOpenPaywall, allowAccess) },
                ) {
                    ScanResultContent(
                        result = phase.result,
                        isPremium = state.isPremium,
                        // Locked rows open their filter too: its own teaser explains Pro there.
                        onOpen = { onOpenApps(it.tab) },
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

/**
 * Scrollable content with the actions pinned above the navigation bar. [centered] content (the
 * scanning ring) sits in the middle of the free height instead of hugging the top.
 */
@Composable
private fun PhaseLayout(actions: (@Composable () -> Unit)?, centered: Boolean = false, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        // Without pinned actions the content itself must end above the navigation bar (+16dp).
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(if (actions == null) Modifier.bottomContentPadding() else Modifier),
        ) {
            val viewport = maxHeight
            Box(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = if (centered) viewport else 0.dp)
                    .padding(horizontal = Dimens.gutter, vertical = Dimens.space8),
                contentAlignment = if (centered) Alignment.Center else Alignment.TopCenter,
            ) { content() }
        }
        if (actions != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                    .padding(start = Dimens.gutter, end = Dimens.gutter, top = Dimens.space12, bottom = Dimens.space16),
                verticalArrangement = Arrangement.spacedBy(Dimens.stackedButtonGap),
            ) { actions() }
        }
    }
}

/**
 * Result CTAs (design review §2.6): the label says what the tap frees and how many apps it
 * touches ([ScanCtas]). Free users get what the free app can do (Large, then cache); Unused is
 * Pro and carries the one quiet lock ([ProHint], or the "Review with Pro" button when it is all
 * there is). The secondary "Review all apps" is a text action, [Dimens.stackedButtonGap] below.
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
    when (val cta = ScanCtas.primary(result, isPremium)) {
        ScanCta.AllowAccess -> PrimaryButton(
            stringResource(R.string.scan_cta_allow_full),
            onClick = onAllowAccess,
            icon = Icons.Rounded.Radar,
            modifier = Modifier.fillMaxWidth(),
        )
        is ScanCta.ReviewApps -> PrimaryButton(
            pluralStringResource(R.plurals.scan_cta_review_apps, cta.count, cta.count, formatBytes(context, cta.bytes)),
            onClick = {
                viewModel.preselectReview(result)
                onOpenApps(cta.tab)
            },
            modifier = Modifier.fillMaxWidth(),
        )
        is ScanCta.ReviewCache -> PrimaryButton(
            stringResource(R.string.scan_cta_review_cache, formatBytes(context, cta.bytes)),
            onClick = { onOpenApps(HomeTab.CACHE) },
            modifier = Modifier.fillMaxWidth(),
        )
        is ScanCta.ReviewLarge -> PrimaryButton(
            pluralStringResource(R.plurals.scan_cta_review_large, cta.count, cta.count, formatBytes(context, cta.bytes)),
            onClick = {
                viewModel.preselectLarge()
                onOpenApps(HomeTab.LARGE)
            },
            modifier = Modifier.fillMaxWidth(),
        )
        ScanCta.Unlock -> PrimaryButton(
            stringResource(R.string.scan_cta_unlock),
            onClick = { onOpenPaywall(PaywallSource.SCAN_RESULT) },
            icon = Icons.Outlined.Lock,
            modifier = Modifier.fillMaxWidth(),
        )
        ScanCta.None -> Unit
    }
    if (ScanCtas.showProHint(result, isPremium)) {
        ProHint(result.unusedCount, onClick = { onOpenPaywall(PaywallSource.SCAN_RESULT) })
    }
    TextAction(stringResource(R.string.scan_cta_review), onClick = { onOpenApps(HomeTab.ALL) }, modifier = Modifier.fillMaxWidth())
}

/** The quiet Pro line under a free button: lock + "5 unused apps · Pro", in secondary text. */
@Composable
private fun ProHint(unusedCount: Int, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.minTouchTarget)
            .clip(RoundedCornerShape(Dimens.chipRadius))
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.space16),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LockIcon(size = 16.dp)
        Spacer(Modifier.width(Dimens.space8))
        Text(
            pluralStringResource(R.plurals.scan_pro_hint_unused, unusedCount, unusedCount),
            style = MaterialTheme.typography.labelLarge,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
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
                IntroPoint(Icons.Outlined.HourglassEmpty, stringResource(R.string.scan_intro_point_unused))
                IntroPoint(Icons.Outlined.Inventory2, stringResource(R.string.scan_intro_point_hogs))
                IntroPoint(Icons.Outlined.Layers, stringResource(R.string.scan_intro_point_cache))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.scan_intro_quick_hint),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun IntroPoint(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon = icon)
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

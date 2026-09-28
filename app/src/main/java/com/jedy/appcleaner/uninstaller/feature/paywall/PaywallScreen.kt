package com.jedy.appcleaner.uninstaller.feature.paywall

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.format.formatBytes
import com.jedy.appcleaner.uninstaller.core.model.PaywallSource
import com.jedy.appcleaner.uninstaller.core.ui.component.PrimaryButton
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens
import com.jedy.appcleaner.uninstaller.data.billing.BillingLinks
import com.jedy.appcleaner.uninstaller.data.billing.openExternalUrl
import kotlinx.coroutines.launch

/**
 * CONTRACT (frozen signature). PRD §4 Screen 3 — the one paywall, opened from onboarding (once)
 * and from every premium surface.
 *
 * Rules this screen keeps:
 *  - The close (X) is drawn from the very first frame and always works, whatever the billing
 *    state; system back behaves the same (PRD §3 step 3, §6 item 16).
 *  - The plan card never spins forever and is never blank: loading resolves to a price, an
 *    offline card with "Try again", or a neutral "not available yet" card.
 *  - "Free" is only promised to accounts Play still grants a trial (PRD §6 item 19).
 *  - Gold appears only as the premium accent; the CTA is the kit's spring-green [PrimaryButton]
 *    like every primary action. Layout and copy are owned by the owner's team: this file only
 *    maps them onto the v2 kit and tokens (no borders on plain cards, green text via accentText).
 */
@Composable
fun PaywallScreen(
    source: PaywallSource,
    onClose: () -> Unit,
) {
    val viewModel: PaywallViewModel = hiltViewModel()
    LaunchedEffect(source) { viewModel.onShown(source) }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val context = LocalContext.current
    // Localized by LocalizedContent and configuration-aware, for strings resolved outside composition.
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val dismiss: () -> Unit = {
        viewModel.onDismiss()
        onClose()
    }
    BackHandler(onBack = dismiss)

    // Keyed on resources too, so a language switch re-resolves messages in the new language.
    LaunchedEffect(viewModel, resources) {
        viewModel.events.collect { event ->
            when (event) {
                PaywallEvent.Close -> onClose()
                is PaywallEvent.Message -> scope.launch {
                    snackbarHostState.showSnackbar(resources.getString(event.messageRes))
                }
            }
        }
    }
    val showLinkError: () -> Unit = {
        scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.billing_link_unavailable)) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.background),
    ) {
        AnimatedContent(
            targetState = state.isPremium,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            label = "paywallPremium",
        ) { isPremium ->
            if (isPremium) {
                PremiumConfirmation(onContinue = onClose)
            } else {
                PaywallContent(
                    state = state,
                    onPurchase = { viewModel.purchase(activity) },
                    onRetry = viewModel::retry,
                    onRestore = viewModel::restore,
                    onOpenTerms = { if (!context.openExternalUrl(BillingLinks.TERMS_URL)) showLinkError() },
                    onOpenPrivacy = { if (!context.openExternalUrl(BillingLinks.PRIVACY_URL)) showLinkError() },
                )
            }
        }

        // Outside the animated content, so it exists on the first frame and never fades.
        IconButton(
            onClick = if (state.isPremium) onClose else dismiss,
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(4.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.action_close),
                tint = AppTheme.colors.textSecondary,
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 140.dp),
        )
    }
}

@Composable
private fun PaywallContent(
    state: PaywallUiState,
    onPurchase: () -> Unit,
    onRetry: () -> Unit,
    onRestore: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.gutterLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(52.dp)) // room for the close button
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(colors.premiumGoldSurface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.WorkspacePremium,
                    contentDescription = null,
                    tint = colors.premiumGold,
                    modifier = Modifier.size(34.dp),
                )
            }
            Text(
                text = headlineText(state.headline),
                style = MaterialTheme.typography.headlineLarge,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Dimens.gutter),
            )
            Text(
                text = stringResource(
                    if (state.headline is PaywallHeadline.Default) R.string.paywall_subtitle
                    else R.string.paywall_finding_subtitle
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )

            Spacer(Modifier.height(Dimens.gutterLarge))
            BenefitsCard()
            Spacer(Modifier.height(Dimens.gutter))
            PlanCard(plan = state.plan, onRetry = onRetry)
            Spacer(Modifier.height(Dimens.gutter))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.gutterLarge)
                .padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The kit button has no busy state, so while a purchase is in flight it keeps its green,
            // drops its label and ignores taps, and a spinner sits on top.
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PrimaryButton(
                    text = if (state.purchasing) "" else stringResource(ctaLabel(state.plan)),
                    onClick = { if (!state.purchasing) onPurchase() },
                    enabled = state.plan.canPurchase,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.purchasing) {
                    CircularProgressIndicator(
                        color = colors.onAccent,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Text(
                text = stringResource(
                    when (val plan = state.plan) {
                        is PaywallPlan.Weekly -> R.string.paywall_terms_plain
                        is PaywallPlan.NotConfigured -> if (plan.debugUnlock) R.string.paywall_debug_note else R.string.paywall_terms_trial
                        else -> R.string.paywall_terms_trial
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp),
            )
            Row(
                modifier = Modifier.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                FooterLink(
                    text = stringResource(R.string.paywall_restore),
                    onClick = onRestore,
                    enabled = !state.restoring,
                )
                FooterDot()
                FooterLink(text = stringResource(R.string.paywall_terms), onClick = onOpenTerms)
                FooterDot()
                FooterLink(text = stringResource(R.string.paywall_privacy), onClick = onOpenPrivacy)
            }
        }
    }
}

@Composable
private fun headlineText(headline: PaywallHeadline): String {
    val context = LocalContext.current
    return when (headline) {
        PaywallHeadline.Default -> stringResource(R.string.paywall_headline)
        is PaywallHeadline.Unused -> pluralStringResource(
            R.plurals.paywall_finding_unused,
            headline.count,
            headline.count,
            headline.thresholdDays,
            formatBytes(context, headline.bytes),
        )
        is PaywallHeadline.Large -> pluralStringResource(
            R.plurals.paywall_finding_large,
            headline.count,
            headline.count,
            formatBytes(context, headline.bytes),
        )
    }
}

private fun ctaLabel(plan: PaywallPlan): Int = when (plan) {
    is PaywallPlan.Weekly -> R.string.paywall_cta_subscribe
    is PaywallPlan.NotConfigured -> if (plan.debugUnlock) R.string.paywall_cta_debug else R.string.paywall_cta_trial
    else -> R.string.paywall_cta_trial
}

private data class Benefit(val icon: ImageVector, val titleRes: Int, val bodyRes: Int)

private val benefits = listOf(
    Benefit(Icons.Rounded.HourglassBottom, R.string.paywall_benefit_unused_title, R.string.paywall_benefit_unused_body),
    Benefit(Icons.Rounded.PieChart, R.string.paywall_benefit_storage_title, R.string.paywall_benefit_storage_body),
    Benefit(Icons.Rounded.NotificationsActive, R.string.paywall_benefit_reminders_title, R.string.paywall_benefit_reminders_body),
    Benefit(Icons.Rounded.Block, R.string.paywall_benefit_no_ads_title, R.string.paywall_benefit_no_ads_body),
)

@Composable
private fun BenefitsCard() {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(Dimens.cardRadius)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .padding(horizontal = Dimens.gutter, vertical = 6.dp),
    ) {
        benefits.forEach { benefit ->
            Row(
                modifier = Modifier.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.accentSurface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(benefit.icon, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(Dimens.gutterSmall))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(benefit.titleRes),
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = stringResource(benefit.bodyRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanCard(plan: PaywallPlan, onRetry: () -> Unit) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(Dimens.cardRadius)
    val selectable = plan is PaywallPlan.Trial || plan is PaywallPlan.Weekly
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 84.dp)
            .clip(shape)
            .background(if (selectable) colors.accentSurface else colors.surface)
            // Only the selected plan gets an edge: the green ring *is* the selection state.
            .then(if (selectable) Modifier.border(BorderStroke(2.dp, colors.accent), shape) else Modifier)
            .padding(Dimens.gutter),
        contentAlignment = Alignment.CenterStart,
    ) {
        when (plan) {
            is PaywallPlan.Trial -> PlanRow(
                title = pluralStringResource(R.plurals.paywall_plan_trial, plan.trialDays, plan.trialDays, plan.price),
                caption = stringResource(R.string.paywall_plan_caption_trial),
            )
            is PaywallPlan.Weekly -> PlanRow(
                title = stringResource(R.string.paywall_plan_weekly, plan.price),
                caption = stringResource(R.string.paywall_plan_caption_weekly),
            )
            PaywallPlan.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = colors.accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(Dimens.gutterSmall))
                Text(
                    text = stringResource(R.string.paywall_plan_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
            PaywallPlan.Offline -> PlanMessage(
                icon = Icons.Rounded.CloudOff,
                title = stringResource(R.string.paywall_offline),
                body = null,
                onRetry = onRetry,
            )
            PaywallPlan.Unavailable, is PaywallPlan.NotConfigured -> PlanMessage(
                icon = Icons.Rounded.Schedule,
                title = stringResource(R.string.paywall_not_available),
                body = stringResource(R.string.paywall_not_available_body),
                onRetry = onRetry,
            )
        }
    }
}

@Composable
private fun PlanRow(title: String, caption: String) {
    val colors = AppTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.width(Dimens.gutterSmall))
        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun PlanMessage(icon: ImageVector, title: String, body: String?, onRetry: () -> Unit) {
    val colors = AppTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(Dimens.gutterSmall))
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
            if (body != null) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        // Compact tonal pill: the kit's SecondaryButton is sized for full-width actions.
        Text(
            text = stringResource(R.string.action_retry),
            style = MaterialTheme.typography.labelMedium,
            color = colors.textPrimary,
            maxLines = 1,
            modifier = Modifier
                .clip(RoundedCornerShape(Dimens.chipRadius))
                .background(colors.surfaceMuted)
                .clickable(onClick = onRetry)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun FooterLink(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = AppTheme.colors.textSecondary),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun FooterDot() {
    Text(text = "·", style = MaterialTheme.typography.labelMedium, color = AppTheme.colors.textSecondary)
}

/** Shown instead of the offer when the user already has premium (or just restored it). */
@Composable
private fun PremiumConfirmation(onContinue: () -> Unit) {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = Dimens.gutterLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(colors.premiumGoldSurface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.WorkspacePremium,
                contentDescription = null,
                tint = colors.premiumGold,
                modifier = Modifier.size(52.dp),
            )
        }
        Text(
            text = stringResource(R.string.paywall_premium_title),
            style = MaterialTheme.typography.headlineLarge,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Dimens.gutterLarge),
        )
        Text(
            text = stringResource(R.string.paywall_premium_body),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        PrimaryButton(
            text = stringResource(R.string.action_continue),
            onClick = onContinue,
            modifier = Modifier
                .padding(top = Dimens.gutterLarge)
                .fillMaxWidth(),
        )
    }
}

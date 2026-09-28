package com.jedy.appcleaner.uninstaller.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.StarRate
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jedy.appcleaner.uninstaller.BuildConfig
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.locale.AppLanguage
import com.jedy.appcleaner.uninstaller.core.model.PaywallSource
import com.jedy.appcleaner.uninstaller.core.model.UsageAccessTrigger
import com.jedy.appcleaner.uninstaller.core.ui.component.AppTopBar
import com.jedy.appcleaner.uninstaller.core.ui.component.LanguagePickerSheet
import com.jedy.appcleaner.uninstaller.core.ui.component.LargeTitle
import com.jedy.appcleaner.uninstaller.core.ui.component.ProBadge
import com.jedy.appcleaner.uninstaller.core.ui.component.SegmentOption
import com.jedy.appcleaner.uninstaller.core.ui.component.bottomContentPadding
import com.jedy.appcleaner.uninstaller.core.ui.component.rememberIsLargeTitleCollapsed
import com.jedy.appcleaner.uninstaller.core.ui.component.rememberIsScrolled
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens
import com.jedy.appcleaner.uninstaller.data.billing.BillingLinks
import com.jedy.appcleaner.uninstaller.data.billing.openExternalUrl
import com.jedy.appcleaner.uninstaller.data.billing.startActivitySafely
import com.jedy.appcleaner.uninstaller.data.prefs.AppPreferences
import com.jedy.appcleaner.uninstaller.data.prefs.ThemeMode
import kotlinx.coroutines.launch

/**
 * CONTRACT (frozen signature). PRD §4 Screen 13, redesigned for v2 (Sept 2026).
 *
 * Layout, top to bottom: the shared AppTopBar (its small title fades in once the big one scrolls
 * away), the big "Settings" title, Preferences, Reminders, then Subscription led by the Pro card
 * (settings first, the sell second), all on the 20dp gutter. Choices with two or
 * three values (theme, size mode, reminder threshold) are inline segmented controls instead of dialogs,
 * because seeing every option at once is faster than opening a dialog to find them. The old
 * About section is gone: version and legal links are a one-line footer, since nobody visits
 * Settings to read them.
 *
 * Premium controls stay visible to free users with the gold PRO mark and open the paywall when
 * tapped: showing what premium adds is the sale, hiding it would not be. Usage Access and
 * notification state are re-read on every resume, because both are changed in system Settings.
 */
@Composable
fun SettingsScreen(
    language: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    onBack: () -> Unit,
    onOpenPaywall: (PaywallSource) -> Unit,
    onOpenUsageAccess: (UsageAccessTrigger) -> Unit,
) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Localized by LocalizedContent and configuration-aware, for strings resolved outside composition.
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var languageSheetVisible by rememberSaveable { mutableStateOf(false) }

    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose { }
    }
    // Keyed on resources too: the language can change on this very screen.
    LaunchedEffect(viewModel, resources) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.Message -> scope.launch {
                    snackbarHostState.showSnackbar(resources.getString(event.messageRes))
                }
            }
        }
    }
    val showLinkError: () -> Unit = {
        scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.billing_link_unavailable)) }
    }
    val openUrl: (String, String?) -> Unit = { url, fallback ->
        if (!context.openExternalUrl(url, fallback)) showLinkError()
    }

    val scrollState = rememberScrollState()
    val scrolled = rememberIsScrolled(scrollState)
    val titleCollapsed = rememberIsLargeTitleCollapsed(scrollState)

    Scaffold(
        containerColor = AppTheme.colors.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        snackbarHost = { SnackbarHost(snackbarHostState, Modifier.navigationBarsPadding()) },
        topBar = {
            AppTopBar(
                title = stringResource(R.string.settings_title),
                onBack = onBack,
                scrolled = scrolled,
                titleVisible = titleCollapsed,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .bottomContentPadding(),
        ) {
            LargeTitle(stringResource(R.string.settings_title))

            Column(Modifier.padding(horizontal = Dimens.gutter)) {
                PreferencesGroup(
                    state = state,
                    language = language,
                    onOpenLanguage = { languageSheetVisible = true },
                    onThemeSelected = viewModel::setThemeMode,
                    onUsageAccess = {
                        if (state.usageAccessGranted) {
                            if (!context.startActivitySafely(viewModel.usageAccessSettingsIntent())) showLinkError()
                        } else {
                            onOpenUsageAccess(UsageAccessTrigger.SETTINGS)
                        }
                    },
                    onRate = {
                        openUrl(
                            BillingLinks.playListingMarket(context.packageName),
                            BillingLinks.playListingWeb(context.packageName),
                        )
                    },
                )

                RemindersGroup(
                    state = state,
                    onToggle = { enabled ->
                        if (state.isPremium) viewModel.setRemindersEnabled(enabled)
                        else onOpenPaywall(PaywallSource.REMINDER_TOGGLE)
                    },
                    onThreshold = { days ->
                        if (state.isPremium) viewModel.setUnusedThresholdDays(days)
                        else if (days != state.thresholdDays) onOpenPaywall(PaywallSource.REMINDER_TOGGLE)
                    },
                    onAllowNotifications = {
                        if (!context.startActivitySafely(viewModel.notificationSettingsIntent())) showLinkError()
                    },
                    onAllowUsageAccess = { onOpenUsageAccess(UsageAccessTrigger.SETTINGS) },
                )

                // Settings first, the sell second (design review §2.6): the Pro card leads the
                // Subscription section instead of opening the screen.
                SettingsGroup(
                    title = stringResource(R.string.settings_section_subscription),
                    lead = {
                        if (state.isPremium) {
                            ProActiveCard(hasStoreSubscription = state.hasStoreSubscription)
                        } else {
                            ProHeroCard(
                                trialAvailable = state.trialAvailable,
                                onUpgrade = { onOpenPaywall(PaywallSource.SETTINGS) },
                            )
                        }
                    },
                ) {
                    SettingsRow(
                        icon = Icons.Rounded.ManageAccounts,
                        title = stringResource(R.string.settings_manage_subscription),
                        onClick = { openUrl(viewModel.manageSubscriptionUrl(), null) },
                    )
                    SettingsRow(
                        icon = Icons.Rounded.Restore,
                        title = stringResource(R.string.settings_restore),
                        onClick = viewModel::restore,
                        enabled = !state.restoring,
                        trailing = if (state.restoring) {
                            {
                                CircularProgressIndicator(
                                    color = AppTheme.colors.accent,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        } else {
                            null
                        },
                        showChevron = !state.restoring,
                    )
                }

                // Debug builds only: BuildConfig.DEBUG is a compile-time false in release, so R8
                // drops this whole group (and the force-premium switch) from the shipped app.
                if (BuildConfig.DEBUG) {
                    SettingsGroup(title = stringResource(R.string.settings_section_debug)) {
                        SettingsSwitchRow(
                            icon = Icons.Rounded.BugReport,
                            title = stringResource(R.string.settings_debug_force_premium),
                            subtitle = null,
                            checked = state.debugForcePremium,
                            onCheckedChange = viewModel::setDebugForcePremium,
                        )
                        SettingsRow(
                            icon = Icons.Rounded.Cloud,
                            title = stringResource(R.string.settings_debug_revenuecat),
                            value = stringResource(
                                if (state.billingConfigured) R.string.settings_debug_configured
                                else R.string.settings_debug_not_configured
                            ),
                            onClick = null,
                        )
                    }
                }

                SettingsFooter(
                    onPrivacy = { openUrl(BillingLinks.PRIVACY_URL, null) },
                    onTerms = { openUrl(BillingLinks.TERMS_URL, null) },
                )
            }
        }
    }

    if (languageSheetVisible) {
        LanguagePickerSheet(
            selected = language,
            onSelect = {
                languageSheetVisible = false
                onLanguageSelected(it)
            },
            onDismiss = { languageSheetVisible = false },
        )
    }
}

@Composable
private fun PreferencesGroup(
    state: SettingsUiState,
    language: AppLanguage,
    onOpenLanguage: () -> Unit,
    onThemeSelected: (ThemeMode) -> Unit,
    onUsageAccess: () -> Unit,
    onRate: () -> Unit,
) {
    // First group: the large title above already ends in 12dp, so 12 more (not a 32dp section gap).
    SettingsGroup(title = stringResource(R.string.settings_section_preferences), topSpacing = Dimens.space12) {
        SettingsRow(
            icon = Icons.Rounded.Language,
            title = stringResource(R.string.settings_language),
            value = language.nativeName,
            onClick = onOpenLanguage,
        )
        SegmentedSettingRow(
            icon = Icons.Rounded.DarkMode,
            title = stringResource(R.string.settings_theme),
            segments = ThemeMode.entries.map { SegmentOption(stringResource(it.labelRes())) },
            selectedIndex = ThemeMode.entries.indexOf(state.themeMode),
            onSelect = { onThemeSelected(ThemeMode.entries[it]) },
        )
        SettingsRow(
            icon = Icons.Rounded.QueryStats,
            title = stringResource(R.string.settings_usage_access),
            subtitle = stringResource(R.string.settings_usage_access_body),
            onClick = onUsageAccess,
            // Status under the title, not in a trailing column, so the description keeps the width.
            status = { UsageAccessStatus(granted = state.usageAccessGranted) },
        )
        SettingsRow(
            icon = Icons.Rounded.StarRate,
            title = stringResource(R.string.settings_rate),
            onClick = onRate,
        )
    }
}

@Composable
private fun RemindersGroup(
    state: SettingsUiState,
    onToggle: (Boolean) -> Unit,
    onThreshold: (Int) -> Unit,
    onAllowNotifications: () -> Unit,
    onAllowUsageAccess: () -> Unit,
) {
    val locked = !state.isPremium
    SettingsGroup(title = stringResource(R.string.settings_section_reminders)) {
        SettingsSwitchRow(
            icon = Icons.Rounded.NotificationsActive,
            title = stringResource(R.string.settings_reminders_toggle),
            subtitle = stringResource(R.string.settings_reminders_body),
            checked = state.isPremium && state.remindersEnabled,
            onCheckedChange = onToggle,
            // Under the title, never beside it: a pill on the title line made it wrap at 360dp.
            status = if (locked) ({ ProBadge() }) else null,
        )
        val thresholds = AppPreferences.UNUSED_THRESHOLDS
        SegmentedSettingRow(
            icon = Icons.Rounded.Timelapse,
            title = stringResource(R.string.settings_reminders_threshold),
            // The value the scan really uses is always shown selected, Pro or not (design review §9:
            // an empty control next to "haven't opened in 60 days" looked broken). A free user
            // sees the other values locked; the row's PRO badge above is the one Pro marker.
            segments = thresholds.map {
                SegmentOption(
                    label = pluralStringResource(R.plurals.settings_threshold_days, it, it),
                    locked = locked && it != state.thresholdDays,
                )
            },
            selectedIndex = thresholds.indexOf(state.thresholdDays).coerceAtLeast(0),
            onSelect = { onThreshold(thresholds[it]) },
        )
        AnimatedVisibility(
            visible = !state.notificationsEnabled,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            NoticeTile(
                icon = Icons.Rounded.NotificationsOff,
                text = stringResource(R.string.settings_notifications_off),
                action = stringResource(R.string.settings_notifications_allow),
                onAction = onAllowNotifications,
            )
        }
        AnimatedVisibility(
            visible = state.isPremium && state.remindersEnabled && !state.usageAccessGranted,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            NoticeTile(
                icon = Icons.Rounded.QueryStats,
                text = stringResource(R.string.settings_reminders_needs_access),
                action = stringResource(R.string.settings_usage_access_allow),
                onAction = onAllowUsageAccess,
            )
        }
    }
}

/**
 * "App Cleaner 1.0 · Privacy · Terms": everything the old About section held, in one line. The
 * version gets the same side padding as the links, so every "·" has an equal gap on both sides.
 */
@Composable
private fun SettingsFooter(onPrivacy: () -> Unit, onTerms: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Dimens.sectionGap),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.settings_footer_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
            modifier = Modifier.padding(horizontal = FooterItemPadding),
        )
        FooterDot()
        FooterLink(stringResource(R.string.settings_footer_privacy), onPrivacy)
        FooterDot()
        FooterLink(stringResource(R.string.settings_footer_terms), onTerms)
    }
}

@Composable
private fun FooterDot() {
    Text("·", style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted, modifier = Modifier.padding(horizontal = 2.dp))
}

@Composable
private fun FooterLink(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = AppTheme.colors.textSecondary,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            // A 48dp-tall touch target around small text.
            .padding(horizontal = FooterItemPadding, vertical = 15.dp),
    )
}

private val FooterItemPadding = 6.dp

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}

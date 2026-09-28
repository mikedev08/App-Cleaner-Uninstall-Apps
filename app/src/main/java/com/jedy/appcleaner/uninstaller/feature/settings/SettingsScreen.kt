package com.jedy.appcleaner.uninstaller.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.StarRate
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jedy.appcleaner.uninstaller.BuildConfig
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.locale.AppLanguage
import com.jedy.appcleaner.uninstaller.core.model.PaywallSource
import com.jedy.appcleaner.uninstaller.core.model.UsageAccessTrigger
import com.jedy.appcleaner.uninstaller.core.ui.component.LanguagePickerSheet
import com.jedy.appcleaner.uninstaller.core.ui.component.ProBadge
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens
import com.jedy.appcleaner.uninstaller.data.billing.BillingLinks
import com.jedy.appcleaner.uninstaller.data.billing.openExternalUrl
import com.jedy.appcleaner.uninstaller.data.billing.startActivitySafely
import com.jedy.appcleaner.uninstaller.data.prefs.AppPreferences
import com.jedy.appcleaner.uninstaller.data.prefs.SizeDisplay
import com.jedy.appcleaner.uninstaller.data.prefs.ThemeMode
import kotlinx.coroutines.launch

/**
 * CONTRACT (frozen signature). PRD §4 Screen 13, redesigned for v2 (Sept 2026).
 *
 * Layout, top to bottom: a pinned back arrow (its small title fades in once the big one scrolls
 * away), the big "Settings" title, the Pro hero, then soft grouped cards. Choices with two or
 * three values (theme, size mode, reminder threshold) are inline pill controls instead of dialogs,
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
    val bigTitleGonePx = with(LocalDensity.current) { 72.dp.toPx() }
    val showBarTitle by remember { derivedStateOf { scrollState.value > bigTitleGonePx } }

    Scaffold(
        containerColor = AppTheme.colors.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        snackbarHost = { SnackbarHost(snackbarHostState, Modifier.navigationBarsPadding()) },
        topBar = { PinnedBar(showTitle = showBarTitle, onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = Dimens.gutter),
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.displaySmall,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier
                    .padding(start = 4.dp, top = 4.dp, bottom = Dimens.gutter)
                    .semantics { heading() },
            )

            if (state.isPremium) {
                ProActiveCard(
                    hasStoreSubscription = state.hasStoreSubscription,
                    onManage = { openUrl(viewModel.manageSubscriptionUrl(), null) },
                )
            } else {
                ProHeroCard(onUpgrade = { onOpenPaywall(PaywallSource.SETTINGS) })
            }

            PreferencesGroup(
                state = state,
                language = language,
                onOpenLanguage = { languageSheetVisible = true },
                onThemeSelected = viewModel::setThemeMode,
                onSizeSelected = { value ->
                    if (value == SizeDisplay.TOTAL && !state.isPremium) onOpenPaywall(PaywallSource.SETTINGS)
                    else viewModel.setSizeDisplay(value)
                },
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
                    else onOpenPaywall(PaywallSource.REMINDER_TOGGLE)
                },
                onAllowNotifications = {
                    if (!context.startActivitySafely(viewModel.notificationSettingsIntent())) showLinkError()
                },
                onAllowUsageAccess = { onOpenUsageAccess(UsageAccessTrigger.SETTINGS) },
            )

            SettingsGroup(title = stringResource(R.string.settings_section_subscription)) {
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
            // Edge to edge: the list scrolls behind the gesture bar and ends just above it.
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
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

/** Back arrow on a soft round button; the compact title appears only once the big one is gone. */
@Composable
private fun PinnedBar(showTitle: Boolean, onBack: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .statusBarsPadding()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(horizontal = Dimens.gutterSmall, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(colors.surface)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                tint = colors.textPrimary,
            )
        }
        AnimatedVisibility(visible = showTitle, enter = fadeIn(), exit = fadeOut()) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
                modifier = Modifier.padding(start = 14.dp),
            )
        }
    }
}

@Composable
private fun PreferencesGroup(
    state: SettingsUiState,
    language: AppLanguage,
    onOpenLanguage: () -> Unit,
    onThemeSelected: (ThemeMode) -> Unit,
    onSizeSelected: (SizeDisplay) -> Unit,
    onUsageAccess: () -> Unit,
    onRate: () -> Unit,
) {
    SettingsGroup(title = stringResource(R.string.settings_section_preferences)) {
        SettingsRow(
            icon = Icons.Rounded.Language,
            title = stringResource(R.string.settings_language),
            value = language.nativeName,
            onClick = onOpenLanguage,
        )
        SegmentedSettingRow(
            icon = Icons.Rounded.DarkMode,
            title = stringResource(R.string.settings_theme),
            segments = ThemeMode.entries.map { Segment(stringResource(it.labelRes())) },
            selectedIndex = ThemeMode.entries.indexOf(state.themeMode),
            onSelect = { onThemeSelected(ThemeMode.entries[it]) },
        )
        SegmentedSettingRow(
            icon = Icons.Rounded.SdStorage,
            title = stringResource(R.string.settings_size_display),
            subtitle = if (state.sizeDisplay == SizeDisplay.TOTAL && state.isPremium && !state.usageAccessGranted) {
                stringResource(R.string.settings_size_needs_access)
            } else {
                null
            },
            segments = SizeDisplay.entries.map {
                Segment(stringResource(it.labelRes()), locked = it == SizeDisplay.TOTAL && !state.isPremium)
            },
            selectedIndex = SizeDisplay.entries.indexOf(state.sizeDisplay),
            onSelect = { onSizeSelected(SizeDisplay.entries[it]) },
        )
        SettingsRow(
            icon = Icons.Rounded.QueryStats,
            title = stringResource(R.string.settings_usage_access),
            subtitle = stringResource(R.string.settings_usage_access_body),
            onClick = onUsageAccess,
            trailing = { UsageAccessStatus(granted = state.usageAccessGranted) },
            showChevron = false,
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
            titleBadge = if (locked) ({ ProBadge() }) else null,
        )
        val thresholds = AppPreferences.UNUSED_THRESHOLDS
        SegmentedSettingRow(
            icon = Icons.Rounded.Timelapse,
            title = stringResource(R.string.settings_reminders_threshold),
            segments = thresholds.map { Segment(pluralStringResource(R.plurals.settings_threshold_days, it, it)) },
            // Nothing looks selected for a free user: the setting is not theirs yet.
            selectedIndex = if (locked) -1 else thresholds.indexOf(state.thresholdDays),
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

/** "App Cleaner 1.0 · Privacy · Terms": everything the old About section held, in one line. */
@Composable
private fun SettingsFooter(onPrivacy: () -> Unit, onTerms: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 36.dp, bottom = Dimens.gutter),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.settings_footer_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
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
            // Keeps a ~40dp touch target around small text.
            .padding(horizontal = 6.dp, vertical = 11.dp),
    )
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}

private fun SizeDisplay.labelRes(): Int = when (this) {
    SizeDisplay.APK -> R.string.settings_size_apk_short
    SizeDisplay.TOTAL -> R.string.settings_size_total_short
}

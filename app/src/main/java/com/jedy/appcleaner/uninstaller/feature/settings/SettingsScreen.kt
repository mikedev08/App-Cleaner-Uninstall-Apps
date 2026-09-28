package com.jedy.appcleaner.uninstaller.feature.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.StarRate
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import com.jedy.appcleaner.uninstaller.core.ui.component.PremiumCrown
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens
import com.jedy.appcleaner.uninstaller.data.billing.BillingLinks
import com.jedy.appcleaner.uninstaller.data.billing.openExternalUrl
import com.jedy.appcleaner.uninstaller.data.billing.startActivitySafely
import com.jedy.appcleaner.uninstaller.data.prefs.AppPreferences
import com.jedy.appcleaner.uninstaller.data.prefs.SizeDisplay
import com.jedy.appcleaner.uninstaller.data.prefs.ThemeMode
import kotlinx.coroutines.launch

private enum class SettingsDialog { THEME, SIZE }

/**
 * CONTRACT (frozen signature). PRD §4 Screen 13.
 *
 * Premium rows stay visible to free users with a gold crown and open the paywall when tapped:
 * showing what premium adds is the sale, hiding it would not be. Usage Access and notification
 * state are re-read on every resume, because both are changed in system Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    var dialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }

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

    Scaffold(
        containerColor = AppTheme.colors.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        snackbarHost = { SnackbarHost(snackbarHostState, Modifier.navigationBarsPadding()) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppTheme.colors.background,
                    scrolledContainerColor = AppTheme.colors.background,
                    titleContentColor = AppTheme.colors.textPrimary,
                    navigationIconContentColor = AppTheme.colors.textPrimary,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.gutter),
        ) {
            PremiumCard(
                state = state,
                onUpgrade = { onOpenPaywall(PaywallSource.SETTINGS) },
                onManage = { openUrl(viewModel.manageSubscriptionUrl(), null) },
            )

            SectionTitle(stringResource(R.string.settings_section_general))
            SettingsGroup {
                SettingsRow(
                    icon = Icons.Rounded.Language,
                    title = stringResource(R.string.settings_language),
                    value = language.nativeName,
                    onClick = { languageSheetVisible = true },
                )
                GroupDivider()
                SettingsRow(
                    icon = Icons.Rounded.DarkMode,
                    title = stringResource(R.string.settings_theme),
                    value = stringResource(state.themeMode.labelRes()),
                    onClick = { dialog = SettingsDialog.THEME },
                )
                GroupDivider()
                SettingsRow(
                    icon = Icons.Rounded.SdStorage,
                    title = stringResource(R.string.settings_size_display),
                    value = stringResource(state.sizeDisplay.shortLabelRes()),
                    onClick = { dialog = SettingsDialog.SIZE },
                )
                GroupDivider()
                SettingsRow(
                    icon = Icons.Rounded.QueryStats,
                    title = stringResource(R.string.settings_usage_access),
                    subtitle = stringResource(R.string.settings_usage_access_body),
                    onClick = {
                        if (state.usageAccessGranted) {
                            if (!context.startActivitySafely(viewModel.usageAccessSettingsIntent())) showLinkError()
                        } else {
                            onOpenUsageAccess(UsageAccessTrigger.SETTINGS)
                        }
                    },
                    trailing = { UsageAccessStatus(granted = state.usageAccessGranted) },
                    showChevron = false,
                )
            }

            SectionTitle(stringResource(R.string.settings_section_reminders), premium = !state.isPremium)
            SettingsGroup {
                SettingsSwitchRow(
                    icon = Icons.Rounded.NotificationsActive,
                    title = stringResource(R.string.settings_reminders_toggle),
                    subtitle = stringResource(R.string.settings_reminders_body),
                    checked = state.isPremium && state.remindersEnabled,
                    locked = !state.isPremium,
                    onCheckedChange = { enabled ->
                        if (state.isPremium) viewModel.setRemindersEnabled(enabled)
                        else onOpenPaywall(PaywallSource.REMINDER_TOGGLE)
                    },
                )
                GroupDivider()
                ThresholdRow(
                    selectedDays = state.thresholdDays,
                    locked = !state.isPremium,
                    onSelect = { days ->
                        if (state.isPremium) viewModel.setUnusedThresholdDays(days)
                        else onOpenPaywall(PaywallSource.REMINDER_TOGGLE)
                    },
                )
                if (!state.notificationsEnabled) {
                    GroupDivider()
                    NoticeRow(
                        icon = Icons.Rounded.NotificationsOff,
                        text = stringResource(R.string.settings_notifications_off),
                        action = stringResource(R.string.settings_notifications_allow),
                        onAction = {
                            if (!context.startActivitySafely(viewModel.notificationSettingsIntent())) showLinkError()
                        },
                    )
                }
                if (state.isPremium && state.remindersEnabled && !state.usageAccessGranted) {
                    GroupDivider()
                    NoticeRow(
                        icon = Icons.Rounded.QueryStats,
                        text = stringResource(R.string.settings_reminders_needs_access),
                        action = stringResource(R.string.settings_usage_access_allow),
                        onAction = { onOpenUsageAccess(UsageAccessTrigger.SETTINGS) },
                    )
                }
            }

            SectionTitle(stringResource(R.string.settings_section_subscription))
            SettingsGroup {
                SettingsRow(
                    icon = Icons.Rounded.ManageAccounts,
                    title = stringResource(R.string.settings_manage_subscription),
                    onClick = { openUrl(viewModel.manageSubscriptionUrl(), null) },
                )
                GroupDivider()
                SettingsRow(
                    icon = Icons.Rounded.Restore,
                    title = stringResource(R.string.settings_restore),
                    onClick = viewModel::restore,
                    enabled = !state.restoring,
                    trailing = if (state.restoring) {
                        {
                            CircularProgressIndicator(
                                color = AppTheme.colors.teal,
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

            SectionTitle(stringResource(R.string.settings_section_about))
            SettingsGroup {
                SettingsRow(
                    icon = Icons.Rounded.PrivacyTip,
                    title = stringResource(R.string.settings_privacy),
                    onClick = { openUrl(BillingLinks.PRIVACY_URL, null) },
                )
                GroupDivider()
                SettingsRow(
                    icon = Icons.Rounded.Description,
                    title = stringResource(R.string.settings_terms),
                    onClick = { openUrl(BillingLinks.TERMS_URL, null) },
                )
                GroupDivider()
                SettingsRow(
                    icon = Icons.Rounded.StarRate,
                    title = stringResource(R.string.settings_rate),
                    onClick = {
                        openUrl(
                            BillingLinks.playListingMarket(context.packageName),
                            BillingLinks.playListingWeb(context.packageName),
                        )
                    },
                )
                GroupDivider()
                SettingsRow(
                    icon = Icons.Rounded.Info,
                    title = stringResource(R.string.settings_version),
                    value = BuildConfig.VERSION_NAME,
                    onClick = null,
                    showChevron = false,
                )
            }

            if (BuildConfig.DEBUG) {
                SectionTitle(stringResource(R.string.settings_section_debug))
                SettingsGroup {
                    SettingsSwitchRow(
                        icon = Icons.Rounded.BugReport,
                        title = stringResource(R.string.settings_debug_force_premium),
                        subtitle = null,
                        checked = state.debugForcePremium,
                        locked = false,
                        onCheckedChange = viewModel::setDebugForcePremium,
                    )
                    GroupDivider()
                    SettingsRow(
                        icon = Icons.Rounded.Cloud,
                        title = stringResource(R.string.settings_debug_revenuecat),
                        value = stringResource(
                            if (state.billingConfigured) R.string.settings_debug_configured
                            else R.string.settings_debug_not_configured
                        ),
                        onClick = null,
                        showChevron = false,
                    )
                }
            }

            Spacer(
                Modifier
                    .navigationBarsPadding()
                    .height(Dimens.gutterLarge)
            )
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

    when (dialog) {
        SettingsDialog.THEME -> ChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = ThemeMode.entries.map { mode ->
                ChoiceOption(label = stringResource(mode.labelRes()), selected = mode == state.themeMode)
            },
            onSelect = { index ->
                viewModel.setThemeMode(ThemeMode.entries[index])
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        SettingsDialog.SIZE -> ChoiceDialog(
            title = stringResource(R.string.settings_size_display),
            options = SizeDisplay.entries.map { value ->
                ChoiceOption(
                    label = stringResource(value.labelRes()),
                    selected = value == state.sizeDisplay,
                    premium = value == SizeDisplay.TOTAL && !state.isPremium,
                    note = if (value == SizeDisplay.TOTAL && state.isPremium && !state.usageAccessGranted) {
                        stringResource(R.string.settings_size_needs_access)
                    } else {
                        null
                    },
                )
            },
            onSelect = { index ->
                val value = SizeDisplay.entries[index]
                dialog = null
                if (value == SizeDisplay.TOTAL && !state.isPremium) {
                    onOpenPaywall(PaywallSource.SETTINGS)
                } else {
                    viewModel.setSizeDisplay(value)
                }
            },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}

private fun SizeDisplay.labelRes(): Int = when (this) {
    SizeDisplay.APK -> R.string.settings_size_apk
    SizeDisplay.TOTAL -> R.string.settings_size_total
}

private fun SizeDisplay.shortLabelRes(): Int = when (this) {
    SizeDisplay.APK -> R.string.settings_size_apk
    SizeDisplay.TOTAL -> R.string.settings_size_total_short
}

// ---------------------------------------------------------------- premium card

@Composable
private fun PremiumCard(state: SettingsUiState, onUpgrade: () -> Unit, onManage: () -> Unit) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(Dimens.cardRadius)
    Column(
        modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(Dimens.hairline, colors.border, shape)
            .then(if (state.isPremium) Modifier else Modifier.clickable(onClick = onUpgrade))
            .padding(Dimens.gutter),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(colors.premiumGoldSurface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (state.isPremium) Icons.Rounded.CheckCircle else Icons.Rounded.WorkspacePremium,
                    contentDescription = null,
                    tint = colors.premiumGold,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.width(Dimens.gutterSmall))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        if (state.isPremium) R.string.settings_premium_active_title
                        else R.string.settings_premium_upgrade_title
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                )
                Text(
                    text = stringResource(
                        when {
                            !state.isPremium -> R.string.settings_premium_upgrade_body
                            !state.hasStoreSubscription -> R.string.settings_premium_active_debug
                            else -> R.string.settings_premium_active_body
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (!state.isPremium) {
            Button(
                onClick = onUpgrade,
                modifier = Modifier
                    .padding(top = Dimens.gutterSmall)
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(Dimens.controlRadius),
                colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = colors.onTeal),
            ) {
                Text(stringResource(R.string.settings_premium_upgrade_cta), style = MaterialTheme.typography.labelLarge)
            }
        } else if (state.hasStoreSubscription) {
            OutlinedButton(
                onClick = onManage,
                modifier = Modifier
                    .padding(top = Dimens.gutterSmall)
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(Dimens.controlRadius),
                border = BorderStroke(Dimens.hairline, colors.teal),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.teal),
            ) {
                Text(stringResource(R.string.settings_manage_subscription), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

// ---------------------------------------------------------------- building blocks

@Composable
private fun SectionTitle(text: String, premium: Boolean = false) {
    Row(
        modifier = Modifier.padding(start = 4.dp, top = Dimens.gutterLarge, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = AppTheme.colors.textSecondary,
        )
        if (premium) {
            Spacer(Modifier.width(6.dp))
            PremiumCrown(size = 16.dp)
        }
    }
}

/** One bordered card per section, so the rows have an edge to sit against (house style). */
@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(Dimens.cardRadius)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppTheme.colors.surface)
            .border(Dimens.hairline, AppTheme.colors.border, shape),
    ) { content() }
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(
        color = AppTheme.colors.border,
        thickness = Dimens.hairline,
        modifier = Modifier.padding(start = 64.dp),
    )
}

@Composable
private fun RowIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(AppTheme.colors.tealSurface),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = AppTheme.colors.teal, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    onClick: (() -> Unit)?,
    subtitle: String? = null,
    value: String? = null,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    showChevron: Boolean = onClick != null,
) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.gutterSmall),
    ) {
        RowIcon(icon)
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (value != null) {
            Text(text = value, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        }
        trailing?.invoke()
        if (showChevron) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    locked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.gutterSmall),
    ) {
        RowIcon(icon)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (locked) {
                    Spacer(Modifier.width(6.dp))
                    PremiumCrown(size = 16.dp)
                }
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onTeal,
                checkedTrackColor = colors.teal,
                uncheckedThumbColor = colors.textSecondary,
                uncheckedTrackColor = colors.surfaceMuted,
                uncheckedBorderColor = colors.border,
            ),
        )
    }
}

@Composable
private fun ThresholdRow(selectedDays: Int, locked: Boolean, onSelect: (Int) -> Unit) {
    val colors = AppTheme.colors
    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowIcon(Icons.Rounded.Timelapse)
            Spacer(Modifier.width(Dimens.gutterSmall))
            Text(
                text = stringResource(R.string.settings_reminders_threshold),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.padding(start = 48.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppPreferences.UNUSED_THRESHOLDS.forEach { days ->
                FilterChip(
                    selected = !locked && days == selectedDays,
                    onClick = { onSelect(days) },
                    label = { Text(pluralStringResource(R.plurals.settings_threshold_days, days, days)) },
                    shape = RoundedCornerShape(Dimens.chipRadius),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = colors.background,
                        labelColor = if (locked) colors.textSecondary else colors.textPrimary,
                        selectedContainerColor = colors.tealSurface,
                        selectedLabelColor = colors.teal,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = !locked && days == selectedDays,
                        borderColor = colors.border,
                        selectedBorderColor = colors.teal,
                    ),
                )
            }
        }
    }
}

@Composable
private fun UsageAccessStatus(granted: Boolean) {
    val colors = AppTheme.colors
    if (granted) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = colors.teal, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.settings_usage_access_granted),
                style = MaterialTheme.typography.labelMedium,
                color = colors.teal,
            )
        }
    } else {
        Text(
            text = stringResource(R.string.settings_usage_access_allow),
            style = MaterialTheme.typography.labelLarge,
            color = colors.onTeal,
            modifier = Modifier
                .clip(RoundedCornerShape(Dimens.chipRadius))
                .background(colors.teal)
                .padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

/** An inline problem with its fix, e.g. notifications switched off for the app. */
@Composable
private fun NoticeRow(icon: ImageVector, text: String, action: String, onAction: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.gutterSmall),
    ) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = onAction,
            colors = ButtonDefaults.textButtonColors(contentColor = colors.teal),
        ) {
            Text(action, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private data class ChoiceOption(
    val label: String,
    val selected: Boolean,
    val premium: Boolean = false,
    val note: String? = null,
)

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<ChoiceOption>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AppTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        titleContentColor = colors.textPrimary,
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup()) {
                options.forEachIndexed { index, option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Dimens.controlRadius))
                            .selectable(selected = option.selected, role = Role.RadioButton) { onSelect(index) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = option.selected,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = colors.teal,
                                unselectedColor = colors.textSecondary,
                            ),
                        )
                        Spacer(Modifier.width(Dimens.gutterSmall))
                        Column(Modifier.weight(1f)) {
                            Text(option.label, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
                            if (option.note != null) {
                                Text(option.note, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                            }
                        }
                        if (option.premium) PremiumCrown(size = 18.dp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = colors.teal)) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}

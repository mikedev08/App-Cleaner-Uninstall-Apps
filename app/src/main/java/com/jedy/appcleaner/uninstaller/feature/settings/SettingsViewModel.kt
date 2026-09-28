package com.jedy.appcleaner.uninstaller.feature.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jedy.appcleaner.uninstaller.BuildConfig
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.analytics.Analytics
import com.jedy.appcleaner.uninstaller.core.analytics.AnalyticsEvent
import com.jedy.appcleaner.uninstaller.data.billing.BillingRepository
import com.jedy.appcleaner.uninstaller.data.billing.Premium
import com.jedy.appcleaner.uninstaller.data.billing.RestoreOutcome
import com.jedy.appcleaner.uninstaller.data.prefs.AppPreferences
import com.jedy.appcleaner.uninstaller.data.prefs.SizeDisplay
import com.jedy.appcleaner.uninstaller.data.prefs.ThemeMode
import com.jedy.appcleaner.uninstaller.data.usage.UsageAccess
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val isPremium: Boolean = false,
    /** A real store entitlement, as opposed to the debug override: gates "Manage subscription". */
    val hasStoreSubscription: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val sizeDisplay: SizeDisplay = SizeDisplay.APK,
    val remindersEnabled: Boolean = false,
    val thresholdDays: Int = AppPreferences.DEFAULT_UNUSED_DAYS,
    val usageAccessGranted: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val debugForcePremium: Boolean = false,
    val billingConfigured: Boolean = false,
    val restoring: Boolean = false,
)

sealed interface SettingsEvent {
    data class Message(@param:StringRes val messageRes: Int) : SettingsEvent
}

private data class PrefsSnapshot(
    val themeMode: ThemeMode,
    val sizeDisplay: SizeDisplay,
    val remindersEnabled: Boolean,
    val thresholdDays: Int,
    val debugForcePremium: Boolean,
)

private data class StatusSnapshot(
    val isPremium: Boolean,
    val hasStoreSubscription: Boolean,
    val usageAccessGranted: Boolean,
    val notificationsEnabled: Boolean,
    val restoring: Boolean,
)

/**
 * Screen 13 (PRD §4). Settings only *writes* preferences: the theme is observed by
 * MainViewModel, and the reminder switch and threshold are observed by the Insights feature,
 * which schedules or cancels its workers. Premium gates are enforced here as well as in the UI,
 * so a stray call can never switch a free user onto a premium setting.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val preferences: AppPreferences,
    private val premium: Premium,
    private val billing: BillingRepository,
    private val usageAccess: UsageAccess,
    private val analytics: Analytics,
) : ViewModel() {

    private val notificationsEnabled = MutableStateFlow(areNotificationsEnabled())
    private val restoring = MutableStateFlow(false)

    private val prefs = combine(
        preferences.themeMode,
        preferences.sizeDisplay,
        preferences.remindersEnabled,
        preferences.unusedThresholdDays,
        preferences.debugForcePremium,
    ) { theme, size, reminders, days, forced -> PrefsSnapshot(theme, size, reminders, days, forced) }

    private val status = combine(
        premium.isPremium,
        billing.entitlementActive,
        usageAccess.isGranted,
        notificationsEnabled,
        restoring,
    ) { isPremium, store, access, notifications, restoring ->
        StatusSnapshot(isPremium, store, access, notifications, restoring)
    }

    val uiState: StateFlow<SettingsUiState> = combine(prefs, status) { p, s ->
        SettingsUiState(
            isPremium = s.isPremium,
            hasStoreSubscription = s.hasStoreSubscription,
            themeMode = p.themeMode,
            sizeDisplay = p.sizeDisplay,
            remindersEnabled = p.remindersEnabled,
            thresholdDays = p.thresholdDays,
            usageAccessGranted = s.usageAccessGranted,
            notificationsEnabled = s.notificationsEnabled,
            debugForcePremium = p.debugForcePremium,
            billingConfigured = billing.isConfigured,
            restoring = s.restoring,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(
            isPremium = premium.isPremium.value,
            hasStoreSubscription = billing.entitlementActive.value,
            usageAccessGranted = usageAccess.isGranted.value,
            notificationsEnabled = notificationsEnabled.value,
            billingConfigured = billing.isConfigured,
        ),
    )

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = _events.receiveAsFlow()

    /** Both can change in system Settings while we are in the background (PRD §6 item 12). */
    fun onResume() {
        usageAccess.recheck()
        notificationsEnabled.value = areNotificationsEnabled()
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun setSizeDisplay(value: SizeDisplay) {
        if (value == SizeDisplay.TOTAL && !premium.isPremium.value) return
        viewModelScope.launch { preferences.setSizeDisplay(value) }
    }

    fun setRemindersEnabled(enabled: Boolean) {
        if (enabled && !premium.isPremium.value) return
        viewModelScope.launch {
            preferences.setRemindersEnabled(enabled)
            // A deliberate choice here supersedes the one-time "switch on at first unlock" default.
            preferences.setRemindersInitialised(true)
        }
    }

    fun setUnusedThresholdDays(days: Int) {
        if (!premium.isPremium.value || days !in AppPreferences.UNUSED_THRESHOLDS) return
        viewModelScope.launch { preferences.setUnusedThresholdDays(days) }
    }

    fun setDebugForcePremium(enabled: Boolean) {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch { preferences.setDebugForcePremium(enabled) }
    }

    fun restore() {
        if (restoring.value) return
        viewModelScope.launch {
            restoring.value = true
            val outcome = billing.restore()
            restoring.value = false
            val message = when (outcome) {
                RestoreOutcome.Restored -> {
                    analytics.log(AnalyticsEvent.PurchaseRestored(restoredPremium = true))
                    R.string.billing_restore_success
                }
                RestoreOutcome.NothingToRestore -> {
                    analytics.log(AnalyticsEvent.PurchaseRestored(restoredPremium = false))
                    R.string.billing_restore_nothing
                }
                is RestoreOutcome.Failed ->
                    if (outcome.network) R.string.billing_network_error else R.string.billing_restore_failed
                RestoreOutcome.NotConfigured -> R.string.billing_not_available
            }
            _events.send(SettingsEvent.Message(message))
        }
    }

    fun manageSubscriptionUrl(): String = billing.manageSubscriptionUrl()

    /** The system usage-access page, for a user who wants to revoke what they granted. */
    fun usageAccessSettingsIntent(): Intent = usageAccess.settingsIntent().intent

    fun notificationSettingsIntent(): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    private fun areNotificationsEnabled(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()
}
